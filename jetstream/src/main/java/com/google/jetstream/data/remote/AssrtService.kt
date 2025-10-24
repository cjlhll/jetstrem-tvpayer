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
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

/**
 * ASSRT 射手网字幕 API 服务
 * 
 * API 文档: https://assrt.net/api/doc
 */
object AssrtService {
    private const val TAG = "AssrtService"
    private const val BASE_URL = "https://api.assrt.net"
    private const val API_TOKEN = "0k4uATEWYFeuaEleVJvzTFXlBBCTvP1A"
    
    // 支持的字幕格式
    private val SUPPORTED_FORMATS = listOf("srt", "vtt", "ttml", "xml", "ssa", "ass")
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * 搜索字幕
     * 
     * @param keyword 搜索关键词（影片名称）
     * @param count 返回结果数量，默认15
     * @return 字幕列表
     */
    suspend fun searchSubtitles(
        keyword: String,
        count: Int = 15
    ): List<AssrtSubtitle> = withContext(Dispatchers.IO) {
        try {
            if (keyword.length < 3) {
                Log.w(TAG, "搜索关键词至少需要3个字符")
                return@withContext emptyList()
            }
            
            val response = get("/v1/sub/search", mapOf(
                "token" to API_TOKEN,
                "q" to keyword,
                "cnt" to count.toString()
            ))
            
            if (response == null) {
                Log.e(TAG, "搜索字幕失败: 无响应")
                return@withContext emptyList()
            }
            
            val result = json.decodeFromString<AssrtResponse<AssrtSearchResult>>(response)
            
            if (result.status != 0) {
                Log.e(TAG, "搜索字幕失败: status=${result.status}, error=${result.error}")
                return@withContext emptyList()
            }
            
            val subs = result.sub?.subs ?: emptyList()
            Log.d(TAG, "搜索到 ${subs.size} 个字幕")
            
            return@withContext subs
            
        } catch (e: Exception) {
            Log.e(TAG, "搜索字幕异常", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * 获取字幕详情（包含下载链接）
     * 
     * @param id 字幕ID
     * @return 字幕详情
     */
    suspend fun getSubtitleDetail(id: Int): AssrtSubtitleDetail? = withContext(Dispatchers.IO) {
        try {
            val response = get("/v1/sub/detail", mapOf(
                "token" to API_TOKEN,
                "id" to id.toString()
            ))
            
            if (response == null) {
                Log.e(TAG, "获取字幕详情失败: 无响应")
                return@withContext null
            }
            
            val result = json.decodeFromString<AssrtResponse<AssrtDetailResult>>(response)
            
            if (result.status != 0) {
                Log.e(TAG, "获取字幕详情失败: status=${result.status}, error=${result.error}")
                return@withContext null
            }
            
            return@withContext result.sub?.subs?.firstOrNull()
            
        } catch (e: Exception) {
            Log.e(TAG, "获取字幕详情异常", e)
            return@withContext null
        }
    }
    
    /**
     * 下载字幕文件
     * 
     * @param url 下载地址
     * @return 字幕文件内容
     */
    suspend fun downloadSubtitleFile(url: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "下载字幕文件: $url")
            
            val conn = URL(url).openConnection() as HttpURLConnection
            
            // 配置 SSL（如果是 HTTPS）
            if (conn is HttpsURLConnection) {
                try {
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, createTrustAllTrustManager(), java.security.SecureRandom())
                    conn.sslSocketFactory = sslContext.socketFactory
                    conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                } catch (e: Exception) {
                    Log.w(TAG, "配置SSL失败，使用默认配置: ${e.message}")
                }
            }
            
            conn.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "JetStream-Android/1.0")
                connectTimeout = 30000
                readTimeout = 30000
            }
            
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                // 读取字节数据
                val bytes = conn.inputStream.use { it.readBytes() }
                Log.d(TAG, "下载成功，大小: ${bytes.size} 字节")
                
                // 尝试多种编码（中文字幕常见编码）
                val content = detectAndDecodeContent(bytes)
                Log.d(TAG, "使用编码解析，内容前100字符: ${content.take(100)}")
                
