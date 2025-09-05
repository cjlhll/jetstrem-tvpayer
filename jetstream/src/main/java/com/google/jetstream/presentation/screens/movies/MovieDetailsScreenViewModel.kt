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

package com.google.jetstream.presentation.screens.movies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.jetstream.data.entities.MovieDetails
import com.google.jetstream.data.repositories.MovieRepository
import com.google.jetstream.data.database.dao.ScrapedItemDao
import com.google.jetstream.data.repositories.RecentlyWatchedRepository
import com.google.jetstream.data.repositories.ScrapedMoviesStore
import com.google.jetstream.data.repositories.ScrapedTvStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import com.google.jetstream.data.webdav.WebDavService
import com.google.jetstream.data.webdav.WebDavResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import android.net.Uri
import java.io.File
import com.google.jetstream.data.entities.Episode
import com.google.jetstream.data.remote.TmdbService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


@HiltViewModel
class MovieDetailsScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: MovieRepository,
    scrapedMoviesStore: ScrapedMoviesStore,
    scrapedTvStore: ScrapedTvStore,
    private val webDavService: WebDavService,
    private val scrapedItemDao: ScrapedItemDao,
    private val recentlyWatchedRepository: RecentlyWatchedRepository,
    private val episodeMatchingService: com.google.jetstream.data.services.EpisodeMatchingService,
    private val tvPlaybackService: com.google.jetstream.data.services.TvPlaybackService,
) : ViewModel() {
    
    // 剧集列表状态
    private val _episodesState = MutableStateFlow<EpisodesUiState>(EpisodesUiState.Loading)
    val episodesState: StateFlow<EpisodesUiState> = _episodesState.asStateFlow()
    private val _sourceInfoEpisode = MutableStateFlow<Episode?>(null)
    val sourceInfoEpisode: StateFlow<Episode?> = _sourceInfoEpisode.asStateFlow()

    val uiState = savedStateHandle
        .getStateFlow<String?>(MovieDetailsScreen.MovieIdBundleKey, null)
        .map { id ->
            if (id == null) {
                MovieDetailsScreenUiState.Error
            } else {
                // 并行加载详情与文件大小
                val details = repository.getMovieDetails(movieId = id)

                // 若数据库有已刮削的源路径，优先使用它
                val entity = try { scrapedItemDao.getById(id) } catch (_: Exception) { null }
                val merged = entity?.sourcePath?.takeIf { it.isNotBlank() }?.let { details.copy(videoUri = it) } ?: details

                val titleFromStores = scrapedMoviesStore.movies.value.firstOrNull { it.id == id }?.name
                    ?: scrapedTvStore.shows.value.firstOrNull { it.id == id }?.name
                val fixed = if (titleFromStores != null) merged.copy(name = titleFromStores) else merged

                val sizeBytes: Long? = try {
                    val url = fixed.videoUri
                    when {
                        url.startsWith("http") -> {
                            when (val r = webDavService.statFileSizeByUrl(url)) {
                                is WebDavResult.Success -> r.data
                                else -> null
                            }
                        }
                        url.startsWith("file") -> {
                            val uri = Uri.parse(url)
                            val path = uri.path
                            if (path != null) {
                                val file = File(path)
                                if (file.exists()) file.length() else null
                            } else null
                        }
                        else -> {
                            // For local paths without file:// scheme
                            val file = File(url)
                            if (file.exists()) file.length() else null
                        }
                    }
                } catch (_: Exception) { null }

                // 获取播放记录
                val recentlyWatched = try {
                    recentlyWatchedRepository.getRecentlyWatchedByMovieId(id)
                } catch (_: Exception) { null }

                // 如果是电视剧，获取用于显示源信息的剧集
                if (fixed.isTV) {
                    viewModelScope.launch {
                        val playbackInfo = tvPlaybackService.getPlaybackInfo(fixed.id, fixed)
                        _sourceInfoEpisode.value = playbackInfo?.episode
                    }
                }

                MovieDetailsScreenUiState.Done(
                    movieDetails = fixed, 
                    fileSizeBytes = sizeBytes,
                    recentlyWatched = recentlyWatched
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MovieDetailsScreenUiState.Loading
        )
    
    /**
     * 获取指定季的剧集列表（只显示本地存在的剧集）
     */
    fun loadEpisodes(tvId: String, seasonNumber: Int) {
        viewModelScope.launch {
            _episodesState.value = EpisodesUiState.Loading
            try {
                // 获取本地季信息
                val localSeasons = getLocalSeasonsForTv(tvId)
                
                // 使用EpisodeMatchingService获取过滤后的剧集列表
                val filteredEpisodes = episodeMatchingService.getFilteredEpisodes(
                    tvId = tvId,
                    seasonNumber = seasonNumber,
                    localSeasons = localSeasons
                )
                
                if (filteredEpisodes.isNotEmpty()) {
                    // 为每个剧集添加播放进度信息
                    val episodesWithProgress = addPlaybackProgressToEpisodes(tvId, filteredEpisodes)
                    _episodesState.value = EpisodesUiState.Success(episodesWithProgress)
                } else {
                    _episodesState.value = EpisodesUiState.Error("本地没有找到该季的剧集文件")
                }
            } catch (e: Exception) {
                _episodesState.value = EpisodesUiState.Error(e.message ?: "获取剧集信息失败")
            }
        }
    }
    
    /**
     * 为剧集列表添加播放进度信息
     */
    private suspend fun addPlaybackProgressToEpisodes(tvId: String, episodes: List<Episode>): List<Episode> {
        return try {
            // 获取该电视剧的播放历史记录
            val recentlyWatched = recentlyWatchedRepository.getRecentlyWatchedByMovieId(tvId)
            
            episodes.map { episode ->
                // 检查当前剧集是否有播放记录
                if (recentlyWatched != null && 
                    recentlyWatched.episodeId == episode.id &&
                    recentlyWatched.seasonNumber == episode.seasonNumber &&
                    recentlyWatched.episodeNumber == episode.episodeNumber) {
                    // 有播放记录，添加进度信息
                    episode.copy(
                        watchProgress = recentlyWatched.watchProgress,
                        currentPositionMs = recentlyWatched.currentPositionMs,
                        durationMs = recentlyWatched.durationMs ?: (episode.runtime?.let { it * 60 * 1000L })
                    )
                } else {
                    // 没有播放记录，保持原样
                    episode
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MovieDetailsVM", "添加播放进度信息失败: tvId=$tvId", e)
            episodes // 如果失败，返回原始列表
        }
    }
    
    /**
     * 从数据库获取本地季信息
     */
    private suspend fun getLocalSeasonsForTv(tvId: String): List<com.google.jetstream.data.entities.TvSeason> {
        return try {
            val scrapedItem = scrapedItemDao.getById(tvId)
            if (scrapedItem?.availableSeasons != null) {
                kotlinx.serialization.json.Json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(com.google.jetstream.data.entities.TvSeason.serializer()),
                    scrapedItem.availableSeasons
                )
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.w("MovieDetailsVM", "获取本地季信息失败: $tvId", e)
            emptyList()
        }
    }
    
    /**
     * 清空剧集列表
     */
    fun clearEpisodes() {
        _episodesState.value = EpisodesUiState.Loading
    }
    
    /**
     * 开始电视剧播放（智能播放逻辑）
     */
    fun startTvPlayback(movieDetails: MovieDetails, onEpisodeSelected: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val playbackInfo = tvPlaybackService.getPlaybackInfo(movieDetails.id, movieDetails)
                if (playbackInfo != null) {
                    // 找到了可播放的剧集
                    android.util.Log.d("MovieDetailsVM", 
                        "开始播放: 第${playbackInfo.episode.seasonNumber}季第${playbackInfo.episode.episodeNumber}集, " +
                        "起始位置: ${playbackInfo.startPositionMs}ms, 是否续播: ${playbackInfo.isResuming}")
                    
                    // 调用播放回调，传入剧集ID
                    onEpisodeSelected(playbackInfo.episode.id)
                } else {
                    android.util.Log.w("MovieDetailsVM", "无法获取播放信息: ${movieDetails.name}")
                    // TODO: 可以显示错误提示给用户
                }
            } catch (e: Exception) {
                android.util.Log.e("MovieDetailsVM", "开始播放失败: ${movieDetails.name}", e)
                // TODO: 可以显示错误提示给用户
            }
        }
    }
}

sealed class MovieDetailsScreenUiState {
    data object Loading : MovieDetailsScreenUiState()
    data object Error : MovieDetailsScreenUiState()
    data class Done(
        val movieDetails: MovieDetails, 
        val fileSizeBytes: Long? = null,
        val recentlyWatched: com.google.jetstream.data.database.entities.RecentlyWatchedEntity? = null
    ) : MovieDetailsScreenUiState()
}

sealed class EpisodesUiState {
    data object Loading : EpisodesUiState()
    data class Success(val episodes: List<Episode>) : EpisodesUiState()
    data class Error(val message: String) : EpisodesUiState()
}