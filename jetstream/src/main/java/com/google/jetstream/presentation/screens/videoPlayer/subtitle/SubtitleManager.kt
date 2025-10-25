/*
 * Copyright 2025 Google LLC
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

package com.google.jetstream.presentation.screens.videoPlayer.subtitle

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.jetstream.data.remote.AssrtService
import com.google.jetstream.data.remote.OpenSubtitlesService
import kotlinx.coroutines.*
import java.net.URL

/**
 * 字幕管理器 - 使用 Media3 ExoPlayer
 */
class SubtitleManager(private val context: Context? = null) {
    
    private val _currentSubtitle = mutableStateOf<SubtitleItem?>(null)
    val currentSubtitle: State<SubtitleItem?> = _currentSubtitle
    
    private val _availableSubtitles = mutableStateOf<List<SubtitleTrack>>(emptyList())
    val availableSubtitles: State<List<SubtitleTrack>> = _availableSubtitles
    
    private val _selectedSubtitle = mutableStateOf<SubtitleTrack?>(null)
    val selectedSubtitle: State<SubtitleTrack?> = _selectedSubtitle
    
    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading
    
    private val _enabled = mutableStateOf(false)  // 默认关闭
    val enabled: State<Boolean> = _enabled
    
    private val _delayMs = mutableStateOf(0L)
    val delayMs: State<Long> = _delayMs
    
    private var subtitleItems: List<SubtitleItem> = emptyList()
    private var syncJob: Job? = null
    private var movieName: String? = null
    private var movieTmdbId: String? = null
    private var movieYear: Int? = null
    private var hasAutoSearched = false
    
    private val srtParser = SRTSubtitleParser()
    private val vttParser = VTTSubtitleParser()
    private val assParser = ASSSubtitleParser()
    private val ttmlParser = TTMLSubtitleParser()
    
    /**
     * 加载字幕文件
     */
    suspend fun loadSubtitle(track: SubtitleTrack) = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            android.util.Log.d("SubtitleManager", "Loading subtitle from: ${track.url}")
            
            val content = URL(track.url).readText()
            android.util.Log.d("SubtitleManager", "Downloaded ${content.length} bytes")
            
            subtitleItems = when (track.format) {
                SubtitleFormat.SRT -> srtParser.parseSRT(content)
                SubtitleFormat.VTT -> vttParser.parseVTT(content)
                SubtitleFormat.ASS -> assParser.parseASS(content)
                SubtitleFormat.TTML -> ttmlParser.parseTTML(content)
            }
            
            _selectedSubtitle.value = track
            android.util.Log.d("SubtitleManager", "Loaded ${subtitleItems.size} subtitle items")
            
            subtitleItems.take(3).forEach { item ->
                android.util.Log.d("SubtitleManager", "Subtitle: ${item.startTimeMs}ms - ${item.endTimeMs}ms: ${item.text.take(50)}")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("SubtitleManager", "Failed to load subtitle", e)
            subtitleItems = emptyList()
            _selectedSubtitle.value = null
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * 设置可用的字幕轨道列表
     */
    fun setAvailableSubtitles(subtitles: List<SubtitleTrack>) {
        _availableSubtitles.value = subtitles
    }
    
    /**
     * 开始同步字幕（根据播放位置）
     */
    fun startSync(coroutineScope: CoroutineScope, player: ExoPlayer?) {
        syncJob?.cancel()
        syncJob = coroutineScope.launch {
            while (isActive) {
                try {
                    if (_enabled.value && player != null) {
                        val currentPos = player.currentPosition
                        
                        // 检查是否是内嵌字幕
                        val isEmbeddedSubtitle = _selectedSubtitle.value?.url?.startsWith("embedded://") == true
                        
                        if (isEmbeddedSubtitle) {
                            // 内嵌字幕：检查当前字幕是否在有效时间范围内
                            val currentSubtitle = _currentSubtitle.value
                            if (currentSubtitle != null) {
                                val isInRange = currentPos >= currentSubtitle.startTimeMs && 
                                               currentPos <= currentSubtitle.endTimeMs
                                
                                if (!isInRange) {
                                    _currentSubtitle.value = null
                                    android.util.Log.d("SubtitleManager", "内嵌字幕超出时间范围，清除显示")
                                }
                            }
                        } else {
                            // 外部字幕：根据播放位置查找对应字幕
                            _currentSubtitle.value = findSubtitleAtPosition(currentPos)
                        }
                    } else {
                        _currentSubtitle.value = null
                    }
                } catch (e: Exception) {
                    // 播放器未初始化
                }
                delay(100)
            }
        }
    }
    
    /**
     * 停止同步
     */
    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
        _currentSubtitle.value = null
    }
    
