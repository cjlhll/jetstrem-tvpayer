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
    val seasonNumber: Int // 所属季号
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