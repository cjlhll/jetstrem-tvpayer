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
import com.google.jetstream.data.database.entities.ResourceDirectoryEntity
import com.google.jetstream.data.database.entities.WebDavConfigEntity
import com.google.jetstream.data.database.entities.ScrapedItemEntity

@Database(
    entities = [
        WebDavConfigEntity::class,
        ResourceDirectoryEntity::class,
        ScrapedItemEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class JetStreamDatabase : RoomDatabase() {
    
    abstract fun webDavConfigDao(): WebDavConfigDao
    abstract fun resourceDirectoryDao(): ResourceDirectoryDao
    abstract fun scrapedItemDao(): ScrapedItemDao
    
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
        
        fun getDatabase(context: Context): JetStreamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JetStreamDatabase::class.java,
                    "jetstream_database"
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