                return@withContext content
            } else {
                Log.e(TAG, "下载失败: HTTP $responseCode")
                return@withContext null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "下载字幕文件异常", e)
            return@withContext null
        }
    }
    
    /**
     * 检测并解码字节内容
     * 尝试常见的中文编码：UTF-8, GBK, GB2312, Big5
     */
    private fun detectAndDecodeContent(bytes: ByteArray): String {
        // 尝试的编码顺序
        val encodings = listOf(
            "UTF-8",
            "GBK",
            "GB2312",
            "Big5",
            "UTF-16"
        )
        
        for (encoding in encodings) {
            try {
                val content = String(bytes, charset(encoding))
                // 检查是否包含乱码特征
                if (!content.contains("��") && content.isNotEmpty()) {
                    // 验证内容是否合理（ASS/SRT/VTT 格式特征）
                    if (content.contains("[Script Info]") ||  // ASS
                        content.contains("WEBVTT") ||         // VTT
                        content.matches(Regex(".*\\d+\\s+-->\\s+\\d+.*", RegexOption.DOT_MATCHES_ALL)) || // SRT/VTT
                        content.contains("Dialogue:")) {      // ASS
                        Log.d(TAG, "成功使用编码: $encoding")
                        return content
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "编码 $encoding 解析失败: ${e.message}")
            }
        }
        
        // 如果所有编码都失败，默认使用 UTF-8
        Log.w(TAG, "所有编码尝试失败，使用 UTF-8 作为后备")
        return String(bytes, Charsets.UTF_8)
    }
    
    /**
     * 自动搜索并下载最佳匹配的字幕
     * 
     * @param movieName 电影名称
     * @return 字幕文件内容和格式，如果找不到返回 null
     */
    suspend fun findAndDownloadBestSubtitle(movieName: String): Pair<String, String>? {
        try {
            Log.d(TAG, "开始搜索字幕: $movieName")
            
            // 1. 搜索字幕
            val subtitles = searchSubtitles(movieName)
            if (subtitles.isEmpty()) {
                Log.w(TAG, "未找到字幕")
                return null
            }
            
            // 2. 按优先级筛选和排序
            val prioritizedSubtitles = subtitles
                .filter { sub ->
                    // 必须有subtype且格式受支持
                    sub.subtype.isNotBlank() && 
                    SUPPORTED_FORMATS.any { format -> 
                        sub.subtype.contains(format, ignoreCase = true) 
                    }
                }
                .mapNotNull { sub ->
                    val lang = SubtitleLanguage.detect(sub.lang?.desc, sub.lang?.langlist)
                    if (lang != null) {
                        Triple(sub, lang, sub.uploadTime)
                    } else {
                        null
                    }
                }
                .sortedWith(compareBy(
                    { it.second.priority },  // 先按语言优先级排序
                    { -it.third.compareTo("") } // 再按时间倒序（最新的优先）
                ))
            
            if (prioritizedSubtitles.isEmpty()) {
                Log.w(TAG, "没有符合条件的字幕")
                return null
            }
            
            Log.d(TAG, "找到 ${prioritizedSubtitles.size} 个符合条件的字幕")
            
            // 3. 尝试下载第一个匹配的字幕
            for ((subtitle, language, _) in prioritizedSubtitles) {
                Log.d(TAG, "尝试下载字幕 [ID:${subtitle.id}, 语言:${language.name}, 类型:${subtitle.subtype}]")
                
                // 获取详情
                val detail = getSubtitleDetail(subtitle.id)
                if (detail == null) {
                    Log.w(TAG, "获取字幕详情失败，尝试下一个")
                    continue
                }
                
                // 如果有 filelist，优先从 filelist 中找符合条件的文件
                val (downloadUrl, fileName) = if (!detail.filelist.isNullOrEmpty()) {
                    findBestFileInList(detail.filelist, language)
                } else {
                    // 否则使用主 url 和 filename
                    Pair(detail.url, detail.filename)
                }
                
                if (downloadUrl.isBlank()) {
                    Log.w(TAG, "未找到可用的下载链接，尝试下一个")
                    continue
                }
                
                // 下载文件
                val content = downloadSubtitleFile(downloadUrl)
                if (content != null) {
                    // 检测实际格式（使用内容、文件名和API提示三重判断）
                    val format = detectSubtitleFormat(content, fileName, detail.subtype)
                    Log.d(TAG, "成功下载字幕 [文件:$fileName, 语言:${language.name}, 格式:$format]")
                    return Pair(content, format)
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
     * 从文件列表中找到最佳匹配的字幕文件
     * 返回：Pair(下载URL, 文件名)
     */
    private fun findBestFileInList(
        fileList: List<AssrtFile>,
        targetLanguage: SubtitleLanguage
    ): Pair<String, String> {
        // 查找符合格式要求的文件
        val matchedFile = fileList.firstOrNull { file ->
            SUPPORTED_FORMATS.any { format ->
                file.f.endsWith(".$format", ignoreCase = true)
            }
        }
        
        val file = matchedFile ?: fileList.firstOrNull()
        return Pair(file?.url ?: "", file?.f ?: "")
    }
    
    /**
     * 检测字幕格式
     * 优先根据内容特征判断，文件名和API提示作为辅助
     */
    private fun detectSubtitleFormat(content: String, fileName: String, subtypeHint: String): String {
        val trimmed = content.trim()
        
        // 1. 根据内容特征判断（最可靠）
        when {
            trimmed.startsWith("WEBVTT") -> return "vtt"
            trimmed.contains("[Script Info]") || trimmed.contains("[V4+ Styles]") -> return "ass"
            trimmed.contains("Dialogue:") && trimmed.contains("Format:") -> return "ass"  // ASS/SSA 的另一个特征
            trimmed.contains("<tt ") || trimmed.contains("<tt>") || trimmed.contains("<?xml") -> {
                return if (trimmed.contains("ttml", ignoreCase = true)) "ttml" else "xml"
            }
        }
        
        // 2. 根据文件名判断
        for (format in SUPPORTED_FORMATS) {
            if (fileName.endsWith(".$format", ignoreCase = true)) {
                return format
            }
        }
        
        // 3. 根据API类型提示判断
        for (format in SUPPORTED_FORMATS) {
            if (subtypeHint.contains(format, ignoreCase = true)) {
                return format
            }
        }
        
        // 4. 最后尝试SRT特征（序号格式）
        if (trimmed.matches(Regex("^\\d+\\s*\r?\n", RegexOption.MULTILINE))) {
            return "srt"
        }
        
        // 5. 默认返回srt
        return "srt"
    }
    
    /**
     * 执行 HTTP GET 请求
     */
    private fun get(path: String, params: Map<String, String>): String? {
        try {
            val queryString = params.entries.joinToString("&") { (key, value) ->
                "$key=${URLEncoder.encode(value, "UTF-8")}"
            }
            val url = URL("$BASE_URL$path?$queryString")
            
            Log.d(TAG, "请求: $url")
            
            val conn = url.openConnection() as HttpURLConnection
            
            // 配置 SSL（如果是 HTTPS）
            if (conn is HttpsURLConnection) {
                try {
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, createTrustAllTrustManager(), java.security.SecureRandom())
                    conn.sslSocketFactory = sslContext.socketFactory
                    conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                } catch (e: Exception) {
                    Log.w(TAG, "配置SSL失败，使用默认配置: ${e.message}")
                }
            }
            
            conn.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "JetStream-Android/1.0")
                connectTimeout = 15000
                readTimeout = 15000
            }
            
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                Log.e(TAG, "请求失败: HTTP $responseCode")
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "错误响应: $errorBody")
                return null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "请求异常", e)
            return null
        }
    }
    
    /**
     * 创建信任所有证书的 TrustManager（仅用于开发测试）
     */
    private fun createTrustAllTrustManager(): Array<javax.net.ssl.TrustManager> {
        return arrayOf(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
    }
}

