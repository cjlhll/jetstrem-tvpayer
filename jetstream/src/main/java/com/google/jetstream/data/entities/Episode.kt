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

package com.google.jetstream.data.entities

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * 剧集信息数据模型
 */
@Serializable
data class Episode(
    val id: String,
    val episodeNumber: Int,
    val name: String,
    val overview: String,
    val stillPath: String?, // 剧集封面图片路径
    val airDate: String?,
    val voteAverage: Float?,
    val runtime: Int?, // 运行时长（分钟）
    val tvId: String, // 所属电视剧ID
    val seasonNumber: Int, // 所属季号
    val watchProgress: Float? = null, // 观看进度，0.0 到 1.0
    val currentPositionMs: Long? = null, // 当前播放位置（毫秒）
    val durationMs: Long? = null, // 总时长（毫秒）

    // 以下为本地文件信息，不参与序列化
    @Transient
    val videoUri: String? = null, // 视频文件路径
    @Transient
    val fileName: String? = null, // 视频文件名
    @Transient
    val fileSizeBytes: Long? = null // 视频文件大小
) {
    /**
     * 获取完整的剧集封面图片URL
     */
    fun getStillImageUrl(): String? {
        return stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    }
    
    /**
     * 获取格式化的播出日期
     */
    fun getFormattedAirDate(): String {
        return airDate ?: "未知"
    }
    
    /**
     * 获取格式化的时长
     */
    fun getFormattedRuntime(): String {
        return runtime?.let { "${it}分钟" } ?: "未知时长"
    }
    
    /**
     * 获取格式化的评分
     */
    fun getFormattedRating(): String {
        return voteAverage?.let { String.format("⭐ %.1f", it) } ?: "⭐ --"
    }
}