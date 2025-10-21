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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun PlayerSubtitles(
    player: ExoPlayer,
    contentPadding: PaddingValues = PaddingValues(horizontal = 56.dp, vertical = 8.dp)
) {
    var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                Log.d("PlayerSubtitles", "onCues size=${cueGroup.cues.size}")
                cues = cueGroup.cues
            }
        }
        Log.d("PlayerSubtitles", "attach listener; currentCues=${try { player.currentCues.cues.size } catch (_: Throwable) { -1 }}")
        player.addListener(listener)
        try { cues = player.currentCues.cues } catch (_: Throwable) {}
        onDispose { player.removeListener(listener) }
    }

    if (cues.isEmpty()) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .alpha(0.92f),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            text = cues.joinToString("\n") { it.text?.toString().orEmpty() }.trim(),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}


