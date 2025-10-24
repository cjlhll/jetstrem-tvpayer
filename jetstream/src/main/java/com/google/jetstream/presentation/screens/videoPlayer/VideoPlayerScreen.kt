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
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.jetstream.data.entities.MovieDetails
import com.google.jetstream.presentation.common.Error
import com.google.jetstream.presentation.common.Loading
import com.google.jetstream.presentation.screens.videoPlayer.components.HoldSeekProgressBar
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerControls
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerOverlay
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerPulse
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerPulse.Type.BACK
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerPulse.Type.FORWARD
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerPulseState
import com.google.jetstream.presentation.screens.videoPlayer.components.VideoPlayerState
import com.google.jetstream.presentation.screens.videoPlayer.components.rememberVideoPlayerPulseState
import com.google.jetstream.presentation.screens.videoPlayer.components.rememberVideoPlayerState
import com.google.jetstream.presentation.screens.videoPlayer.subtitle.EmbeddedSubtitleExtractor
import com.google.jetstream.presentation.screens.videoPlayer.subtitle.SubtitleConfigDialog
import com.google.jetstream.presentation.screens.videoPlayer.subtitle.SubtitleFormat
import com.google.jetstream.presentation.screens.videoPlayer.subtitle.SubtitleManager
import com.google.jetstream.presentation.screens.videoPlayer.subtitle.SubtitleOverlay
import com.google.jetstream.presentation.screens.videoPlayer.subtitle.SubtitleTrack
import com.google.jetstream.presentation.utils.handleDPadKeyEvents
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
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
    var gsyPlayer by remember { mutableStateOf<StandardGSYVideoPlayer?>(null) }
    var playerInitialized by remember { mutableStateOf(false) }
    
    // 内核状态管理
    var currentCore by remember { mutableStateOf(PlayerCore.MEDIA3) }
    var savedPositionForCoreSwitch by remember { mutableStateOf(0L) }
    
    // 字幕管理器
    val subtitleManager = remember { SubtitleManager(context) }
    val currentSubtitle by subtitleManager.currentSubtitle
    val subtitleEnabled by subtitleManager.enabled
    val subtitleDelay by subtitleManager.delayMs
    var showSubtitleConfig by remember { mutableStateOf(false) }

    // 初始化字幕管理器
    LaunchedEffect(Unit) {
        // 设置电影名称用于自动搜索字幕
        subtitleManager.setMovieName(movieDetails.name)
        
        // 开始字幕同步
        subtitleManager.startSync(coroutineScope)
    }
    
    // Hold-seek states
    val isHoldSeeking = remember { mutableStateOf(false) }
    val holdSeekPreviewMs = remember { mutableStateOf(0L) }
    val holdSeekDirection = remember { mutableStateOf(0) }
    var holdSeekJob by remember { mutableStateOf<Job?>(null) }
    var holdStarterJob by remember { mutableStateOf<Job?>(null) }
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }

    fun startHold(direction: Int) {
        if (holdSeekJob != null) return
        holdSeekDirection.value = direction
        isHoldSeeking.value = true
        // 使用 GSYVideoManager API 获取当前位置
        holdSeekPreviewMs.value = GSYVideoManager.instance().currentPosition
        val duration = GSYVideoManager.instance().duration.coerceAtLeast(0L)
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
                // 使用 GSYVideoManager API 进行 seek
                try { GSYVideoManager.instance().seekTo(next) } catch (_: Throwable) {}
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
    DisposableEffect(Unit) {
        onDispose {
            try {
                // 停止字幕同步
                subtitleManager.stopSync()
                
                // 使用 GSYVideoManager API 保存播放进度
                val currentPosition = GSYVideoManager.instance().currentPosition
                val duration = GSYVideoManager.instance().duration
                if (currentPosition > 0 && duration > 0) {
                    onSaveProgress(currentPosition, duration)
                }
                
                // 使用 GSYVideoManager API 释放播放器
                GSYVideoManager.releaseAllVideos()
            } catch (_: Throwable) {}
        }
    }

    // 当应用进入后台时暂停播放
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                try {
                    // 使用 GSYVideoManager 静态方法暂停
                    GSYVideoManager.onPause()
                } catch (_: Throwable) {}
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                try {
                    // 使用 GSYVideoManager 静态方法恢复
                    GSYVideoManager.onResume()
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

    BackHandler(onBack = {
        // 若控制栏可见，先隐藏控制栏
        if (videoPlayerState.isControlsVisible) {
            videoPlayerState.hideControls()
            return@BackHandler
        }

        // 控制栏已隐藏，则退出播放器
        try {
            // 使用 GSYVideoManager API 保存播放进度
            val currentPosition = GSYVideoManager.instance().currentPosition
            val duration = GSYVideoManager.instance().duration
            if (currentPosition > 0 && duration > 0) {
                onSaveProgress(currentPosition, duration)
            }
            
            // 使用 GSYVideoManager API 释放播放器
            GSYVideoManager.releaseAllVideos()
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
                videoPlayerState,
                pulseState
            )
            .focusable()
    ) {
        // 使用 AndroidView 渲染 GSYVideoPlayer
        // key 参数确保内核切换时重新创建 View
        androidx.compose.runtime.key(currentCore) {
            AndroidView(
                factory = { ctx ->
                    StandardGSYVideoPlayer(ctx).apply {
                        // 准备 Headers Map
                        val mapHeaders = hashMapOf<String, String>()
                        headers.forEach { (k, v) -> mapHeaders[k] = v }
                        
                        android.util.Log.d("VideoPlayer", "Setting up GSY with headers: ${mapHeaders.keys}, core: $currentCore")
                        
                        // 设置视频 URL (url, cacheWithPlay, cachePath, mapHeadData, title)
                        setUp(movieDetails.videoUri, true, null, mapHeaders, "")
                        
                        // 隐藏所有 GSY 自带的控制 UI（使用 Compose UI）
                        try {
                            titleTextView.visibility = View.GONE
                            backButton.visibility = View.GONE
                            fullscreenButton.visibility = View.GONE
                            startButton.visibility = View.GONE
                        } catch (_: Throwable) {}
                        
                        // 禁用触摸手势（TV 不需要）
                        setIsTouchWiget(false)
                        
                        // 设置起始播放位置（考虑内核切换时的保存位置）
                        val startPos = if (savedPositionForCoreSwitch > 0) {
                            savedPositionForCoreSwitch
                        } else {
                            startPositionMs ?: 0L
                        }
                        
                        if (startPos > 0) {
                            seekOnStart = startPos
                            android.util.Log.d("VideoPlayer", "Setting start position: ${startPos}ms")
                        }
                        
                        // 设置回调监听
                        setVideoAllCallBack(object : GSYSampleCallBack() {
                            override fun onPrepared(url: String, vararg objects: Any) {
                                super.onPrepared(url, *objects)
                                android.util.Log.d("VideoPlayer", "Video prepared with core: $currentCore")
                                
                                // 检测并提取内嵌字幕
                                if (EmbeddedSubtitleExtractor.mayHaveEmbeddedSubtitles(url)) {
                                    android.util.Log.d("VideoPlayer", "视频可能包含内嵌字幕，开始提取")
                                    coroutineScope.launch {
                                        try {
                                            val tracks = EmbeddedSubtitleExtractor.extractSubtitleTracks(this@apply)
                                            if (tracks.isNotEmpty()) {
                                                // 选择最佳字幕轨道
                                                val bestTrack = EmbeddedSubtitleExtractor.selectBestSubtitle(tracks)
                                                if (bestTrack != null) {
                                                    android.util.Log.d("VideoPlayer", "选中内嵌字幕: ${bestTrack.label}")
                                                    subtitleManager.markEmbeddedSubtitleDetected(bestTrack)
                                                    
                                                    // 配置 ExoPlayer 监听字幕数据
                                                    setupEmbeddedSubtitleListener(this@apply, subtitleManager)
                                                }
                                            } else {
                                                android.util.Log.d("VideoPlayer", "未发现内嵌字幕轨道")
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("VideoPlayer", "提取内嵌字幕失败", e)
                                        }
                                    }
                                }
                                
                                if (!playerInitialized) {
                                    playerInitialized = true
                                    onVideoStarted()
                                }
                                // 清除保存的位置
                                savedPositionForCoreSwitch = 0L
                            }
                            
                            override fun onPlayError(url: String, vararg objects: Any) {
                                super.onPlayError(url, *objects)
                                android.util.Log.e("VideoPlayer", "Play error: $url")
                            }
                        })
                        
                        // 保存播放器引用
                        gsyPlayer = this
                        
                        // 开始播放
                        startPlayLogic()
                        android.util.Log.d("VideoPlayer", "GSYVideoPlayer initialized and started with core: $currentCore")
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { player ->
                    // 可以在这里更新播放器配置
                }
            )
        }

        val focusRequester = remember { FocusRequester() }
        
        // 轮询获取播放状态（从 GSYVideoManager）
        var isPlaying by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (isActive) {
                try {
                    isPlaying = GSYVideoManager.instance().isPlaying
                } catch (_: Throwable) {}
                delay(500)
            }
        }
        
        VideoPlayerOverlay(
            modifier = Modifier.align(Alignment.BottomCenter),
            focusRequester = focusRequester,
            isPlaying = isPlaying,
            isControlsVisible = videoPlayerState.isControlsVisible,
            centerButton = { VideoPlayerPulse(pulseState) },
            showControls = { if (!isHoldSeeking.value) videoPlayerState.showControls(isPlaying) },
            controls = {
                VideoPlayerControls(
                    player = null, // 不再传递 ExoPlayer
                    movieDetails = movieDetails,
                    focusRequester = focusRequester,
                    currentCore = currentCore,
                    onSwitchCore = { newCore ->
                        if (newCore != currentCore) {
                            android.util.Log.i("VideoPlayer", "开始切换内核: $currentCore -> $newCore")
                            
                            // 保存当前播放位置
                            savedPositionForCoreSwitch = try {
                                val pos = GSYVideoManager.instance().currentPosition
                                android.util.Log.i("VideoPlayer", "保存播放位置: ${pos}ms")
                                pos
                            } catch (e: Throwable) {
                                android.util.Log.e("VideoPlayer", "获取播放位置失败", e)
                                0L
                            }
                            
                            // 先释放当前播放器
                            try {
                                GSYVideoManager.releaseAllVideos()
                                android.util.Log.d("VideoPlayer", "已释放旧播放器")
                            } catch (e: Throwable) {
                                android.util.Log.e("VideoPlayer", "释放播放器失败", e)
                            }
                            
                            // 切换内核
                            switchPlayerCore(context, newCore)
                            
                            // 更新当前内核（这将触发 AndroidView 重组）
                            currentCore = newCore
                            playerInitialized = false // 重置初始化标记
                            
                            android.util.Log.i("VideoPlayer", "内核切换完成: $newCore, 将从 ${savedPositionForCoreSwitch}ms 继续播放")
                        }
                    },
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
        
        // 字幕显示层（内嵌字幕始终显示，不受控制栏影响）
        if (currentSubtitle != null && subtitleEnabled) {
            SubtitleOverlay(
                subtitle = currentSubtitle!!,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)  // 更靠近底部
            )
        }

        // Separate progress bar shown only while hold-seeking with hidden controls
        if (isHoldSeeking.value) {
            val duration = try { GSYVideoManager.instance().duration } catch (_: Throwable) { 0L }
            HoldSeekProgressBar(
                progress = if (duration > 0) holdSeekPreviewMs.value.toFloat() / duration.toFloat() else 0f,
                currentPositionMs = holdSeekPreviewMs.value,
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
            delayMs = subtitleDelay,
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

/**
 * 切换播放器内核
 */
private fun switchPlayerCore(context: android.content.Context, core: PlayerCore) {
    try {
        // 先释放当前播放器
        GSYVideoManager.releaseAllVideos()
        
        // 根据选择的内核切换
        when (core) {
            PlayerCore.MEDIA3 -> {
                // 切换到 ExoPlayer 内核
                com.shuyu.gsyvideoplayer.player.PlayerFactory.setPlayManager(
                    tv.danmaku.ijk.media.exo2.Exo2PlayerManager::class.java
                )
                // 使用 ExoPlayerCacheManager（支持 m3u8）
                com.shuyu.gsyvideoplayer.cache.CacheFactory.setCacheManager(
                    tv.danmaku.ijk.media.exo2.ExoPlayerCacheManager::class.java
                )
                android.util.Log.i("VideoPlayer", "切换到 Media3 (ExoPlayer) 内核 + ExoPlayerCacheManager")
            }
            PlayerCore.IJK -> {
                // TODO: 需要添加 IJK 依赖后才能使用
                android.util.Log.w("VideoPlayer", "IJK 内核未启用，请添加依赖")
                android.widget.Toast.makeText(context, "IJK 内核未启用", android.widget.Toast.LENGTH_SHORT).show()
            }
            PlayerCore.SYSTEM -> {
                // 切换到系统播放器内核
                com.shuyu.gsyvideoplayer.player.PlayerFactory.setPlayManager(
                    com.shuyu.gsyvideoplayer.player.SystemPlayerManager::class.java
                )
                // 系统播放器不支持 ExoPlayerCacheManager，切换为 ProxyCacheManager
                com.shuyu.gsyvideoplayer.cache.CacheFactory.setCacheManager(
                    com.shuyu.gsyvideoplayer.cache.ProxyCacheManager::class.java
                )
                android.util.Log.i("VideoPlayer", "切换到系统播放器内核 + ProxyCacheManager")
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("VideoPlayer", "切换内核失败", e)
    }
}

/**
 * 配置内嵌字幕监听器
 * 通过 ExoPlayer 的 TextOutput 接收字幕数据，传递给我们的渲染系统
 */
private fun setupEmbeddedSubtitleListener(
    player: StandardGSYVideoPlayer,
    subtitleManager: SubtitleManager
) {
    try {
        // 从 GSYVideoManager 获取播放器管理器
        val gsyVideoManager = GSYVideoManager.instance()
        val playerManager = gsyVideoManager.player
        
        android.util.Log.d("VideoPlayer", "PlayerManager 类型: ${playerManager?.javaClass?.name}")
        
        // 从 Exo2PlayerManager 获取 ExoPlayer（两层反射）
        val exoPlayer = try {
            // 第一步：获取 IjkExo2MediaPlayer
            val mediaPlayerField = playerManager?.javaClass?.getDeclaredField("mediaPlayer")
            mediaPlayerField?.isAccessible = true
            val ijkExo2MediaPlayer = mediaPlayerField?.get(playerManager)
            
            android.util.Log.d("VideoPlayer", "获取到 mediaPlayer: ${ijkExo2MediaPlayer?.javaClass?.name}")
            
            if (ijkExo2MediaPlayer != null) {
                // 第二步：从 IjkExo2MediaPlayer 获取真正的 ExoPlayer
                // 正确的字段名是 mInternalPlayer
                try {
                    val exoPlayerField = ijkExo2MediaPlayer.javaClass.getDeclaredField("mInternalPlayer")
                    exoPlayerField.isAccessible = true
                    exoPlayerField.get(ijkExo2MediaPlayer) as? androidx.media3.exoplayer.ExoPlayer
                } catch (e: Exception) {
                    android.util.Log.e("VideoPlayer", "获取 mInternalPlayer 失败: ${e.message}")
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayer", "无法获取 ExoPlayer: ${e.message}")
            null
        }
        
        if (exoPlayer != null) {
            android.util.Log.d("VideoPlayer", "成功获取 ExoPlayer: ${exoPlayer.javaClass.name}")
            
            // 启用字幕轨道（确保 ExoPlayer 选中字幕）
            try {
                val trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setPreferredTextLanguage("zh")  // 优先选择中文字幕
                    .setSelectUndeterminedTextLanguage(true)  // 选择未确定语言的字幕
                    .build()
                exoPlayer.trackSelectionParameters = trackSelectionParameters
                android.util.Log.d("VideoPlayer", "已启用字幕轨道选择")
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayer", "设置轨道选择参数失败", e)
            }
            
            // 添加字幕输出监听器
            exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
                override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                    super.onCues(cueGroup)
                    
                    // 处理字幕数据
                    if (cueGroup.cues.isNotEmpty()) {
                        // 合并所有字幕行
                        val text = cueGroup.cues.joinToString("\n") { cue ->
                            cue.text?.toString() ?: ""
                        }
                        
                        if (text.isNotBlank()) {
                            // 获取当前播放位置
                            val currentPosition = exoPlayer.currentPosition
                            
                            // 使用 CueGroup 的时间戳（如果有）
                            val startTime = if (cueGroup.presentationTimeUs != androidx.media3.common.C.TIME_UNSET) {
                                cueGroup.presentationTimeUs / 1000 // 微秒转毫秒
                            } else {
                                currentPosition
                            }
                            
                            // 估算结束时间（字幕通常显示2-5秒）
                            // 根据文本长度动态调整
                            val duration = when {
                                text.length < 20 -> 2000L
                                text.length < 50 -> 3000L
                                text.length < 100 -> 4000L
                                else -> 5000L
                            }
                            val endTime = startTime + duration
                            
                            android.util.Log.d("EmbeddedSubtitle", "字幕: $text, 时间: ${startTime}ms-${endTime}ms")
                            
                            // 更新字幕管理器
                            subtitleManager.updateEmbeddedSubtitle(text, startTime, endTime)
                        }
                    }
                    // 注意：不要在 cues 为空时清除字幕，让它自然超时
                    // ExoPlayer 会在字幕结束时发送空的 cueGroup，但我们希望字幕保持显示直到时间到
                }
            })
            
            android.util.Log.d("VideoPlayer", "内嵌字幕监听器已配置，字幕已自动启用")
        } else {
            android.util.Log.w("VideoPlayer", "无法获取 ExoPlayer 实例")
        }
    } catch (e: Exception) {
        android.util.Log.e("VideoPlayer", "配置内嵌字幕监听器失败", e)
    }
}

private fun Modifier.dPadEvents(
    videoPlayerState: VideoPlayerState,
    pulseState: VideoPlayerPulseState
): Modifier = this.handleDPadKeyEvents(
    onLeft = {
        if (!videoPlayerState.isControlsVisible) {
            // 使用 GSYVideoManager API 后退10秒
            try {
                val currentPos = GSYVideoManager.instance().currentPosition
                GSYVideoManager.instance().seekTo((currentPos - 10000).coerceAtLeast(0))
            } catch (_: Throwable) {}
            pulseState.setType(BACK)
        }
    },
    onRight = {
        if (!videoPlayerState.isControlsVisible) {
            // 使用 GSYVideoManager API 前进10秒
            try {
                val currentPos = GSYVideoManager.instance().currentPosition
                val duration = GSYVideoManager.instance().duration
                GSYVideoManager.instance().seekTo((currentPos + 10000).coerceAtMost(duration))
            } catch (_: Throwable) {}
            pulseState.setType(FORWARD)
        }
    },
    onUp = { videoPlayerState.showControls() },
    onDown = { videoPlayerState.showControls() },
    onEnter = {
        // 使用 GSYVideoManager API 暂停
        try {
            GSYVideoManager.instance().pause()
        } catch (_: Throwable) {}
        videoPlayerState.showControls()
    }
)


