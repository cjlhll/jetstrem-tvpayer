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

/**
 * ASS/SSA 字幕解析器
 */
class ASSSubtitleParser {
    
    /**
     * 解析 ASS/SSA 字幕文件
     */
    fun parseASS(content: String): List<SubtitleItem> {
        val items = mutableListOf<SubtitleItem>()
        
        // ASS 格式包含多个section:
        // [Script Info] - 脚本信息
        // [V4+ Styles] - 样式定义
        // [Events] - 事件(字幕)
        //
        // Events格式:
        // Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        // Dialogue: 0,0:00:10.50,0:00:13.00,Default,,0,0,0,,字幕文本
        
        val lines = content.lines()
        var inEventsSection = false
        var formatIndices = mapOf<String, Int>()
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            // 检查是否进入 Events section
            if (trimmedLine.equals("[Events]", ignoreCase = true)) {
                inEventsSection = true
                continue
            }
            
            // 检查是否离开 Events section
            if (trimmedLine.startsWith("[") && trimmedLine.endsWith("]") && 
                !trimmedLine.equals("[Events]", ignoreCase = true)) {
                inEventsSection = false
                continue
            }
            
            if (!inEventsSection) continue
            
            // 解析 Format 行
            if (trimmedLine.startsWith("Format:", ignoreCase = true)) {
                val formatLine = trimmedLine.substring(7).trim()
                val fields = formatLine.split(",").map { it.trim() }
                formatIndices = fields.withIndex().associate { it.value to it.index }
                continue
            }
            
            // 解析 Dialogue 行
            if (trimmedLine.startsWith("Dialogue:", ignoreCase = true)) {
                try {
                    val dialogueLine = trimmedLine.substring(9).trim()
                    val parts = dialogueLine.split(",", limit = formatIndices.size)
                    
                    if (parts.size < formatIndices.size) continue
                    
                    val startIdx = formatIndices["Start"] ?: continue
                    val endIdx = formatIndices["End"] ?: continue
                    val textIdx = formatIndices["Text"] ?: continue
                    
                    val startTime = parseASSTime(parts[startIdx].trim())
                    val endTime = parseASSTime(parts[endIdx].trim())
                    
                    // 获取文本内容（可能包含逗号，所以要重组）
                    val text = if (textIdx < parts.size) {
                        parts.subList(textIdx, parts.size).joinToString(",")
                            .let { cleanASSText(it) }
                    } else {
                        ""
                    }
                    
                    if (text.isNotEmpty()) {
                        items.add(SubtitleItem(startTime, endTime, text))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ASSParser", "Error parsing dialogue: $trimmedLine", e)
                }
            }
        }
        
        return items.sortedBy { it.startTimeMs }
    }
    
    /**
     * 将 ASS 时间格式转换为毫秒
     * 格式：H:MM:SS.CC (小时:分钟:秒.厘秒)
     * 例如：0:00:10.50
     */
    private fun parseASSTime(time: String): Long {
        try {
            val parts = time.split(":")
            if (parts.size != 3) return 0L
            
            val hours = parts[0].toLongOrNull() ?: 0L
            val minutes = parts[1].toLongOrNull() ?: 0L
            // 秒和厘秒用点分隔
            val secondParts = parts[2].split(".")
            val seconds = secondParts[0].toLongOrNull() ?: 0L
            // 厘秒（1/100秒）转换为毫秒
            val centiseconds = if (secondParts.size > 1) {
                secondParts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L
            } else {
                0L
            }
            
            return (hours * 3600 * 1000) + 
                   (minutes * 60 * 1000) + 
                   (seconds * 1000) + 
                   (centiseconds * 10)
        } catch (e: Exception) {
            android.util.Log.e("ASSParser", "Error parsing time: $time", e)
            return 0L
        }
    }
    
    /**
     * 清理 ASS 文本中的格式标签
     * ASS 支持多种标签，如 {\i1} {\b1} {\fs20} 等
     */
    private fun cleanASSText(text: String): String {
        return text
            // 移除所有 ASS 格式标签 (例如: {\i1}, {\b1}, {\fs20}, etc.)
            .replace(Regex("""\\[^\\}]*"""), "")
            .replace(Regex("""\{[^}]*\}"""), "")
            // 处理 \N 和 \n (ASS的换行符)
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            // 处理 \h (硬空格)
            .replace("\\h", " ")
            .trim()
    }
}

/**
 * TTML 字幕解析器
 */
