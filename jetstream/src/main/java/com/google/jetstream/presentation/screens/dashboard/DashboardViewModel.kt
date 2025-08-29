package com.google.jetstream.presentation.screens.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.jetstream.data.database.dao.ResourceDirectoryDao
import com.google.jetstream.data.database.dao.WebDavConfigDao
import com.google.jetstream.data.database.entities.WebDavConfigEntity
import com.google.jetstream.data.webdav.WebDavResult
import com.google.jetstream.data.webdav.WebDavService
import com.thegrizzlylabs.sardineandroid.DavResource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import javax.inject.Inject
import com.google.jetstream.data.entities.Movie
import com.google.jetstream.data.repositories.ScrapedMoviesStore

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val resourceDirectoryDao: ResourceDirectoryDao,
    private val webDavConfigDao: WebDavConfigDao,
    private val webDavService: WebDavService,
    private val scrapedMoviesStore: ScrapedMoviesStore,
) : ViewModel() {

    fun refreshAndScrape() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val aggregated = mutableListOf<Movie>()
                val directories = resourceDirectoryDao.getAllDirectories().first()
                for (dir in directories) {
                    val config: WebDavConfigEntity = webDavConfigDao.getConfigById(dir.webDavConfigId)
                        ?: continue
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
                    Log.i(TAG, "开始扫描：${config.displayName} -> ${if (startPath.isEmpty()) "/" else startPath}")
                    traverseAndScrape(startPath, aggregated)
                }
                // 更新首页 Movies 模块数据源
                scrapedMoviesStore.setMovies(aggregated)
                Log.i(TAG, "扫描完成")
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "刷新/刮削失败: ${e.message}", e)
            }
        }
    }

    private suspend fun traverseAndScrape(path: String, aggregated: MutableList<Movie>) {
        when (val res = webDavService.listDirectory(path)) {
            is WebDavResult.Success -> {
                val items = res.data.filter { it.name.isNotBlank() }
                val files = items.filter { !it.isDirectory }
                val folders = items.filter { it.isDirectory }

                // 判断该目录是否像剧集目录（包含多个集）。目录名若包含“电影”，强制当电影目录处理
                val fileNames = files.map { it.name }
                val looksLikeTv = !path.contains("电影") && looksLikeTvSeries(fileNames)

                // 文件刮削
                for (file in files) {
                    val isEp = isEpisodeFile(file.name)
                    val rawName = stripExtension(file.name)
                    val cleaned = extractTitleFromName(rawName)
                    val candidates = buildCandidates(cleaned)
                    val year = extractYear(rawName)
                    val english = isEnglishTitle(rawName)

                    if (isEp || looksLikeTv) {
                        for (q in candidates) {
                            val extras = mutableMapOf<String, String>()
                            if (year != null) extras["first_air_date_year"] = year
                            if (english) extras["region"] = "US"
                            val res = TmdbApi.search("/3/search/tv", q, extras)
                            if (res?.results?.firstOrNull() != null) break
                        }
                    } else {
                        var matched: TmdbApi.SearchItem? = null
                        for (q in candidates) {
                            val extras = mutableMapOf("include_adult" to "false")
                            if (year != null) extras["year"] = year
                            if (english) extras["region"] = "US"
                            val res = TmdbApi.search("/3/search/movie", q, extras)
                            matched = res?.results?.firstOrNull()
                            if (matched != null) {
                                val poster = matched.posterPathOrNull()?.let { "https://image.tmdb.org/t/p/w500$it" } ?: ""
                                aggregated.add(
                                    Movie(
                                        id = matched.id.toString(),
                                        videoUri = "",
                                        subtitleUri = null,
                                        posterUri = poster,
                                        name = matched.title ?: matched.name ?: q,
                                        description = matched.overview ?: ""
                                    )
                                )
                                Log.i(TAG, "电影匹配: $q -> ${matched.title ?: matched.name} (${matched.id})")
                                break
                            }
                        }
                    }
                }

                // 递归：如果当前目录名是“电影”，则不再深入“电影/电影”这种重复层级
                for (dir in folders) {
                    val dirName = dir.name.removeSuffix("/")
                    val next = if (path.isBlank()) dirName else "$path/$dirName"
                    if (!(path.contains("电影") && dirName == "电影")) {
                        traverseAndScrape(next, aggregated)
                    }
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
        val sxe = Regex("s[0-9]{1,2}e[0-9]{1,2}")
        // 仅识别文件名开头的 1-2 位数字（常用 01/02 集），避免把年份识别为集数
        val leadingNum = Regex("^(?:[ _.-])?([0-2]?\\d)(?:[ _.-])")
        return sxe.containsMatchIn(n) || leadingNum.containsMatchIn(n)
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
        @SerialName("poster_path") val posterPath: String? = null
    )

    suspend fun search(path: String, query: String, extra: Map<String, String> = emptyMap()): SearchResponse? = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) return@withContext null
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
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $BEARER")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 10000
            }
            conn.inputStream.bufferedReader().use { r ->
                val txt = r.readText()
                return@withContext json.decodeFromString<SearchResponse>(txt)
            }
        } catch (e: Exception) {
            Log.w("TmdbApi", "search failed for '$query': ${e.message}")
            null
        }
    }
}

