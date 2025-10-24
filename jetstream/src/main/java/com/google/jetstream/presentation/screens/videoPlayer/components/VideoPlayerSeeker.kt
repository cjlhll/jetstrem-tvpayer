/*
 * Copyright 2024 Google LLC
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

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.google.jetstream.data.util.StringConstants
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun VideoPlayerSeeker(
    player: ExoPlayer?,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onShowControls: () -> Unit = {},
) {
    // 从 ExoPlayer 获取播放状态和进度
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    
    LaunchedEffect(player) {
        while (isActive) {
            if (player != null) {
                currentPositionMs = player.currentPosition
                durationMs = if (player.duration != C.TIME_UNSET) player.duration else 0L
                isPlaying = player.isPlaying
            }
            delay(300)
        }
    }
    
    val contentDuration = durationMs.milliseconds
    val currentPosition = currentPositionMs.milliseconds
    
    // Seek 回调
    val onSeek: (Float) -> Unit = { progress ->
        if (player != null) {
            val targetPos = (durationMs * progress).toLong()
            player.seekTo(targetPos)
        }
    }

    val contentProgressString =
        currentPosition.toComponents { h, m, s, _ ->
            if (h > 0) {
                "$h:${m.padStartWith0()}:${s.padStartWith0()}"
            } else {
                "${m.padStartWith0()}:${s.padStartWith0()}"
            }
        }
    val contentDurationString =
        contentDuration.toComponents { h, m, s, _ ->
            if (h > 0) {
                "$h:${m.padStartWith0()}:${s.padStartWith0()}"
            } else {
                "${m.padStartWith0()}:${s.padStartWith0()}"
            }
        }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoPlayerControlsIcon(
            modifier = Modifier.focusRequester(focusRequester),
            icon = if (!isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
            onClick = {
                if (player != null) {
                    if (isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                }
            },
            isPlaying = isPlaying,
            contentDescription = StringConstants
                .Composable
                .VideoPlayerControlPlayPauseButton
        )
        VideoPlayerControllerText(text = contentProgressString)
        VideoPlayerControllerIndicator(
            progress = if (durationMs > 0) (currentPosition / contentDuration).toFloat() else 0f,
            onSeek = onSeek,
            onShowControls = onShowControls
        )
        VideoPlayerControllerText(text = contentDurationString)
    }
}

private fun Number.padStartWith0() = this.toString().padStart(2, '0')
