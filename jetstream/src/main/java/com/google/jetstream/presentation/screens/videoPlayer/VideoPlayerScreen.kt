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

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.jetstream.data.entities.MovieDetails
import com.google.jetstream.presentation.common.Error
import com.google.jetstream.presentation.common.Loading
import com.google.jetstream.presentation.screens.videoPlayer.components.HoldSeekProgressBar
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerControls
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerOverlay
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerPulse
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerPulse.Type.BACK
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerPulse.Type.FORWARD
import androidx.media3.ui.PlayerView
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerPulseState
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerState
import com.google.jetstream.presentation.screens.videoPlayer.components.rememberVideoPlayerPulseState
import com.google.jetstream.presentation.screens.videoPlayer.components.rememberVideoPlayerState
import com.google.jetstream.presentation.screens.videoPlayer.subtitle.SubtitleConfigDialog
import com.google.jetstream.presentation.screens.videoPlayer.subtitle.SubtitleManager
import com.google.jetstream.presentation.screens.videoPlayer.subtitle.SubtitleOverlay
import com.google.jetstream.presentation.utils.handleDPadKeyEvents
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
                        videoPlayerScreenViewModel.saveCurrentEpisodeProgress(
                            movieDetails = s.movieDetails,
                            episodeId = s.episodeId,
                            currentPositionMs = 0L,
                            durationMs = 0L
                        )
                    } else {
                        videoPlayerScreenViewModel.addToRecentlyWatched(s.movieDetails)
                    }
                },
                onSaveProgress = { currentPositionMs, durationMs ->
                    if (s.movieDetails.isTV && s.episodeId != null) {
                        videoPlayerScreenViewModel.saveCurrentEpisodeProgress(
                            movieDetails = s.movieDetails,
                            episodeId = s.episodeId,
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs
                        )
                    } else {
                        videoPlayerScreenViewModel.saveWatchProgress(s.movieDetails, currentPositionMs, durationMs)
                    }
                }
            )
        }
    }
}

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
    val coroutineScope = rememberCoroutineScope()

    // 创建并记住 ExoPlayer 实例
    val exoPlayer = remember {
        // 创建支持自定义Headers的HttpDataSource工厂
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("JetStream/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setDefaultRequestProperties(headers)

        // 创建MediaSource工厂
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        // 创建ExoPlayer
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                // 设置字幕选择参数
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setPreferredTextLanguage("zh")
                    .setSelectUndeterminedTextLanguage(true)
                    .build()
            }
    }

    var playerInitialized by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var bufferingProgress by remember { mutableStateOf(0) }

    // 辅助函数：获取播放状态字符串
    fun getStateString(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE(空闲)"
        Player.STATE_BUFFERING -> "BUFFERING(缓冲中)"
        Player.STATE_READY -> "READY(就绪)"
        Player.STATE_ENDED -> "ENDED(结束)"
        else -> "UNKNOWN($state)"
    }

    // 字幕管理器
    val subtitleManager = remember { SubtitleManager(context) }
    val currentSubtitle by subtitleManager.currentSubtitle
    val subtitleEnabled by subtitleManager.enabled
    var showSubtitleConfig by remember { mutableStateOf(false) }

    // 初始化字幕管理器和播放器
    LaunchedEffect(Unit) {
        subtitleManager.setMovieName(movieDetails.name)
        subtitleManager.startSync(coroutineScope, exoPlayer)

        // 准备播放媒体
        val mediaItem = MediaItem.Builder()
            .setUri(movieDetails.videoUri)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        // 设置起始位置
        if (startPositionMs != null && startPositionMs > 0) {
            exoPlayer.seekTo(startPositionMs)
            android.util.Log.d("VideoPlayer", "Setting start position: ${startPositionMs}ms")
        }

        // 添加播放器监听器
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                android.util.Log.d("VideoPlayer", "播放状态变化: ${getStateString(playbackState)}")
                
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                        android.util.Log.d("VideoPlayer", "缓冲中... 已缓冲: ${exoPlayer.bufferedPercentage}%")
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        if (!playerInitialized) {
                            playerInitialized = true
                            onVideoStarted()
                            android.util.Log.d("VideoPlayer", "Video prepared and ready")
                            // 检测并设置内嵌字幕
                            subtitleManager.detectEmbeddedSubtitles(exoPlayer)
                        }
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        android.util.Log.d("VideoPlayer", "播放结束")
                    }
                    Player.STATE_IDLE -> {
                        android.util.Log.d("VideoPlayer", "播放器空闲")
                    }
                }
            }
            
            override fun onIsLoadingChanged(isLoading: Boolean) {
                android.util.Log.d("VideoPlayer", "加载状态: $isLoading")
            }
        })
        
        // 单独启动一个协程来监听缓冲进度
        launch {
            while (isActive) {
                if (isBuffering) {
                    val newProgress = exoPlayer.bufferedPercentage
                    if (newProgress != bufferingProgress) {
                        bufferingProgress = newProgress
                        android.util.Log.d("VideoPlayer", "缓冲进度: $bufferingProgress%")
                    }
                }
                delay(500)
            }
        }

        // 开始播放
        exoPlayer.playWhenReady = true
        android.util.Log.d("VideoPlayer", "ExoPlayer initialized and started")
    }

    // Hold-seek states
    val isHoldSeeking = remember { mutableStateOf(false) }
    val holdSeekPreviewMs = remember { mutableLongStateOf(0L) }
    val holdSeekDirection = remember { mutableStateOf(0) }
    var holdSeekJob by remember { mutableStateOf<Job?>(null) }
    var holdStarterJob by remember { mutableStateOf<Job?>(null) }
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }
    // 追踪是否正在快进（包括短按和长按），用于隐藏缓冲图标
    var isSeeking by remember { mutableStateOf(false) }
    var seekHideJob by remember { mutableStateOf<Job?>(null) }
    // 进度条可见性单独管理，避免闪烁
    var seekOverlayVisible by remember { mutableStateOf(false) }
    var seekOverlayHideJob by remember { mutableStateOf<Job?>(null) }

    fun startHold(direction: Int) {
        if (holdSeekJob != null) return
        holdSeekDirection.value = direction
        isHoldSeeking.value = true
        isSeeking = true
        seekHideJob?.cancel()
        // 长按开始时立即显示进度条，并取消隐藏计时
        seekOverlayVisible = true
        seekOverlayHideJob?.cancel()
        holdSeekPreviewMs.longValue = exoPlayer.currentPosition
        val duration = exoPlayer.duration.coerceAtLeast(0L)
        android.util.Log.d("VideoPlayer", "开始长按快进/快退，方向: $direction, 当前位置: ${holdSeekPreviewMs.longValue}ms, 总时长: ${duration}ms")
        holdSeekJob = coroutineScope.launch {
            val startAt = System.currentTimeMillis()
            val intervalMs = 100L  // 从220ms降低到100ms，提高刷新频率
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startAt
                // 按照100ms间隔调整每次步进的大小，保持整体速度一致
                val stepMs = when {
                    elapsed < 1000L -> 2700L   // ~6s/s
                    elapsed < 3000L -> 6400L   // ~14s/s
                    elapsed < 6000L -> 11000L  // ~24s/s
                    else -> 18000L             // ~40s/s
                }
                val next = (holdSeekPreviewMs.longValue + direction * stepMs)
                    .coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
                holdSeekPreviewMs.longValue = next
                exoPlayer.seekTo(next)
                delay(intervalMs)
            }
        }
    }

    fun stopHold() {
        holdStarterJob?.cancel()
        holdStarterJob = null
        holdSeekJob?.cancel()
        holdSeekJob = null
        android.util.Log.d("VideoPlayer", "停止长按快进/快退")
        isHoldSeeking.value = false
        holdSeekDirection.value = 0
        // 延迟清除seeking标志，给缓冲一些时间
        seekHideJob = coroutineScope.launch {
            delay(500)
            isSeeking = false
        }
        // 长按结束后延迟隐藏进度条，避免闪烁
        seekOverlayHideJob?.cancel()
        seekOverlayHideJob = coroutineScope.launch {
            delay(1200)
            seekOverlayVisible = false
        }
    }

    // 在离开页面或销毁时停止并释放播放器
    DisposableEffect(Unit) {
        onDispose {
            try {
                subtitleManager.stopSync()

                val currentPosition = exoPlayer.currentPosition
                val duration = exoPlayer.duration
                if (currentPosition > 0 && duration > 0) {
                    onSaveProgress(currentPosition, duration)
                }

                exoPlayer.release()
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayer", "Error releasing player", e)
            }
        }
    }

    // 当应用进入后台时暂停播放
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                exoPlayer.play()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val videoPlayerState = rememberVideoPlayerState(hideSeconds = 4)

    android.util.Log.i("VideoPlayer", "准备播放 URL: ${movieDetails.videoUri}, startPosition: ${startPositionMs}ms")

    BackHandler(onBack = {
        if (videoPlayerState.isControlsVisible) {
            videoPlayerState.hideControls()
            return@BackHandler
        }

        try {
            val currentPosition = exoPlayer.currentPosition
            val duration = exoPlayer.duration
            if (currentPosition > 0 && duration > 0) {
                onSaveProgress(currentPosition, duration)
            }
            exoPlayer.release()
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayer", "Error on back", e)
        }
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
                                // 不消费事件，让dPadEvents处理短按
                                return@onPreviewKeyEvent false
                            } else if (it.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                leftPressed = false
                                holdStarterJob?.cancel()
                                holdStarterJob = null
                                if (holdSeekJob != null) {
                                    // 长按结束，消费事件防止dPadEvents再次处理
                                    stopHold()
                                    return@onPreviewKeyEvent true
                                }
                                // 短按，不消费，让dPadEvents处理
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
                                holdStarterJob?.cancel()
                                holdStarterJob = null
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
                videoPlayerState,
                pulseState,
                exoPlayer,
                onSeekStart = {
                    isSeeking = true
                    // 短按开始显示进度条
                    seekOverlayVisible = true
                    seekOverlayHideJob?.cancel()
                    seekHideJob?.cancel()
                },
                onSeekEnd = {
                    // 先在较短时间后关闭 seeking 状态，用于缓冲遮罩
                    seekHideJob = coroutineScope.launch {
                        delay(500)
                        isSeeking = false
                    }
                    // 进度条延迟更久再隐藏，保证视觉稳定
                    seekOverlayHideJob?.cancel()
                    seekOverlayHideJob = coroutineScope.launch {
                        delay(1200)
                        seekOverlayVisible = false
                    }
                }
            )
            .focusable()
    ) {
        // 使用 AndroidView 渲染 PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // 使用自定义控制器
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        val focusRequester = remember { FocusRequester() }

        // 轮询获取播放状态
        var isPlaying by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (isActive) {
                isPlaying = exoPlayer.isPlaying
                delay(500)
            }
        }

        // 缓冲状态显示（快进/快退时不显示）
        if (isBuffering && !isHoldSeeking.value && !isSeeking) {
            // 添加一个短暂延迟，避免在快速跳转时闪现
            var shouldShowBuffering by remember { mutableStateOf(false) }
            LaunchedEffect(isBuffering) {
                if (isBuffering) {
                    delay(300) // 延迟300ms再显示
                    shouldShowBuffering = true
                } else {
                    shouldShowBuffering = false
                }
            }
            
            if (shouldShowBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                        Text(
                            text = if (bufferingProgress > 0) "缓冲中... $bufferingProgress%" else "加载中...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        VideoPlayerOverlay(
            modifier = Modifier.align(Alignment.BottomCenter),
            focusRequester = focusRequester,
            isPlaying = isPlaying,
            isControlsVisible = videoPlayerState.isControlsVisible,
            centerButton = { VideoPlayerPulse(pulseState) },
            showControls = { 
                // 只在控制栏已经可见时才重置自动隐藏计时器，避免在隐藏状态下自动显示
                if (!isHoldSeeking.value && videoPlayerState.isControlsVisible) {
                    videoPlayerState.showControls(isPlaying)
                }
            },
            controls = {
                VideoPlayerControls(
                    player = exoPlayer,
                    movieDetails = movieDetails,
                    focusRequester = focusRequester,
                    onShowControls = { videoPlayerState.showControls(isPlaying) },
                    onClickSubtitles = {
                        showSubtitleConfig = true
                    },
                    onClickAudio = {
                        // TODO: 实现音轨选择功能
                    }
                )
            }
        )

        // 字幕显示层
        if (currentSubtitle != null && subtitleEnabled) {
            SubtitleOverlay(
                subtitle = currentSubtitle!!,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
            )
        }

        // Seek progress bar (long-press and short-press) with debounced visibility
        if (seekOverlayVisible || isHoldSeeking.value) {
            val duration = exoPlayer.duration
            val currentMs = if (isHoldSeeking.value) holdSeekPreviewMs.longValue else exoPlayer.currentPosition
            android.util.Log.d("VideoPlayer", "显示进度条: 当前=${currentMs}ms, 总=${duration}ms, hold=${isHoldSeeking.value}")
            HoldSeekProgressBar(
                progress = if (duration > 0) currentMs.toFloat() / duration.toFloat() else 0f,
                currentPositionMs = currentMs,
                durationMs = duration,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
            )
        }
    }

    // 字幕配置对话框
    if (showSubtitleConfig) {
        SubtitleConfigDialog(
            showDialog = showSubtitleConfig,
            enabled = subtitleEnabled,
            delayMs = subtitleManager.delayMs.value,
            onDismiss = { showSubtitleConfig = false },
            onToggleEnabled = { enabled ->
                subtitleManager.setEnabled(enabled, coroutineScope)
            },
            onDelayChanged = { newDelay ->
                subtitleManager.setDelay(newDelay)
            }
        )
    }
}

private fun Modifier.dPadEvents(
    videoPlayerState: VideoPlayerState,
    pulseState: VideoPlayerPulseState,
    player: ExoPlayer,
    onSeekStart: () -> Unit = {},
    onSeekEnd: () -> Unit = {}
): Modifier = this.handleDPadKeyEvents(
    onLeft = {
        if (!videoPlayerState.isControlsVisible) {
            onSeekStart()
            val currentPos = player.currentPosition
            player.seekTo((currentPos - 10000).coerceAtLeast(0))
            pulseState.setType(BACK)
            onSeekEnd()
        }
    },
    onRight = {
        if (!videoPlayerState.isControlsVisible) {
            onSeekStart()
            val currentPos = player.currentPosition
            val duration = player.duration
            player.seekTo((currentPos + 10000).coerceAtMost(if (duration != C.TIME_UNSET) duration else Long.MAX_VALUE))
            pulseState.setType(FORWARD)
            onSeekEnd()
        }
    },
    onUp = { videoPlayerState.showControls() },
    onDown = { videoPlayerState.showControls() },
    onEnter = {
        player.pause()
        videoPlayerState.showControls()
    }
)
