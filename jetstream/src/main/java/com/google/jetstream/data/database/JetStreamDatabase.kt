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

package com.google.jetstream.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.google.jetstream.data.database.dao.ResourceDirectoryDao
import com.google.jetstream.data.database.dao.WebDavConfigDao
import com.google.jetstream.data.database.dao.ScrapedItemDao
import com.google.jetstream.data.database.dao.RecentlyWatchedDao
import com.google.jetstream.data.database.dao.EpisodesCacheDao
import com.google.jetstream.data.database.entities.ResourceDirectoryEntity
import com.google.jetstream.data.database.entities.WebDavConfigEntity
import com.google.jetstream.data.database.entities.ScrapedItemEntity
import com.google.jetstream.data.database.entities.RecentlyWatchedEntity
import com.google.jetstream.data.database.entities.EpisodesCacheEntity

@Database(
    entities = [
        WebDavConfigEntity::class,
        ResourceDirectoryEntity::class,
        ScrapedItemEntity::class,
        RecentlyWatchedEntity::class,
        EpisodesCacheEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class JetStreamDatabase : RoomDatabase() {
    
    abstract fun webDavConfigDao(): WebDavConfigDao
    abstract fun resourceDirectoryDao(): ResourceDirectoryDao
    abstract fun scrapedItemDao(): ScrapedItemDao
    abstract fun recentlyWatchedDao(): RecentlyWatchedDao
    abstract fun episodesCacheDao(): EpisodesCacheDao
    
    companion object {
        @Volatile
        private var INSTANCE: JetStreamDatabase? = null
        
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加新的详情字段到 scraped_items 表
                database.execSQL("ALTER TABLE scraped_items ADD COLUMN backdropUri TEXT")
                database.execSQL("ALTER TABLE scraped_items ADD COLUMN pgRating TEXT")
                database.execSQL("ALTER TABLE scraped_items ADD COLUMN categories TEXT")
                database.execSQL("ALTER TABLE scraped_items ADD COLUMN duration TEXT")
                database.execSQL("ALTER TABLE scraped_items ADD COLUMN director TEXT")
                database.execSQL("ALTER TABLE scraped_items ADD COLUMN screenplay TEXT")
                database.execSQL("ALTER TABLE scraped_items ADD COLUMN music TEXT")
                database.execSQL("ALTER TABLE scraped_items ADD COLUMN castAndCrew TEXT")
            }
        }
        
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 移除不再使用的字段（通过重建表的方式）
                // 创建新表
                database.execSQL("""
                    CREATE TABLE scraped_items_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        posterUri TEXT NOT NULL,
                        releaseDate TEXT,
                        rating REAL,
                        type TEXT NOT NULL,
                        sourcePath TEXT,
                        backdropUri TEXT,
                        pgRating TEXT,
                        categories TEXT,
                        duration TEXT,
                        director TEXT,
                        screenplay TEXT,
                        music TEXT,
                        castAndCrew TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """)
                
                // 复制数据（显式指定列，包含 createdAt）
                database.execSQL("""
                    INSERT INTO scraped_items_new (
                        id, title, description, posterUri, releaseDate, rating, type, sourcePath,
                        backdropUri, pgRating, categories, duration, director, screenplay, music, castAndCrew, createdAt
                    )
                    SELECT 
                        id, title, description, posterUri, releaseDate, rating, type, sourcePath,
                        backdropUri, pgRating, categories, duration, director, screenplay, music, castAndCrew, createdAt
                    FROM scraped_items
                """)
                
                // 删除旧表
                database.execSQL("DROP TABLE scraped_items")
                
                // 重命名新表
                database.execSQL("ALTER TABLE scraped_items_new RENAME TO scraped_items")
            }
        }
        
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加 availableSeasons 字段到 scraped_items 表
                database.execSQL("ALTER TABLE scraped_items ADD COLUMN availableSeasons TEXT")
            }
        }
        
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 删除旧的recently_watched表（如果存在）并创建新的表结构
                database.execSQL("DROP TABLE IF EXISTS recently_watched")
                
                // 创建新的recently_watched表
                database.execSQL("""
                    CREATE TABLE recently_watched (
                        movieId TEXT NOT NULL PRIMARY KEY,
                        movieTitle TEXT NOT NULL,
                        backdropUri TEXT NOT NULL,
                        posterUri TEXT NOT NULL,
                        description TEXT NOT NULL,
                        releaseDate TEXT,
                        rating REAL,
                        type TEXT NOT NULL,
                        lastWatchedAt INTEGER NOT NULL,
                        watchProgress REAL
                    )
                """)
            }
        }
        
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加播放进度相关的新字段到 recently_watched 表
                database.execSQL("ALTER TABLE recently_watched ADD COLUMN currentPositionMs INTEGER")
                database.execSQL("ALTER TABLE recently_watched ADD COLUMN durationMs INTEGER")
            }
        }
        
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加电视剧相关字段到 recently_watched 表
                database.execSQL("ALTER TABLE recently_watched ADD COLUMN episodeId TEXT")
                database.execSQL("ALTER TABLE recently_watched ADD COLUMN episodeNumber INTEGER")
                database.execSQL("ALTER TABLE recently_watched ADD COLUMN seasonNumber INTEGER")
                database.execSQL("ALTER TABLE recently_watched ADD COLUMN episodeTitle TEXT")
            }
        }
        
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建剧集缓存表
                database.execSQL("""
                    CREATE TABLE episodes_cache (
                        id TEXT NOT NULL PRIMARY KEY,
                        tvId TEXT NOT NULL,
                        seasonNumber INTEGER NOT NULL,
                        episodesJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)
                
                // 创建索引以提高查询性能
                database.execSQL("CREATE INDEX index_episodes_cache_tvId ON episodes_cache(tvId)")
            }
        }
        
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 检查索引是否已存在，如果不存在则创建
                try {
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_episodes_cache_tvId ON episodes_cache(tvId)")
                } catch (e: Exception) {
                    // 如果创建索引失败，忽略错误（可能索引已存在）
                }
            }
        }
        
        fun getDatabase(context: Context): JetStreamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JetStreamDatabase::class.java,
                    "jetstream_database"
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