    /**
     * 根据播放位置查找对应的字幕
     * 
     * 延迟逻辑说明：
     * - 如果字幕比视频晚出现（延迟），delayMs 应该设置为正值，让播放器提前去找字幕
     * - 如果字幕比视频早出现（提前），delayMs 应该设置为负值，让播放器延后去找字幕
     */
    private fun findSubtitleAtPosition(positionMs: Long): SubtitleItem? {
        val adjustedPosition = positionMs - _delayMs.value
        return subtitleItems.firstOrNull { 
            adjustedPosition >= it.startTimeMs && adjustedPosition <= it.endTimeMs 
        }
    }
    
    /**
     * 清除字幕
     */
    fun clearSubtitle() {
        subtitleItems = emptyList()
        _currentSubtitle.value = null
        _selectedSubtitle.value = null
    }
    
    /**
     * 设置电影信息（用于自动搜索字幕）
     */
    fun setMovieInfo(name: String, tmdbId: String? = null, year: Int? = null) {
        movieName = name
        movieTmdbId = tmdbId
        movieYear = year
        hasAutoSearched = false
        android.util.Log.d("SubtitleManager", "设置电影信息: name=$name, tmdbId=$tmdbId, year=$year")
    }
    
    /**
     * 检测并启用内嵌字幕
     */
    fun detectEmbeddedSubtitles(player: ExoPlayer) {
        try {
            val currentTracks = player.currentTracks
            val textTracks = mutableListOf<SubtitleTrack>()
            
            for (trackGroup in currentTracks.groups) {
                if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                    for (i in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(i)
                        val language = format.language ?: "und"
                        val label = format.label ?: language
                        val mimeType = format.sampleMimeType ?: "application/x-subrip"
                        
                        val subtitleFormat = when {
                            mimeType.contains("subrip") || mimeType.contains("srt") -> SubtitleFormat.SRT
                            mimeType.contains("webvtt") || mimeType.contains("vtt") -> SubtitleFormat.VTT
                            mimeType.contains("ass") || mimeType.contains("ssa") -> SubtitleFormat.ASS
                            mimeType.contains("ttml") || mimeType.contains("xml") -> SubtitleFormat.TTML
                            else -> SubtitleFormat.SRT
                        }
                        
                        textTracks.add(
                            SubtitleTrack(
                                name = label,
                                language = language,
                                url = "embedded://$i",
                                format = subtitleFormat
                            )
                        )
                        
                        android.util.Log.d("SubtitleManager", "发现内嵌字幕轨道: $label ($language)")
                    }
                }
            }
            
            if (textTracks.isNotEmpty()) {
                hasAutoSearched = true
                _enabled.value = true
                _selectedSubtitle.value = textTracks.firstOrNull()
                
                // 添加字幕监听器
                player.addListener(object : Player.Listener {
                    override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                        if (_enabled.value && cueGroup.cues.isNotEmpty()) {
                            val text = cueGroup.cues.joinToString("\n") { cue ->
                                cue.text?.toString() ?: ""
                            }
                            
                            if (text.isNotBlank()) {
                                val currentPosition = player.currentPosition
                                val startTime = if (cueGroup.presentationTimeUs != C.TIME_UNSET) {
                                    cueGroup.presentationTimeUs / 1000
                                } else {
                                    currentPosition
                                }
                                
                                val duration = when {
                                    text.length < 20 -> 2000L
                                    text.length < 50 -> 3000L
                                    text.length < 100 -> 4000L
                                    else -> 5000L
                                }
                                val endTime = startTime + duration
                                
                                _currentSubtitle.value = SubtitleItem(
                                    startTimeMs = startTime,
                                    endTimeMs = endTime,
                                    text = text
                                )
                            }
                        }
                    }
                })
                
                android.util.Log.d("SubtitleManager", "检测到 ${textTracks.size} 个内嵌字幕轨道，已自动启用")
            }
        } catch (e: Exception) {
            android.util.Log.e("SubtitleManager", "检测内嵌字幕失败", e)
        }
    }
    
    /**
     * 启用/禁用字幕显示
     */
    fun setEnabled(enabled: Boolean, coroutineScope: CoroutineScope? = null) {
        _enabled.value = enabled
        if (!enabled) {
            _currentSubtitle.value = null
        } else {
            if (subtitleItems.isEmpty() && !hasAutoSearched && movieName != null && coroutineScope != null) {
                android.util.Log.d("SubtitleManager", "字幕已启用，开始自动搜索")
                coroutineScope.launch {
                    autoSearchAndLoadSubtitle()
                }
            }
        }
    }
    
    /**
     * 切换字幕显示状态
     */
    fun toggleEnabled(coroutineScope: CoroutineScope? = null) {
        setEnabled(!_enabled.value, coroutineScope)
    }
    
    /**
     * 自动搜索并加载字幕
     */
    suspend fun autoSearchAndLoadSubtitle(): Boolean {
        val name = movieName
        if (name.isNullOrBlank()) {
            android.util.Log.w("SubtitleManager", "电影名称为空，无法搜索字幕")
            showToast("电影名称为空，无法搜索字幕")
            return false
        }
        
        if (hasAutoSearched) {
            android.util.Log.d("SubtitleManager", "已经搜索过字幕，跳过")
            return false
        }
        
        hasAutoSearched = true
        
        return withContext(Dispatchers.IO) {
            try {
                _isLoading.value = true
                android.util.Log.d("SubtitleManager", "开始自动搜索字幕: $name")
                
                // === 尝试 ASSRT API（主字幕源） ===
                android.util.Log.d("SubtitleManager", "→ 尝试字幕源1: ASSRT")
                var result = try {
                    AssrtService.findAndDownloadBestSubtitle(name)
                } catch (e: Exception) {
                    android.util.Log.w("SubtitleManager", "ASSRT 搜索异常: ${e.message}")
                    null
                }
                
                // === 如果 ASSRT 失败，尝试 OpenSubtitles（备用字幕源） ===
                if (result == null) {
                    android.util.Log.d("SubtitleManager", "→ ASSRT 失败，尝试字幕源2: OpenSubtitles")
                    showToast("正在尝试备用字幕源...")
                    
                    result = try {
                        OpenSubtitlesService.findAndDownloadBestSubtitle(
                            movieName = name,
                            tmdbId = movieTmdbId,
                            year = movieYear
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("SubtitleManager", "OpenSubtitles 搜索异常: ${e.message}")
                        null
                    }
                }
                
                // === 处理搜索结果 ===
                if (result == null) {
                    android.util.Log.w("SubtitleManager", "所有字幕源均未找到合适的字幕")
                    showToast("未找到适合的字幕")
                    return@withContext false
                }
                
                val (content, format) = result
                android.util.Log.d("SubtitleManager", "✓ 找到字幕，格式: $format, 大小: ${content.length} 字节")
                
                val subtitleFormat = when (format.lowercase()) {
                    "srt" -> SubtitleFormat.SRT
                    "vtt" -> SubtitleFormat.VTT
                    "ass", "ssa" -> SubtitleFormat.ASS
                    "ttml", "xml" -> SubtitleFormat.TTML
                    else -> SubtitleFormat.SRT
                }
                
                subtitleItems = when (subtitleFormat) {
                    SubtitleFormat.SRT -> srtParser.parseSRT(content)
                    SubtitleFormat.VTT -> vttParser.parseVTT(content)
                    SubtitleFormat.ASS -> assParser.parseASS(content)
                    SubtitleFormat.TTML -> ttmlParser.parseTTML(content)
                }
                
                val track = SubtitleTrack(
                    name = "自动下载 - $format",
                    language = "zh-CN",
                    url = "",
                    format = subtitleFormat
                )
                _selectedSubtitle.value = track
                
                android.util.Log.d("SubtitleManager", "✓ 成功加载 ${subtitleItems.size} 条字幕")
                
                subtitleItems.take(3).forEach { item ->
                    android.util.Log.d("SubtitleManager", "字幕: ${item.startTimeMs}ms - ${item.endTimeMs}ms: ${item.text.take(50)}")
                }
                
                showToast("成功加载 ${subtitleItems.size} 条字幕")
                return@withContext true
                
            } catch (e: java.net.SocketTimeoutException) {
                android.util.Log.e("SubtitleManager", "搜索字幕超时", e)
                showToast("字幕搜索超时，请稍后重试")
                return@withContext false
            } catch (e: java.net.UnknownHostException) {
                android.util.Log.e("SubtitleManager", "网络连接失败", e)
                showToast("网络连接失败，请检查网络")
                return@withContext false
            } catch (e: java.io.IOException) {
                android.util.Log.e("SubtitleManager", "网络IO异常", e)
                showToast("网络异常，字幕服务暂时不可用")
                return@withContext false
            } catch (e: Exception) {
                android.util.Log.e("SubtitleManager", "自动搜索加载字幕失败", e)
                showToast("字幕加载失败，请稍后重试")
                return@withContext false
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 显示 Toast 提示
     */
    private fun showToast(message: String) {
        context?.let { ctx ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 设置字幕延迟
     */
    fun setDelay(delayMs: Long) {
        _delayMs.value = delayMs
    }
}
