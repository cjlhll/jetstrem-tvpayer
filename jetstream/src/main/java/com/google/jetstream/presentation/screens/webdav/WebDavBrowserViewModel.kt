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

package com.google.jetstream.presentation.screens.webdav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.jetstream.data.webdav.WebDavResult
import com.google.jetstream.data.webdav.WebDavService
import com.thegrizzlylabs.sardineandroid.DavResource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * WebDAV目录浏览器的ViewModel
 */
@HiltViewModel
class WebDavBrowserViewModel @Inject constructor(
    val webDavService: WebDavService,
    val repository: com.google.jetstream.data.repositories.WebDavRepository
) : ViewModel() {

    // UI状态
    private val _uiState = MutableStateFlow(WebDavBrowserUiState())
    val uiState: StateFlow<WebDavBrowserUiState> = _uiState.asStateFlow()

    // 当前路径
    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    // 目录项目
    private val _directoryItems = MutableStateFlow<WebDavResult<List<WebDavDirectoryItem>>>(WebDavResult.Loading)
    val directoryItems: StateFlow<WebDavResult<List<WebDavDirectoryItem>>> = _directoryItems.asStateFlow()

    // 路径面包屑
    private val _breadcrumbs = MutableStateFlow<List<String>>(emptyList())
    val breadcrumbs: StateFlow<List<String>> = _breadcrumbs.asStateFlow()

    // 保存的路径
    private val _savedPath = MutableStateFlow("")
    val savedPath: StateFlow<String> = _savedPath.asStateFlow()

    /**
     * 加载目录内容
     */
    fun loadDirectory(path: String = _currentPath.value) {
        if (!webDavService.isConfigured()) {
            _directoryItems.value = WebDavResult.Error("请先配置WebDAV服务器")
            return
        }

        viewModelScope.launch {
            _directoryItems.value = WebDavResult.Loading
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = webDavService.listDirectory(path)) {
                is WebDavResult.Success -> {
                    val items = result.data
                        .filter { resource ->
                            // 过滤掉当前目录本身和父目录引用
                            val resourceName = resource.name.removeSuffix("/")
                            val resourcePath = resource.path

                            // 过滤条件：
                            // 1. 名称不为空
                            // 2. 不是当前目录本身
                            // 3. 不是父目录的引用
                            // 4. 路径不等于当前路径
                            // 5. 在根目录时，过滤掉"dav"目录（WebDAV服务器根路径）
                            val isCurrentDirectory = resourcePath.endsWith("/$path/") ||
                                                    resourcePath.equals("/$path", ignoreCase = true) ||
                                                    resourceName == path.substringAfterLast("/")

                            val isDavRootDirectory = path.isEmpty() && resourceName.equals("dav", ignoreCase = true)

                            resourceName.isNotEmpty() && !isCurrentDirectory && !isDavRootDirectory
                        }
                        .map { davResource ->
                            WebDavDirectoryItem(
                                name = davResource.name.removeSuffix("/"),
                                path = davResource.path,
                                isDirectory = davResource.isDirectory,
                                contentLength = davResource.contentLength ?: 0L,
                                lastModified = davResource.modified
                            )
                        }
                        .sortedWith(compareBy<WebDavDirectoryItem> { !it.isDirectory }.thenBy { it.name })

                    _directoryItems.value = WebDavResult.Success(items)
                    _currentPath.value = path
                    updateBreadcrumbs(path)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                is WebDavResult.Error -> {
                    _directoryItems.value = result
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                else -> {
                    _directoryItems.value = WebDavResult.Error("未知错误")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "未知错误"
                    )
                }
            }
        }
    }

    /**
     * 导航到指定目录
     */
    fun navigateToDirectory(directoryName: String) {
        val newPath = if (_currentPath.value.isEmpty()) {
            directoryName
        } else {
            "${_currentPath.value}/$directoryName"
        }
        loadDirectory(newPath)
    }

    /**
     * 返回上级目录
     */
    fun navigateUp() {
        val currentPath = _currentPath.value
        if (currentPath.isNotEmpty()) {
            val parentPath = currentPath.substringBeforeLast("/", "")
            loadDirectory(parentPath)
        }
    }

    /**
     * 保存当前路径
     */
    fun saveCurrentPath() {
        _savedPath.value = _currentPath.value
        _uiState.value = _uiState.value.copy(
            successMessage = "路径已保存: ${_currentPath.value.ifEmpty { "根目录" }}"
        )
    }

    /**
     * 更新面包屑导航
     */
    private fun updateBreadcrumbs(path: String) {
        _breadcrumbs.value = if (path.isEmpty()) {
            emptyList()
        } else {
            path.split("/").filter { it.isNotEmpty() }
        }
    }

    /**
     * 清除消息
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }
}

/**
 * WebDAV目录浏览器UI状态
 */
data class WebDavBrowserUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

/**
 * WebDAV目录项目
 */
data class WebDavDirectoryItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val contentLength: Long,
    val lastModified: java.util.Date?
)
