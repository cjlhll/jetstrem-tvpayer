package com.google.jetstream.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.google.jetstream.data.database.entities.ScrapedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScrapedItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ScrapedItemEntity>)

    @Query("SELECT * FROM scraped_items WHERE type = :type ORDER BY createdAt DESC")
    fun getAllByType(type: String): Flow<List<ScrapedItemEntity>>

    @Query("SELECT * FROM scraped_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ScrapedItemEntity?

    @Query("DELETE FROM scraped_items")
    suspend fun clearAll()
    
    // 删除不在指定webDavConfigId列表中的项
    @Query("DELETE FROM scraped_items WHERE webDavConfigId NOT IN (:configIds)")
    suspend fun deleteByConfigIdsNotIn(configIds: List<String>)
    
    // 删除webDavConfigId为null的项
    @Query("DELETE FROM scraped_items WHERE webDavConfigId IS NULL")
    suspend fun deleteWithNullConfigId()
    
    // 删除指定webDavConfigId的所有项
    @Query("DELETE FROM scraped_items WHERE webDavConfigId = :configId")
    suspend fun deleteByConfigId(configId: String)
    
    // 删除指定webDavConfigId列表的所有项
    @Query("DELETE FROM scraped_items WHERE webDavConfigId IN (:configIds)")
    suspend fun deleteByConfigIds(configIds: List<String>)
}

