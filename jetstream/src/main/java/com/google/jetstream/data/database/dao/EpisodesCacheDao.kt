package com.google.jetstream.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.google.jetstream.data.database.entities.EpisodesCacheEntity
import kotlinx.coroutines.flow.Flow

/**
 * 剧集列表缓存数据访问对象
 */
@Dao
interface EpisodesCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodesCache(episodesCache: EpisodesCacheEntity)

    @Query("SELECT * FROM episodes_cache WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EpisodesCacheEntity?

    @Query("SELECT * FROM episodes_cache WHERE tvId = :tvId")
    suspend fun getByTvId(tvId: String): List<EpisodesCacheEntity>

    @Query("DELETE FROM episodes_cache WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM episodes_cache WHERE tvId = :tvId")
    suspend fun deleteByTvId(tvId: String)

    @Query("DELETE FROM episodes_cache")
    suspend fun deleteAll()
}