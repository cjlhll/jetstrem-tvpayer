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

package com.google.jetstream.presentation.screens.videoPlayer.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.jetstream.presentation.theme.JetStreamTheme

/**
 * Handles the visibility and animation of the controls.
 */
@Composable
fun VideoPlayerOverlay(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    isControlsVisible: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
    showControls: () -> Unit = {},
    centerButton: @Composable () -> Unit = {},
    controls: @Composable () -> Unit = {}
) {
    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(isPlaying) {
        showControls()
    }

    Box(
        // 使用fillMaxSize确保背景覆盖全屏，但不阻止其他元素显示
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 背景层
        AnimatedVisibility(isControlsVisible, Modifier, fadeIn(), fadeOut()) {
            CinematicBackground(Modifier.fillMaxSize())
        }

        // 控制栏层
        Column {
            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                isControlsVisible,
                Modifier,
                slideInVertically { it },
                slideOutVertically { it }
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 56.dp)
                        .padding(bottom = 36.dp, top = 8.dp)
                ) {
                    controls()
                }
            }
        }

        // 中心按钮层
        centerButton()
    }
}

@Composable
fun CinematicBackground(modifier: Modifier = Modifier) {
    Spacer(
        modifier.background(
            Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = 0.1f),
                    Color.Black.copy(alpha = 0.8f)
                )
            )
        )
    )
}

@Preview(device = "id:tv_4k")
@Composable
private fun VideoPlayerOverlayPreview() {
    JetStreamTheme {
        Box(Modifier.fillMaxSize()) {
            VideoPlayerOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                isPlaying = true,
                controls = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color.Blue)
                    )
                },
                centerButton = {
                    Box(
                        Modifier
                            .size(88.dp)
                            .background(Color.Green)
                    )
                }
            )
        }
    }
}
