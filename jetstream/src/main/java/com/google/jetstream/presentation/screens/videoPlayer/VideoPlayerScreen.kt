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

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import com.google.jetstream.data.webdav.WebDavService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import com.google.jetstream.data.entities.Movie
import com.google.jetstream.data.entities.MovieDetails
import com.google.jetstream.presentation.common.Error
import com.google.jetstream.presentation.common.Loading
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerControls
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerOverlay
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerPulse
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerPulse.Type.BACK
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerPulse.Type.FORWARD
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerPulseState
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerState
import com.google.jetstream.presentation.screens.videoPlayer.components.rememberPlayer
import com.google.jetstream.presentation.screens.videoPlayer.components.PlayerSubtitles
import com.google.jetstream.presentation.screens.videoPlayer.components.rememberVideoPlayerPulseState
import com.google.jetstream.presentation.screens.videoPlayer.components.rememberVideoPlayerState
import com.google.jetstream.presentation.utils.handleDPadKeyEvents

object VideoPlayerScreen {
    const val MovieIdBundleKey = "movieId"
    const val EpisodeIdBundleKey = "episodeId"
}

/**
 * [Work in progress] A composable screen for playing a video.
 *
 * @param onBackPressed The callback to invoke when the user presses the back button.
 * @param videoPlayerScreenViewModel The view model for the video player screen.
 */
