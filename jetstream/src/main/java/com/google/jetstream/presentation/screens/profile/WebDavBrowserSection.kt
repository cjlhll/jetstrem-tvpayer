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
import com.google.jetstream.presentation.screens.webdav.WebDavBrowserViewModel
import androidx.compose.runtime.rememberCoroutineScope
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
    val serverName: String,
    val configId: String = ""
)

/**
 * 资源目录列表组件
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WebDavBrowserSection(
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 72.dp,
    viewModel: WebDavBrowserViewModel = hiltViewModel(),
    repository: WebDavRepository = viewModel.repository,
    onDirectoryDeleted: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var showWebDavListDialog by remember { mutableStateOf(false) }
    var showDirectoryPickerDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedWebDavConfig by remember { mutableStateOf<WebDavConfig?>(null) }
    var selectedDirectoryForDelete by remember { mutableStateOf<ResourceDirectory?>(null) }
    var editingDirectory by remember { mutableStateOf<ResourceDirectory?>(null) }
    var isEditMode by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // 从Repository加载WebDAV配置和资源目录
    val webDavConfigs by repository.getAllWebDavConfigs().collectAsState(initial = emptyList())
    val resourceDirectories by repository.getAllResourceDirectories().collectAsState(initial = emptyList())
    
    // 使用LaunchedEffect来处理初始化异常
    LaunchedEffect(Unit) {
        try {
            // 这里可以添加任何需要在组件初始化时执行的操作
        } catch (e: Exception) {
            hasError = true
            errorMessage = "初始化失败: ${e.message}"
        }
    }

    // 如果有错误，显示错误信息
    if (hasError) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 72.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "加载失败",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Button(
                    onClick = {
                        hasError = false
                        errorMessage = ""
                    }
                ) {
                    Text("重试")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding),
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
                    onClick = {
                        // 清除编辑状态，进入新建模式
                        editingDirectory = null
                        isEditMode = false
                        showWebDavListDialog = true
                    },
                    scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1f)
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
                    onClick = {
                        // 点击时进入编辑模式，重新选择目录
                        coroutineScope.launch {
                            try {
                                // 加载完整的配置（包括密码）
                                val configEntity = repository.getWebDavConfigEntityById(directory.configId)
                                if (configEntity != null) {
                                    // 转换为 data 层的 WebDavConfig（包含密码）
                                    val dataConfig = com.google.jetstream.data.webdav.WebDavConfig(
                                        serverUrl = configEntity.serverUrl,
                                        username = configEntity.username,
                                        password = configEntity.password,
                                        displayName = configEntity.displayName,
                                        isEnabled = true
                                    )
                                    
                                    // 设置到 WebDavService
                                    viewModel.webDavService.setConfig(dataConfig)
                                    
                                    // 加载 presentation 层的配置用于显示
                                    val presentationConfig = repository.getWebDavConfigById(directory.configId)
                                    if (presentationConfig != null) {
                                        editingDirectory = directory
                                        selectedWebDavConfig = presentationConfig
                                        isEditMode = true
                                        showDirectoryPickerDialog = true
                                    }
                                } else {
                                    hasError = true
                                    errorMessage = "找不到对应的WebDAV配置"
                                }
                            } catch (e: Exception) {
                                hasError = true
                                errorMessage = "加载配置失败: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyUp && 
                                (keyEvent.key == Key.Menu || keyEvent.key == Key.DirectionLeft)) {
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
            // 设置配置到 webDavService
            coroutineScope.launch {
                try {
                    val configEntity = repository.getWebDavConfigEntityById(config.id)
                    if (configEntity != null) {
                        val dataConfig = com.google.jetstream.data.webdav.WebDavConfig(
                            serverUrl = configEntity.serverUrl,
                            username = configEntity.username,
                            password = configEntity.password,
                            displayName = configEntity.displayName,
                            isEnabled = true
                        )
                        viewModel.webDavService.setConfig(dataConfig)
                        selectedWebDavConfig = config
                        showDirectoryPickerDialog = true
                    } else {
                        hasError = true
                        errorMessage = "找不到对应的WebDAV配置"
                    }
                } catch (e: Exception) {
                    hasError = true
                    errorMessage = "加载配置失败: ${e.message}"
                }
            }
        },
        webDavConfigs = webDavConfigs,
        onDeleteConfig = { configId ->
            coroutineScope.launch {
                try {
                    repository.deleteWebDavConfig(configId)
                } catch (e: Exception) {
                    hasError = true
                    errorMessage = "删除WebDAV配置失败: ${e.message}"
                }
            }
        },
        repository = repository,
        webDavService = viewModel.webDavService
    )

    // 目录选择弹窗
    DirectoryPickerDialog(
        showDialog = showDirectoryPickerDialog,
        viewModel = viewModel,
        initialPath = if (isEditMode && editingDirectory != null) editingDirectory!!.path else "",
        onDismiss = {
            showDirectoryPickerDialog = false
            selectedWebDavConfig = null
            editingDirectory = null
            isEditMode = false
        },
        onBackToServerList = {
            showDirectoryPickerDialog = false
            if (!isEditMode) {
                showWebDavListDialog = true
            }
            editingDirectory = null
            isEditMode = false
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

                val directory = if (isEditMode && editingDirectory != null) {
                    // 编辑模式：更新现有目录
                    ResourceDirectory(
                        id = editingDirectory!!.id,
                        name = directoryName,
                        path = path,
                        serverName = config.displayName,
                        configId = config.id
                    )
                } else {
                    // 新建模式：创建新目录
                    ResourceDirectory(
                        id = "",
                        name = directoryName,
                        path = path,
                        serverName = config.displayName,
                        configId = config.id
                    )
                }

                coroutineScope.launch {
                    try {
                        repository.saveResourceDirectory(directory, config.id, config.displayName)
                        // 重置状态
                        editingDirectory = null
                        isEditMode = false
                    } catch (e: Exception) {
                        hasError = true
                        errorMessage = "保存资源目录失败: ${e.message}"
                    }
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
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                    ) {
                        Button(
                            onClick = { showDeleteDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "取消",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        Button(
                            onClick = {
                                selectedDirectoryForDelete?.let { directory ->
                                    coroutineScope.launch {
                                        try {
                                            repository.deleteResourceDirectory(directory.id)
                                            // 删除成功后触发刷新回调
                                            onDirectoryDeleted?.invoke()
                                        } catch (e: Exception) {
                                            hasError = true
                                            errorMessage = "删除资源目录失败: ${e.message}"
                                        }
                                    }
                                }
                                showDeleteDialog = false
                                selectedDirectoryForDelete = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "确定",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}


