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

import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebDAV服务类，用于处理WebDAV相关操作
 */
@Singleton
class WebDavService @Inject constructor() {
    
    private var sardine: Sardine? = null
    private var currentConfig: WebDavConfig? = null
    
    /**
     * 初始化WebDAV客户端
     */
    private fun initializeSardine(config: WebDavConfig): Sardine {
        return OkHttpSardine().apply {
            setCredentials(config.username, config.password)
        }
    }
    
    /**
     * 测试WebDAV连接
     */
    suspend fun testConnection(config: WebDavConfig): WebDavResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!config.isValid()) {
                return@withContext WebDavResult.Error("配置信息不完整或无效")
            }
            
            val testSardine = initializeSardine(config)
            val resources = testSardine.list(config.getFormattedServerUrl())
            
            WebDavResult.Success(true)
        } catch (e: Exception) {
            WebDavResult.Error("连接失败: ${e.message}", e)
        }
    }
    
    /**
     * 设置WebDAV配置
     */
    fun setConfig(config: WebDavConfig) {
        currentConfig = config
        sardine = if (config.isValid()) {
            initializeSardine(config)
        } else {
            null
        }
    }
    
    /**
     * 获取当前配置
     */
    fun getCurrentConfig(): WebDavConfig? = currentConfig
    
    /**
     * 列出目录内容
     */
    suspend fun listDirectory(path: String = ""): WebDavResult<List<DavResource>> = withContext(Dispatchers.IO) {
        try {
            val config = currentConfig ?: return@withContext WebDavResult.Error("未配置WebDAV")
            val client = sardine ?: return@withContext WebDavResult.Error("WebDAV客户端未初始化")

            // 规范化避免出现双斜杠
            val base = config.getFormattedServerUrl().removeSuffix("/")
            val normalizedPath = path.removePrefix("/")
            val fullPath = if (normalizedPath.isEmpty()) base else "$base/$normalizedPath"

            val resources = client.list(fullPath)

            WebDavResult.Success(resources)
        } catch (e: Exception) {
            WebDavResult.Error("获取目录列表失败: ${e.message}", e)
        }
    }
    
    /**
     * 检查WebDAV是否已配置且有效
     */
    fun isConfigured(): Boolean {
        return currentConfig?.isValid() == true && sardine != null
    }
    
    /**
     * 清除配置
     */
    fun clearConfig() {
        currentConfig = null
        sardine = null
    }
}
