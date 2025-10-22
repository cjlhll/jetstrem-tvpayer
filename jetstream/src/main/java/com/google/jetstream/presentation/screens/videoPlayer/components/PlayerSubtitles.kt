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

import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.SubtitleView

@Composable
fun PlayerSubtitles(
    player: ExoPlayer,
    contentPadding: PaddingValues = PaddingValues(horizontal = 56.dp, vertical = 8.dp)
) {
    var subtitleView by remember { mutableStateOf<SubtitleView?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                Log.d("PlayerSubtitles", "onCues size=${cueGroup.cues.size}")
                // Forward cues directly to SubtitleView which aligns rendering to the player's time base
                subtitleView?.setCues(cueGroup.cues)
            }
        }
        Log.d("PlayerSubtitles", "attach listener; currentCues=${try { player.currentCues.cues.size } catch (_: Throwable) { -1 }}")
        player.addListener(listener)
        try { subtitleView?.setCues(player.currentCues.cues) } catch (_: Throwable) {}
        onDispose { player.removeListener(listener) }
    }

    AndroidView<SubtitleView>(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding),
        factory = { ctx ->
            SubtitleView(ctx).apply {
                // Basic styling aligned with previous UI intent (slightly translucent background-like effect is handled by the view)
                setApplyEmbeddedStyles(true)
                setApplyEmbeddedFontSizes(true)
                // Slightly larger default size for TV readability
                setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.15f)
                subtitleView = this
            }
        },
        update = { _ ->
            // No-op; cues are pushed via listener
        }
    )
}


