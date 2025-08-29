package com.google.jetstream.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scraped_items")
data class ScrapedItemEntity(
    @PrimaryKey val id: String, // TMDB id
    val title: String,
    val description: String,
    val posterUri: String,
    val releaseDate: String?,
    val rating: Float?,
    val type: String, // "movie" or "tv"
    val sourcePath: String?,
    val createdAt: Long = System.currentTimeMillis()
)

