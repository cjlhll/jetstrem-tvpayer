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

package com.google.jetstream.presentation.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CompactCard
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.google.jetstream.presentation.theme.JetStreamBorderWidth

/**
 * 将毫秒转换为时间格式字符串 (mm:ss 或 h:mm:ss)
 */
private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * 通用媒体卡片组件，可用于电影和剧集
 * 
 * @param imageUrl 图片URL
 * @param title 标题
 * @param subtitle 副标题（可选）
 * @param itemWidth 卡片宽度
 * @param watchProgress 观看进度（0.0-1.0，可选）
 * @param currentPositionMs 当前播放位置（毫秒，可选）
 * @param durationMs 总时长（毫秒，可选）
 * @param contentDescription 内容描述
 * @param onClick 点击回调
 * @param modifier Modifier
 */
@Composable
fun MediaCard(
    imageUrl: String,
    title: String,
    subtitle: String? = null,
    itemWidth: Dp,
    watchProgress: Float? = null,
    currentPositionMs: Long? = null,
    durationMs: Long? = null,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(JetStreamBorderWidth))
        var isFocused by remember { mutableStateOf(false) }
        
        // 卡片部分
        CompactCard(
            modifier = modifier
                .width(itemWidth)
                .aspectRatio(1.6f)
                .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
            scale = CardDefaults.scale(focusedScale = 1.1f),
            glow = CardDefaults.glow(
                focusedGlow = androidx.tv.material3.Glow(
                    elevationColor = MaterialTheme.colorScheme.onSurface,
                    elevation = 16.dp
                )
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(
                        width = JetStreamBorderWidth, 
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            ),
            colors = CardDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            onClick = onClick,
            image = {
                Box(modifier = Modifier.fillMaxSize()) {
                    val contentAlpha by animateFloatAsState(
                        targetValue = 1f, // 默认都是亮色，不再根据焦点状态改变透明度
                        label = "",
                    )
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = contentAlpha }
                    )
                    
                    // 显示进度条和时间信息
                    // 如果有观看进度，显示进度条和当前时间/总时间
                    // 如果没有观看进度但有总时长，只显示总时长
                    if (watchProgress != null || durationMs != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.8f)
                                        ),
                                        startY = 0f,
                                        endY = Float.POSITIVE_INFINITY
                                    )
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            // 时间显示在上方，右对齐
                            val timeText = if (watchProgress != null) {
                                // 有观看进度时显示：当前时间 / 总时间
                                val currentTime = currentPositionMs?.let { formatTime(it) } ?: "00:00"
                                val totalTime = durationMs?.let { formatTime(it) } ?: "00:00"
                                "$currentTime / $totalTime"
                            } else {
                                // 没有观看进度时只显示总时间
                                durationMs?.let { formatTime(it) } ?: "00:00"
                            }
                            
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(bottom = if (watchProgress != null) 4.dp else 0.dp)
                            )
                            
                            // 进度条在下方（仅当有观看进度时显示）
                            watchProgress?.let { progress ->
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp),
                                    color = Color.White,
                                    trackColor = Color.Gray.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            },
            title = {}
        )
        
        // 卡片外部下方的标题和副标题
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(itemWidth)
        ) {
            // 主标题
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                maxLines = if (subtitle != null) 1 else 2,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // 副标题（如果有）
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}