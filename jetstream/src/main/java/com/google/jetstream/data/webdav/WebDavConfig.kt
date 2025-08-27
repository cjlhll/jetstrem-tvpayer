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

package com.google.jetstream.data.webdav

import kotlinx.serialization.Serializable

/**
 * WebDAV配置数据类
 */
@Serializable
data class WebDavConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val isEnabled: Boolean = false
) {
    /**
     * 验证配置是否有效
     */
    fun isValid(): Boolean {
        return serverUrl.isNotBlank() && 
               username.isNotBlank() && 
               password.isNotBlank() &&
               isValidUrl(serverUrl)
    }
    
    /**
     * 验证URL格式是否正确
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val normalizedUrl = url.lowercase()
            normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取格式化的服务器URL（确保以/结尾）
     */
    fun getFormattedServerUrl(): String {
        return if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
    }
}

/**
 * WebDAV连接状态
 */
enum class WebDavConnectionStatus {
    IDLE,           // 空闲状态
    CONNECTING,     // 连接中
    CONNECTED,      // 已连接
    FAILED,         // 连接失败
    TESTING         // 测试连接中
}

/**
 * WebDAV操作结果
 */
sealed class WebDavResult<out T> {
    data class Success<T>(val data: T) : WebDavResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : WebDavResult<Nothing>()
    object Loading : WebDavResult<Nothing>()
}
