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
import com.google.jetstream.data.remote.AssrtService
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
    private var movieName: String? = null
    private var hasAutoSearched = false  // 标记是否已自动搜索过
    
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
     * 设置电影名称（用于自动搜索字幕）
     */
    fun setMovieName(name: String) {
        movieName = name
        hasAutoSearched = false  // 重置搜索标记
        android.util.Log.d("SubtitleManager", "设置电影名称: $name")
    }
    
    /**
     * 启用/禁用字幕显示
     */
    fun setEnabled(enabled: Boolean, coroutineScope: CoroutineScope? = null) {
        _enabled.value = enabled
        if (!enabled) {
            _currentSubtitle.value = null
        } else {
            // 启用字幕时，如果还没有加载字幕且有电影名称，自动搜索下载
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
            return false
        }
        
        if (hasAutoSearched) {
            android.util.Log.d("SubtitleManager", "已经搜索过字幕，跳过")
            return false
        }
        
        hasAutoSearched = true  // 标记已搜索
        
        return withContext(Dispatchers.IO) {
            try {
                _isLoading.value = true
                android.util.Log.d("SubtitleManager", "开始自动搜索字幕: $name")
                
                // 调用 ASSRT API 搜索下载字幕
                val result = AssrtService.findAndDownloadBestSubtitle(name)
                
                if (result == null) {
                    android.util.Log.w("SubtitleManager", "未找到合适的字幕")
                    return@withContext false
                }
                
                val (content, format) = result
                android.util.Log.d("SubtitleManager", "找到字幕，格式: $format, 大小: ${content.length} 字节")
                
                // 解析字幕内容
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
                
                // 创建虚拟字幕轨道
                val track = SubtitleTrack(
                    name = "自动下载 - $format",
                    language = "zh-CN",
                    url = "", // 已经下载完成，不需要URL
                    format = subtitleFormat
                )
                _selectedSubtitle.value = track
                
                android.util.Log.d("SubtitleManager", "成功加载 ${subtitleItems.size} 条字幕")
                
                // 打印前几条字幕用于调试
                subtitleItems.take(3).forEach { item ->
                    android.util.Log.d("SubtitleManager", "字幕: ${item.startTimeMs}ms - ${item.endTimeMs}ms: ${item.text.take(50)}")
                }
                
                return@withContext true
                
            } catch (e: Exception) {
                android.util.Log.e("SubtitleManager", "自动搜索加载字幕失败", e)
                return@withContext false
            } finally {
                _isLoading.value = false
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

