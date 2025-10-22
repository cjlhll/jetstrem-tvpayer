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
    val createdAt: Long = System.currentTimeMillis(),
    
    // 详情页面所需的额外字段
    val backdropUri: String? = null,
    val pgRating: String? = null, // 评级信息，如 "⭐ 7.5" 或 "PG-13"
    val categories: String? = null, // JSON字符串存储类型列表
    val duration: String? = null, // 时长，如 "2h 30m"
    val director: String? = null,
    val screenplay: String? = null,
    val music: String? = null,
    val castAndCrew: String? = null, // JSON字符串存储演员列表
    val availableSeasons: String? = null, // JSON字符串存储季信息
    val webDavConfigId: String? = null // WebDAV配置ID，用于在播放时设置正确的配置
)

