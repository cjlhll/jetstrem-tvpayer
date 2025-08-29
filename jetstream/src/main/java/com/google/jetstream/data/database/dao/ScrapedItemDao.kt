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
}

