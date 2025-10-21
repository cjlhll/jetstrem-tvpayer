package com.google.jetstream.presentation.screens.videoPlayer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
                    var isFocused by remember { mutableStateOf(false) }
                    
                    ListItem(
                        selected = idx == selectedIndex,
                        onClick = { onSelectIndex(idx) },
                        modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
                        headlineContent = { 
                            Text(
                                text = label,
                                color = if (isFocused) Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingContent = {
                            if (idx == selectedIndex) {
                                Icon(
                                    imageVector = Icons.Default.Done, 
                                    contentDescription = null,
                                    tint = if (isFocused) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedContainerColor = Color.White
                        )
                    )
                }
            }
        },
        confirmButton = { }
    )
}
