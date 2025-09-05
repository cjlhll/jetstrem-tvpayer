package com.google.jetstream.data.services

import android.util.Log
import com.google.jetstream.data.entities.Episode
import com.google.jetstream.data.entities.TvSeason
import com.google.jetstream.data.remote.TmdbService
import com.google.jetstream.data.webdav.WebDavResult
import com.google.jetstream.data.webdav.WebDavService
import com.thegrizzlylabs.sardineandroid.DavResource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 剧集匹配服务
 * 负责将本地视频文件与TMDB剧集信息进行精确匹配
 */
@Singleton
class EpisodeMatchingService @Inject constructor(
    private val webDavService: WebDavService
) {
    companion object {
        private const val TAG = "EpisodeMatchingService"
    }

    /**
 * 获取过滤后的剧集列表，只包含本地存在的剧集
 * 
 * @param tvId TMDB电视剧ID
 * @param seasonNumber 季号
 * @param localSeasons 本地检测到的季信息
 * @return 过滤后的剧集列表
 */
    suspend fun getFilteredEpisodes(
        tvId: String,
        seasonNumber: Int,
        localSeasons: List<TvSeason>
    ): List<Episode> {
        try {
            // 获取TMDB完整剧集列表
            val seasonDetails = TmdbService.getTvSeasonDetails(tvId, seasonNumber)
            if (seasonDetails == null) {
                Log.w(TAG, "无法获取TMDB季详情: tvId=$tvId, season=$seasonNumber")
                return emptyList()
            }

            // 找到对应的本地季信息
            val localSeason = localSeasons.find { it.number == seasonNumber }
            if (localSeason == null) {
                Log.w(TAG, "本地没有找到对应的季: season=$seasonNumber")
                return emptyList()
            }

            // 获取本地视频文件列表
            val localFiles = getLocalVideoFiles(localSeason.webDavPath)
            if (localFiles.isEmpty()) {
                Log.w(TAG, "本地季目录没有视频文件: ${localSeason.webDavPath}")
                return emptyList()
            }

            // 将TMDB剧集与本地文件进行匹配
            val matchedEpisodes = mutableListOf<Episode>()
            for (tmdbEpisode in seasonDetails.episodes) {
                val matchedFile = findMatchingLocalFile(tmdbEpisode.episodeNumber, localFiles)
                if (matchedFile != null) {
                    // 构造完整的视频文件URI
                    val videoUri = localSeason.webDavPath.removeSuffix("/") + "/" + matchedFile.name

                    // 创建包含本地文件信息的Episode对象
                    val episode = Episode(
                        id = tmdbEpisode.id.toString(),
                        episodeNumber = tmdbEpisode.episodeNumber,
                        name = tmdbEpisode.name ?: "第${tmdbEpisode.episodeNumber}集",
                        overview = tmdbEpisode.overview ?: "",
                        stillPath = tmdbEpisode.stillPath,
                        airDate = tmdbEpisode.airDate,
                        voteAverage = tmdbEpisode.voteAverage,
                        runtime = tmdbEpisode.runtime,
                        tvId = tvId,
                        seasonNumber = seasonNumber,
                        // 这里可以添加本地文件的观看进度信息
                        watchProgress = null, // TODO: 从数据库获取观看进度
                        currentPositionMs = null,
                        durationMs = tmdbEpisode.runtime?.let { it * 60 * 1000L },
                        videoUri = videoUri,
                        fileName = matchedFile.name,
                        fileSizeBytes = matchedFile.contentLength
                    )
                    matchedEpisodes.add(episode)
                    Log.d(TAG, "匹配成功: 第${tmdbEpisode.episodeNumber}集 -> ${matchedFile.name}")
                } else {
                    Log.d(TAG, "本地未找到: 第${tmdbEpisode.episodeNumber}集")
                }
            }

            Log.i(TAG, "剧集匹配完成: TMDB=${seasonDetails.episodes.size}集, 本地匹配=${matchedEpisodes.size}集")
            return matchedEpisodes.sortedBy { it.episodeNumber }

        } catch (e: Exception) {
            Log.e(TAG, "剧集匹配失败: tvId=$tvId, season=$seasonNumber", e)
            return emptyList()
        }
    }

    /**
     * 获取本地视频文件列表
     */
    private suspend fun getLocalVideoFiles(webDavPath: String): List<DavResource> {
        return try {
            when (val result = webDavService.listDirectory(webDavPath)) {
                is WebDavResult.Success -> {
                    result.data
                        .filter { !it.isDirectory && isVideoFile(it.name) }
                        .sortedBy { it.name }
                }
                else -> {
                    Log.w(TAG, "无法列出目录: $webDavPath")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取本地文件失败: $webDavPath", e)
            emptyList()
        }
    }

    /**
     * 查找与指定集数匹配的本地文件
     */
    private fun findMatchingLocalFile(episodeNumber: Int, localFiles: List<DavResource>): DavResource? {
        // 尝试多种匹配模式
        val patterns = listOf(
            // 标准格式：S01E01, S1E1
            Regex("(?i)s\\d{1,2}[ _.-]?e0*${episodeNumber}(?![0-9])"),
            // 前导数字：01, 001
            Regex("(?i)^0*${episodeNumber}[ _.\\-] "),
            // 中文格式：第01集, 第1集
            Regex("(?i)第\\s*0*${episodeNumber}\\s*集"),
            // E格式：E01, EP01
            Regex("(?i)\\b(e|ep)\\s*0*${episodeNumber}(?![0-9])"),
            // 纯数字匹配（最后尝试）
            Regex("(?i)\\b0*${episodeNumber}(?![0-9])")
        )

        for (pattern in patterns) {
            val matchedFile = localFiles.find { fileEntry ->
                pattern.containsMatchIn(fileEntry.name)
            }
            if (matchedFile != null) {
                return matchedFile
            }
        }

        return null
    }

    /**
     * 判断是否为视频文件
     */
    private fun isVideoFile(fileName: String): Boolean {
        val videoExtensions = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "m2ts"
        )
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in videoExtensions
    }

    /**
     * 判断文件名是否看起来像剧集文件
     */
    private fun isEpisodeFile(name: String): Boolean {
        val n = name.lowercase()
        val sxe = Regex("(?i)s\\d{1,2}[ _.-]?e\\d{1,2}")
        // 识别常见的集数样式：前导 01/02/001、中文"第01集/第1集/第001集"、以及 E01/E1、EP01/EP1
        val leadingNum = Regex("^\\s*([0-9]{1,3})[ _.\\-]")
        val cnEpisode = Regex("第\\s*([0-9]{1,3})\\s*集")
        val eNum = Regex("(?i)\\b(e|ep)\\s*([0-9]{1,3})\\b")
        return sxe.containsMatchIn(n) || leadingNum.containsMatchIn(n) || cnEpisode.containsMatchIn(n) || eNum.containsMatchIn(n)
    }
}