package com.google.jetstream.data.services

import android.util.Log
import com.google.jetstream.data.database.dao.RecentlyWatchedDao
import com.google.jetstream.data.database.entities.RecentlyWatchedEntity
import com.google.jetstream.data.entities.Episode
import com.google.jetstream.data.entities.MovieDetails
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 电视剧播放服务
 * 负责处理电视剧的智能播放逻辑
 */
@Singleton
class TvPlaybackService @Inject constructor(
    private val recentlyWatchedDao: RecentlyWatchedDao,
    private val episodeMatchingService: EpisodeMatchingService,
    private val scrapedItemDao: com.google.jetstream.data.database.dao.ScrapedItemDao
) {
    companion object {
        private const val TAG = "TvPlaybackService"
    }

    /**
     * 播放信息数据类
     */
    data class PlaybackInfo(
        val episode: Episode,
        val startPositionMs: Long = 0L,
        val isResuming: Boolean = false
    )

    /**
     * 获取电视剧的播放信息
     * 如果有历史记录，返回上次观看的剧集和位置
     * 如果没有历史记录，返回第一集
     * 
     * @param tvId 电视剧ID
     * @param movieDetails 电视剧详情
     * @return 播放信息，如果无法获取则返回null
     */
    suspend fun getPlaybackInfo(tvId: String, movieDetails: MovieDetails): PlaybackInfo? {
        try {
            // 检查是否有播放历史记录
            val recentlyWatched = recentlyWatchedDao.getByMovieId(tvId)
            Log.d(TAG, "检查播放历史: tvId=$tvId, 历史记录=${recentlyWatched != null}")
            
            if (recentlyWatched != null) {
                Log.d(TAG, "历史记录详情: episodeId=${recentlyWatched.episodeId}, seasonNumber=${recentlyWatched.seasonNumber}, episodeNumber=${recentlyWatched.episodeNumber}, position=${recentlyWatched.currentPositionMs}ms")
                
                // 如果有剧集相关信息，尝试续播
                if (recentlyWatched.episodeId != null && recentlyWatched.seasonNumber != null && recentlyWatched.episodeNumber != null) {
                    Log.d(TAG, "尝试续播: 第${recentlyWatched.seasonNumber}季第${recentlyWatched.episodeNumber}集")
                    
                    val resumeEpisode = findEpisodeById(
                        tvId = tvId,
                        episodeId = recentlyWatched.episodeId,
                        seasonNumber = recentlyWatched.seasonNumber,
                        episodeNumber = recentlyWatched.episodeNumber,
                        movieDetails = movieDetails
                    )
                    
                    if (resumeEpisode != null) {
                        Log.d(TAG, "续播成功: 第${resumeEpisode.seasonNumber}季第${resumeEpisode.episodeNumber}集, 起始位置: ${recentlyWatched.currentPositionMs}ms")
                        return PlaybackInfo(
                            episode = resumeEpisode,
                            startPositionMs = recentlyWatched.currentPositionMs ?: 0L,
                            isResuming = true
                        )
                    } else {
                        Log.w(TAG, "续播失败: 无法找到历史记录中的剧集")
                    }
                } else {
                    Log.d(TAG, "历史记录不完整，缺少剧集信息")
                }
            } else {
                Log.d(TAG, "没有找到播放历史记录")
            }
            
            // 没有历史记录或无法恢复，从第一集开始
            Log.d(TAG, "开始查找第一集")
            val firstEpisode = getFirstAvailableEpisode(tvId, movieDetails)
            if (firstEpisode != null) {
                Log.d(TAG, "从第一集开始播放: 第${firstEpisode.seasonNumber}季第${firstEpisode.episodeNumber}集")
                return PlaybackInfo(
                    episode = firstEpisode,
                    startPositionMs = 0L,
                    isResuming = false
                )
            }
            
            Log.w(TAG, "无法找到可播放的剧集")
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "获取播放信息失败: tvId=$tvId", e)
            return null
        }
    }

    /**
     * 根据ID查找剧集
     */
    private suspend fun findEpisodeById(
        tvId: String,
        episodeId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        movieDetails: MovieDetails
    ): Episode? {
        try {
            // 获取本地季信息
            val localSeasons = getLocalSeasonsForTv(tvId)
            if (localSeasons.isEmpty()) {
                Log.w(TAG, "没有找到本地季信息")
                return null
            }
            
            // 获取指定季的剧集列表
            val episodes = episodeMatchingService.getFilteredEpisodes(
                tvId = tvId,
                seasonNumber = seasonNumber,
                localSeasons = localSeasons
            )
            
            // 首先尝试通过episodeId精确匹配
            val episodeById = episodes.find { it.id == episodeId }
            if (episodeById != null) {
                return episodeById
            }
            
            // 如果ID匹配失败，尝试通过季号和集号匹配
            val episodeByNumber = episodes.find { 
                it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber 
            }
            if (episodeByNumber != null) {
                Log.d(TAG, "通过季号和集号找到剧集: 第${seasonNumber}季第${episodeNumber}集")
                return episodeByNumber
            }
            
            Log.w(TAG, "无法找到指定的剧集: episodeId=$episodeId, S${seasonNumber}E${episodeNumber}")
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "查找剧集失败", e)
            return null
        }
    }

    /**
     * 获取第一个可播放的剧集
     */
    private suspend fun getFirstAvailableEpisode(tvId: String, movieDetails: MovieDetails): Episode? {
        try {
            // 获取本地季信息
            val localSeasons = getLocalSeasonsForTv(tvId)
            if (localSeasons.isEmpty()) {
                Log.w(TAG, "没有找到本地季信息")
                return null
            }
            
            // 按季号排序，从第一季开始查找
            val sortedSeasons = localSeasons.sortedBy { it.number }
            
            for (season in sortedSeasons) {
                val episodes = episodeMatchingService.getFilteredEpisodes(
                    tvId = tvId,
                    seasonNumber = season.number,
                    localSeasons = localSeasons
                )
                
                if (episodes.isNotEmpty()) {
                    // 返回该季的第一集
                    val firstEpisode = episodes.minByOrNull { it.episodeNumber }
                    if (firstEpisode != null) {
                        Log.d(TAG, "找到第一个可播放剧集: 第${firstEpisode.seasonNumber}季第${firstEpisode.episodeNumber}集")
                        return firstEpisode
                    }
                }
            }
            
            Log.w(TAG, "没有找到任何可播放的剧集")
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "获取第一集失败", e)
            return null
        }
    }

    /**
     * 从数据库中获取本地季信息
     */
    private suspend fun getLocalSeasonsForTv(tvId: String): List<com.google.jetstream.data.entities.TvSeason> {
        return try {
            val scrapedItem = scrapedItemDao.getById(tvId)
            Log.d(TAG, "获取刮削项: tvId=$tvId, scrapedItem=${scrapedItem != null}, availableSeasons=${scrapedItem?.availableSeasons}")
            
            if (scrapedItem?.availableSeasons != null) {
                // 使用kotlinx.serialization解析
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val seasons = json.decodeFromString<List<com.google.jetstream.data.entities.TvSeason>>(scrapedItem.availableSeasons)
                Log.d(TAG, "解析季信息成功: 共${seasons.size}季")
                seasons
            } else {
                Log.w(TAG, "没有找到季信息: tvId=$tvId")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取本地季信息失败: $tvId", e)
            emptyList()
        }
    }

    /**
     * 保存播放进度（包含剧集信息）
     */
    suspend fun savePlaybackProgress(
        movieDetails: MovieDetails,
        episode: Episode,
        currentPositionMs: Long,
        durationMs: Long
    ) {
        try {
            val watchProgress = if (durationMs > 0) {
                (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else null
            
            val entity = RecentlyWatchedEntity(
                movieId = movieDetails.id,
                movieTitle = movieDetails.name,
                backdropUri = movieDetails.backdropUri,
                posterUri = movieDetails.posterUri,
                description = movieDetails.description,
                releaseDate = movieDetails.releaseDate,
                rating = null,
                type = "tv",
                lastWatchedAt = System.currentTimeMillis(),
                watchProgress = watchProgress,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                // 剧集相关信息
                episodeId = episode.id,
                episodeNumber = episode.episodeNumber,
                seasonNumber = episode.seasonNumber,
                episodeTitle = episode.name
            )
            
            recentlyWatchedDao.insertOrUpdate(entity)
            Log.d(TAG, "保存播放进度: ${movieDetails.name} 第${episode.seasonNumber}季第${episode.episodeNumber}集 ${currentPositionMs}ms")
            
        } catch (e: Exception) {
            Log.e(TAG, "保存播放进度失败", e)
        }
    }
}