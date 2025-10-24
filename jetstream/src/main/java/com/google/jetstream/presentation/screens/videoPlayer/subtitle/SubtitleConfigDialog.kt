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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import com.google.jetstream.presentation.theme.JetStreamButtonShape

/**
 * 字幕配置弹窗
 * 
 * @param showDialog 是否显示弹窗
 * @param enabled 字幕是否启用
 * @param delayMs 字幕延迟（毫秒）
 * @param onDismiss 关闭弹窗回调
 * @param onToggleEnabled 切换字幕开关回调
 * @param onDelayChanged 延迟改变回调
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SubtitleConfigDialog(
    showDialog: Boolean,
    enabled: Boolean,
    delayMs: Long,
    onDismiss: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelayChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!showDialog) return

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "字幕设置",
                    style = MaterialTheme.typography.headlineSmall
                )

                // 字幕开关
                val switchInteractionSource = remember { MutableInteractionSource() }
                val isSwitchFocused by switchInteractionSource.collectIsFocusedAsState()
                
                androidx.tv.material3.Surface(
                    onClick = { onToggleEnabled(!enabled) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClickableSurfaceDefaults.shape(shape = JetStreamButtonShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)),
                            shape = JetStreamButtonShape
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = JetStreamButtonShape
                        )
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    interactionSource = switchInteractionSource
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "字幕开关",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        Switch(
                            checked = enabled,
                            onCheckedChange = null, // 由 Surface 处理点击
                            colors = SwitchDefaults.colors(
                                // 焦点时使用深色以便在白色背景上清晰可见
                                checkedThumbColor = if (isSwitchFocused) Color(0xFF1976D2) else MaterialTheme.colorScheme.primary,
                                checkedTrackColor = if (isSwitchFocused) Color(0xFF1976D2).copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                uncheckedThumbColor = if (isSwitchFocused) Color(0xFF424242) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                uncheckedTrackColor = if (isSwitchFocused) Color(0xFF757575) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 字幕延迟调整
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "字幕延迟",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 减少延迟按钮
                        Button(
                            onClick = { onDelayChanged(delayMs - 100) },
                            enabled = enabled,
                            modifier = Modifier
                                .weight(1f)
                                .focusProperties {
                                    // 禁用时不可获得焦点
                                    canFocus = enabled
                                },
                            shape = ButtonDefaults.shape(shape = JetStreamButtonShape),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = Border(
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)),
                                    shape = JetStreamButtonShape
                                ),
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, Color.White),
                                    shape = JetStreamButtonShape
                                )
                            ),
                            scale = ButtonDefaults.scale(focusedScale = 1.05f)
                        ) {
                            Text(
                                text = "-100ms",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        // 当前延迟显示
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(
                                    color = Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${delayMs}ms",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = if (enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                }
                            )
                        }
                        
                        // 增加延迟按钮
                        Button(
                            onClick = { onDelayChanged(delayMs + 100) },
                            enabled = enabled,
                            modifier = Modifier
                                .weight(1f)
                                .focusProperties {
                                    // 禁用时不可获得焦点
                                    canFocus = enabled
                                },
                            shape = ButtonDefaults.shape(shape = JetStreamButtonShape),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = Border(
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)),
                                    shape = JetStreamButtonShape
                                ),
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, Color.White),
                                    shape = JetStreamButtonShape
                                )
                            ),
                            scale = ButtonDefaults.scale(focusedScale = 1.05f)
                        ) {
                            Text(
                                text = "+100ms",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 关闭按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.width(120.dp),
                        shape = ButtonDefaults.shape(shape = JetStreamButtonShape),
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black
                        ),
                        border = ButtonDefaults.border(
                            border = Border(
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)),
                                shape = JetStreamButtonShape
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = JetStreamButtonShape
                            )
                        ),
                        scale = ButtonDefaults.scale(focusedScale = 1.05f)
                    ) {
                        Text(
                            text = "关闭",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

