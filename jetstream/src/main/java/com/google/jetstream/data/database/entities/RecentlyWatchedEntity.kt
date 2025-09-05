package com.google.jetstream.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recently_watched")
data class RecentlyWatchedEntity(
    @PrimaryKey val movieId: String, // 电影ID作为主键，确保每部电影只有一条最近观看记录
    val movieTitle: String,
    val backdropUri: String, // 使用详情页的背景图URL作为封面
    val posterUri: String, // 备用海报URL
    val description: String,
    val releaseDate: String?,
    val rating: Float?,
    val type: String, // "movie" or "tv"
    val lastWatchedAt: Long = System.currentTimeMillis(), // 最后观看时间
    val watchProgress: Float? = null, // 观看进度，0.0 到 1.0
    val currentPositionMs: Long? = null, // 当前播放位置（毫秒）
    val durationMs: Long? = null, // 总时长（毫秒）
    // 电视剧相关字段
    val episodeId: String? = null, // 当前观看的剧集ID
    val episodeNumber: Int? = null, // 当前观看的剧集号
    val seasonNumber: Int? = null, // 当前观看的季号
    val episodeTitle: String? = null // 当前观看的剧集标题
)