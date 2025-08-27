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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.jetstream.data.repositories.WebDavRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * 资源目录数据类
 */
data class ResourceDirectory(
    val id: String,
    val name: String,
    val path: String,
    val serverName: String
)

/**
 * 资源目录列表组件
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WebDavBrowserSection(
    modifier: Modifier = Modifier,
    repository: WebDavRepository = hiltViewModel<WebDavBrowserViewModel>().repository
) {
    var showWebDavListDialog by remember { mutableStateOf(false) }
    var showDirectoryPickerDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedWebDavConfig by remember { mutableStateOf<WebDavConfig?>(null) }
    var selectedDirectoryForDelete by remember { mutableStateOf<ResourceDirectory?>(null) }

    // 从Repository加载WebDAV配置和资源目录
    val webDavConfigs by repository.getAllWebDavConfigs().collectAsState(initial = emptyList())
    val resourceDirectories by repository.getAllResourceDirectories().collectAsState(initial = emptyList())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 72.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "资源目录",
                    style = MaterialTheme.typography.headlineSmall
                )

                Button(
                    onClick = { showWebDavListDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加")
                }
            }
        }

        // 资源目录列表
        if (resourceDirectories.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "暂无资源目录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "点击右上角添加按钮选择目录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        } else {
            items(resourceDirectories) { directory ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = directory.name
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                text = "路径: ${directory.path}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "服务器: ${directory.serverName}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    selected = false,
                    onClick = { /* 可以添加点击查看目录内容的功能 */ },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Menu) {
                                selectedDirectoryForDelete = directory
                                showDeleteDialog = true
                                true
                            } else {
                                false
                            }
                        },
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

    // WebDAV列表弹窗
    WebDavListDialog(
        showDialog = showWebDavListDialog,
        onDismiss = { showWebDavListDialog = false },
        onConfigSelected = { config ->
            selectedWebDavConfig = config
            showDirectoryPickerDialog = true
        },
        webDavConfigs = webDavConfigs,
        onDeleteConfig = { configId ->
            CoroutineScope(Dispatchers.IO).launch {
                repository.deleteWebDavConfig(configId)
            }
        },
        repository = repository,
        webDavService = hiltViewModel<WebDavBrowserViewModel>().webDavService
    )

    // 目录选择弹窗
    DirectoryPickerDialog(
        showDialog = showDirectoryPickerDialog,
        onDismiss = {
            showDirectoryPickerDialog = false
            selectedWebDavConfig = null
        },
        onBackToServerList = {
            showDirectoryPickerDialog = false
            showWebDavListDialog = true
        },
        onDirectorySaved = { path ->
            selectedWebDavConfig?.let { config ->
                val directoryName = if (path.isEmpty()) {
                    "${config.displayName} - 根目录"
                } else {
                    val folderName = path.substringAfterLast("/").ifEmpty {
                        path.substringBeforeLast("/").substringAfterLast("/")
                    }
                    "${config.displayName} - ${folderName}"
                }

                val newDirectory = ResourceDirectory(
                    id = "",
                    name = directoryName,
                    path = path,
                    serverName = config.displayName
                )

                CoroutineScope(Dispatchers.IO).launch {
                    repository.saveResourceDirectory(newDirectory, config.id, config.displayName)
                }
            }
        }
    )

    // 删除确认对话框
    if (showDeleteDialog && selectedDirectoryForDelete != null) {
        Dialog(onDismissRequest = { showDeleteDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "确认删除",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "确定要删除资源目录 \"${selectedDirectoryForDelete!!.name}\" 吗？",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { showDeleteDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("取消")
                        }
                        Button(
                            onClick = {
                                selectedDirectoryForDelete?.let { directory ->
                                    CoroutineScope(Dispatchers.IO).launch {
                                        repository.deleteResourceDirectory(directory.id)
                                    }
                                }
                                showDeleteDialog = false
                                selectedDirectoryForDelete = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}


