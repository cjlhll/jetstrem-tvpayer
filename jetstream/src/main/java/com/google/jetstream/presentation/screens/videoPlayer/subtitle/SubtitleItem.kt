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
 * 字幕项数据模型
 */
data class SubtitleItem(
    val startTimeMs: Long,      // 字幕开始时间（毫秒）
    val endTimeMs: Long,        // 字幕结束时间（毫秒）
    val text: String            // 字幕文本内容
)

/**
 * 字幕轨道数据模型
 */
data class SubtitleTrack(
    val name: String,           // 字幕名称（如"中文"、"英文"）
    val url: String,            // 字幕文件 URL
    val language: String,       // 语言代码（zh, en）
    val format: SubtitleFormat  // 字幕格式
)

/**
 * 字幕格式枚举
 */
enum class SubtitleFormat {
    SRT,    // SubRip (.srt)
    VTT,    // WebVTT (.vtt)
    ASS,    // Advanced SubStation Alpha (.ass)
    TTML    // Timed Text Markup Language (.ttml)
}

