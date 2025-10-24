# ASSRT 字幕 API 集成指南

## 概述

ASSRT API 是射手网（伪）提供的免费字幕搜索和下载服务接口。

- **API 域名**: `https://api.assrt.net` 或 `https://api.makedie.me`（备用）
- **通信协议**: HTTP/HTTPS
- **数据格式**: JSON
- **请求方式**: GET 或 POST
- **API 配额**: 20次/分钟（每个 Token 和 IP 共享配额）

## 一、认证方式

### 获取 Token

1. 在 [assrt.net](https://assrt.net) 注册账号
2. 登录后在用户面板查看 API Token（32位随机字符串）

### Token 使用方式

**方式一：URL 参数**
```
GET https://api.assrt.net/v1/sub/search?token=YOUR_TOKEN&q=keyword
```

**方式二：请求头**
```
GET https://api.assrt.net/v1/sub/search?q=keyword
Authorization: Bearer YOUR_TOKEN
```

## 二、搜索字幕

### 接口地址
```
GET /v1/sub/search
```

### 请求参数

| 参数 | 必填 | 说明 |
|------|------|------|
| token | 是 | API 密钥 |
| q | 是 | 搜索关键词（至少3个字符） |
| pos | 否 | 分页起始位置，默认0 |
| cnt | 否 | 返回结果数量，默认15，最多15 |
| is_file | 否 | 值为1时，将搜索词作为视频文件名处理 |
| no_muxer | 否 | 值为1时，忽略压制组信息（自动启用is_file） |
| filelist | 否 | 值为1时，返回压缩包文件列表（但无下载链接） |

### 请求示例

```bash
curl "https://api.assrt.net/v1/sub/search?token=YOUR_TOKEN&q=big+bang+theory&cnt=10"
```

### 响应示例

```json
{
    "status": 0,
    "sub": {
        "action": "search",
        "keyword": "big bang theory",
        "result": "succeed",
        "subs": [
            {
                "id": 594897,
                "native_name": "生活大爆炸 第1季",
                "videoname": "The.Big.Bang.Theory.S01E01.720p.HDTV.x264",
                "subtype": "Subrip(srt)",
                "upload_time": "2015-08-21 22:11:00",
                "vote_score": 85,
                "release_site": "人人影视",
                "lang": {
                    "desc": "简体中文",
                    "langlist": {
                        "langchs": true
                    }
                }
            }
        ]
    }
}
```

### 搜索优化建议

1. **普通搜索**：直接使用影片名称
   ```
   q=Gone with the Wind
   ```

2. **视频文件名搜索**：当有完整文件名时使用
   ```
   q=Gone.with.the.Wind.1939.1080p.BluRay.x264-WiKi&is_file=1
   实际搜索: Gone with the Wind 1939 -WiKi
   ```

3. **忽略压制组搜索**：结果太多时使用
   ```
   q=Gone.with.the.Wind.1939.1080p.BluRay.x264-WiKi&no_muxer=1
   实际搜索: Gone with the Wind 1939
   ```

## 三、获取字幕详情

### 接口地址
```
GET /v1/sub/detail
```

### 请求参数

| 参数 | 必填 | 说明 |
|------|------|------|
| token | 是 | API 密钥 |
| id | 是 | 字幕ID（从搜索结果中获取） |

### 请求示例

```bash
curl "https://api.assrt.net/v1/sub/detail?token=YOUR_TOKEN&id=602333"
```

### 响应示例

```json
{
    "status": 0,
    "sub": {
        "result": "succeed",
        "action": "detail",
        "subs": [
            {
                "id": 602333,
                "filename": "movie.subtitle.rar",
                "native_name": "电影名称",
                "url": "http://file0.assrt.net/download/602333/movie.subtitle.rar?_=xxx&-=xxx&api=1",
                "size": 20180,
                "subtype": "Subrip(srt)",
                "upload_time": "2015-07-03 11:28:53",
                "down_count": 14,
                "view_count": 289,
                "vote_score": 0,
                "filelist": [
                    {
                        "f": "movie.subtitle.srt",
                        "s": "52KB",
                        "url": "http://file0.assrt.net/onthefly/602333/-/1/movie.subtitle.srt?_=xxx&-=xxx&api=1"
                    }
                ],
                "producer": {
                    "uploader": "用户名",
                    "producer": "制作者",
                    "verifier": "校订者",
                    "source": "来源"
                },
                "lang": {
                    "desc": "简体中文",
                    "langlist": {
                        "langchs": true
                    }
                }
            }
        ]
    }
}
```

## 四、下载字幕

### 下载流程

1. **搜索字幕**：使用 `/v1/sub/search` 获取字幕列表和 ID
2. **获取详情**：使用 `/v1/sub/detail?id=xxx` 获取下载地址
3. **下载文件**：使用响应中的 `url` 字段直接下载

### 重要提示

⚠️ **不要保存下载地址**：每次生成的下载地址都是临时的，具有时效性。每次下载前都应重新获取。

### 下载示例代码（Kotlin）

```kotlin
// 1. 搜索字幕
suspend fun searchSubtitles(keyword: String): List<Subtitle> {
    val response = httpClient.get("https://api.assrt.net/v1/sub/search") {
        parameter("token", API_TOKEN)
        parameter("q", keyword)
        parameter("cnt", 15)
    }
    return parseSearchResponse(response)
}

// 2. 获取下载地址
suspend fun getSubtitleDetail(id: Int): SubtitleDetail {
    val response = httpClient.get("https://api.assrt.net/v1/sub/detail") {
        parameter("token", API_TOKEN)
        parameter("id", id)
    }
    return parseDetailResponse(response)
}

// 3. 下载字幕文件
suspend fun downloadSubtitle(url: String): ByteArray {
    return httpClient.get(url).readBytes()
}
```

## 五、字幕数据结构

### 搜索结果字段（subs）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Int | 字幕ID（用于获取详情） |
| native_name | String | 影片原始名称 |
| videoname | String | 匹配的视频文件名 |
| subtype | String | 字幕格式（如Subrip(srt)、VobSub） |
| upload_time | String | 上传时间 |
| vote_score | Int | 用户评分 |
| release_site | String | 字幕组名称 |
| lang.desc | String | 语言描述 |
| lang.langlist | Object | 语言列表（langchs/简体、langcht/繁体、langeng/英文等） |

### 详情结果额外字段

| 字段 | 类型 | 说明 |
|------|------|------|
| filename | String | 字幕文件名 |
| url | String | 字幕下载地址（临时地址） |
| size | Int | 文件大小（字节） |
| filelist | Array | 压缩包内的文件列表 |
| filelist[].f | String | 文件名 |
| filelist[].s | String | 文件大小（易读格式） |
| filelist[].url | String | 单个文件下载地址 |
| down_count | Int | 下载次数 |
| view_count | Int | 浏览次数 |

## 六、错误处理

### 状态码判断

响应中的 `status` 字段表示请求状态：
- **0**：成功
- **非0**：错误（参考错误代码表）

### 常见错误

| 错误码 | 错误信息 | 说明 | HTTP状态 |
|--------|----------|------|----------|
| 1 | no such user | 用户不存在 | 200 |
| 101 | length of keyword must be longer than 3 | 关键词太短 | 200 |
| 20000 | missing essential arguments | 缺少必要参数 | 400 |
| 20001 | invalid token | Token无效 | 400 |
| 20400 | API endpoint not found | 接口不存在 | 404 |
| 20900 | subtitle not found | 字幕不存在 | 404 |
| 30900 | exceeding request limits | 超过配额限制 | 5xx |

### 错误处理示例

```kotlin
data class ApiResponse<T>(
    val status: Int,
    val sub: T?,
    val error: String?
)

suspend fun searchSubtitlesWithErrorHandling(keyword: String): Result<List<Subtitle>> {
    try {
        val response: ApiResponse<SearchResult> = httpClient.get("...") { ... }
        
        return when (response.status) {
            0 -> Result.success(response.sub?.subs ?: emptyList())
            101 -> Result.failure(Exception("搜索关键词至少需要3个字符"))
            20001 -> Result.failure(Exception("API Token无效"))
            30900 -> Result.failure(Exception("API请求超过限制，请稍后再试"))
            else -> Result.failure(Exception("未知错误: ${response.status}"))
        }
    } catch (e: Exception) {
        return Result.failure(e)
    }
}
```

## 七、最佳实践

### 1. 配额管理

```kotlin
class SubtitleApiManager {
    private var lastRequestTime = 0L
    private val minInterval = 3000L // 3秒间隔，确保不超过20次/分钟
    
    suspend fun <T> rateLimitedRequest(block: suspend () -> T): T {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRequestTime
        if (elapsed < minInterval) {
            delay(minInterval - elapsed)
        }
        lastRequestTime = System.currentTimeMillis()
        return block()
    }
}
```

### 2. 缓存策略

```kotlin
// 缓存搜索结果，避免重复请求
private val searchCache = LruCache<String, List<Subtitle>>(maxSize = 50)

suspend fun searchWithCache(keyword: String): List<Subtitle> {
    searchCache[keyword]?.let { return it }
    
    val results = searchSubtitles(keyword)
    searchCache.put(keyword, results)
    return results
}
```

### 3. 重试机制

```kotlin
suspend fun <T> retryRequest(
    times: Int = 3,
    delay: Long = 1000,
    block: suspend () -> T
): T {
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            delay(delay * (it + 1))
        }
    }
    return block() // 最后一次尝试，失败则抛出异常
}
```

### 4. 文件解压

字幕可能是压缩包格式（.rar/.zip），需要解压：

```kotlin
fun extractSubtitle(compressedFile: File): File? {
    // 使用 Apache Commons Compress 或其他解压库
    val subtitleExtensions = listOf(".srt", ".ass", ".ssa")
    
    // 解压并找到字幕文件
    ZipFile(compressedFile).use { zip ->
        val entry = zip.entries().asSequence()
            .find { entry -> 
                subtitleExtensions.any { entry.name.endsWith(it, ignoreCase = true) }
            }
        
        entry?.let { 
            val outputFile = File(cacheDir, it.name)
            zip.getInputStream(it).use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return outputFile
        }
    }
    return null
}
```

## 八、集成检查清单

- [ ] 注册 ASSRT 账号并获取 API Token
- [ ] 在应用中添加来源说明："字幕服务由 [assrt.net](https://assrt.net) 提供"
- [ ] 实现搜索功能（/v1/sub/search）
- [ ] 实现详情获取（/v1/sub/detail）
- [ ] 实现文件下载
- [ ] 添加错误处理机制
- [ ] 实现配额管理（避免超过20次/分钟）
- [ ] 添加缓存策略
- [ ] 处理压缩包解压
- [ ] 测试不同格式字幕（.srt、.ass、.ssa）

## 九、注意事项

1. **免费服务**：ASSRT API 对个人用户完全免费，但需标注来源
2. **配额限制**：默认20次/分钟，需要更高配额需发邮件申请
3. **地址时效**：下载地址是临时的，不要保存，每次下载前重新获取
4. **编码问题**：字幕文件可能是各种编码（UTF-8、GBK等），需要处理编码转换
5. **格式支持**：主要是 SRT 和 ASS 格式，需要相应的解析器

## 十、参考资源

- [ASSRT API 官方文档](https://assrt.net/api/doc)
- [API 测试控制台](https://assrt.net/api/console)
- [API 统计信息](https://api.assrt.net/stat.html)

---

*文档生成时间: 2025-10-24*  
*API 版本: v1*

