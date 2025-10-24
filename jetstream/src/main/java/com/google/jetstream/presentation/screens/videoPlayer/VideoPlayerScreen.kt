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
import androidx.compose.foundation.layout.padding
import com.google.jetstream.data.webdav.WebDavService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
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
import com.google.jetstream.presentation.screens.videoPlayer.components.HoldSeekProgressBar
import com.google.jetstream.presentation.screens.videoPlayer.components.rememberVideoPlayerPulseState
import com.google.jetstream.presentation.screens.videoPlayer.components.rememberVideoPlayerState
import com.google.jetstream.presentation.utils.handleDPadKeyEvents
import android.view.KeyEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    var playerInitialized by remember { mutableStateOf(false) }

    // Hold-seek states
    val isHoldSeeking = remember { mutableStateOf(false) }
    val holdSeekPreviewMs = remember { mutableStateOf(0L) }
    val holdSeekDirection = remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    var holdSeekJob by remember { mutableStateOf<Job?>(null) }
    var holdStarterJob by remember { mutableStateOf<Job?>(null) }
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }

    fun startHold(direction: Int) {
        if (holdSeekJob != null) return
        holdSeekDirection.value = direction
        isHoldSeeking.value = true
        holdSeekPreviewMs.value = exoPlayer.currentPosition
        val duration = exoPlayer.duration.coerceAtLeast(0L)
        holdSeekJob = coroutineScope.launch {
            val startAt = System.currentTimeMillis()
            val intervalMs = 220L
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startAt
                // Accelerate step size based on hold duration (2x faster)
                val stepMs = when {
                    elapsed < 1000L -> 6000L
                    elapsed < 3000L -> 14000L
                    elapsed < 6000L -> 24000L
                    else -> 40000L
                }
                val next = (holdSeekPreviewMs.value + direction * stepMs)
                    .coerceIn(0L, if (duration > 0) duration else 0L)
                holdSeekPreviewMs.value = next
                try { exoPlayer.seekTo(next) } catch (_: Throwable) {}
                delay(intervalMs)
            }
        }
    }

    fun stopHold() {
        holdStarterJob?.cancel()
        holdStarterJob = null
        holdSeekJob?.cancel()
        holdSeekJob = null
        isHoldSeeking.value = false
        holdSeekDirection.value = 0
    }

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
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
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
        if (!playerInitialized) {
            val keepPositionMs = startPositionMs ?: 0L
            val mediaItem = MediaItem.Builder().setUri(movieDetails.videoUri).build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            try {
                if (keepPositionMs > 0) {
                    exoPlayer.seekTo(keepPositionMs)
                    android.util.Log.d("VideoPlayer", "Seeking to position: ${keepPositionMs}ms")
                }
                exoPlayer.playWhenReady = true
            } catch (_: Throwable) {}
            playerInitialized = true
            android.util.Log.d("VideoPlayer", "Video started")
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
        // 若控制栏可见，先隐藏控制栏
        if (videoPlayerState.isControlsVisible) {
            videoPlayerState.hideControls()
            return@BackHandler
        }

        // 控制栏已隐藏，则退出播放器
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
    })

    val pulseState = rememberVideoPlayerPulseState()

    Box(
        Modifier
            .onPreviewKeyEvent {
                // Handle long-press hold-seek when controls are hidden
                if (!videoPlayerState.isControlsVisible) {
                    when (it.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> {
                            if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                leftPressed = true
                                if (holdStarterJob == null && holdSeekJob == null) {
                                    holdStarterJob = coroutineScope.launch {
                                        delay(300)
                                        if (leftPressed && holdSeekJob == null) startHold(-1)
                                    }
                                }
                                // Don't consume to allow single-tap to be handled on ACTION_UP by dPadEvents
                                return@onPreviewKeyEvent false
                            } else if (it.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                leftPressed = false
                                holdStarterJob?.cancel(); holdStarterJob = null
                                if (holdSeekJob != null) {
                                    stopHold()
                                    return@onPreviewKeyEvent true
                                }
                                return@onPreviewKeyEvent false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> {
                            if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                rightPressed = true
                                if (holdStarterJob == null && holdSeekJob == null) {
                                    holdStarterJob = coroutineScope.launch {
                                        delay(300)
                                        if (rightPressed && holdSeekJob == null) startHold(1)
                                    }
                                }
                                return@onPreviewKeyEvent false
                            } else if (it.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                rightPressed = false
                                holdStarterJob?.cancel(); holdStarterJob = null
                                if (holdSeekJob != null) {
                                    stopHold()
                                    return@onPreviewKeyEvent true
                                }
                                return@onPreviewKeyEvent false
                            }
                        }
                    }
                }

                if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK && it.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    if (videoPlayerState.isControlsVisible) {
                        videoPlayerState.hideControls()
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
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
            showControls = { if (!isHoldSeeking.value) videoPlayerState.showControls(exoPlayer.isPlaying) },
            controls = {
                VideoPlayerControls(
                    player = exoPlayer,
                    movieDetails = movieDetails,
                    focusRequester = focusRequester,
                    onShowControls = { videoPlayerState.showControls(exoPlayer.isPlaying) },
                    onClickSubtitles = { 
                        // TODO: 实现字幕选择功能
                    },
                    onClickAudio = { 
                        // TODO: 实现音轨选择功能
                    }
                )
            }
        )

        // Separate progress bar shown only while hold-seeking with hidden controls
        if (isHoldSeeking.value) {
            HoldSeekProgressBar(
                progress = if (exoPlayer.duration > 0) holdSeekPreviewMs.value.toFloat() / exoPlayer.duration.toFloat() else 0f,
                currentPositionMs = holdSeekPreviewMs.value,
                durationMs = exoPlayer.duration,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
            )
        }

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

private fun Movie.intoMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(videoUri)
        .build()
}


