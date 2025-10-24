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

package com.google.jetstream.presentation.screens.videoPlayer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.jetstream.presentation.screens.videoPlayer.PlayerCore

/**
 * 内核选择对话框
 */
@Composable
fun CoreSelectorDialog(
    currentCore: PlayerCore,
    onDismiss: () -> Unit,
    onCoreSelected: (PlayerCore) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择播放内核") },
        text = {
            Column {
                RadioButtonOption(
                    text = "Media3 (ExoPlayer) - 推荐",
                    description = "官方支持，功能完善，HLS/DASH 支持好",
                    selected = currentCore == PlayerCore.MEDIA3,
                    onClick = { onCoreSelected(PlayerCore.MEDIA3) }
                )
                RadioButtonOption(
                    text = "IJKPlayer - 更多格式",
                    description = "支持 mpeg、rtsp 等特殊格式",
                    selected = currentCore == PlayerCore.IJK,
                    onClick = { onCoreSelected(PlayerCore.IJK) }
                )
                RadioButtonOption(
                    text = "系统播放器 - 轻量级",
                    description = "体积最小，基础功能",
                    selected = currentCore == PlayerCore.SYSTEM,
                    onClick = { onCoreSelected(PlayerCore.SYSTEM) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun RadioButtonOption(
    text: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text)
            Text(
                text = description,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

