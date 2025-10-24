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
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 内嵌字幕提取器 - 直接使用 Media3 ExoPlayer
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
     * 从 ExoPlayer 中提取内嵌字幕轨道信息
     */
    suspend fun extractSubtitleTracks(
        player: ExoPlayer
    ): List<EmbeddedSubtitleTrack> = withContext(Dispatchers.Main) {
        try {
            val tracks = mutableListOf<EmbeddedSubtitleTrack>()
            val currentTracks = player.currentTracks
            
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
     */
    fun selectBestSubtitle(tracks: List<EmbeddedSubtitleTrack>): EmbeddedSubtitleTrack? {
        if (tracks.isEmpty()) return null
        
        val languagePriority = mapOf(
            "chi" to 1, "zh" to 1,
            "zh-CN" to 2, "zh-Hans" to 2, "zho" to 2, "chi_sim" to 2,
            "zh-TW" to 4, "zh-Hant" to 4, "chi_tra" to 4,
            "eng" to 5, "en" to 5, "en-US" to 5
        )
        
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
