package com.google.jetstream.data.repositories

import com.google.jetstream.data.database.dao.RecentlyWatchedDao
import com.google.jetstream.data.database.entities.RecentlyWatchedEntity
import com.google.jetstream.data.entities.Movie
import com.google.jetstream.data.entities.MovieDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentlyWatchedRepository @Inject constructor(
    private val recentlyWatchedDao: RecentlyWatchedDao,
    private val episodeMatchingService: com.google.jetstream.data.services.EpisodeMatchingService
) {
    
    /**
     * 添加或更新最近观看记录
     */
    suspend fun addRecentlyWatched(movieDetails: MovieDetails, currentPositionMs: Long? = null, durationMs: Long? = null) {
        val watchProgress = if (currentPositionMs != null && durationMs != null && durationMs > 0) {
            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else null
        
        val entity = RecentlyWatchedEntity(
            movieId = movieDetails.id,
            movieTitle = movieDetails.name,
            backdropUri = movieDetails.backdropUri, // 使用详情页的背景图URL
            posterUri = movieDetails.posterUri,
            description = movieDetails.description,
            releaseDate = movieDetails.releaseDate,
            rating = null, // 从MovieDetails中无法直接获取rating，可以后续扩展
            type = if (movieDetails.isTV) "tv" else "movie",
            lastWatchedAt = System.currentTimeMillis(),
            watchProgress = watchProgress,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs
        )
        
        recentlyWatchedDao.insertOrUpdate(entity)
        
        // 限制最近观看记录数量，最多保留50条
        val count = recentlyWatchedDao.getCount()
        if (count > 50) {
            recentlyWatchedDao.deleteOldest(count - 50)
        }
    }
    
    /**
     * 获取最近观看的电影列表，转换为Movie对象以便UI使用
     */
    fun getRecentlyWatchedMovies(limit: Int = 20): Flow<List<Movie>> {
        return recentlyWatchedDao.getRecentlyWatched(limit).map { entities ->
            entities.map { entity ->
                // 对于电视剧，尝试获取当前播放集的封面
                val posterUri = if (entity.type == "tv" && entity.episodeId != null && 
                    entity.seasonNumber != null && entity.episodeNumber != null) {
                    // 尝试获取剧集封面，如果失败则使用电视剧主封面
                    getEpisodePosterUri(entity.movieId, entity.seasonNumber, entity.episodeNumber) 
                        ?: entity.backdropUri
                } else {
                    entity.backdropUri // 电影或无剧集信息的电视剧使用背景图
                }
                
                Movie(
                    id = entity.movieId,
                    videoUri = "", // VideoUri在播放时会从ScrapedItemEntity或其他源获取
                    subtitleUri = null,
                    posterUri = posterUri,
                    name = entity.movieTitle,
                    description = entity.description,
                    releaseDate = entity.releaseDate,
                    rating = entity.rating,
                    watchProgress = entity.watchProgress,
                    currentPositionMs = entity.currentPositionMs,
                    durationMs = entity.durationMs,
                    // 电视剧相关字段
                    episodeId = entity.episodeId,
                    seasonNumber = entity.seasonNumber,
                    episodeNumber = entity.episodeNumber,
                    isTV = entity.type == "tv"
                )
            }
        }
    }
    
    /**
     * 获取剧集封面URI
     */
    private suspend fun getEpisodePosterUri(tvId: String, seasonNumber: Int, episodeNumber: Int): String? {
        return try {
            val seasonDetails = com.google.jetstream.data.remote.TmdbService.getTvSeasonDetails(tvId, seasonNumber)
            val episode = seasonDetails?.episodes?.find { it.episodeNumber == episodeNumber }
            episode?.stillPath?.let { stillPath ->
                "https://image.tmdb.org/t/p/w500$stillPath"
            }
        } catch (e: Exception) {
            android.util.Log.w("RecentlyWatchedRepo", "获取剧集封面失败: tvId=$tvId, S${seasonNumber}E${episodeNumber}", e)
            null
        }
    }
    
    /**
     * 获取特定电影的最近观看记录
     */
    suspend fun getRecentlyWatchedByMovieId(movieId: String): RecentlyWatchedEntity? {
        return recentlyWatchedDao.getByMovieId(movieId)
    }
    
    /**
     * 删除特定电影的最近观看记录
     */
    suspend fun removeRecentlyWatched(movieId: String) {
        recentlyWatchedDao.deleteByMovieId(movieId)
    }
    
    /**
     * 清空所有最近观看记录
     */
    suspend fun clearAllRecentlyWatched() {
        recentlyWatchedDao.clearAll()
    }
    
    /**
     * 获取DAO实例（供需要直接操作数据库的组件使用）
     */
    fun getDao(): RecentlyWatchedDao {
        return recentlyWatchedDao
    }
}