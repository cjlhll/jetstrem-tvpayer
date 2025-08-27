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

package com.google.jetstream.presentation.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.google.jetstream.data.webdav.WebDavResult
import com.google.jetstream.presentation.screens.webdav.WebDavBrowserViewModel

/**
 * 目录选择弹窗
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DirectoryPickerDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onDirectorySaved: (String) -> Unit,
    onBackToServerList: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebDavBrowserViewModel = hiltViewModel()
) {
    if (!showDialog) return

    val currentPath by viewModel.currentPath.collectAsState()
    val directoryItems by viewModel.directoryItems.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.loadDirectory()
    }

    // 当目录内容变化时，聚焦到第一个项目
    LaunchedEffect(directoryItems) {
        val items = directoryItems
        if (items is WebDavResult.Success && items.data.isNotEmpty()) {
            focusRequester.requestFocus()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Back) {
                        if (breadcrumbs.isNotEmpty()) {
                            viewModel.navigateUp()
                            true
                        } else {
                            // 没有上级目录时，返回WebDAV服务器列表
                            onBackToServerList()
                            true
                        }
                    } else {
                        false
                    }
                },
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .height(500.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "选择目录",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        if (breadcrumbs.isNotEmpty()) {
                            Text(
                                text = "当前路径: ${breadcrumbs.joinToString(" / ")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 导航按钮
                if (breadcrumbs.isNotEmpty()) {
                    Button(
                        onClick = { viewModel.navigateUp() },
                        modifier = Modifier.focusRequester(focusRequester)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("返回上级")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 目录内容
                when (val result = directoryItems) {
                    is WebDavResult.Loading -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "正在加载目录...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    is WebDavResult.Error -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "加载失败",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = result.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Button(
                                    onClick = { viewModel.loadDirectory() }
                                ) {
                                    Text("重试")
                                }
                            }
                        }
                    }
                    is WebDavResult.Success -> {
                        if (result.data.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "目录为空",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(result.data.filter { it.isDirectory }) { item ->
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                text = item.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        leadingContent = {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = "文件夹",
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        selected = false,
                                        onClick = {
                                            viewModel.navigateToDirectory(item.name)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (result.data.indexOf(item) == 0) {
                                                    Modifier.focusRequester(focusRequester)
                                                } else {
                                                    Modifier
                                                }
                                            ),
                                        colors = ListItemDefaults.colors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                                            selectedContainerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.4f),
                                            focusedContentColor = MaterialTheme.colorScheme.surface,
                                            selectedContentColor = MaterialTheme.colorScheme.surface
                                        ),
                                        scale = ListItemDefaults.scale(focusedScale = 1.02f),
                                        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.extraSmall)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }

                    Button(
                        onClick = {
                            onDirectorySaved(currentPath)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存当前目录")
                    }
                }
            }
        }
    }
}
