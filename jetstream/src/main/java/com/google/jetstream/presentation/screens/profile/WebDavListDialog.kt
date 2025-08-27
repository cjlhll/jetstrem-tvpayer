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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.jetstream.data.repositories.WebDavRepository
import com.google.jetstream.data.webdav.WebDavService
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
 * WebDAV配置数据类
 */
data class WebDavConfig(
    val id: String,
    val displayName: String,
    val serverUrl: String,
    val username: String,
    val isConnected: Boolean = false
)

/**
 * WebDAV列表弹窗
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WebDavListDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfigSelected: (WebDavConfig) -> Unit,
    webDavConfigs: List<WebDavConfig>,
    onDeleteConfig: (String) -> Unit,
    repository: WebDavRepository,
    webDavService: WebDavService,
    modifier: Modifier = Modifier
) {
    if (!showDialog) return

    var showConfigDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedConfigForDelete by remember { mutableStateOf<WebDavConfig?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WebDAV服务器",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    IconButton(
                        onClick = { showConfigDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加WebDAV配置",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // WebDAV配置列表
                if (webDavConfigs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "暂无WebDAV配置",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "点击右上角添加按钮创建配置",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(webDavConfigs) { config ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = config.displayName
                                    )
                                },
                                supportingContent = {
                                    Column {
                                        Text(
                                            text = config.serverUrl,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "用户: ${config.username}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = if (config.isConnected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        }
                                    )
                                },
                                selected = false,
                                onClick = {
                                    isConnecting = true
                                    connectionError = null
                                    coroutineScope.launch {
                                        try {
                                            // 从数据库获取完整配置信息（包括密码）
                                            val configEntity = repository.getWebDavConfigEntityById(config.id)
                                            if (configEntity != null) {
                                                // 创建WebDAV配置对象
                                                val webDavConfig = com.google.jetstream.data.webdav.WebDavConfig(
                                                    serverUrl = configEntity.serverUrl,
                                                    username = configEntity.username,
                                                    password = configEntity.password,
                                                    isEnabled = true
                                                )

                                                val result = webDavService.testConnection(webDavConfig)
                                                if (result is com.google.jetstream.data.webdav.WebDavResult.Success) {
                                                    webDavService.setConfig(webDavConfig)
                                                    isConnecting = false
                                                    onConfigSelected(config)
                                                    onDismiss()
                                                } else {
                                                    isConnecting = false
                                                    connectionError = "连接失败，请检查配置信息"
                                                }
                                            } else {
                                                isConnecting = false
                                                connectionError = "配置信息不完整"
                                            }
                                        } catch (e: Exception) {
                                            isConnecting = false
                                            connectionError = "连接失败: ${e.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Menu) {
                                        selectedConfigForDelete = config
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

                Spacer(modifier = Modifier.height(16.dp))

                // 连接状态和错误信息显示
                if (isConnecting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "正在连接...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                connectionError?.let { error ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }

    // WebDAV配置弹窗
    WebDavConfigDialog(
        showDialog = showConfigDialog,
        onDismiss = { showConfigDialog = false },
        onSaved = {
            // 配置保存后自动刷新列表（通过Repository的Flow）
            showConfigDialog = false
        }
    )

    // 删除确认对话框
    if (showDeleteDialog && selectedConfigForDelete != null) {
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
                        text = "确定要删除WebDAV配置 \"${selectedConfigForDelete!!.displayName}\" 吗？\n\n删除后相关的资源目录也会被删除。",
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
                                selectedConfigForDelete?.let { config ->
                                    onDeleteConfig(config.id)
                                }
                                showDeleteDialog = false
                                selectedConfigForDelete = null
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
