package com.google.jetstream.presentation.screens.videoPlayer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onSelectIndex: (Int) -> Unit,
    subtitleDelayMs: Int,
    onAdjustDelay: (Int) -> Unit
) {
    StandardDialog(
        showDialog = show,
        onDismissRequest = onDismissRequest,
        title = { Text(text = "字幕", style = MaterialTheme.typography.titleLarge) },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // One-line delay controls at the top: [ - ] [ current ] [ + ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var focusMinus by remember { mutableStateOf(false) }
                    var focusPlus by remember { mutableStateOf(false) }

                    androidx.tv.material3.ListItem(
                        selected = false,
                        onClick = { onAdjustDelay(-250) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .onFocusChanged { focusMinus = it.isFocused },
                        headlineContent = {
                            Text(
                                text = "−",
                                color = if (focusMinus) Color.Black else MaterialTheme.colorScheme.onSurface,
                                fontSize = 28.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        colors = androidx.tv.material3.ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedContainerColor = Color.White
                        )
                    )

                    androidx.tv.material3.ListItem(
                        selected = false,
                        onClick = {},
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        headlineContent = {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                Text(
                                    text = "${subtitleDelayMs} ms",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    fontSize = 22.sp,
                                    lineHeight = 22.sp,
                                    maxLines = 1
                                )
                            }
                        },
                        colors = androidx.tv.material3.ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    androidx.tv.material3.ListItem(
                        selected = false,
                        onClick = { onAdjustDelay(250) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .onFocusChanged { focusPlus = it.isFocused },
                        headlineContent = {
                            Text(
                                text = "+",
                                color = if (focusPlus) Color.Black else MaterialTheme.colorScheme.onSurface,
                                fontSize = 28.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        colors = androidx.tv.material3.ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedContainerColor = Color.White
                        )
                    )
                }
                val items = listOf("关闭字幕") + languages
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(items) { idx, label ->
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

                // end of timing controls
            }
        },
        confirmButton = { }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AudioDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    options: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
) {
    StandardDialog(
        showDialog = show,
        onDismissRequest = onDismissRequest,
        title = { Text(text = "音轨", style = MaterialTheme.typography.titleLarge) },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(options) { idx, label ->
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
