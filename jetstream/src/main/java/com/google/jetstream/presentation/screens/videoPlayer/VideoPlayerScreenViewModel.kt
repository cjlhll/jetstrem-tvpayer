/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jetstream.presentation.screens.videoPlayer

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.jetstream.data.entities.MovieDetails
import android.util.Base64
import com.google.jetstream.data.webdav.WebDavService
import com.google.jetstream.data.repositories.MovieRepository
import com.google.jetstream.data.repositories.RecentlyWatchedRepository
import com.google.jetstream.data.database.dao.ScrapedItemDao
import com.google.jetstream.data.database.dao.WebDavConfigDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class VideoPlayerScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MovieRepository,
    private val recentlyWatchedRepository: RecentlyWatchedRepository,
    private val scrapedItemDao: ScrapedItemDao,
    private val webDavService: WebDavService,
    private val webDavConfigDao: WebDavConfigDao,
    private val tvPlaybackService: com.google.jetstream.data.services.TvPlaybackService,
) : ViewModel() {

    private suspend fun buildAuthHeaders(): Map<String, String> {
        // 优先从 WebDavService（运行时）获取；若为空，尝试从数据库读取最后一次配置
        val cfg = webDavService.getCurrentConfig() ?: run {
            val latest = webDavConfigDao.getAllConfigs().first().firstOrNull()
            if (latest != null) {
                com.google.jetstream.data.webdav.WebDavConfig(
                    serverUrl = latest.serverUrl,
                    username = latest.username,
                    password = latest.password,
                    displayName = latest.displayName,
                    isEnabled = true
                )
            } else null
        }
        return if (cfg != null && cfg.isValid()) {
            val creds = "${cfg.username}:${cfg.password}"
            val token = Base64.encodeToString(creds.toByteArray(), Base64.NO_WRAP)
            mapOf("Authorization" to "Basic $token")
        } else emptyMap()
    }
    
    /**
     * 添加电影到最近观看记录
     */
    fun addToRecentlyWatched(movieDetails: MovieDetails) {
        viewModelScope.launch {
            try {
                recentlyWatchedRepository.addRecentlyWatched(movieDetails)
                android.util.Log.d("VideoPlayerVM", "Added to recently watched: ${movieDetails.name}")
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayerVM", "Failed to add to recently watched", e)
            }
        }
    }

    /**
     * 保存播放进度到最近观看记录
     */
    fun saveWatchProgress(movieDetails: MovieDetails, currentPositionMs: Long, durationMs: Long) {
        viewModelScope.launch {
            try {
                if (movieDetails.isTV) {
                    // 电视剧：需要保存剧集信息
                    // TODO: 这里需要获取当前播放的剧集信息
                    // 暂时使用基础的保存方法
                    recentlyWatchedRepository.addRecentlyWatched(movieDetails, currentPositionMs, durationMs)
                    android.util.Log.d("VideoPlayerVM", "Saved TV progress: ${movieDetails.name}, position: ${currentPositionMs}ms, duration: ${durationMs}ms")
                } else {
                    // 电影：使用原有逻辑
                    recentlyWatchedRepository.addRecentlyWatched(movieDetails, currentPositionMs, durationMs)
                    android.util.Log.d("VideoPlayerVM", "Saved movie progress: ${movieDetails.name}, position: ${currentPositionMs}ms, duration: ${durationMs}ms")
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayerVM", "Failed to save watch progress", e)
            }
        }
    }
    
    /**
     * 保存电视剧剧集播放进度
     */
    fun saveEpisodeWatchProgress(
        movieDetails: MovieDetails, 
        episodeId: String,
        episodeNumber: Int,
        seasonNumber: Int,
        episodeTitle: String,
        currentPositionMs: Long, 
        durationMs: Long
    ) {
        viewModelScope.launch {
            try {
                // 创建包含剧集信息的播放记录
                val watchProgress = if (durationMs > 0) {
                    (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else null
                
                val entity = com.google.jetstream.data.database.entities.RecentlyWatchedEntity(
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
                    episodeId = episodeId,
                    episodeNumber = episodeNumber,
                    seasonNumber = seasonNumber,
                    episodeTitle = episodeTitle
                )
                
                // 直接使用DAO保存
                val recentlyWatchedDao = recentlyWatchedRepository.getDao()
                recentlyWatchedDao.insertOrUpdate(entity)
                
                android.util.Log.d("VideoPlayerVM", "Saved episode progress: ${movieDetails.name} 第${seasonNumber}季第${episodeNumber}集 ${currentPositionMs}ms")
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayerVM", "Failed to save episode progress", e)
            }
        }
    }
    
    /**
     * 构建剧集播放URL
     */
    private suspend fun buildEpisodeVideoUri(
        baseUri: String,
        seasonNumber: Int,
        episodeNumber: Int,
        entity: com.google.jetstream.data.database.entities.ScrapedItemEntity?
    ): String {
        try {
            // 如果有本地季信息，尝试从中获取具体的剧集文件路径
            if (entity?.availableSeasons != null) {
                val seasons = kotlinx.serialization.json.Json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(com.google.jetstream.data.entities.TvSeason.serializer()),
                    entity.availableSeasons
                )
                
                val targetSeason = seasons.find { it.number == seasonNumber }
                if (targetSeason != null) {
                    // 获取该季的本地文件列表
                    val localFiles = getLocalVideoFiles(targetSeason.webDavPath)
                    val matchedFile = findMatchingLocalFile(episodeNumber, localFiles)
                    
                    if (matchedFile != null) {
                        // 构建完整的WebDAV URL
                        val base = webDavService.getCurrentConfig()?.getFormattedServerUrl()?.removeSuffix("/") ?: ""
                        val seasonPath = targetSeason.webDavPath.trim('/')
                        val fullUrl = if (base.isNotBlank()) "$base/$seasonPath/$matchedFile" else "$seasonPath/$matchedFile"
                        android.util.Log.d("VideoPlayerVM", "构建剧集URL: $fullUrl")
                        return fullUrl
                    }
                }
            }
            
            // 如果无法获取具体文件，返回基础URI
            android.util.Log.w("VideoPlayerVM", "无法构建具体剧集URL，使用基础URI: $baseUri")
            return baseUri
            
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayerVM", "构建剧集URL失败", e)
            return baseUri
        }
    }
    
    /**
     * 获取本地视频文件列表
     */
    private suspend fun getLocalVideoFiles(webDavPath: String): List<String> {
        return try {
            when (val result = webDavService.listDirectory(webDavPath)) {
                is com.google.jetstream.data.webdav.WebDavResult.Success -> {
                    result.data
                        .filter { !it.isDirectory && isVideoFile(it.name) }
                        .map { it.name }
                        .sorted()
                }
                else -> {
                    android.util.Log.w("VideoPlayerVM", "无法列出目录: $webDavPath")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayerVM", "获取本地文件失败: $webDavPath", e)
            emptyList()
        }
    }
    
    /**
     * 查找与指定集数匹配的本地文件
     */
    private fun findMatchingLocalFile(episodeNumber: Int, localFiles: List<String>): String? {
        val patterns = listOf(
            Regex("(?i)s\\d{1,2}[ _.-]?e0*${episodeNumber}(?![0-9])"),
            Regex("(?i)^0*${episodeNumber}[ _.\\-]"),
            Regex("(?i)第\\s*0*${episodeNumber}\\s*集"),
            Regex("(?i)\\b(e|ep)\\s*0*${episodeNumber}(?![0-9])"),
            Regex("(?i)\\b0*${episodeNumber}(?![0-9])")
        )
        
        for (pattern in patterns) {
            val matchedFile = localFiles.find { fileName ->
                pattern.containsMatchIn(fileName)
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
    
    // 基本认证请求头（若运行时未配置，会从数据库读取最近配置）
    lateinit var headers: Map<String, String>

    init {
        // headers 初始化放到 init 中执行挂起读取
        // 这里简单地同步调用，若需要严格的异步安全可改为状态流
        runCatching {
            kotlinx.coroutines.runBlocking { headers = buildAuthHeaders() }
        }.onFailure {
            headers = emptyMap()
        }
    }
    val uiState = savedStateHandle
        .getStateFlow<String?>(VideoPlayerScreen.MovieIdBundleKey, null)
        .map { movieId ->
            if (movieId == null) {
                VideoPlayerScreenUiState.Error
            } else {
                // 获取剧集ID（如果有）
                val episodeId = savedStateHandle.get<String?>(VideoPlayerScreen.EpisodeIdBundleKey)
                
                val details = repository.getMovieDetails(movieId = movieId)
                // 若数据库有对应的 WebDAV 源路径，则将其填入 videoUri 用于播放
                val entity = scrapedItemDao.getById(movieId)
                val webdavUri = entity?.sourcePath
                val detailsWithUri = if (!webdavUri.isNullOrBlank()) details.copy(videoUri = webdavUri) else details
                
                // 获取播放记录，用于从上次位置开始播放
                val recentlyWatched = try {
                    recentlyWatchedRepository.getRecentlyWatchedByMovieId(movieId)
                } catch (_: Exception) { null }
                
                // 如果是电视剧且指定了剧集ID，获取剧集播放信息
                if (detailsWithUri.isTV && !episodeId.isNullOrBlank()) {
                    android.util.Log.i("VideoPlayerVM", "播放电视剧剧集: movieId=$movieId, episodeId=$episodeId")
                    
                    // 获取剧集播放信息
                    val playbackInfo = try {
                        tvPlaybackService.getPlaybackInfo(movieId, detailsWithUri)
                    } catch (e: Exception) {
                        android.util.Log.e("VideoPlayerVM", "获取剧集播放信息失败", e)
                        null
                    }
                    
                    if (playbackInfo != null && playbackInfo.episode.id == episodeId) {
                        // 构建剧集播放URL（基于季和集数）
                        val episodeVideoUri = buildEpisodeVideoUri(
                            baseUri = detailsWithUri.videoUri,
                            seasonNumber = playbackInfo.episode.seasonNumber,
                            episodeNumber = playbackInfo.episode.episodeNumber,
                            entity = entity
                        )
                        
                        val episodeDetails = detailsWithUri.copy(
                            videoUri = episodeVideoUri,
                            name = "${detailsWithUri.name} - 第${playbackInfo.episode.seasonNumber}季第${playbackInfo.episode.episodeNumber}集"
                        )
                        
                        android.util.Log.i("VideoPlayerVM", "剧集播放URL: $episodeVideoUri, 起始位置: ${playbackInfo.startPositionMs}ms")
                        
                        VideoPlayerScreenUiState.Done(
                            movieDetails = episodeDetails,
                            startPositionMs = playbackInfo.startPositionMs,
                            episodeId = episodeId
                        )
                    } else {
                        android.util.Log.w("VideoPlayerVM", "无法获取剧集播放信息，使用默认设置")
                        VideoPlayerScreenUiState.Done(
                            movieDetails = detailsWithUri,
                            startPositionMs = recentlyWatched?.currentPositionMs,
                            episodeId = episodeId
                        )
                    }
                } else {
                    android.util.Log.i("VideoPlayerVM", "播放电影: movieId=$movieId, finalPlayUri=${detailsWithUri.videoUri}, startPosition=${recentlyWatched?.currentPositionMs}ms")
                    
                    VideoPlayerScreenUiState.Done(
                        movieDetails = detailsWithUri,
                        startPositionMs = recentlyWatched?.currentPositionMs
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = VideoPlayerScreenUiState.Loading
        )
}



@Immutable
sealed class VideoPlayerScreenUiState {
    data object Loading : VideoPlayerScreenUiState()
    data object Error : VideoPlayerScreenUiState()
    data class Done(
        val movieDetails: MovieDetails,
        val startPositionMs: Long? = null,
        val episodeId: String? = null // 剧集ID（如果是电视剧）
    ) : VideoPlayerScreenUiState()
}
