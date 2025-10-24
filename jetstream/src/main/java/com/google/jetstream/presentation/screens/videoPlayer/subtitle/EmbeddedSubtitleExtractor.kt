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

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 内嵌字幕提取器
 * 用于从视频文件（MKV、MP4等）中提取内嵌字幕轨道信息
 * 
 * 注意：这里只提取轨道信息，实际字幕数据由 ExoPlayer 提供，我们的解析器统一渲染
 */
object EmbeddedSubtitleExtractor {
    private const val TAG = "EmbeddedSubtitleExtractor"
    
    /**
     * 检测视频是否可能包含内嵌字幕
     */
    fun mayHaveEmbeddedSubtitles(videoUri: String): Boolean {
        val uri = videoUri.lowercase()
        return uri.endsWith(".mkv") || 
               uri.endsWith(".mp4") || 
               uri.endsWith(".m4v") ||
               uri.endsWith(".webm")
    }
    
    /**
     * 从播放器中提取内嵌字幕轨道信息
     * 
     * @param player GSYVideoPlayer 实例
     * @return 字幕轨道列表
     */
    suspend fun extractSubtitleTracks(
        player: com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
    ): List<EmbeddedSubtitleTrack> = withContext(Dispatchers.Main) {
        try {
            // 从 GSYVideoManager 获取播放器管理器
            val gsyVideoManager = com.shuyu.gsyvideoplayer.GSYVideoManager.instance()
            val playerManager = gsyVideoManager.player
            
            Log.d(TAG, "PlayerManager 类型: ${playerManager?.javaClass?.name}")
            
            // 尝试从 Exo2PlayerManager 获取 ExoPlayer
            val exoPlayer = try {
                // 第一步：获取 IjkExo2MediaPlayer
                val mediaPlayerField = playerManager?.javaClass?.getDeclaredField("mediaPlayer")
                mediaPlayerField?.isAccessible = true
                val ijkExo2MediaPlayer = mediaPlayerField?.get(playerManager)
                
                Log.d(TAG, "获取到 mediaPlayer: ${ijkExo2MediaPlayer?.javaClass?.name}")
                
                if (ijkExo2MediaPlayer != null) {
                    // 第二步：从 IjkExo2MediaPlayer 获取真正的 ExoPlayer
                    // 正确的字段名是 mInternalPlayer
                    val exoPlayerInstance = try {
                        val exoPlayerField = ijkExo2MediaPlayer.javaClass.getDeclaredField("mInternalPlayer")
                        exoPlayerField.isAccessible = true
                        exoPlayerField.get(ijkExo2MediaPlayer) as? ExoPlayer
                    } catch (e: Exception) {
                        Log.e(TAG, "获取 mInternalPlayer 失败: ${e.message}")
                        null
                    }
                    exoPlayerInstance
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取 ExoPlayer 失败: ${e.message}")
                null
            }
            
            if (exoPlayer == null) {
                Log.w(TAG, "无法获取 ExoPlayer 实例，尝试列出 PlayerManager 的所有字段")
                playerManager?.javaClass?.declaredFields?.forEach { field ->
                    Log.d(TAG, "字段: ${field.name}, 类型: ${field.type.name}")
                }
                return@withContext emptyList()
            }
            
            Log.d(TAG, "成功获取 ExoPlayer 实例: ${exoPlayer.javaClass.name}")
            
            val tracks = mutableListOf<EmbeddedSubtitleTrack>()
            val currentTracks = exoPlayer.currentTracks
            
            // 遍历所有轨道组
            for (trackGroup in currentTracks.groups) {
                if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                    // 找到字幕轨道
                    for (i in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(i)
                        
                        // 解析字幕信息
                        val language = format.language ?: "und"
                        val label = format.label ?: language
                        val mimeType = format.sampleMimeType ?: "application/x-subrip"
                        
                        // 判断字幕格式
                        val subtitleFormat = when {
                            mimeType.contains("subrip") || mimeType.contains("srt") -> SubtitleFormat.SRT
                            mimeType.contains("webvtt") || mimeType.contains("vtt") -> SubtitleFormat.VTT
                            mimeType.contains("ass") || mimeType.contains("ssa") -> SubtitleFormat.ASS
                            mimeType.contains("ttml") || mimeType.contains("xml") -> SubtitleFormat.TTML
                            else -> SubtitleFormat.SRT
                        }
                        
                        tracks.add(
                            EmbeddedSubtitleTrack(
                                trackIndex = i,
                                groupIndex = currentTracks.groups.indexOf(trackGroup),
                                language = language,
                                label = label,
                                format = subtitleFormat,
                                mimeType = mimeType
                            )
                        )
                        
                        Log.d(TAG, "发现内嵌字幕轨道: $label ($language) - $subtitleFormat")
                    }
                }
            }
            
            Log.d(TAG, "共发现 ${tracks.size} 个内嵌字幕轨道")
            return@withContext tracks
            
        } catch (e: Exception) {
            Log.e(TAG, "提取字幕轨道失败", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * 根据语言优先级选择最佳字幕轨道
     * 优先级：简英双语 → 简体 → 繁英双语 → 繁体 → 英语
     */
    fun selectBestSubtitle(tracks: List<EmbeddedSubtitleTrack>): EmbeddedSubtitleTrack? {
        if (tracks.isEmpty()) return null
        
        // 定义语言优先级
        val languagePriority = mapOf(
            "chi" to 1, "zh" to 1,
            "zh-CN" to 2, "zh-Hans" to 2, "zho" to 2, "chi_sim" to 2,
            "zh-TW" to 4, "zh-Hant" to 4, "chi_tra" to 4,
            "eng" to 5, "en" to 5, "en-US" to 5
        )
        
        // 检查标签中的关键字
        fun getLanguagePriorityByLabel(label: String): Int {
            return when {
                label.contains("简", ignoreCase = true) && 
                (label.contains("英", ignoreCase = true) || label.contains("双语", ignoreCase = true)) -> 1
                label.contains("简", ignoreCase = true) || label.contains("chs", ignoreCase = true) -> 2
                label.contains("繁", ignoreCase = true) && 
                (label.contains("英", ignoreCase = true) || label.contains("雙語", ignoreCase = true)) -> 3
                label.contains("繁", ignoreCase = true) || label.contains("cht", ignoreCase = true) -> 4
                label.contains("eng", ignoreCase = true) || label.contains("英", ignoreCase = true) -> 5
                else -> 99
            }
        }
        
        // 按优先级排序
        val sortedTracks = tracks.sortedWith(compareBy(
            { 
                val labelPriority = getLanguagePriorityByLabel(it.label)
                if (labelPriority < 99) labelPriority 
                else languagePriority[it.language] ?: 99
            },
            { it.trackIndex }
        ))
        
        val selected = sortedTracks.first()
        Log.d(TAG, "选择字幕轨道: ${selected.label} (${selected.language})")
        
        return selected
    }
}

/**
 * 内嵌字幕轨道信息
 */
data class EmbeddedSubtitleTrack(
    val trackIndex: Int,
    val groupIndex: Int,
    val language: String,
    val label: String,
    val format: SubtitleFormat,
    val mimeType: String
)
