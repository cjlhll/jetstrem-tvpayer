package com.google.jetstream.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 剧集列表缓存实体
 * 用于缓存每个电视剧每季的剧集列表，避免每次都扫描文件系统
 */
@Entity(
    tableName = "episodes_cache",
    indices = [Index(value = ["tvId"])]
)
data class EpisodesCacheEntity(
    @PrimaryKey
    val id: String, // 格式: tvId_seasonNumber
    val tvId: String, // 电视剧ID
    val seasonNumber: Int, // 季号
    val episodesJson: String, // 剧集列表的JSON字符串
    val createdAt: Long = System.currentTimeMillis() // 缓存创建时间
)