class TTMLSubtitleParser {
    
    /**
     * 解析 TTML 字幕文件
     */
    fun parseTTML(content: String): List<SubtitleItem> {
        val items = mutableListOf<SubtitleItem>()
        
        // TTML 是基于 XML 的格式
        // <tt xmlns="http://www.w3.org/ns/ttml">
        //   <body>
        //     <div>
        //       <p begin="00:00:10.500" end="00:00:13.000">字幕文本</p>
        //     </div>
        //   </body>
        // </tt>
        
        try {
            // 简单的正则表达式解析（适用于基本的TTML文件）
            // 对于复杂的 TTML，建议使用 XML 解析器
            val pElementPattern = Regex(
                """<p\s+[^>]*begin\s*=\s*["']([^"']+)["'][^>]*end\s*=\s*["']([^"']+)["'][^>]*>(.*?)</p>""",
                RegexOption.DOT_MATCHES_ALL
            )
            
            // 也支持反向的 end 在前
            val pElementPattern2 = Regex(
                """<p\s+[^>]*end\s*=\s*["']([^"']+)["'][^>]*begin\s*=\s*["']([^"']+)["'][^>]*>(.*?)</p>""",
                RegexOption.DOT_MATCHES_ALL
            )
            
            // 查找所有 <p> 元素
            pElementPattern.findAll(content).forEach { match ->
                val (beginStr, endStr, textContent) = match.destructured
                val startMs = parseTTMLTime(beginStr)
                val endMs = parseTTMLTime(endStr)
                val text = cleanTTMLText(textContent)
                
                if (text.isNotEmpty()) {
                    items.add(SubtitleItem(startMs, endMs, text))
                }
            }
            
            pElementPattern2.findAll(content).forEach { match ->
                val (endStr, beginStr, textContent) = match.destructured
                val startMs = parseTTMLTime(beginStr)
                val endMs = parseTTMLTime(endStr)
                val text = cleanTTMLText(textContent)
                
                if (text.isNotEmpty() && items.none { it.startTimeMs == startMs && it.endTimeMs == endMs }) {
                    items.add(SubtitleItem(startMs, endMs, text))
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("TTMLParser", "Error parsing TTML", e)
        }
        
        return items.sortedBy { it.startTimeMs }
    }
    
    /**
     * 将 TTML 时间格式转换为毫秒
     * 支持多种格式:
     * - 00:00:10.500 (HH:MM:SS.mmm)
     * - 10.5s (秒)
     * - 10500ms (毫秒)
     * - 00:10.500 (MM:SS.mmm)
     */
    private fun parseTTMLTime(time: String): Long {
        try {
            val trimmedTime = time.trim()
            
            // 处理毫秒格式: 10500ms
            if (trimmedTime.endsWith("ms")) {
                return trimmedTime.removeSuffix("ms").toLongOrNull() ?: 0L
            }
            
            // 处理秒格式: 10.5s
            if (trimmedTime.endsWith("s")) {
                val seconds = trimmedTime.removeSuffix("s").toDoubleOrNull() ?: 0.0
                return (seconds * 1000).toLong()
            }
            
            // 处理时间码格式: HH:MM:SS.mmm 或 MM:SS.mmm
            val parts = trimmedTime.split(":")
            when (parts.size) {
                3 -> {
                    // HH:MM:SS.mmm
                    val hours = parts[0].toLongOrNull() ?: 0L
                    val minutes = parts[1].toLongOrNull() ?: 0L
                    val seconds = parts[2].toDoubleOrNull() ?: 0.0
                    return (hours * 3600 * 1000) + (minutes * 60 * 1000) + (seconds * 1000).toLong()
                }
                2 -> {
                    // MM:SS.mmm
                    val minutes = parts[0].toLongOrNull() ?: 0L
                    val seconds = parts[1].toDoubleOrNull() ?: 0.0
                    return (minutes * 60 * 1000) + (seconds * 1000).toLong()
                }
                else -> return 0L
            }
        } catch (e: Exception) {
            android.util.Log.e("TTMLParser", "Error parsing time: $time", e)
            return 0L
        }
    }
    
    /**
     * 清理 TTML 文本中的 XML 标签
     */
    private fun cleanTTMLText(text: String): String {
        return text
            // 处理 <br/> 标签为换行
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            // 移除所有 XML 标签
            .replace(Regex("""<[^>]+>"""), "")
            // 解码 HTML 实体
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            // 清理多余空白
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }
}

