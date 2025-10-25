/*
 * Copyright 2025 Google LLC
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

package com.google.jetstream.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/**
 * OpenSubtitles API 服务
 * 
 * API 文档: https://www.opensubtitles.com/docs/api/html/index.htm
 */
object OpenSubtitlesService {
    private const val TAG = "OpenSubtitlesService"
    private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
    private const val API_KEY = "76TreFCob7CN0Lx6qeta8thIMYsweDie"
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * 使用 TMDB ID 搜索字幕（最精准）
     */
    suspend fun searchByTmdbId(tmdbId: String): List<OpenSubtitle> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "使用 TMDB ID 搜索: $tmdbId")
            
            val response = get("/subtitles", mapOf(
                "tmdb_id" to tmdbId,
                "languages" to "zh,zh-CN,zh-TW"
            ))
            
            if (response == null) {
                Log.e(TAG, "TMDB ID 搜索失败: 无响应")
                return@withContext emptyList()
            }
            
            val result = json.decodeFromString<OpenSubtitlesResponse>(response)
            Log.d(TAG, "✓ TMDB ID 搜索到 ${result.data.size} 个字幕")
            
            return@withContext result.data
            
        } catch (e: Exception) {
            Log.e(TAG, "TMDB ID 搜索异常", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * 搜索字幕（使用电影名称）
     * 
     * @param query 搜索关键词（电影名称）
     * @return 字幕列表
     */
    suspend fun searchSubtitles(query: String): List<OpenSubtitle> = withContext(Dispatchers.IO) {
        try {
            if (query.length < 2) {
                Log.w(TAG, "搜索关键词至少需要2个字符")
                return@withContext emptyList()
            }
            
            Log.d(TAG, "搜索字幕: $query")
            
            val response = get("/subtitles", mapOf(
                "query" to query,
                "languages" to "zh,zh-CN,zh-TW"  // 简体中文、繁体中文
            ))
            
            if (response == null) {
                Log.e(TAG, "搜索字幕失败: 无响应")
                return@withContext emptyList()
            }
            
            val result = json.decodeFromString<OpenSubtitlesResponse>(response)
            Log.d(TAG, "搜索到 ${result.data.size} 个字幕")
            
            return@withContext result.data
            
        } catch (e: Exception) {
            Log.e(TAG, "搜索字幕异常", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * 下载字幕文件
     * 
     * @param fileId 字幕文件ID
     * @return 字幕内容和格式，失败返回 null
     */
    suspend fun downloadSubtitle(fileId: Int): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "请求下载链接: fileId=$fileId")
            
            // 1. 获取下载链接
            val response = post("/download", mapOf("file_id" to fileId))
            
            if (response == null) {
                Log.e(TAG, "获取下载链接失败")
                return@withContext null
            }
            
            val downloadInfo = json.decodeFromString<OpenSubtitlesDownloadResponse>(response)
            val downloadLink = downloadInfo.link
            
            if (downloadLink.isBlank()) {
                Log.e(TAG, "下载链接为空")
                return@withContext null
            }
            
            Log.d(TAG, "下载字幕: $downloadLink")
            
            // 2. 下载字幕文件
            val conn = URL(downloadLink).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 30000
            }
            
            if (conn.responseCode == 200) {
                // 读取可能是 GZIP 压缩的内容
                val bytes = if (conn.contentEncoding == "gzip") {
                    GZIPInputStream(conn.inputStream).use { it.readBytes() }
                } else {
                    conn.inputStream.use { it.readBytes() }
                }
                
                Log.d(TAG, "下载成功: ${bytes.size} 字节")
                
                // 尝试检测编码
                val content = detectEncoding(bytes)
                
                // 从文件名检测格式
                val fileName = downloadInfo.file_name ?: ""
                val format = when {
                    fileName.endsWith(".srt", ignoreCase = true) -> "srt"
                    fileName.endsWith(".vtt", ignoreCase = true) -> "vtt"
                    fileName.endsWith(".ass", ignoreCase = true) -> "ass"
                    fileName.endsWith(".ssa", ignoreCase = true) -> "ass"
                    else -> detectFormatFromContent(content)
                }
                
                Log.d(TAG, "字幕格式: $format, 文件名: $fileName")
                return@withContext Pair(content, format)
            } else {
                Log.e(TAG, "下载失败: HTTP ${conn.responseCode}")
                return@withContext null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "下载字幕异常", e)
            return@withContext null
        }
    }
    
    /**
     * 自动搜索并下载最佳字幕
     */
    suspend fun findAndDownloadBestSubtitle(
        movieName: String,
        tmdbId: String? = null,
        year: Int? = null
    ): Pair<String, String>? {
        try {
            Log.d(TAG, "开始搜索字幕: name=$movieName, tmdbId=$tmdbId, year=$year")
            
            // 1. 优先使用 TMDB ID 搜索（更精准）
            val subtitles = if (!tmdbId.isNullOrBlank()) {
                Log.d(TAG, "使用 TMDB ID 搜索: $tmdbId")
                searchByTmdbId(tmdbId)
            } else {
                Log.d(TAG, "使用电影名称搜索: $movieName")
                searchSubtitles(movieName)
            }
            if (subtitles.isEmpty()) {
                Log.w(TAG, "未找到字幕")
                return null
            }
            
            // 2. 按优先级排序（简体中文 > 繁体中文 > 其他中文）
            val prioritized = subtitles.sortedWith(compareBy(
                { sub ->
                    when (sub.attributes.language) {
                        "zh-CN", "zh" -> 0  // 简体中文最优先
                        "zh-TW" -> 1         // 繁体中文次之
                        else -> 2             // 其他
                    }
                },
                { -(it.attributes.download_count ?: 0) }  // 下载量高的优先
            ))
            
            Log.d(TAG, "找到 ${prioritized.size} 个字幕，尝试下载前3个")
            
            // 3. 尝试下载前3个
            for ((index, subtitle) in prioritized.take(3).withIndex()) {
                val fileId = subtitle.attributes.files?.firstOrNull()?.file_id
                if (fileId == null) {
                    Log.w(TAG, "字幕 ${index + 1} 没有可用的文件ID")
                    continue
                }
                
                Log.d(TAG, "尝试下载字幕 ${index + 1}: [ID:$fileId, 语言:${subtitle.attributes.language}, 下载:${subtitle.attributes.download_count}]")
                
                val result = downloadSubtitle(fileId)
                if (result != null) {
                    Log.d(TAG, "✓ 成功下载字幕 (格式: ${result.second})")
                    return result
                } else {
                    Log.w(TAG, "下载失败，尝试下一个")
                }
            }
            
            Log.w(TAG, "所有字幕下载均失败")
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "查找下载字幕异常", e)
            return null
        }
    }
    
    /**
     * 执行 HTTP GET 请求
     */
    private suspend fun get(path: String, params: Map<String, String>): String? {
        val maxRetries = 2
        
        repeat(maxRetries) { attemptIndex ->
            try {
                val queryString = params.entries.joinToString("&") { (key, value) ->
                    "$key=${URLEncoder.encode(value, "UTF-8")}"
                }
                val url = URL("$BASE_URL$path?$queryString")
                
                if (attemptIndex == 0) {
                    Log.d(TAG, "GET请求: $url")
                } else {
                    Log.d(TAG, "重试 ($attemptIndex/$maxRetries): $url")
                }
                
                val conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "GET"
                    setRequestProperty("Api-Key", API_KEY)
                    setRequestProperty("User-Agent", "JetStream-Android v1.0")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 20000
                    readTimeout = 20000
                }
                
                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val response = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    Log.d(TAG, "✓ 请求成功")
                    return response
                } else if (responseCode in 500..599 && attemptIndex < maxRetries - 1) {
                    Log.w(TAG, "服务器错误: HTTP $responseCode，等待重试...")
                    kotlinx.coroutines.delay(1000L)
                } else {
                    Log.e(TAG, "请求失败: HTTP $responseCode")
                    return null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "请求异常 (尝试 ${attemptIndex + 1}/$maxRetries)", e)
                if (attemptIndex < maxRetries - 1) {
                    kotlinx.coroutines.delay(1000L)
                }
            }
        }
        
        Log.e(TAG, "所有重试均失败")
        return null
    }
    
    /**
     * 执行 HTTP POST 请求
     */
    private suspend fun post(path: String, body: Map<String, Any>): String? {
        try {
            val url = URL("$BASE_URL$path")
            Log.d(TAG, "POST请求: $url")
            
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                setRequestProperty("Api-Key", API_KEY)
                setRequestProperty("User-Agent", "JetStream-Android v1.0")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 20000
                readTimeout = 20000
                doOutput = true
            }
            
            // 手动构建简单的 JSON 请求体
            val jsonBody = buildJsonBody(body)
            conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(jsonBody) }
            
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val response = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                Log.d(TAG, "✓ POST请求成功")
                return response
            } else {
                Log.e(TAG, "POST请求失败: HTTP $responseCode")
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "错误: $errorBody")
                return null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "POST请求异常", e)
            return null
        }
    }
    
    /**
     * 构建简单的 JSON 请求体
     */
    private fun buildJsonBody(body: Map<String, Any>): String {
        val entries = body.entries.joinToString(",") { (key, value) ->
            when (value) {
                is String -> "\"$key\":\"$value\""
                is Number -> "\"$key\":$value"
                is Boolean -> "\"$key\":$value"
                else -> "\"$key\":\"$value\""
            }
        }
        return "{$entries}"
    }
    
    /**
     * 检测字节内容的编码
     */
    private fun detectEncoding(bytes: ByteArray): String {
        val encodings = listOf("UTF-8", "GBK", "GB2312", "Big5", "UTF-16")
        
        for (encoding in encodings) {
            try {
                val content = String(bytes, charset(encoding))
                if (!content.contains("��") && content.isNotEmpty()) {
                    Log.d(TAG, "检测到编码: $encoding")
                    return content
                }
            } catch (e: Exception) {
                // 继续尝试下一个编码
            }
        }
        
        Log.w(TAG, "编码检测失败，使用 UTF-8")
        return String(bytes, Charsets.UTF_8)
    }
    
    /**
     * 从内容检测字幕格式
     */
    private fun detectFormatFromContent(content: String): String {
        val trimmed = content.trim()
        return when {
            trimmed.startsWith("WEBVTT") -> "vtt"
            trimmed.contains("[Script Info]") || trimmed.contains("Dialogue:") -> "ass"
            trimmed.contains("<?xml") || trimmed.contains("<tt") -> "ttml"
            else -> "srt"  // 默认 SRT
        }
    }
}

