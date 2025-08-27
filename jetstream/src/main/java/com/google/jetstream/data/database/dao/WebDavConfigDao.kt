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

package com.google.jetstream.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.google.jetstream.data.database.entities.WebDavConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebDavConfigDao {
    
    @Query("SELECT * FROM webdav_configs ORDER BY createdAt DESC")
    fun getAllConfigs(): Flow<List<WebDavConfigEntity>>
    
    @Query("SELECT * FROM webdav_configs WHERE id = :id")
    suspend fun getConfigById(id: String): WebDavConfigEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: WebDavConfigEntity)
    
    @Update
    suspend fun updateConfig(config: WebDavConfigEntity)
    
    @Delete
    suspend fun deleteConfig(config: WebDavConfigEntity)
    
    @Query("DELETE FROM webdav_configs WHERE id = :id")
    suspend fun deleteConfigById(id: String)
}
