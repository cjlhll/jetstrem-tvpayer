/*
 * Copyright 2025
 */

package com.google.jetstream.presentation.screens.videoPlayer.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun HoldSeekProgressBar(
    progress: Float,
    currentPositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val timeText = remember(currentPositionMs) { formatTime(currentPositionMs) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 56.dp)
    ) {
        val density = LocalDensity.current
        var labelWidthDp by remember { mutableStateOf(0.dp) }
        val maxWidthDp = this.maxWidth
        val xDp = (maxWidthDp * clamped) - (labelWidthDp / 2)
        val clampedXDp = xDp.coerceIn(0.dp, (maxWidthDp - labelWidthDp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val y = size.height / 2f
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = size.height,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(0f, y),
                end = Offset(size.width * clamped, y),
                strokeWidth = size.height,
                cap = StrokeCap.Round
            )
        }

        // Time label positioned above current progress (raised to avoid overlap)
        Text(
            text = timeText,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomStart)
                .offset(x = clampedXDp, y = -(height + 16.dp))
                .onGloballyPositioned { coords ->
                    with(density) { labelWidthDp = coords.size.width.toDp() }
                },
            textAlign = TextAlign.Center
        )

        // Total duration at the right end of the bar
        Text(
            text = formatTime(durationMs),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .offset(x = 0.dp, y = -(height + 16.dp)),
            textAlign = TextAlign.End
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = (totalSeconds % 60).toInt()
    val minutes = ((totalSeconds / 60) % 60).toInt()
    val hours = (totalSeconds / 3600).toInt()
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}