/**
 * OpenSubtitles API 响应数据类
 */
@Serializable
data class OpenSubtitlesResponse(
    val total_pages: Int = 0,
    val total_count: Int = 0,
    val page: Int = 1,
    val data: List<OpenSubtitle> = emptyList()
)

@Serializable
data class OpenSubtitle(
    val id: String,
    val type: String,
    val attributes: OpenSubtitleAttributes
)

@Serializable
data class OpenSubtitleAttributes(
    val subtitle_id: String? = null,
    val language: String? = null,
    val download_count: Int? = null,
    val new_download_count: Int? = null,
    val hearing_impaired: Boolean? = null,
    val hd: Boolean? = null,
    val fps: Double? = null,
    val votes: Int? = null,
    val ratings: Double? = null,
    val from_trusted: Boolean? = null,
    val foreign_parts_only: Boolean? = null,
    val upload_date: String? = null,
    val ai_translated: Boolean? = null,
    val machine_translated: Boolean? = null,
    val release: String? = null,
    val comments: String? = null,
    val legacy_subtitle_id: Int? = null,
    val uploader: OpenSubtitleUploader? = null,
    val feature_details: OpenSubtitleFeature? = null,
    val url: String? = null,
    val related_links: List<OpenSubtitleRelatedLink>? = null,
    val files: List<OpenSubtitleFile>? = null,
    val moviehash_match: Boolean? = null
)

@Serializable
data class OpenSubtitleUploader(
    val uploader_id: Int? = null,
    val name: String? = null,
    val rank: String? = null
)

@Serializable
data class OpenSubtitleFeature(
    val feature_id: Int? = null,
    val feature_type: String? = null,
    val year: Int? = null,
    val title: String? = null,
    val movie_name: String? = null,
    val imdb_id: Int? = null,
    val tmdb_id: Int? = null
)

@Serializable
data class OpenSubtitleRelatedLink(
    val label: String = "",
    val url: String = "",
    val img_url: String = ""
)

@Serializable
data class OpenSubtitleFile(
    val file_id: Int? = null,
    val cd_number: Int? = null,
    val file_name: String? = null
)

@Serializable
data class OpenSubtitlesDownloadResponse(
    val link: String,
    val file_name: String = "",
    val requests: Int = 0,
    val remaining: Int = 0,
    val message: String = "",
    val reset_time: String = "",
    val reset_time_utc: String = ""
)

