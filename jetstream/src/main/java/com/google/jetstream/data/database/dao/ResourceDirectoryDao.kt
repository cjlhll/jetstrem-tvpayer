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
import com.google.jetstream.data.database.entities.ResourceDirectoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResourceDirectoryDao {
    
    @Query("SELECT * FROM resource_directories ORDER BY createdAt DESC")
    fun getAllDirectories(): Flow<List<ResourceDirectoryEntity>>
    
    @Query("SELECT * FROM resource_directories WHERE id = :id")
    suspend fun getDirectoryById(id: String): ResourceDirectoryEntity?
    
    @Query("SELECT * FROM resource_directories WHERE webDavConfigId = :configId")
    suspend fun getDirectoriesByConfigId(configId: String): List<ResourceDirectoryEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectory(directory: ResourceDirectoryEntity)
    
    @Update
    suspend fun updateDirectory(directory: ResourceDirectoryEntity)
    
    @Delete
    suspend fun deleteDirectory(directory: ResourceDirectoryEntity)
    
    @Query("DELETE FROM resource_directories WHERE id = :id")
    suspend fun deleteDirectoryById(id: String)
    
    @Query("DELETE FROM resource_directories WHERE webDavConfigId = :configId")
    suspend fun deleteDirectoriesByConfigId(configId: String)
}
