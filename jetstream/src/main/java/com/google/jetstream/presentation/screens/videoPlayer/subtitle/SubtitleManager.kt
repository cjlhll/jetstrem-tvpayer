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

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import kotlinx.coroutines.*
import java.net.URL

/**
 * 字幕管理器
 */
class SubtitleManager {
    
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
            
            // 下载字幕文件内容
            val content = URL(track.url).readText()
            android.util.Log.d("SubtitleManager", "Downloaded ${content.length} bytes")
            
            // 根据格式解析
            subtitleItems = when (track.format) {
                SubtitleFormat.SRT -> srtParser.parseSRT(content)
                SubtitleFormat.VTT -> vttParser.parseVTT(content)
                SubtitleFormat.ASS -> assParser.parseASS(content)
                SubtitleFormat.TTML -> ttmlParser.parseTTML(content)
            }
            
            _selectedSubtitle.value = track
            android.util.Log.d("SubtitleManager", "Loaded ${subtitleItems.size} subtitle items")
            
            // 打印前几条字幕用于调试
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
    fun startSync(coroutineScope: CoroutineScope) {
        syncJob?.cancel()
        syncJob = coroutineScope.launch {
            while (isActive) {
                try {
                    if (_enabled.value) {
                        val currentPos = com.shuyu.gsyvideoplayer.GSYVideoManager.instance().currentPosition
                        _currentSubtitle.value = findSubtitleAtPosition(currentPos)
                    } else {
                        _currentSubtitle.value = null
                    }
                } catch (e: Exception) {
                    // 播放器未初始化
                }
                delay(100) // 每100ms更新一次
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
     */
    private fun findSubtitleAtPosition(positionMs: Long): SubtitleItem? {
        val adjustedPosition = positionMs + _delayMs.value
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
     * 启用/禁用字幕显示
     */
    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (!enabled) {
            _currentSubtitle.value = null
        }
    }
    
    /**
     * 切换字幕显示状态
     */
    fun toggleEnabled() {
        setEnabled(!_enabled.value)
    }
    
    /**
     * 设置字幕延迟
     */
    fun setDelay(delayMs: Long) {
        _delayMs.value = delayMs
    }
}

