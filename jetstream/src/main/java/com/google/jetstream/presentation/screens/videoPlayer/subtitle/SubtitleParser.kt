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

/**
 * SRT 字幕解析器
 */
class SRTSubtitleParser {
    
    /**
     * 解析 SRT 字幕文件
     */
    fun parseSRT(content: String): List<SubtitleItem> {
        val items = mutableListOf<SubtitleItem>()
        
        // SRT 格式：
        // 1
        // 00:00:10,500 --> 00:00:13,000
        // 字幕文本
        
        val blocks = content.split("\n\n").filter { it.isNotBlank() }
        
        for (block in blocks) {
            val lines = block.split("\n").filter { it.isNotBlank() }
            if (lines.size < 3) continue
            
            // 第二行是时间戳
            val timeLine = lines[1]
            val times = timeLine.split(" --> ")
            if (times.size != 2) continue
            
            val startMs = parseTimeToMillis(times[0].trim())
            val endMs = parseTimeToMillis(times[1].trim())
            
            // 第三行及之后是字幕文本（可能多行）
            val text = lines.drop(2).joinToString("\n")
            
            items.add(SubtitleItem(startMs, endMs, text))
        }
        
        return items.sortedBy { it.startTimeMs }
    }
    
    /**
     * 将 SRT 时间格式转换为毫秒
     * 格式：00:00:10,500 (小时:分钟:秒,毫秒)
     */
    private fun parseTimeToMillis(time: String): Long {
        try {
            val parts = time.replace(",", ".").split(":")
            if (parts.size != 3) return 0L
            
            val hours = parts[0].toLongOrNull() ?: 0L
            val minutes = parts[1].toLongOrNull() ?: 0L
            val seconds = parts[2].toDoubleOrNull() ?: 0.0
            
            return (hours * 3600 * 1000) + 
                   (minutes * 60 * 1000) + 
                   (seconds * 1000).toLong()
        } catch (e: Exception) {
            android.util.Log.e("SRTParser", "Error parsing time: $time", e)
            return 0L
        }
    }
}

/**
 * VTT 字幕解析器
 */
class VTTSubtitleParser {
    
    /**
     * 解析 VTT 字幕文件
     */
    fun parseVTT(content: String): List<SubtitleItem> {
        val items = mutableListOf<SubtitleItem>()
        
        // VTT 格式：
        // WEBVTT
        //
        // 00:00:10.500 --> 00:00:13.000
        // 字幕文本
        
        val lines = content.lines()
        var i = 0
        
        // 跳过 WEBVTT 头部
        while (i < lines.size && !lines[i].contains("-->")) {
            i++
        }
        
        while (i < lines.size) {
            val line = lines[i]
            
            if (line.contains("-->")) {
                val times = line.split("-->")
                if (times.size != 2) {
                    i++
                    continue
                }
                
                val startMs = parseVTTTime(times[0].trim())
                val endMs = parseVTTTime(times[1].trim())
                
                // 读取字幕文本（直到空行）
                val textLines = mutableListOf<String>()
                i++
                while (i < lines.size && lines[i].isNotBlank()) {
                    // 过滤掉 VTT 标签（如 <v Speaker>）
                    val cleanLine = lines[i].replace(Regex("<[^>]*>"), "").trim()
                    if (cleanLine.isNotEmpty()) {
                        textLines.add(cleanLine)
                    }
                    i++
                }
                
                val text = textLines.joinToString("\n")
                if (text.isNotEmpty()) {
                    items.add(SubtitleItem(startMs, endMs, text))
                }
            }
            i++
        }
        
        return items.sortedBy { it.startTimeMs }
    }
    
    /**
     * 将 VTT 时间格式转换为毫秒
     * 格式：00:00:10.500 或 00:10.500
     */
    private fun parseVTTTime(time: String): Long {
        try {
            val parts = time.split(":")
            if (parts.isEmpty()) return 0L
            
            val hours: Long
            val minutes: Long
            val seconds: Double
            
            when (parts.size) {
                3 -> {
                    // HH:MM:SS.mmm
                    hours = parts[0].toLongOrNull() ?: 0L
                    minutes = parts[1].toLongOrNull() ?: 0L
                    seconds = parts[2].toDoubleOrNull() ?: 0.0
                }
                2 -> {
                    // MM:SS.mmm
                    hours = 0L
                    minutes = parts[0].toLongOrNull() ?: 0L
                    seconds = parts[1].toDoubleOrNull() ?: 0.0
                }
                else -> return 0L
            }
            
            return (hours * 3600 * 1000) + 
                   (minutes * 60 * 1000) + 
                   (seconds * 1000).toLong()
        } catch (e: Exception) {
            android.util.Log.e("VTTParser", "Error parsing time: $time", e)
            return 0L
        }
    }
}

