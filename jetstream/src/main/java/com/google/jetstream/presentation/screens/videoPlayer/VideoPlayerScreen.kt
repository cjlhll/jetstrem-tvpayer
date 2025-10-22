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
import com.google.jetstream.presentation.screens.videoPlayer.components.PlayerSubtitles
import com.google.jetstream.presentation.screens.videoPlayer.components.SubtitleDialog
import com.google.jetstream.presentation.screens.videoPlayer.components.AudioDialog
import com.google.jetstream.presentation.screens.videoPlayer.components.HoldSeekProgressBar
import com.google.jetstream.presentation.screens.videoPlayer.components.rememberVideoPlayerPulseState
import com.google.jetstream.presentation.screens.videoPlayer.components.rememberVideoPlayerState
import com.google.jetstream.presentation.utils.handleDPadKeyEvents
import com.google.jetstream.BuildConfig
import com.google.jetstream.data.subs.AssrtApi
import com.google.jetstream.data.subs.SubtitleMime
import com.google.jetstream.data.subs.SubtitleFetcher
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import java.io.File
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

    // Subtitle popover & language labels state
    val showSubtitlePopoverState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val assrtLanguagesState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<String>>(emptyList()) }
    val showAudioPopoverState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // 用户选择的外部字幕标签（例如：简体中文/繁体中文/英语/双语）；null 表示关闭外部字幕
    val selectedSubtitleLabelState = remember { mutableStateOf<String?>(null) }
    // External subtitle delay in milliseconds (negative = show earlier, positive = show later)
    val subtitleDelayMsState = remember { mutableStateOf(0) }
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
    LaunchedEffect(exoPlayer, movieDetails, startPositionMs, subtitleDelayMsState.value) {
        val keepPositionMs = if (playerInitialized) exoPlayer.currentPosition else (startPositionMs ?: 0L)
        val wasPlaying = if (playerInitialized) exoPlayer.isPlaying else true
        val (mediaItem, langs, defaultLabel) = movieDetails.intoMediaItemDynamicSubAsync(
            cacheDir = context.cacheDir,
            subtitleDelayUs = subtitleDelayMsState.value.toLong() * 1000L,
            preferredLabel = selectedSubtitleLabelState.value,
            headers = headers
        )
        assrtLanguagesState.value = langs
        if (selectedSubtitleLabelState.value == null && defaultLabel != null) {
            // 初始化时记录默认选择，便于弹窗高亮
            selectedSubtitleLabelState.value = defaultLabel
        }
        android.util.Log.d("VideoPlayer", "addMediaItem: uri=${mediaItem.localConfiguration?.uri} subCount=${mediaItem.localConfiguration?.subtitleConfigurations?.size}")
        mediaItem.localConfiguration?.subtitleConfigurations?.forEachIndexed { idx, sc ->
            android.util.Log.d("VideoPlayer", "subtitle[$idx]: uri=${sc.uri} mime=${sc.mimeType} lang=${sc.language} flags=${sc.selectionFlags}")
        }
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        try {
            if (keepPositionMs > 0) {
                exoPlayer.seekTo(keepPositionMs)
                android.util.Log.d("VideoPlayer", "Seeking to position: ${keepPositionMs}ms (init=${!playerInitialized})")
            }
            exoPlayer.playWhenReady = wasPlaying
        } catch (_: Throwable) {}
        playerInitialized = true
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
        // 1) 若字幕弹窗打开，先关闭弹窗
        if (showSubtitlePopoverState.value) {
            showSubtitlePopoverState.value = false
            return@BackHandler
        }

        // 2) 若控制栏可见，先隐藏控制栏
        if (videoPlayerState.isControlsVisible) {
            videoPlayerState.hideControls()
            return@BackHandler
        }

        // 3) 控制栏已隐藏，则退出播放器
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
                    if (showSubtitlePopoverState.value) {
                        showSubtitlePopoverState.value = false
                        return@onPreviewKeyEvent true
                    }
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
            // While hold-seeking, do not render subtitles (avoid updating position during fast seek)
            subtitles = { if (!isHoldSeeking.value) PlayerSubtitles(player = exoPlayer) },
            showControls = { if (!isHoldSeeking.value) videoPlayerState.showControls(exoPlayer.isPlaying) },
            controls = {
                VideoPlayerControls(
                    player = exoPlayer,
                    movieDetails = movieDetails,
                    focusRequester = focusRequester,
                    onShowControls = { videoPlayerState.showControls(exoPlayer.isPlaying) },
                    onClickSubtitles = { showSubtitlePopoverState.value = true },
                    onClickAudio = { showAudioPopoverState.value = true }
                )
        if (showAudioPopoverState.value) {
            // 音轨语言映射函数
            fun displayAudioLanguage(code: String?): String {
                if (code.isNullOrBlank() || code == "und") return "未知语言"
                val lc = code.lowercase()
                return when {
                    lc == "mul" -> "多语言"
                    lc == "zh-hans" || lc == "zh_cn" || lc == "zh-cn" || lc == "zh" || lc == "chi" || lc == "zho" -> "中文"
                    lc.startsWith("zh-hant") || lc == "zh-tw" || lc == "zh-hk" -> "中文繁体"
                    lc.startsWith("en") || lc == "eng" -> "英语"
                    lc.startsWith("ja") || lc == "jpn" -> "日语"
                    lc.startsWith("ko") || lc == "kor" -> "韩语"
                    lc.startsWith("yue") -> "粤语"
                    lc.startsWith("ar") || lc == "ara" -> "阿拉伯语"
                    lc.startsWith("pt") || lc == "por" -> "葡萄牙语"
                    lc.startsWith("th") || lc == "tha" -> "泰语"
                    lc.startsWith("es") || lc == "spa" -> "西班牙语"
                    lc.startsWith("fr") || lc == "fra" || lc == "fre" -> "法语"
                    lc.startsWith("de") || lc == "deu" || lc == "ger" -> "德语"
                    lc.startsWith("it") || lc == "ita" -> "意大利语"
                    lc.startsWith("ru") || lc == "rus" -> "俄语"
                    lc.startsWith("vi") || lc == "vie" -> "越南语"
                    lc.startsWith("ms") || lc == "may" || lc == "msa" -> "马来语"
                    lc.startsWith("id") || lc == "ind" -> "印尼语"
                    lc.startsWith("hi") || lc == "hin" -> "印地语"
                    lc.startsWith("tr") || lc == "tur" -> "土耳其语"
                    lc.startsWith("pl") || lc == "pol" -> "波兰语"
                    lc.startsWith("nl") || lc == "nld" || lc == "dut" -> "荷兰语"
                    lc.startsWith("sv") || lc == "swe" -> "瑞典语"
                    lc.startsWith("da") || lc == "dan" -> "丹麦语"
                    lc.startsWith("no") || lc == "nor" -> "挪威语"
                    lc.startsWith("fi") || lc == "fin" -> "芬兰语"
                    lc.startsWith("el") || lc == "gre" || lc == "ell" -> "希腊语"
                    lc.startsWith("he") || lc == "heb" -> "希伯来语"
                    lc.startsWith("cs") || lc == "cze" || lc == "ces" -> "捷克语"
                    lc.startsWith("hu") || lc == "hun" -> "匈牙利语"
                    lc.startsWith("ro") || lc == "rum" || lc == "ron" -> "罗马尼亚语"
                    lc.startsWith("uk") || lc == "ukr" -> "乌克兰语"
                    lc.startsWith("bg") || lc == "bul" -> "保加利亚语"
                    lc.startsWith("sr") || lc == "srp" -> "塞尔维亚语"
                    lc.startsWith("hr") || lc == "hrv" -> "克罗地亚语"
                    else -> code.uppercase()
                }
            }
            
            // 声道数映射函数
            fun displayChannelCount(count: Int): String {
                return when (count) {
                    1 -> "单声道"
                    2 -> "立体声"
                    6 -> "5.1声道"
                    8 -> "7.1声道"
                    else -> "${count}声道"
                }
            }
            
            val audioGroups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            val formats = audioGroups.flatMap { g -> (0 until g.mediaTrackGroup.length).map { i -> g.mediaTrackGroup.getFormat(i) } }
            val labels = formats.mapIndexed { i, f ->
                val lang = displayAudioLanguage(f.language)
                val ch = if (f.channelCount > 0) displayChannelCount(f.channelCount) else null
                val codec = when {
                    f.sampleMimeType?.contains("ac3", true) == true -> "AC3"
                    f.sampleMimeType?.contains("eac3", true) == true -> "EAC3"
                    f.sampleMimeType?.contains("dts", true) == true -> "DTS"
                    f.sampleMimeType?.contains("aac", true) == true -> "AAC"
                    f.sampleMimeType?.contains("opus", true) == true -> "Opus"
                    f.sampleMimeType?.contains("vorbis", true) == true -> "Vorbis"
                    f.sampleMimeType?.contains("flac", true) == true -> "FLAC"
                    f.sampleMimeType?.contains("mp3", true) == true -> "MP3"
                    else -> null
                }
                listOfNotNull(lang, ch, codec).joinToString(" · ").ifBlank { "音轨 ${i+1}" }
            }
            AudioDialog(
                show = true,
                onDismissRequest = { showAudioPopoverState.value = false },
                options = listOf("关闭音轨") + labels,
                selectedIndex = 1,
                onSelectIndex = { idx ->
                    if (idx <= 0) { showAudioPopoverState.value = false; return@AudioDialog }
                    val fmt = formats.getOrNull(idx - 1) ?: return@AudioDialog
                    val lang = fmt.language
                    val params = exoPlayer.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setPreferredAudioLanguage(lang)
                        .build()
                    exoPlayer.trackSelectionParameters = params
                    showAudioPopoverState.value = false
                }
            )
        }
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

        if (showSubtitlePopoverState.value) {
            val textGroups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            data class InternalEntry(val label: String, val group: androidx.media3.common.Tracks.Group, val trackIndex: Int)
            fun displayTextLanguage(code: String?): String {
                if (code.isNullOrBlank()) return "未知语言"
                val lc = code.lowercase()
                return when {
                    lc == "und" -> "未知语言"
                    lc == "mul" -> "多语言"
                    lc == "zh-hans" || lc == "zh_cn" || lc == "zh-cn" || lc == "zh" || lc == "chi" || lc == "zho" -> "简体中文"
                    lc.startsWith("zh-hant") || lc == "zh-tw" || lc == "zh-hk" -> "繁体中文"
                    lc.startsWith("en") -> "英语"
                    lc.startsWith("ja") -> "日语"
                    lc.startsWith("ko") -> "韩语"
                    lc.startsWith("yue") -> "粤语"
                    lc.startsWith("ar") -> "阿拉伯语"
                    lc.startsWith("pt") -> "葡萄牙语"
                    lc.startsWith("th") -> "泰语"
                    lc.startsWith("es") || lc == "spa" -> "西班牙语"
                    lc.startsWith("fr") || lc == "fra" || lc == "fre" -> "法语"
                    lc.startsWith("de") || lc == "deu" || lc == "ger" -> "德语"
                    lc.startsWith("it") || lc == "ita" -> "意大利语"
                    lc.startsWith("ru") || lc == "rus" -> "俄语"
                    lc.startsWith("vi") || lc == "vie" -> "越南语"
                    lc.startsWith("ms") || lc == "may" || lc == "msa" -> "马来语"
                    else -> lc
                }
            }
            fun refineWithTrackLabel(base: String, trackLabel: String?): String {
                val l = trackLabel?.lowercase() ?: return base
                return when {
                    l.contains("chs") || l.contains("简") || l.contains("sc") -> "简体中文"
                    l.contains("cht") || l.contains("繁") || l.contains("tc") -> "繁体中文"
                    l.contains("粵") || l.contains("粤") || l.contains("yue") || l.contains("canton") -> "粤语"
                    else -> base
                }
            }
            val internalEntries = mutableListOf<InternalEntry>()
            for (g in textGroups) {
                val len = g.mediaTrackGroup.length
                for (i in 0 until len) {
                    val f = g.mediaTrackGroup.getFormat(i)
                    val base = displayTextLanguage(f.language)
                    val friendly = refineWithTrackLabel(base, f.label)
                    internalEntries += InternalEntry(label = friendly + "（内嵌）", group = g, trackIndex = i)
                }
            }
            val internalLabels = internalEntries.map { it.label }
            val externalLabels = assrtLanguagesState.value
            val combined = externalLabels + internalLabels
            val enabled = textGroups.any { it.isSelected }
            SubtitleDialog(
                show = true,
                onDismissRequest = { showSubtitlePopoverState.value = false },
                languages = combined,
                selectedIndex = run {
                    val current = selectedSubtitleLabelState.value
                    val extIdx = externalLabels.indexOf(current)
                    val intPref = internalLabels.indexOfFirst { it.contains("简体中文") }
                    when {
                        !enabled -> 0
                        extIdx >= 0 -> extIdx + 1
                        intPref >= 0 -> externalLabels.size + intPref + 1
                        internalLabels.isNotEmpty() -> externalLabels.size + 1
                        else -> 0
                    }
                },
                onSelectIndex = { idx ->
                    if (idx == 0) {
                        val params = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .setPreferredTextLanguage(null)
                            .build()
                        exoPlayer.trackSelectionParameters = params
                        selectedSubtitleLabelState.value = null
                    } else {
                        val oneBased = idx
                        val isExternal = oneBased - 1 < externalLabels.size
                        if (isExternal) {
                            val label = externalLabels.getOrNull(oneBased - 1)
                            val lang = labelToLanguageTag(label)
                            val builder = exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                .setPreferredTextLanguage(lang)
                            exoPlayer.trackSelectionParameters = builder.build()
                            selectedSubtitleLabelState.value = label
                        } else {
                            val internalIdx = oneBased - 1 - externalLabels.size
                            val entry = internalEntries.getOrNull(internalIdx)
                            if (entry != null) {
                                val override = TrackSelectionOverride(entry.group.mediaTrackGroup, listOf(entry.trackIndex))
                                val builder = exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                    .addOverride(override)
                                exoPlayer.trackSelectionParameters = builder.build()
                                // 选择内嵌中文优先时，将外部选择状态设为对应文本，便于下次打开高亮
                                selectedSubtitleLabelState.value = if (entry.label.contains("简体中文")) "简体中文" else null
                            }
                        }
                    }
                    showSubtitlePopoverState.value = false
                },
                subtitleDelayMs = subtitleDelayMsState.value,
                onAdjustDelay = { delta ->
                    val next = (subtitleDelayMsState.value + delta).coerceIn(-5000, 5000)
                    subtitleDelayMsState.value = next
                }
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

private fun labelToLanguageTag(label: String?): String? = when (label) {
    "简体中文", "双语" -> "zh"
    "繁体中文" -> "zh-Hant"
    "英语" -> "en"
    else -> null
}

private suspend fun MovieDetails.intoMediaItemDynamicSubAsync(cacheDir: File, subtitleDelayUs: Long? = null, preferredLabel: String? = null, headers: Map<String, String> = emptyMap()): Triple<MediaItem, List<String>, String?> {
    val assrtToken = BuildConfig.ASSRT_TOKEN
    val builder = MediaItem.Builder().setUri(videoUri)
    val subs = mutableListOf<MediaItem.SubtitleConfiguration>()
    var selectedLangs: List<String> = emptyList()
    var defaultPickedLabel: String? = null
    try {
        if (!assrtToken.isNullOrBlank()) {
            val api = AssrtApi(assrtToken)
            val keyword = name.ifBlank { director.ifBlank { releaseDate } }
            val selected = api.searchBest(keyword)
            android.util.Log.d("VideoPlayer", "ASSRT search keyword=$keyword id=${selected?.id}")
            if (selected?.id != null) {
                val detail = api.detail(selected.id)
                selectedLangs = detail.labels
                android.util.Log.d("VideoPlayer", "ASSRT detail urls=${detail.urls.size} labels=${detail.labels}")

                // 识别各URL对应的标签
                fun labelOfUrl(url: String): String? {
                    val p = url.substringBefore('?').lowercase()
                    return when {
                        p.contains("chs&eng") || (p.contains("chs") && p.contains("eng")) || p.contains("双语") -> "双语"
                        (p.contains(".chs.") || p.endsWith(".chs.srt") || p.contains("chs")) && !p.contains("cht") -> "简体中文"
                        p.contains("cht") -> "繁体中文"
                        p.contains("eng") -> "英语"
                        else -> null
                    }
                }

                val allowed = detail.urls.filter { SubtitleMime.fromUrl(it) != null }
                val labelToUrl = linkedMapOf<String, String>()
                for (u in allowed) {
                    val lbl = labelOfUrl(u) ?: continue
                    if (!labelToUrl.containsKey(lbl)) labelToUrl[lbl] = u
                }

                // 确定默认选择
                val priority = listOf("双语", "简体中文", "繁体中文", "英语")
                val defaultLabel = preferredLabel ?: priority.firstOrNull { labelToUrl.containsKey(it) }
                defaultPickedLabel = defaultLabel

                // 预下载并挂载所有可用字幕文件，默认项打上 DEFAULT
                for ((lbl, u) in labelToUrl) {
                    val mime = SubtitleMime.fromUrl(u) ?: MimeTypes.TEXT_VTT
                    val tmp = SubtitleFetcher.fetchToUtf8File(
                        url = u,
                        client = OkHttpClient(),
                        cacheDir = cacheDir,
                        timeShiftMs = ((subtitleDelayUs ?: 0L) / 1000L).toInt(),
                        mime = mime
                    )
                    val uri = if (tmp != null) Uri.fromFile(tmp) else Uri.parse(u)
                    val scBuilder = MediaItem.SubtitleConfiguration
                        .Builder(uri)
                        .setMimeType(mime)
                        .setLanguage(labelToLanguageTag(lbl))
                        .setLabel(lbl)
                    if (lbl == defaultLabel) scBuilder.setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    subs += scBuilder.build()
                }
            }
        }
        // 在尝试 ASSRT 之前，优先尝试同目录同名字幕文件（WebDAV 直链）
        if (subs.isEmpty()) {
            val parent = videoUri.substringBeforeLast('/')
            val base = videoUri.substringAfterLast('/').substringBeforeLast('.')
            val exts = listOf("srt","vtt","ass","ssa","ttml","dfxp")
            val langHints = listOf("chs&eng","chs","cht","sc","tc","eng","en")
            val candidates = mutableListOf<String>()
            // 精确同名优先
            for (ext in exts) candidates += "$parent/$base.$ext"
            // 带语言后缀
            for (hint in langHints) for (ext in exts) candidates += "$parent/$base.$hint.$ext"

            fun detectLabelFromName(name: String): String? {
                val n = name.lowercase()
                return when {
                    n.contains("chs&eng") || (n.contains("chs") && n.contains("eng")) || n.contains("bilingual") -> "双语"
                    n.contains(".chs.") || n.endsWith(".chs.srt") || n.contains("sc") || n.contains("chinese") -> "简体中文"
                    n.contains(".cht.") || n.endsWith(".cht.srt") || n.contains("tc") -> "繁体中文"
                    n.contains("eng") || n.contains("en") -> "英语"
                    else -> null
                }
            }

            val authHeaders = headers // 避免与 Request.Builder 的 headers 属性冲突
            val headerInterceptor = Interceptor { chain ->
                val req = chain.request().newBuilder().apply {
                    // 添加 WebDAV 鉴权头（Basic Auth）
                    authHeaders.forEach { (key, value) ->
                        header(key, value)
                    }
                }.build()
                chain.proceed(req)
            }
            val client = OkHttpClient.Builder().addInterceptor(headerInterceptor).build()

            val picked = mutableMapOf<String, String>()
            for (u in candidates) {
                val mime = SubtitleMime.fromUrl(u) ?: continue
                val tmp = SubtitleFetcher.fetchToUtf8File(
                    url = u,
                    client = client,
                    cacheDir = cacheDir,
                    timeShiftMs = ((subtitleDelayUs ?: 0L) / 1000L).toInt(),
                    mime = mime
                )
                if (tmp != null) {
                    val lbl = detectLabelFromName(u) ?: "未知"
                    picked.putIfAbsent(lbl, u)
                }
            }
            if (picked.isNotEmpty()) {
                val priority = listOf("双语","简体中文","繁体中文","英语","未知")
                val defaultLabel = preferredLabel ?: priority.firstOrNull { picked.containsKey(it) }
                defaultPickedLabel = defaultLabel
                for ((lbl, u) in picked) {
                    val mime = SubtitleMime.fromUrl(u) ?: MimeTypes.TEXT_VTT
                    val tmp = SubtitleFetcher.fetchToUtf8File(
                        url = u,
                        client = client,
                        cacheDir = cacheDir,
                        timeShiftMs = ((subtitleDelayUs ?: 0L) / 1000L).toInt(),
                        mime = mime
                    )
                    val uri = if (tmp != null) Uri.fromFile(tmp) else Uri.parse(u)
                    val sc = MediaItem.SubtitleConfiguration.Builder(uri)
                        .setMimeType(mime)
                        .setLanguage(labelToLanguageTag(lbl))
                        .setLabel(lbl)
                        .apply { if (lbl == defaultLabel) setSelectionFlags(C.SELECTION_FLAG_DEFAULT) }
                        .build()
                    subs += sc
                }
                selectedLangs = picked.keys.toList()
            }
        }
    } catch (t: Throwable) {
        android.util.Log.w("VideoPlayer", "ASSRT failed: ${t.message}")
    }
    return Triple(builder.setSubtitleConfigurations(subs).build(), selectedLangs, defaultPickedLabel)
}

private fun Movie.intoMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(videoUri)
        .build()
}