@Composable
fun VideoPlayerScreen(
    onBackPressed: () -> Unit,
    videoPlayerScreenViewModel: VideoPlayerScreenViewModel = hiltViewModel()
) {
    val uiState by videoPlayerScreenViewModel.uiState.collectAsStateWithLifecycle()

    // TODO: Handle Loading & Error states
    when (val s = uiState) {
        is VideoPlayerScreenUiState.Loading -> {
            Loading(modifier = Modifier.fillMaxSize())
        }

        is VideoPlayerScreenUiState.Error -> {
            Error(modifier = Modifier.fillMaxSize())
        }

        is VideoPlayerScreenUiState.Done -> {
            VideoPlayerScreenContent(
                movieDetails = s.movieDetails,
                startPositionMs = s.startPositionMs,
                onBackPressed = onBackPressed,
                headers = videoPlayerScreenViewModel.headers,
                onVideoStarted = {
                    // 当视频实际开始播放时，记录到最近观看
                    if (s.movieDetails.isTV && s.episodeId != null) {
                        // 电视剧：保存剧集信息到最近观看
                        videoPlayerScreenViewModel.saveCurrentEpisodeProgress(
                            movieDetails = s.movieDetails,
                            episodeId = s.episodeId,
                            currentPositionMs = 0L, // 刚开始播放
                            durationMs = 0L // 开始时还不知道总时长
                        )
                    } else {
                        // 电影：使用原有逻辑
                        videoPlayerScreenViewModel.addToRecentlyWatched(s.movieDetails)
                    }
                },
                onSaveProgress = { currentPositionMs, durationMs ->
                    // 根据内容类型保存播放进度
                    if (s.movieDetails.isTV && s.episodeId != null) {
                        // 电视剧：需要保存剧集信息
                        // 从TvPlaybackService获取当前播放的剧集信息
                        videoPlayerScreenViewModel.saveCurrentEpisodeProgress(
                            movieDetails = s.movieDetails,
                            episodeId = s.episodeId,
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs
                        )
                    } else {
                        // 电影：使用原有逻辑
                        videoPlayerScreenViewModel.saveWatchProgress(s.movieDetails, currentPositionMs, durationMs)
                    }
                }
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreenContent(
    movieDetails: MovieDetails,
    startPositionMs: Long? = null,
    onBackPressed: () -> Unit, 
    headers: Map<String, String>,
    onVideoStarted: () -> Unit = {},
    onSaveProgress: (currentPositionMs: Long, durationMs: Long) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    // 优先用 SurfaceView 以保证 HDR/色彩路径

    val exoPlayer = rememberPlayer(context, headers)

    // 在离开页面或销毁时停止并释放播放器，避免后台继续播放
    androidx.compose.runtime.DisposableEffect(exoPlayer) {
        onDispose {
            try {
                // 保存播放进度
                val currentPosition = exoPlayer.currentPosition
                val duration = exoPlayer.duration
                if (currentPosition > 0 && duration > 0) {
                    onSaveProgress(currentPosition, duration)
                }
                
                exoPlayer.playWhenReady = false
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            } catch (_: Throwable) {}
            try { exoPlayer.release() } catch (_: Throwable) {}
        }
    }

    // 当应用进入后台时暂停播放
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                try {
                    exoPlayer.playWhenReady = false
                    exoPlayer.pause()
                } catch (_: Throwable) {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val videoPlayerState = rememberVideoPlayerState(
        hideSeconds = 4,
    )

        android.util.Log.i("VideoPlayer", "准备播放 URL: ${movieDetails.videoUri}, startPosition: ${startPositionMs}ms")
    LaunchedEffect(exoPlayer, movieDetails, startPositionMs) {
        val item = movieDetails.intoMediaItem()
        android.util.Log.d("VideoPlayer", "addMediaItem: uri=${item.localConfiguration?.uri} subCount=${item.localConfiguration?.subtitleConfigurations?.size}")
        item.localConfiguration?.subtitleConfigurations?.forEachIndexed { idx, sc ->
            android.util.Log.d("VideoPlayer", "subtitle[$idx]: uri=${sc.uri} mime=${sc.mimeType} lang=${sc.language} flags=${sc.selectionFlags}")
        }
        exoPlayer.addMediaItem(item)
        exoPlayer.prepare()
        
        // 如果有播放记录，设置播放位置
        if (startPositionMs != null && startPositionMs > 0) {
            exoPlayer.seekTo(startPositionMs)
            android.util.Log.d("VideoPlayer", "Seeking to position: ${startPositionMs}ms")
        }
    }
    
    // 监听播放状态，当开始播放时记录到最近观看
    androidx.compose.runtime.DisposableEffect(exoPlayer) {
        var hasStartedPlaying = false
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY && exoPlayer.playWhenReady && !hasStartedPlaying) {
                    hasStartedPlaying = true
                    onVideoStarted()
                    android.util.Log.d("VideoPlayer", "Video started playing, adding to recently watched")
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            try {
                exoPlayer.removeListener(listener)
            } catch (_: Throwable) {}
        }
    }

    BackHandler(onBack = {
        if (videoPlayerState.isControlsVisible) {
            videoPlayerState.hideControls()
        } else {
            try {
                // 保存播放进度
                val currentPosition = exoPlayer.currentPosition
                val duration = exoPlayer.duration
                if (currentPosition > 0 && duration > 0) {
                    onSaveProgress(currentPosition, duration)
                }
                
                exoPlayer.playWhenReady = false
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            } catch (_: Throwable) {}
            onBackPressed()
        }
    })

    val pulseState = rememberVideoPlayerPulseState()

    Box(
        Modifier
            .dPadEvents(
                exoPlayer,
                videoPlayerState,
                pulseState
            )
            .focusable()
    ) {
        PlayerSurface(
            player = exoPlayer,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.resizeWithContentScale(
                contentScale = ContentScale.Fit,
                sourceSizeDp = null
                // 如果设备不支持 HDR，会由系统进行 HDR->SDR 映射；SurfaceView 更能避免 TextureView 颜色空间剪裁导致的发黑问题

            )
        )

        val focusRequester = remember { FocusRequester() }
        VideoPlayerOverlay(
            modifier = Modifier.align(Alignment.BottomCenter),
            focusRequester = focusRequester,
            isPlaying = exoPlayer.isPlaying,
            isControlsVisible = videoPlayerState.isControlsVisible,
            centerButton = { VideoPlayerPulse(pulseState) },
            subtitles = { PlayerSubtitles(player = exoPlayer) },
            showControls = videoPlayerState::showControls,
            controls = {
                VideoPlayerControls(
                    player = exoPlayer,
                    movieDetails = movieDetails,
                    focusRequester = focusRequester,
                    onShowControls = { videoPlayerState.showControls(exoPlayer.isPlaying) },
                )
            }
        )
    }
}

private fun Modifier.dPadEvents(
    exoPlayer: ExoPlayer,
    videoPlayerState: VideoPlayerState,
    pulseState: VideoPlayerPulseState
): Modifier = this.handleDPadKeyEvents(
    onLeft = {
        if (!videoPlayerState.isControlsVisible) {
            exoPlayer.seekBack()
            pulseState.setType(BACK)
        }
    },
    onRight = {
        if (!videoPlayerState.isControlsVisible) {
            exoPlayer.seekForward()
            pulseState.setType(FORWARD)
        }
    },
    onUp = { videoPlayerState.showControls() },
    onDown = { videoPlayerState.showControls() },
    onEnter = {
        exoPlayer.pause()
        videoPlayerState.showControls()
    }
)

private const val FIXED_SUBTITLE_URL = "https://raw.githubusercontent.com/cjlhll/openclash-rules/refs/heads/main/Shaolin.Soccer.2001.CHINESE.1080p.BluRay.x264.DTS-HD.MA.5.1-FGT.vtt"

private fun MovieDetails.intoMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(videoUri)
        .setSubtitleConfigurations(
            listOf(
                MediaItem.SubtitleConfiguration
                    .Builder(Uri.parse(FIXED_SUBTITLE_URL))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage("zh")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            )
        ).build()
}

private fun Movie.intoMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(videoUri)
        .setSubtitleConfigurations(
            listOf(
                MediaItem.SubtitleConfiguration
                    .Builder(Uri.parse(FIXED_SUBTITLE_URL))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage("zh")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            )
        )
        .build()
}
