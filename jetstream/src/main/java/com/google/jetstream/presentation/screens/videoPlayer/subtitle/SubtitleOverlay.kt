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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity

/**
 * 字幕显示组件
 * 使用行业标准样式：白色文字 + 黑色描边 + 阴影
 */
@Composable
fun SubtitleOverlay(
    subtitle: SubtitleItem,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        // 使用多层文字模拟描边效果
        // 底层：黑色描边（绘制多次形成描边）
        val strokeWidth = 1.2.dp
        val density = LocalDensity.current
        val strokeWidthPx = with(density) { strokeWidth.toPx() }
        
        Box(contentAlignment = Alignment.Center) {
            // 描边层（黑色，多个方向）
            listOf(
                Offset(-strokeWidthPx, -strokeWidthPx),
                Offset(strokeWidthPx, -strokeWidthPx),
                Offset(-strokeWidthPx, strokeWidthPx),
                Offset(strokeWidthPx, strokeWidthPx),
                Offset(0f, -strokeWidthPx),
                Offset(0f, strokeWidthPx),
                Offset(-strokeWidthPx, 0f),
                Offset(strokeWidthPx, 0f)
            ).forEach { offset ->
                Text(
                    text = subtitle.text,
                    fontSize = 28.sp,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp,
                    modifier = Modifier.offset(
                        x = with(density) { offset.x.toDp() },
                        y = with(density) { offset.y.toDp() }
                    )
                )
            }
            
            // 主文字层（白色，带阴影）
            Text(
                text = subtitle.text,
                fontSize = 28.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.8f),
                        offset = Offset(2f, 2f),
                        blurRadius = 4f
                    )
                )
            )
        }
    }
}

/**
 * 字幕选择对话框
 */
@Composable
fun SubtitleSelectorDialog(
    availableSubtitles: List<SubtitleTrack>,
    selectedSubtitle: SubtitleTrack?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSubtitleSelected: (SubtitleTrack) -> Unit,
    onToggleEnabled: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("字幕设置") },
        text = {
            Column {
                // 启用/禁用字幕选项
                SubtitleOption(
                    name = if (enabled) "✓ 显示字幕" else "隐藏字幕",
                    selected = enabled,
                    onClick = onToggleEnabled
                )
                
                if (availableSubtitles.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text(
                        text = "选择字幕轨道：",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // 字幕轨道列表
                    availableSubtitles.forEach { track ->
                        SubtitleOption(
                            name = track.name,
                            selected = selectedSubtitle == track,
                            onClick = { onSubtitleSelected(track) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 字幕选项组件
 */
@Composable
fun SubtitleOption(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = name)
    }
}

