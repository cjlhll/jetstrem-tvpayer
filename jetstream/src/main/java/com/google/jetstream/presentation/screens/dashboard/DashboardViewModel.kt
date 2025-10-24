package com.google.jetstream.presentation.screens.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.jetstream.data.database.dao.ResourceDirectoryDao
import com.google.jetstream.data.database.dao.WebDavConfigDao
import com.google.jetstream.data.database.dao.ScrapedItemDao
import com.google.jetstream.data.database.dao.EpisodesCacheDao
import com.google.jetstream.data.database.entities.WebDavConfigEntity
import com.google.jetstream.data.database.entities.ScrapedItemEntity
import com.google.jetstream.data.webdav.WebDavResult
import com.google.jetstream.data.webdav.WebDavService
import com.thegrizzlylabs.sardineandroid.DavResource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import javax.inject.Inject
import com.google.jetstream.data.entities.Movie
import com.google.jetstream.data.repositories.ScrapedMoviesStore
import com.google.jetstream.data.repositories.ScrapedTvStore
import com.google.jetstream.data.remote.TmdbService
import com.google.jetstream.data.entities.MovieCast

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val resourceDirectoryDao: ResourceDirectoryDao,
    private val webDavConfigDao: WebDavConfigDao,
    private val webDavService: WebDavService,
    private val scrapedMoviesStore: ScrapedMoviesStore,
    private val scrapedTvStore: ScrapedTvStore,
    private val scrapedItemDao: ScrapedItemDao,
    private val episodesCacheDao: EpisodesCacheDao,
    private val recentlyWatchedRepository: com.google.jetstream.data.repositories.RecentlyWatchedRepository,
) : ViewModel() {

    private val _isRefreshing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isRefreshing: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRefreshing

    init {

        // 启动时优先从数据库加载已刮削的数据
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrapedItemDao.getAllByType("movie").collect { list ->
                    val movies = list.map {
                        Movie(
                            id = it.id,
                            videoUri = "",
                            subtitleUri = null,
                            posterUri = it.posterUri,
                            name = it.title,
                            description = it.description,
                            releaseDate = it.releaseDate,
                            rating = it.rating,
                        )
                    }
                    scrapedMoviesStore.setMovies(movies)
                }
            } catch (_: Exception) {}
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrapedItemDao.getAllByType("tv").collect { list ->
                    val tv = list.map {
                        Movie(
                            id = it.id,
                            videoUri = "",
                            subtitleUri = null,
                            posterUri = it.posterUri,
                            name = it.title,
                            description = it.description,
                            releaseDate = it.releaseDate,
                            rating = it.rating,
                        )
                    }
                    scrapedTvStore.setShows(tv)
                }
            } catch (_: Exception) {}
        }
    }

    fun refreshAndScrape() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // 清除所有剧集缓存
                try {
                    episodesCacheDao.deleteAll()
                    Log.i(TAG, "已清除所有剧集缓存")
                } catch (e: Exception) {
                    Log.e(TAG, "清除剧集缓存失败: ${e.message}", e)
                }
                
                // 获取所有现有资源目录
                val directories = resourceDirectoryDao.getAllDirectories().first()
                
                // 智能清理：清空将要刷新的配置的数据，确保数据完全一致
                try {
                    if (directories.isEmpty()) {
                        // 如果没有任何资源目录，清空所有数据
                        scrapedItemDao.clearAll()
                        Log.i(TAG, "无资源目录，已清空所有刮削数据")
                    } else {
                        // 获取所有现存资源目录的webDavConfigId
                        val existingConfigIds = directories.map { it.webDavConfigId }.distinct()
                        // 先删除不在此列表中的数据（已删除的配置）
                        scrapedItemDao.deleteByConfigIdsNotIn(existingConfigIds)
                        // 再清空将要刷新的configId的所有数据，确保删除的资源目录的独有数据被清理
                        scrapedItemDao.deleteByConfigIds(existingConfigIds)
                        // 同时删除configId为null的旧数据
                        scrapedItemDao.deleteWithNullConfigId()
                        Log.i(TAG, "已清空待刷新配置的数据，将重新扫描: $existingConfigIds")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "清理刮削数据失败: ${e.message}", e)
                }
                
                // 使用线程安全的集合
                val aggregated = kotlinx.coroutines.sync.Mutex()
                val aggregatedMovies = mutableListOf<Movie>()
                val aggregatedTv = mutableListOf<Movie>()
                val visited = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
                
                // 并行处理所有目录
                kotlinx.coroutines.coroutineScope {
                    directories.map { dir ->
                        launch(Dispatchers.IO) {
                            try {
                                val config: WebDavConfigEntity = webDavConfigDao.getConfigById(dir.webDavConfigId)
                                    ?: return@launch
                                // 设置 WebDAV 客户端配置
                                webDavService.setConfig(
                                    com.google.jetstream.data.webdav.WebDavConfig(
                                        serverUrl = config.serverUrl,
                                        username = config.username,
                                        password = config.password,
                                        displayName = config.displayName,
                                        isEnabled = true
                                    )
                                )

                                val startPath = dir.path
                                val startNorm = startPath.trim('/').replace(Regex("/+"), "/")
                                Log.i(TAG, "开始扫描：${config.displayName} -> ${if (startNorm.isEmpty()) "/" else startNorm}")
                                
                                // 为每个目录创建临时列表
                                val dirMovies = mutableListOf<Movie>()
                                val dirTv = mutableListOf<Movie>()
                                
                                // 遍历收集电影与电视剧（去重递归），传入当前的WebDAV配置ID
                                traverseAndScrape(startNorm, dirMovies, dirTv, visited, config.id)
                                
                                // 合并到总列表（需要加锁）
                                aggregated.withLock {
                                    aggregatedMovies.addAll(dirMovies)
                                    aggregatedTv.addAll(dirTv)
                                }
                                
                                Log.i(TAG, "目录扫描完成：${config.displayName}，电影=${dirMovies.size}，电视剧=${dirTv.size}")
                            } catch (e: Exception) {
                                Log.e(TAG, "处理目录失败: ${dir.path}, ${e.message}", e)
                            }
                        }
                    }.forEach { it.join() }
                }
                
                // 去重并更新首页 Movies/TV 模块数据源，并持久化到数据库
                val moviesDistinct = aggregatedMovies.distinctBy { it.id }
                val tvDistinct = aggregatedTv.distinctBy { it.id }
                scrapedMoviesStore.setMovies(moviesDistinct)
                scrapedTvStore.setShows(tvDistinct)
                // 持久化 - 现在使用增强的实体数据
                val toEntities = mutableListOf<ScrapedItemEntity>()
                toEntities += moviesDistinct.mapNotNull { movie ->
                    // 从Movie对象中提取详情信息（如果有的话）
                    createScrapedItemEntity(movie, "movie")
                }
                toEntities += tvDistinct.mapNotNull { tv ->
                    createScrapedItemEntity(tv, "tv")
                }
                scrapedItemDao.upsertAll(toEntities)
                Log.i(TAG, "扫描完成并已入库：电影=${moviesDistinct.size}，电视剧=${tvDistinct.size}")
                
                // 清理不存在于刮削数据中的最近观看记录
                try {
                    recentlyWatchedRepository.cleanOrphanedRecords()
                    Log.i(TAG, "已清理孤立的最近观看记录")
                } catch (e: Exception) {
                    Log.e(TAG, "清理最近观看记录失败: ${e.message}", e)
                }

            } catch (e: Exception) {
                val msg = e.localizedMessage ?: e.toString()
                Log.e(TAG, "刷新/刮削失败: $msg", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun traverseAndScrape(path: String, aggregatedMovies: MutableList<Movie>, aggregatedTv: MutableList<Movie>, visited: MutableSet<String>, currentWebDavConfigId: String) {
        val norm = path.trim('/').replace(Regex("/+"), "/")
        if (!visited.add(norm)) return
        when (val res = webDavService.listDirectory(path)) {
            is WebDavResult.Success -> {
                val items = res.data.filter { it.name.isNotBlank() }
                val files = items.filter { !it.isDirectory }
                val folders = items.filter { it.isDirectory }

                val fileNames = files.map { it.name }
                val looksLikeTv = looksLikeTvSeries(fileNames)

                if (looksLikeTv && files.isNotEmpty()) {
                    // 该目录看起来是剧集，使用目录名作为剧名刮削一次，加入 aggregatedTv
                    val folderTitleRaw = currentDirName(path)
                    val cleanedFolderTitle = extractTitleFromName(folderTitleRaw)
                    val folderCandidates = buildCandidates(cleanedFolderTitle)
                    val folderYear = extractYear(folderTitleRaw)
                    val english = isEnglishTitle(folderTitleRaw)
                    var matched: TmdbApi.SearchItem? = null
                    for (q in folderCandidates) {
                        val extras = mutableMapOf<String, String>()
                        if (folderYear != null) extras["first_air_date_year"] = folderYear
                        if (english) extras["region"] = "US"
                        val r = TmdbApi.search("/3/search/tv", q, extras)
                        matched = r?.results?.firstOrNull()
                        if (matched != null) break
                    }
                    matched?.let { m ->
                        // 检测季结构，传入当前的WebDAV配置ID
                        val seasons = detectSeasonsInDirectory(path, folders, files, currentWebDavConfigId)
                        val localEpisodeCount = if (seasons.isNotEmpty()) {
                            seasons.sumOf { it.episodeCount }
                        } else {
                            files.size
                        }
                        // 获取完整的TMDB电视剧详情信息，同时传入WebDAV配置ID
                        val tvWithDetails = createTvWithFullDetails(m, "", localEpisodeCount, seasons, currentWebDavConfigId)
                        aggregatedTv.add(tvWithDetails)
                        Log.i(TAG, "剧集目录匹配: $folderTitleRaw -> ${m.name ?: m.title} (${m.id})，库中${localEpisodeCount}集，${seasons.size}季")
                    }
                } else {
                    // 并行处理所有文件的电影匹配
                    kotlinx.coroutines.coroutineScope {
                        val movieDeferred = files.map { file ->
                            async(Dispatchers.IO) {
                                try {
                                    val rawName = stripExtension(file.name)
                                    val cleaned = extractTitleFromName(rawName)
                                    val candidates = buildCandidates(cleaned)
                                    val year = extractYear(rawName)
                                    val english = isEnglishTitle(rawName)
                                    var matched: TmdbApi.SearchItem? = null
                                    for (q in candidates) {
                                        val extras = mutableMapOf("include_adult" to "false")
                                        if (year != null) extras["year"] = year
                                        if (english) extras["region"] = "US"
                                        val r = TmdbApi.search("/3/search/movie", q, extras)
                                        matched = r?.results?.firstOrNull()
                                        if (matched != null) {
                                            val base = webDavService.getCurrentConfig()?.getFormattedServerUrl()?.removeSuffix("/") ?: ""
                                            val rel = (if (path.isBlank()) file.name else "$path/${file.name}")
                                                .trim('/')
                                                .replace(Regex("/+"), "/")
                                            val fullUrl = if (base.isNotBlank()) "$base/$rel" else rel
                                            Log.i(TAG, "WebDAV 视频URL: $fullUrl")
                                            
                                            // 获取完整的TMDB详情信息，同时传入WebDAV配置ID
                                            val movieWithDetails = createMovieWithFullDetails(matched, fullUrl, currentWebDavConfigId)
                                            
                                            Log.i(TAG, "电影匹配: $q -> ${matched.title ?: matched.name} (${matched.id})")
                                            return@async movieWithDetails
                                        }
                                    }
                                    null
                                } catch (e: Exception) {
                                    Log.w(TAG, "处理文件失败: ${file.name}, ${e.message}")
                                    null
                                }
                            }
                        }
                        
                        movieDeferred.awaitAll().filterNotNull().forEach { movie ->
                            aggregatedMovies.add(movie)
                        }
                    }
                }

                // 并行递归处理子目录（过滤当前目录自身，避免 .../电视剧/电视剧 或 .../凡人修仙传/凡人修仙传）
                val currName = currentDirName(path)
                kotlinx.coroutines.coroutineScope {
                    folders.map { dir ->
                        launch(Dispatchers.IO) {
                            // DavResource.name 可能是全路径或带尾斜杠，这里只取末段作为子目录名
                            val childName = dir.name.substringAfterLast('/').removeSuffix("/")
                            if (childName.isBlank() || childName == currName) return@launch // 过滤当前目录自身
                            val next = (if (path.isBlank()) childName else "$path/$childName").trim('/').replace(Regex("/+"), "/")
                            traverseAndScrape(next, aggregatedMovies, aggregatedTv, visited, currentWebDavConfigId)
                        }
                    }.forEach { it.join() }
                }
            }
            is WebDavResult.Error -> Log.w(TAG, "列目录失败(${path}): ${res.message}")
            else -> Unit
        }
    }

    private fun currentDirName(path: String): String {
        return if (path.isBlank()) "" else path.substringAfterLast('/')
    }

    private fun stripExtension(name: String): String = name.replace(Regex("\\.[^.]+$"), "")

    private fun looksLikeTvSeries(fileNames: List<String>): Boolean {
        if (fileNames.size >= 2 && fileNames.count { isEpisodeFile(it) } >= 2) return true
        return false
    }
    private fun isEnglishTitle(s: String): Boolean {
        // 文件名含中文则判为中文，否则按英文处理
        return !Regex("[\\u4e00-\\u9fa5]").containsMatchIn(s)
    }


    private fun isEpisodeFile(name: String): Boolean {
        val n = name.lowercase()
        val sxe = Regex("(?i)s\\d{1,2}[ _.-]?e\\d{1,2}")
        // 识别常见的集数样式：前导 01/02/001、中文“第01集/第1集/第001集”、以及 E01/E1、EP01/EP1
        val leadingNum = Regex("^\\s*([0-9]{1,3})[ _.\\-]")
        val cnEpisode = Regex("第\\s*([0-9]{1,3})\\s*集")
        val eNum = Regex("(?i)\\b(e|ep)\\s*([0-9]{1,3})\\b")
        return sxe.containsMatchIn(n) || leadingNum.containsMatchIn(n) || cnEpisode.containsMatchIn(n) || eNum.containsMatchIn(n)
    }

    private fun extractYear(raw: String): String? {
        val m = Regex("(?<!\\d)(19|20)\\d{2}(?!\\d)").find(raw)
        return m?.value
    }

    private fun extractTitleFromName(raw: String): String {

        var s = raw
        // 去掉常见与命名无关的字段（分辨率、来源、编码、音轨、重制、语言标识、版本标识等）
        s = s.replace(
            Regex("(?i)\\b(4k|8k|720p|1080p|2160p|4320p|uhd|hdr|hdr10|hdr10\\+|dolby|vision|dv|web-?dl|webrip|bluray|blu-?ray|remux|rip|bd|bdremux|dvd|hdtv|h265|hevc|x265|h264|x264|av1|ddp?5\\.?1|ddp?7\\.?1|aac|dts|truehd|atmos|flac|mp3|10bit|8bit|60fps|120fps|fps|hdrip|cam|ts|tc|r5|gp[tx]hd|hq|proper|repack|intl|chs-?cht|chs|cht|big5|gb|cn|uncensored|extended|cut|director'?s|remastered|unrated|limited|multi|dual|3audio|2audio|imax|open-matte|omf|hc|sub|subbed|zh|eng|en|english|chinese|jpn|japanese)\\b")
        , " ")
        // 去掉特殊符号 . _ - () [] 等，转为空格
        s = s.replace(Regex("[._()\\[\\]-]+"), " ")
        // 合并多余空格
        s = s.replace(Regex("\\s+"), " ").trim()
        // 去掉单独的4位年份 token，避免年份参与名称候选
        s = s.split(Regex("\\s+")).filterNot { it.matches(Regex("^(19|20)\\d{2}$")) }.joinToString(" ")

        return s
    }

    private fun buildCandidates(cleaned: String): List<String> {
        val parts = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return emptyList()
        val cands = mutableListOf<String>()
        for (i in parts.size downTo 1) {
            cands.add(parts.take(i).joinToString(" "))
        }
        return cands
    }

    private suspend fun searchMovieOnTmdb(query: String) {
        buildCandidates(query).forEach { q ->
            val result = TmdbApi.search("/3/search/movie", q, mapOf("include_adult" to "false"))
            if (result != null && result.results.isNotEmpty()) {
                val r = result.results.first()
                Log.i(TAG, "电影匹配: ${q} -> ${r.title ?: r.name} (${r.id})")
                return
            }
        }
        Log.w(TAG, "电影未命中: ${query}")
    }

    private suspend fun searchTvOnTmdb(query: String) {
        buildCandidates(query).forEach { q ->
            val result = TmdbApi.search("/3/search/tv", q)
            if (result != null && result.results.isNotEmpty()) {
                val r = result.results.first()
                Log.i(TAG, "剧集匹配: ${q} -> ${r.name ?: r.title} (${r.id})")
                return
            }
        }
        Log.w(TAG, "剧集未命中: ${query}")
    }

    private fun TmdbApi.SearchItem.posterPathOrNull(): String? = this.posterPath

    /**
     * 根据TMDB搜索结果创建包含完整详情的Movie对象
     */
    private suspend fun createMovieWithFullDetails(searchItem: TmdbApi.SearchItem, videoUri: String, webDavConfigId: String): Movie {
        try {
            // 并行获取所有TMDB详情信息
            val detailsResult: com.google.jetstream.data.remote.TmdbService.MovieDetailsResponse?
            val creditsResult: com.google.jetstream.data.remote.TmdbService.CreditsResponse?
            val certificationResult: String
            val similarResult: List<com.google.jetstream.data.remote.TmdbService.SimilarItem>
            
            kotlinx.coroutines.coroutineScope {
                val detailsDeferred = async(Dispatchers.IO) { 
                    TmdbService.getMovieDetails(searchItem.id.toString()) 
                }
                val creditsDeferred = async(Dispatchers.IO) { 
                    TmdbService.getCredits(searchItem.id.toString()) 
                }
                val certificationDeferred = async(Dispatchers.IO) { 
                    TmdbService.getReleaseCertification(searchItem.id.toString()) ?: "PG-13" 
                }
                val similarDeferred = async(Dispatchers.IO) { 
                    TmdbService.getSimilar(searchItem.id.toString())?.results ?: emptyList() 
                }
                
                detailsResult = detailsDeferred.await()
                creditsResult = creditsDeferred.await()
                certificationResult = certificationDeferred.await()
                @Suppress("UNCHECKED_CAST")
                similarResult = similarDeferred.await() as List<com.google.jetstream.data.remote.TmdbService.SimilarItem>
            }
            
            // 使用显式的序列化器将各部分转换为 JsonElement，避免 Map<String, Any> 导致的序列化错误
            val fullDetailsJson = buildMap<String, kotlinx.serialization.json.JsonElement> {
                detailsResult?.let {
                    put(
                        "tmdb_details",
                        Json.encodeToJsonElement(
                            com.google.jetstream.data.remote.TmdbService.MovieDetailsResponse.serializer(),
                            it
                        )
                    )
                }
                creditsResult?.let {
                    put(
                        "tmdb_credits",
                        Json.encodeToJsonElement(
                            com.google.jetstream.data.remote.TmdbService.CreditsResponse.serializer(),
                            it
                        )
                    )
                }
                put("tmdb_certification", JsonPrimitive(certificationResult))
                put(
                    "tmdb_similar",
                    Json.encodeToJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(
                            com.google.jetstream.data.remote.TmdbService.SimilarItem.serializer()
                        ),
                        similarResult.take(10)
                    )
                )
                // 添加WebDAV配置ID
                put("webdav_config_id", JsonPrimitive(webDavConfigId))
            }
            
            return Movie(
                id = searchItem.id.toString(),
                videoUri = videoUri,
                subtitleUri = null,
                posterUri = searchItem.posterPathOrNull()?.let { "https://image.tmdb.org/t/p/w500$it" } ?: "",
                name = searchItem.title ?: searchItem.name ?: "",
                description = Json.encodeToString<Map<String, JsonElement>>(fullDetailsJson), // 临时存储详情信息
                releaseDate = searchItem.releaseDate,
                rating = searchItem.voteAverage
            )
        } catch (e: Exception) {
            Log.w(TAG, "获取TMDB详情失败: ${e.message}")
            // 如果获取详情失败，返回基础信息
            return Movie(
                id = searchItem.id.toString(),
                videoUri = videoUri,
                subtitleUri = null,
                posterUri = searchItem.posterPathOrNull()?.let { "https://image.tmdb.org/t/p/w500$it" } ?: "",
                name = searchItem.title ?: searchItem.name ?: "",
                description = searchItem.overview ?: "",
                releaseDate = searchItem.releaseDate,
                rating = searchItem.voteAverage
            )
        }
    }

    /**
     * 根据TMDB搜索结果创建包含完整详情的电视剧Movie对象
     */
    private suspend fun createTvWithFullDetails(searchItem: TmdbApi.SearchItem, videoUri: String, localEpisodeCount: Int = 0, seasons: List<com.google.jetstream.data.entities.TvSeason> = emptyList(), webDavConfigId: String): Movie {
        try {
            // 并行获取所有TMDB电视剧详情信息
            val detailsResult: com.google.jetstream.data.remote.TmdbService.TvDetailsResponse?
            val creditsResult: com.google.jetstream.data.remote.TmdbService.CreditsResponse?
            val contentRatingResult: String
            val similarResult: List<com.google.jetstream.data.remote.TmdbService.SimilarItem>
            
            kotlinx.coroutines.coroutineScope {
                val detailsDeferred = async(Dispatchers.IO) { 
                    TmdbService.getTvDetails(searchItem.id.toString()) 
                }
                val creditsDeferred = async(Dispatchers.IO) { 
                    TmdbService.getTvCredits(searchItem.id.toString()) 
                }
                val contentRatingDeferred = async(Dispatchers.IO) { 
                    TmdbService.getTvContentRating(searchItem.id.toString()) ?: "TV-14" 
                }
                val similarDeferred = async(Dispatchers.IO) { 
                    TmdbService.getTvSimilar(searchItem.id.toString())?.results ?: emptyList() 
                }
                
                detailsResult = detailsDeferred.await()
                creditsResult = creditsDeferred.await()
                contentRatingResult = contentRatingDeferred.await()
                @Suppress("UNCHECKED_CAST")
                similarResult = similarDeferred.await() as List<com.google.jetstream.data.remote.TmdbService.SimilarItem>
            }
            
            // 使用显式的序列化器将各部分转换为 JsonElement，避免 Map<String, Any> 导致的序列化错误
            val fullDetailsJson = buildMap<String, kotlinx.serialization.json.JsonElement> {
                detailsResult?.let {
                    put(
                        "tmdb_details",
                        Json.encodeToJsonElement(
                            com.google.jetstream.data.remote.TmdbService.TvDetailsResponse.serializer(),
                            it
                        )
                    )
                }
                creditsResult?.let {
                    put(
                        "tmdb_credits",
                        Json.encodeToJsonElement(
                            com.google.jetstream.data.remote.TmdbService.CreditsResponse.serializer(),
                            it
                        )
                    )
                }
                put("tmdb_certification", JsonPrimitive(contentRatingResult))
                put(
                    "tmdb_similar",
                    Json.encodeToJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(
                            com.google.jetstream.data.remote.TmdbService.SimilarItem.serializer()
                        ),
                        similarResult.take(10)
                    )
                )
                // 添加本地集数信息
                put("local_episode_count", JsonPrimitive(localEpisodeCount))
                // 添加季信息
                if (seasons.isNotEmpty()) {
                    put("available_seasons", Json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(com.google.jetstream.data.entities.TvSeason.serializer()), seasons))
                }
                // 添加WebDAV配置ID
                put("webdav_config_id", JsonPrimitive(webDavConfigId))
            }
            
            return Movie(
                id = searchItem.id.toString(),
                videoUri = videoUri,
                subtitleUri = null,
                posterUri = searchItem.posterPathOrNull()?.let { "https://image.tmdb.org/t/p/w500$it" } ?: "",
                name = searchItem.name ?: searchItem.title ?: "",
                description = Json.encodeToString<Map<String, JsonElement>>(fullDetailsJson), // 临时存储详情信息
                releaseDate = searchItem.firstAirDate,
                rating = searchItem.voteAverage
            )
        } catch (e: Exception) {
            Log.w(TAG, "获取TMDB电视剧详情失败: ${e.message}")
            // 如果获取详情失败，返回基础信息
            return Movie(
                id = searchItem.id.toString(),
                videoUri = videoUri,
                subtitleUri = null,
                posterUri = searchItem.posterPathOrNull()?.let { "https://image.tmdb.org/t/p/w500$it" } ?: "",
                name = searchItem.name ?: searchItem.title ?: "",
                description = searchItem.overview ?: "",
                releaseDate = searchItem.firstAirDate,
                rating = searchItem.voteAverage
            )
        }
    }
    
    /**
     * 从Movie对象创建ScrapedItemEntity，包含完整的详情信息
     */
    private fun createScrapedItemEntity(movie: Movie, type: String): ScrapedItemEntity? {
        return try {
            // 尝试解析存储在description中的详情信息
            val detailsMap = try {
                Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(movie.description)
            } catch (e: Exception) {
                null
            }
            
            if (detailsMap != null) {
                if (type == "tv") {
                    // 处理电视剧详情
                    val tmdbTvDetails = detailsMap["tmdb_details"]?.let {
                        try { Json.decodeFromJsonElement(TmdbService.TvDetailsResponse.serializer(), it) } catch (e: Exception) { null }
                    }
                    val tmdbCredits = detailsMap["tmdb_credits"]?.let {
                        try { Json.decodeFromJsonElement(TmdbService.CreditsResponse.serializer(), it) } catch (e: Exception) { null }
                    }
                    val certification = detailsMap["tmdb_certification"]?.let { el ->
                        el.jsonPrimitive.contentOrNull
                    }
                    val similar = detailsMap["tmdb_similar"]?.let { el ->
                        try { Json.decodeFromJsonElement(ListSerializer(TmdbService.SimilarItem.serializer()), el) } catch (e: Exception) { emptyList() }
                    } ?: emptyList()
                    val localEpisodeCount = detailsMap["local_episode_count"]?.let { el ->
                        try { el.jsonPrimitive.content.toIntOrNull() } catch (e: Exception) { null }
                    } ?: 0
                    val availableSeasons = detailsMap["available_seasons"]?.let { el ->
                        try { Json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(com.google.jetstream.data.entities.TvSeason.serializer()), el) } catch (e: Exception) { emptyList() }
                    } ?: emptyList()
                    val webDavConfigId = detailsMap["webdav_config_id"]?.let { el ->
                        el.jsonPrimitive.contentOrNull
                    }
                    
                    // 构建电视剧详情字段
                    val categories = tmdbTvDetails?.genres?.map { mapGenreToChinese(it.name) } ?: emptyList()
                    val castAndCrew = tmdbCredits?.cast?.take(10)?.map {
                        MovieCast(
                            id = it.id.toString(),
                            characterName = it.character ?: "",
                            realName = it.name,
                            avatarUrl = it.profilePath?.let { p -> TmdbService.IMAGE_BASE_W500 + p } ?: ""
                        )
                    } ?: emptyList()
                    
                    // 电视剧的创作人员处理
                    val creator = tmdbTvDetails?.createdBy?.firstOrNull()?.name ?: ""
                    val director = tmdbCredits?.crew?.firstOrNull { it.job.equals("Director", true) }?.name ?: creator
                    val screenplay = tmdbCredits?.crew?.firstOrNull { it.job?.contains("Writer", true) == true || it.job?.contains("Screenplay", true) == true }?.name ?: ""
                    val music = tmdbCredits?.crew?.firstOrNull { it.job?.contains("Music", true) == true || it.job?.contains("Composer", true) == true }?.name ?: ""
                    
                    // 电视剧时长处理：显示总集数和库中集数
                    fun createTvDurationText(tmdbDetails: TmdbService.TvDetailsResponse?, localEpisodeCount: Int): String {
                        val totalEpisodes = tmdbDetails?.numberOfEpisodes ?: 0
                        return if (totalEpisodes > 0 && localEpisodeCount > 0) {
                            "共${totalEpisodes}集（库中有${localEpisodeCount}集）"
                        } else if (totalEpisodes > 0) {
                            "共${totalEpisodes}集"
                        } else if (localEpisodeCount > 0) {
                            "库中有${localEpisodeCount}集"
                        } else {
                            ""
                        }
                    }
                    
                    val ratingText = tmdbTvDetails?.voteAverage?.let { String.format("⭐ %.1f", it) } ?: "⭐ --"
                    
                    ScrapedItemEntity(
                        id = movie.id,
                        title = tmdbTvDetails?.name ?: movie.name,
                        description = tmdbTvDetails?.overview ?: movie.description,
                        posterUri = movie.posterUri,
                        releaseDate = movie.releaseDate,
                        rating = movie.rating,
                        type = type,
                        sourcePath = movie.videoUri.ifBlank { null },
                        backdropUri = tmdbTvDetails?.backdropPath?.let { TmdbService.IMAGE_BASE_W780 + it },
                        pgRating = ratingText,
                        categories = if (categories.isNotEmpty()) Json.encodeToString(categories) else null,
                        duration = createTvDurationText(tmdbTvDetails, localEpisodeCount),
                        director = director,
                        screenplay = screenplay,
                        music = music,
                        castAndCrew = if (castAndCrew.isNotEmpty()) Json.encodeToString(castAndCrew) else null,
                        availableSeasons = if (availableSeasons.isNotEmpty()) Json.encodeToString(availableSeasons) else null,
                        webDavConfigId = webDavConfigId
                    )
                } else {
                    // 处理电影详情
                    val tmdbDetails = detailsMap["tmdb_details"]?.let {
                        try { Json.decodeFromJsonElement(TmdbService.MovieDetailsResponse.serializer(), it) } catch (e: Exception) { null }
                    }
                    val tmdbCredits = detailsMap["tmdb_credits"]?.let {
                        try { Json.decodeFromJsonElement(TmdbService.CreditsResponse.serializer(), it) } catch (e: Exception) { null }
                    }
                    val certification = detailsMap["tmdb_certification"]?.let { el ->
                        el.jsonPrimitive.contentOrNull
                    }
                    val similar = detailsMap["tmdb_similar"]?.let { el ->
                        try { Json.decodeFromJsonElement(ListSerializer(TmdbService.SimilarItem.serializer()), el) } catch (e: Exception) { emptyList() }
                    } ?: emptyList()
                    val webDavConfigId = detailsMap["webdav_config_id"]?.let { el ->
                        el.jsonPrimitive.contentOrNull
                    }
                    
                    // 构建电影详情字段
                    val categories = tmdbDetails?.genres?.map { mapGenreToChinese(it.name) } ?: emptyList()
                    val castAndCrew = tmdbCredits?.cast?.take(10)?.map {
                        MovieCast(
                            id = it.id.toString(),
                            characterName = it.character ?: "",
                            realName = it.name,
                            avatarUrl = it.profilePath?.let { p -> TmdbService.IMAGE_BASE_W500 + p } ?: ""
                        )
                    } ?: emptyList()
                    
                    val director = tmdbCredits?.crew?.firstOrNull { it.job.equals("Director", true) }?.name ?: ""
                    val screenplay = tmdbCredits?.crew?.firstOrNull { it.job?.contains("Writer", true) == true || it.job?.contains("Screenplay", true) == true }?.name ?: ""
                    val music = tmdbCredits?.crew?.firstOrNull { it.job?.contains("Music", true) == true || it.job?.contains("Composer", true) == true }?.name ?: ""
                    
                    fun minutesToDuration(mins: Int?): String = if (mins == null || mins <= 0) "" else "${mins / 60}h ${mins % 60}m"
                    
                    val ratingText = tmdbDetails?.voteAverage?.let { String.format("⭐ %.1f", it) } ?: "⭐ --"
                    
                    ScrapedItemEntity(
                        id = movie.id,
                        title = tmdbDetails?.title ?: movie.name,
                        description = tmdbDetails?.overview ?: movie.description,
                        posterUri = movie.posterUri,
                        releaseDate = movie.releaseDate,
                        rating = movie.rating,
                        type = type,
                        sourcePath = movie.videoUri.ifBlank { null },
                        backdropUri = tmdbDetails?.backdropPath?.let { TmdbService.IMAGE_BASE_W780 + it },
                        pgRating = ratingText,
                        categories = if (categories.isNotEmpty()) Json.encodeToString(categories) else null,
                        duration = minutesToDuration(tmdbDetails?.runtime),
                        director = director,
                        screenplay = screenplay,
                        music = music,
                        castAndCrew = if (castAndCrew.isNotEmpty()) Json.encodeToString(castAndCrew) else null,
                        webDavConfigId = webDavConfigId
                    )
                }
            } else {
                // 如果没有详情信息，创建基础实体
                ScrapedItemEntity(
                    id = movie.id,
                    title = movie.name,
                    description = movie.description,
                    posterUri = movie.posterUri,
                    releaseDate = movie.releaseDate,
                    rating = movie.rating,
                    type = type,
                    sourcePath = movie.videoUri.ifBlank { null }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "创建ScrapedItemEntity失败: ${e.message}")
            // 创建基础实体作为后备
            ScrapedItemEntity(
                id = movie.id,
                title = movie.name,
                description = movie.description,
                posterUri = movie.posterUri,
                releaseDate = movie.releaseDate,
                rating = movie.rating,
                type = type,
                sourcePath = movie.videoUri.ifBlank { null }
            )
        }
    }
    


    /**
     * 检测目录中的季结构（增强版，记录详细文件信息）
     */
    private suspend fun detectSeasonsInDirectory(
        currentPath: String, 
        folders: List<com.thegrizzlylabs.sardineandroid.DavResource>, 
        files: List<com.thegrizzlylabs.sardineandroid.DavResource>,
        webDavConfigId: String
    ): List<com.google.jetstream.data.entities.TvSeason> {
        val seasons = mutableListOf<com.google.jetstream.data.entities.TvSeason>()
        
        // 检查是否有季文件夹（如 Season 1, S01, 第一季等）
        val seasonFolders = folders.filter { folder ->
            val folderName = folder.name.substringAfterLast('/').removeSuffix("/")
            isSeasonFolder(folderName)
        }
        
        if (seasonFolders.isNotEmpty()) {
            // 有季文件夹结构
            for (seasonFolder in seasonFolders) {
                val folderName = seasonFolder.name.substringAfterLast('/').removeSuffix("/")
                val seasonNumber = extractSeasonNumber(folderName)
                if (seasonNumber > 0) {
                    // 获取该季的集数和详细文件信息
                    val seasonPath = if (currentPath.isBlank()) folderName else "$currentPath/$folderName"
                    val episodeCount = countEpisodesInSeason(seasonPath)
                    
                    // 记录详细的季信息，包括WebDAV路径和配置ID
                    seasons.add(
                        com.google.jetstream.data.entities.TvSeason(
                            number = seasonNumber,
                            name = formatSeasonName(seasonNumber),
                            episodeCount = episodeCount,
                            webDavPath = seasonPath,
                            webDavConfigId = webDavConfigId
                        )
                    )
                    
                    Log.d(TAG, "检测到季文件夹: $folderName -> 第${seasonNumber}季，路径: $seasonPath，集数: $episodeCount")
                }
            }
        } else if (files.isNotEmpty()) {
            // 没有季文件夹，但有视频文件，默认为第1季
            val episodeFiles = files.filter { !it.isDirectory && isEpisodeFile(it.name) }
            if (episodeFiles.isNotEmpty()) {
                seasons.add(
                    com.google.jetstream.data.entities.TvSeason(
                        number = 1,
                        name = "第1季",
                        episodeCount = episodeFiles.size,
                        webDavPath = currentPath,
                        webDavConfigId = webDavConfigId
                    )
                )
                
                Log.d(TAG, "检测到单季结构: 路径: $currentPath，集数: ${episodeFiles.size}")
                // 记录文件列表用于调试
                episodeFiles.forEach { file ->
                    Log.v(TAG, "  剧集文件: ${file.name}")
                }
            }
        }
        
        return seasons.sortedBy { it.number }
    }
    
    /**
     * 判断文件夹是否是季文件夹
     */
    private fun isSeasonFolder(folderName: String): Boolean {
        val lowerName = folderName.lowercase()
        return lowerName.matches(Regex("season\\s*\\d+")) ||
               lowerName.matches(Regex("s\\d+")) ||
               lowerName.matches(Regex("第\\d+季")) ||
               lowerName.matches(Regex("第[一二三四五六七八九十]+季"))
    }
    
    /**
     * 从文件夹名提取季号
     */
    private fun extractSeasonNumber(folderName: String): Int {
        val lowerName = folderName.lowercase()
        
        // Season 1, Season 01 等格式
        Regex("season\\s*(\\d+)").find(lowerName)?.let { match ->
            return match.groupValues[1].toIntOrNull() ?: 0
        }
        
        // S1, S01 等格式
        Regex("s(\\d+)").find(lowerName)?.let { match ->
            return match.groupValues[1].toIntOrNull() ?: 0
        }
        
        // 第1季等格式
        Regex("第(\\d+)季").find(lowerName)?.let { match ->
            return match.groupValues[1].toIntOrNull() ?: 0
        }
        
        // 第一季等中文数字格式
        val chineseNumbers = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5,
            "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10
        )
        Regex("第([一二三四五六七八九十]+)季").find(lowerName)?.let { match ->
            return chineseNumbers[match.groupValues[1]] ?: 0
        }
        
        return 0
    }
    
    /**
     * 格式化季名称
     */
    private fun formatSeasonName(seasonNumber: Int): String {
        return "第${seasonNumber}季"
    }
    
    /**
     * 统计季中的集数
     */
    private suspend fun countEpisodesInSeason(seasonPath: String): Int {
        return try {
            when (val result = webDavService.listDirectory(seasonPath)) {
                is WebDavResult.Success -> {
                    result.data.count { resource ->
                        !resource.isDirectory && isEpisodeFile(resource.name)
                    }
                }
                else -> 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "统计季集数失败: $seasonPath", e)
            0
        }
    }

    /**
     * 将英文影片类型映射为中文
     */
    private fun mapGenreToChinese(englishGenre: String): String {
        return when (englishGenre.lowercase()) {
            "action" -> "动作"
            "adventure" -> "冒险"
            "animation" -> "动画"
            "comedy" -> "喜剧"
            "crime" -> "犯罪"
            "documentary" -> "纪录片"
            "drama" -> "剧情"
            "family" -> "家庭"
            "fantasy" -> "奇幻"
            "history" -> "历史"
            "horror" -> "恐怖"
            "music" -> "音乐"
            "mystery" -> "悬疑"
            "romance" -> "爱情"
            "science fiction", "sci-fi" -> "科幻"
            "tv movie" -> "电视电影"
            "thriller" -> "惊悚"
            "war" -> "战争"
            "western" -> "西部"
            "biography" -> "传记"
            "sport" -> "体育"
            "musical" -> "音乐剧"
            "short" -> "短片"
            "news" -> "新闻"
            "reality" -> "真人秀"
            "talk" -> "脱口秀"
            // 电视剧特有类型
            "action & adventure" -> "动作冒险"
            "sci-fi & fantasy" -> "科幻奇幻"
            "soap" -> "肥皂剧"
            "kids" -> "儿童"
            "war & politics" -> "战争政治"
            else -> englishGenre // 如果没有匹配的，返回原文
        }
    }

    companion object {
        private const val TAG = "RefreshScraper"
    }
}

