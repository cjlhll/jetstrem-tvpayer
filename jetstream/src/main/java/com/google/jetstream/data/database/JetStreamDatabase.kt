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
import android.content.Context
import com.google.jetstream.data.database.dao.ResourceDirectoryDao
import com.google.jetstream.data.database.dao.WebDavConfigDao
import com.google.jetstream.data.database.entities.ResourceDirectoryEntity
import com.google.jetstream.data.database.entities.WebDavConfigEntity

@Database(
    entities = [
        WebDavConfigEntity::class,
        ResourceDirectoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class JetStreamDatabase : RoomDatabase() {
    
    abstract fun webDavConfigDao(): WebDavConfigDao
    abstract fun resourceDirectoryDao(): ResourceDirectoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: JetStreamDatabase? = null
        
        fun getDatabase(context: Context): JetStreamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JetStreamDatabase::class.java,
                    "jetstream_database"
                ).fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
