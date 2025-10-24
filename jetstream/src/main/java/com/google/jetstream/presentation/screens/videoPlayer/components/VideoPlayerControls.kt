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

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import com.google.jetstream.data.entities.MovieDetails
import com.google.jetstream.data.util.StringConstants
import com.shuyu.gsyvideoplayer.GSYVideoManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun VideoPlayerControls(
    player: Any?, // 不再使用，保留为 null
    movieDetails: MovieDetails,
    focusRequester: FocusRequester,
    currentCore: com.google.jetstream.presentation.screens.videoPlayer.PlayerCore = com.google.jetstream.presentation.screens.videoPlayer.PlayerCore.MEDIA3,
    onSwitchCore: (com.google.jetstream.presentation.screens.videoPlayer.PlayerCore) -> Unit = {},
    onShowControls: () -> Unit = {},
    onClickSubtitles: () -> Unit = {},
    onClickAudio: () -> Unit = {}
) {
    // 从 GSYVideoManager 获取播放状态
    var isPlaying by remember { mutableStateOf(false) }
    var showCoreDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        while (isActive) {
            try {
                isPlaying = GSYVideoManager.instance().isPlaying
            } catch (_: Throwable) {}
            delay(500)
        }
    }

    VideoPlayerMainFrame(
        mediaTitle = {
            VideoPlayerMediaTitle(
                title = movieDetails.name,
                secondaryText = movieDetails.releaseDate,
                tertiaryText = movieDetails.director,
                type = VideoPlayerMediaTitleType.DEFAULT
            )
        },
        mediaActions = {
            Row(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .onPreviewKeyEvent { keyEvent ->
                        // 当焦点在此行按钮上时，拦截上键事件，防止焦点丢失
                        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP 
                            && keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            true // 消费上键事件，禁止焦点向上移动
                        } else {
                            false // 其他键正常处理
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VideoPlayerControlsIcon(
                    icon = Icons.Default.ClosedCaption,
                    isPlaying = isPlaying,
                    contentDescription = StringConstants.Composable.VideoPlayerControlClosedCaptionsButton,
                    onShowControls = onShowControls,
                    onClick = onClickSubtitles
                )
                VideoPlayerControlsIcon(
                    icon = Icons.Default.Audiotrack,
                    isPlaying = isPlaying,
                    contentDescription = StringConstants.Composable.VideoPlayerControlSettingsButton,
                    onShowControls = onShowControls,
                    onClick = onClickAudio
                )
                // 内核切换按钮
                VideoPlayerControlsIcon(
                    icon = Icons.Default.Settings,
                    isPlaying = isPlaying,
                    contentDescription = "切换播放内核",
                    onShowControls = onShowControls,
                    onClick = {
                        showCoreDialog = true
                    }
                )
            }
        },
        seeker = {
            VideoPlayerSeeker(
                player = null, // 改为 null，内部使用 GSYVideoManager
                focusRequester = focusRequester,
                onShowControls = onShowControls,
            )
        },
        more = null
    )
    
    // 内核选择对话框
    if (showCoreDialog) {
        CoreSelectorDialog(
            currentCore = currentCore,
            onDismiss = { showCoreDialog = false },
            onCoreSelected = { newCore ->
                onSwitchCore(newCore)
                showCoreDialog = false
            }
        )
    }
}
