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
        .map { id ->
            if (id == null) {
                VideoPlayerScreenUiState.Error
            } else {
                val details = repository.getMovieDetails(movieId = id)
                // 若数据库有对应的 WebDAV 源路径，则将其填入 videoUri 用于播放
                val entity = scrapedItemDao.getById(id)
                val webdavUri = entity?.sourcePath
                val detailsWithUri = if (!webdavUri.isNullOrBlank()) details.copy(videoUri = webdavUri) else details
                android.util.Log.i("VideoPlayerVM", "movieId=$id, finalPlayUri=${detailsWithUri.videoUri}")
                
                VideoPlayerScreenUiState.Done(movieDetails = detailsWithUri)
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
    data class Done(val movieDetails: MovieDetails) : VideoPlayerScreenUiState()
}
