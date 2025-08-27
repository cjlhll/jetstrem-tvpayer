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
import com.google.jetstream.data.repositories.WebDavRepository
import com.google.jetstream.data.webdav.WebDavConfig
import com.google.jetstream.data.webdav.WebDavConnectionStatus
import com.google.jetstream.data.webdav.WebDavResult
import com.google.jetstream.data.webdav.WebDavService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * WebDAV配置页面的ViewModel
 */
@HiltViewModel
class WebDavConfigViewModel @Inject constructor(
    private val webDavService: WebDavService,
    private val webDavRepository: WebDavRepository
) : ViewModel() {

    // UI状态
    private val _uiState = MutableStateFlow(WebDavConfigUiState())
    val uiState: StateFlow<WebDavConfigUiState> = _uiState.asStateFlow()

    // 配置字段
    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _connectionStatus = MutableStateFlow(WebDavConnectionStatus.IDLE)
    val connectionStatus: StateFlow<WebDavConnectionStatus> = _connectionStatus.asStateFlow()

    // 配置是否有效的状态流
    val isConfigValid: StateFlow<Boolean> = combine(
        _serverUrl,
        _username,
        _password
    ) { serverUrl, username, password ->
        val config = WebDavConfig(
            serverUrl = serverUrl.trim(),
            username = username.trim(),
            password = password,
            displayName = _displayName.value.trim().ifEmpty { "WebDAV服务器" }
        )
        config.isValid()
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    init {
        // 加载现有配置
        loadCurrentConfig()
    }

    /**
     * 加载当前配置
     */
    private fun loadCurrentConfig() {
        val currentConfig = webDavService.getCurrentConfig()
        if (currentConfig != null) {
            _serverUrl.value = currentConfig.serverUrl
            _username.value = currentConfig.username
            _password.value = currentConfig.password
            _displayName.value = currentConfig.displayName
        }
    }

    /**
     * 更新服务器URL
     */
    fun updateServerUrl(url: String) {
        _serverUrl.value = url
        clearMessages()
    }

    /**
     * 更新用户名
     */
    fun updateUsername(username: String) {
        _username.value = username
        clearMessages()
    }

    /**
     * 更新密码
     */
    fun updatePassword(password: String) {
        _password.value = password
        clearMessages()
    }

    /**
     * 更新显示名称
     */
    fun updateDisplayName(name: String) {
        _displayName.value = name
        clearMessages()
    }

    /**
     * 测试连接
     */
    fun testConnection() {
        val config = getCurrentConfig()
        if (!config.isValid()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "请填写完整的配置信息"
            )
            return
        }

        viewModelScope.launch {
            _connectionStatus.value = WebDavConnectionStatus.TESTING
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                successMessage = null
            )

            when (val result = webDavService.testConnection(config)) {
                is WebDavResult.Success -> {
                    _connectionStatus.value = WebDavConnectionStatus.CONNECTED
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "连接测试成功！"
                    )
                }
                is WebDavResult.Error -> {
                    _connectionStatus.value = WebDavConnectionStatus.FAILED
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                else -> {
                    _connectionStatus.value = WebDavConnectionStatus.FAILED
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "未知错误"
                    )
                }
            }
        }
    }

    /**
     * 保存配置
     */
    fun saveConfig() {
        val config = getCurrentConfig()
        if (!config.isValid()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "请填写完整的配置信息"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                successMessage = null
            )

            // 先测试连接
            when (val result = webDavService.testConnection(config)) {
                is WebDavResult.Success -> {
                    // 连接成功，保存配置到数据库
                    val webDavConfig = com.google.jetstream.presentation.screens.profile.WebDavConfig(
                        id = UUID.randomUUID().toString(),
                        displayName = _displayName.value,
                        serverUrl = _serverUrl.value,
                        username = _username.value,
                        isConnected = true
                    )
                    webDavRepository.saveWebDavConfig(webDavConfig, _password.value)
                    webDavService.setConfig(config.copy(isEnabled = true))
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "配置保存成功！"
                    )
                }
                is WebDavResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "保存失败: ${result.message}"
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "保存失败: 未知错误"
                    )
                }
            }
        }
    }

    /**
     * 清除配置
     */
    fun clearConfig() {
        _serverUrl.value = ""
        _username.value = ""
        _password.value = ""
        _displayName.value = ""
        _connectionStatus.value = WebDavConnectionStatus.IDLE
        webDavService.clearConfig()
        _uiState.value = _uiState.value.copy(
            successMessage = "配置已清除"
        )
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

    /**
     * 获取当前配置
     */
    private fun getCurrentConfig(): WebDavConfig {
        return WebDavConfig(
            serverUrl = _serverUrl.value.trim(),
            username = _username.value.trim(),
            password = _password.value,
            displayName = _displayName.value.trim().ifEmpty { "WebDAV服务器" }
        )
    }


}

/**
 * WebDAV配置页面UI状态
 */
data class WebDavConfigUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
