package com.google.jetstream.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.google.jetstream.data.database.entities.RecentlyWatchedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyWatchedDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: RecentlyWatchedEntity)
    
    @Query("SELECT * FROM recently_watched ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun getRecentlyWatched(limit: Int = 20): Flow<List<RecentlyWatchedEntity>>
    
    @Query("SELECT * FROM recently_watched WHERE movieId = :movieId LIMIT 1")
    suspend fun getByMovieId(movieId: String): RecentlyWatchedEntity?
    
    @Query("DELETE FROM recently_watched WHERE movieId = :movieId")
    suspend fun deleteByMovieId(movieId: String)
    
    @Query("DELETE FROM recently_watched")
    suspend fun clearAll()
    
    @Query("SELECT COUNT(*) FROM recently_watched")
    suspend fun getCount(): Int
    
    // 限制最近观看记录数量，删除最旧的记录
    @Query("DELETE FROM recently_watched WHERE movieId IN (SELECT movieId FROM recently_watched ORDER BY lastWatchedAt ASC LIMIT :deleteCount)")
    suspend fun deleteOldest(deleteCount: Int)
    
    // 清理不存在于刮削数据中的最近观看记录
    @Query("DELETE FROM recently_watched WHERE movieId NOT IN (SELECT id FROM scraped_items)")
    suspend fun deleteOrphanedRecords()
}