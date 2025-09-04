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

package com.google.jetstream.di

import android.content.Context
import com.google.jetstream.data.database.JetStreamDatabase
import com.google.jetstream.data.database.dao.ResourceDirectoryDao
import com.google.jetstream.data.database.dao.RecentlyWatchedDao
import com.google.jetstream.data.database.dao.WebDavConfigDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideJetStreamDatabase(@ApplicationContext context: Context): JetStreamDatabase {
        return JetStreamDatabase.getDatabase(context)
    }

    @Provides
    fun provideWebDavConfigDao(database: JetStreamDatabase): WebDavConfigDao {
        return database.webDavConfigDao()
    }

    @Provides
    fun provideResourceDirectoryDao(database: JetStreamDatabase): ResourceDirectoryDao {
        return database.resourceDirectoryDao()
    }

    @Provides
    fun provideScrapedItemDao(database: JetStreamDatabase): com.google.jetstream.data.database.dao.ScrapedItemDao {
        return database.scrapedItemDao()
    }

    @Provides
    fun provideRecentlyWatchedDao(database: JetStreamDatabase): RecentlyWatchedDao {
        return database.recentlyWatchedDao()
    }
}
