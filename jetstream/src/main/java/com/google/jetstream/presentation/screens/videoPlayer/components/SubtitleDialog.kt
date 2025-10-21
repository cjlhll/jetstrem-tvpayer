package com.google.jetstream.presentation.screens.videoPlayer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.google.jetstream.tvmaterial.StandardDialog
import androidx.compose.ui.ExperimentalComposeUiApi

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SubtitleDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    languages: List<String>,
    selectedIndex: Int, // 0 = Off, >0 correspond to languages index+1
    onSelectIndex: (Int) -> Unit
) {
    StandardDialog(
        showDialog = show,
        onDismissRequest = onDismissRequest,
        title = { Text(text = "Subtitles", style = MaterialTheme.typography.titleLarge) },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val items = listOf("Off") + languages
                items.forEachIndexed { idx, label ->
                    ListItem(
                        selected = idx == selectedIndex,
                        onClick = { onSelectIndex(idx) },
                        headlineContent = { Text(text = label) },
                        trailingContent = {
                            if (idx == selectedIndex) {
                                Icon(imageVector = Icons.Default.Done, contentDescription = null)
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                            selectedContainerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.4f),
                            focusedContentColor = MaterialTheme.colorScheme.surface,
                            selectedContentColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        },
        confirmButton = { }
    )
}
