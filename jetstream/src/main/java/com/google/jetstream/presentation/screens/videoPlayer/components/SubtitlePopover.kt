package com.google.jetstream.presentation.screens.videoPlayer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SubtitlePopover(
    languages: List<String>,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit = {}
) {
    Surface(tonalElevation = 4.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("字幕", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            if (languages.isNotEmpty()) {
                Text("可用语言：", style = MaterialTheme.typography.titleSmall)
                languages.forEach { label ->
                    Text("• ${label}", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text("暂无语言信息", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}


