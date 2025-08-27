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

package com.google.jetstream.data.repositories

import com.google.jetstream.data.database.dao.ResourceDirectoryDao
import com.google.jetstream.data.database.dao.WebDavConfigDao
import com.google.jetstream.data.database.entities.ResourceDirectoryEntity
import com.google.jetstream.data.database.entities.WebDavConfigEntity
import com.google.jetstream.presentation.screens.profile.ResourceDirectory
import com.google.jetstream.presentation.screens.profile.WebDavConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavRepository @Inject constructor(
    private val webDavConfigDao: WebDavConfigDao,
    private val resourceDirectoryDao: ResourceDirectoryDao
) {
    
    // WebDAV配置相关方法
    fun getAllWebDavConfigs(): Flow<List<WebDavConfig>> {
        return webDavConfigDao.getAllConfigs().map { entities ->
            entities.map { it.toWebDavConfig() }
        }
    }
    
    suspend fun saveWebDavConfig(config: WebDavConfig, password: String) {
        webDavConfigDao.insertConfig(config.toEntity(password))
    }
    
    suspend fun deleteWebDavConfig(configId: String) {
        webDavConfigDao.deleteConfigById(configId)
        // 同时删除相关的资源目录
        resourceDirectoryDao.deleteDirectoriesByConfigId(configId)
    }
    
    suspend fun getWebDavConfigById(id: String): WebDavConfig? {
        return webDavConfigDao.getConfigById(id)?.toWebDavConfig()
    }

    suspend fun getWebDavConfigEntityById(id: String): WebDavConfigEntity? {
        return webDavConfigDao.getConfigById(id)
    }
    
    // 资源目录相关方法
    fun getAllResourceDirectories(): Flow<List<ResourceDirectory>> {
        return resourceDirectoryDao.getAllDirectories().map { entities ->
            entities.map { it.toResourceDirectory() }
        }
    }
    
    suspend fun saveResourceDirectory(directory: ResourceDirectory, webDavConfigId: String, serverName: String) {
        val entity = ResourceDirectoryEntity(
            id = directory.id.ifEmpty { UUID.randomUUID().toString() },
            name = directory.name,
            path = directory.path,
            webDavConfigId = webDavConfigId,
            serverName = serverName
        )
        resourceDirectoryDao.insertDirectory(entity)
    }
    
    suspend fun deleteResourceDirectory(directoryId: String) {
        resourceDirectoryDao.deleteDirectoryById(directoryId)
    }
    
    // 扩展函数：实体转换为数据类
    private fun WebDavConfigEntity.toWebDavConfig(): WebDavConfig {
        return WebDavConfig(
            id = id,
            displayName = displayName,
            serverUrl = serverUrl,
            username = username,
            isConnected = isConnected
        )
    }
    
    private fun WebDavConfig.toEntity(password: String): WebDavConfigEntity {
        return WebDavConfigEntity(
            id = id.ifEmpty { UUID.randomUUID().toString() },
            displayName = displayName,
            serverUrl = serverUrl,
            username = username,
            password = password,
            isConnected = isConnected
        )
    }
    
    private fun ResourceDirectoryEntity.toResourceDirectory(): ResourceDirectory {
        return ResourceDirectory(
            id = id,
            name = name,
            path = path,
            serverName = serverName
        )
    }
}