private object TmdbApi {
    // 用户提供
    private const val BEARER = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJlNWVhMWZmMjJhYzUzOTMzNDAwYmMwMjUxZmZmNTk0MyIsIm5iZiI6MTc1NTI1Nzk4OC4xODU5OTk5LCJzdWIiOiI2ODlmMWM4NGRhOWIyNDRhMzkxYTQ1NzMiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.GRMPv8PEpwrEm-6zCPmSr4uFH5JFkJPMQq9P44IvcvM"
    private const val BASE = "https://api.themoviedb.org"
    private val json by lazy { kotlinx.serialization.json.Json { ignoreUnknownKeys = true } }

    @Serializable
    data class SearchResponse(
        val page: Int = 1,
        val results: List<SearchItem> = emptyList()
    )

    @Serializable
    data class SearchItem(
        val id: Int,
        val name: String? = null,
        val title: String? = null,
        @SerialName("original_name") val originalName: String? = null,
        @SerialName("original_title") val originalTitle: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("vote_average") val voteAverage: Float? = null,
    )

    private fun createTrustAllTrustManager(): Array<javax.net.ssl.TrustManager> {
        return arrayOf(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
    }

    suspend fun search(path: String, query: String, extra: Map<String, String> = emptyMap(), retryCount: Int = 3): SearchResponse? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null
        
        repeat(retryCount) { attempt ->
            try {
                val lang = "zh-CN"
                val params = buildString {
                    append("query=").append(URLEncoder.encode(query, "UTF-8"))
                    append("&language=").append(lang)
                    append("&page=1")
                    for ((k, v) in extra) {
                        append("&").append(k).append("=").append(URLEncoder.encode(v, "UTF-8"))
                    }
                }
                val url = URL("$BASE$path?$params")
                val conn = url.openConnection() as HttpURLConnection
                
                // 如果是HTTPS连接，配置SSL
                if (conn is javax.net.ssl.HttpsURLConnection) {
                    try {
                        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
                        sslContext.init(null, createTrustAllTrustManager(), java.security.SecureRandom())
                        conn.sslSocketFactory = sslContext.socketFactory
                        conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                    } catch (e: Exception) {
                        Log.w("TmdbApi", "Failed to configure SSL, using default: ${e.message}")
                    }
                }
                
                conn.apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $BEARER")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "JetStream-Android/1.0")
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                
                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    return@withContext conn.inputStream.bufferedReader().use { r ->
                        val txt = r.readText()
                        json.decodeFromString<SearchResponse>(txt)
                    }
                } else if (responseCode == 429) {
                    // Rate limited, wait and retry
                    Log.w("TmdbApi", "Rate limited for '$query', retrying in ${(attempt + 1) * 1000}ms")
                    kotlinx.coroutines.delay((attempt + 1) * 1000L)
                } else {
                    Log.w("TmdbApi", "HTTP $responseCode for '$query' (attempt ${attempt + 1})")
                    conn.errorStream?.bufferedReader()?.use { r -> 
                        Log.w("TmdbApi", "Error response: ${r.readText()}")
                    }
                    if (responseCode in 400..499 && responseCode != 429) {
                        // Client error, no point retrying
                        return@withContext null
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w("TmdbApi", "search failed for '$query' (attempt ${attempt + 1}): ${e.message}")
                if (attempt == retryCount - 1) {
                    e.printStackTrace()
                    return@withContext null
                }
                // Wait before retry
                kotlinx.coroutines.delay((attempt + 1) * 1000L)
            }
        }
        null
    }
}

