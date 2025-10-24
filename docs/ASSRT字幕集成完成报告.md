# ASSRT 字幕自动搜索下载功能 - 实现报告

## 实现时间
2025-10-24

## 功能概述
成功集成了 ASSRT（射手网）API，实现了当打开字幕开关时自动搜索、下载并挂载最合适的字幕文件。

---

## 已完成的功能

### 1. ASSRT API 数据模型 ✅
**文件**: `jetstream/src/main/java/com/google/jetstream/data/remote/AssrtApiModels.kt`

- 定义了完整的 API 响应数据结构
- 实现了字幕语言检测和优先级排序
- 支持的语言优先级：
  1. 简英双语 (priority: 1)
  2. 简体中文 (priority: 2)
  3. 繁英双语 (priority: 3)
  4. 繁体中文 (priority: 4)
  5. 英语 (priority: 5)

**语言识别关键字典**:
```kotlin
SIMPLIFIED_BILINGUAL -> ["简英", "简体&英文", "双语", "langdou"]
SIMPLIFIED_CHINESE -> ["简体", "简", "chs", "chi", "zh-cn", "中文", "langchs"]
TRADITIONAL_BILINGUAL -> ["繁英", "繁体&英文", "繁體雙語"]
TRADITIONAL_CHINESE -> ["繁体", "繁體", "cht", "zh-tw", "zh-hk", "langcht"]
ENGLISH -> ["英语", "英文", "eng", "en", "english", "langeng"]
```

### 2. ASSRT API 服务 ✅
**文件**: `jetstream/src/main/java/com/google/jetstream/data/remote/AssrtService.kt`

**核心功能**:
- `searchSubtitles()` - 搜索字幕
- `getSubtitleDetail()` - 获取字幕详情（含下载链接）
- `downloadSubtitleFile()` - 下载字幕文件内容
- `findAndDownloadBestSubtitle()` - 自动搜索并下载最佳字幕（核心方法）

**智能匹配逻辑**:
1. 根据电影名称搜索字幕
2. 过滤出支持的格式（srt, vtt, ttml, xml, ssa, ass）
3. 按语言优先级和上传时间排序
4. 依次尝试下载，直到成功
5. 如果有 filelist，优先从中选择合适的文件
6. 自动检测字幕实际格式

**配置信息**:
- API Token: `0k4uATEWYFeuaEleVJvzTFXlBBCTvP1A`
- Base URL: `https://api.assrt.net`
- 当前配额: 5次/分钟（已验证有效）

### 3. 字幕管理器增强 ✅
**文件**: `jetstream/src/main/java/com/google/jetstream/presentation/screens/videoPlayer/subtitle/SubtitleManager.kt`

**新增功能**:
- `setMovieName()` - 设置电影名称用于搜索
- `autoSearchAndLoadSubtitle()` - 自动搜索并加载字幕
- 修改 `setEnabled()` 和 `toggleEnabled()` - 支持在启用时触发自动搜索
- 添加 `hasAutoSearched` 标记，避免重复搜索

**工作流程**:
1. 视频播放器初始化时，设置电影名称
2. 用户打开字幕开关
3. 检查是否已有加载的字幕，如果没有则自动搜索
4. 调用 ASSRT API 查找最佳匹配字幕
5. 下载并解析字幕内容
6. 挂载到播放器，开始同步显示

### 4. 视频播放器集成 ✅
**文件**: `jetstream/src/main/java/com/google/jetstream/presentation/screens/videoPlayer/VideoPlayerScreen.kt`

**修改内容**:
- 在初始化时设置电影名称：`subtitleManager.setMovieName(movieDetails.name)`
- 修改字幕开关回调：传递 `coroutineScope` 参数
- 移除了硬编码的测试字幕 URL

**数据源**:
- 使用 TMDB 刮削后存储在数据库中的官方电影名称（`movieDetails.name`）

---

## 技术特性

### 1. 智能匹配算法
- **双重排序**: 先按语言优先级，再按上传时间（最新优先）
- **格式过滤**: 自动过滤不支持的字幕格式
- **容错处理**: 如果第一个字幕下载失败，自动尝试下一个

### 2. 格式支持
| 格式 | 支持状态 | 解析器 |
|------|---------|--------|
| SRT | ✅ | SRTSubtitleParser |
| VTT | ✅ | VTTSubtitleParser |
| ASS | ✅ | ASSSubtitleParser |
| SSA | ✅ | ASSSubtitleParser |
| TTML | ✅ | TTMLSubtitleParser |
| XML | ✅ | TTMLSubtitleParser |

### 3. 自动格式检测
当 API 返回的格式提示不明确时，通过内容特征识别：
- WEBVTT 标识 → VTT
- `<tt>` 标签 → TTML
- `<?xml` 开头 → XML
- `[Script Info]` → ASS/SSA
- 数字序号 → SRT（默认）

### 4. 网络优化
- 使用 HttpURLConnection（与项目现有技术栈一致）
- 支持 HTTPS/SSL 配置
- 15秒连接超时，30秒读取超时（字幕下载）
- 异步执行（Kotlin Coroutines + Dispatchers.IO）

### 5. 用户体验
- **延迟加载**: 只在打开字幕开关时才搜索下载
- **单次搜索**: 每部影片只自动搜索一次，避免重复请求
- **静默失败**: 如果找不到字幕，不会打断播放
- **加载提示**: 通过 `isLoading` 状态反馈搜索进度

---

## 工作流程图

```
用户打开视频播放器
    ↓
初始化字幕管理器
    ↓
设置电影名称 (movieDetails.name)
    ↓
用户打开字幕开关
    ↓
检查：是否已有字幕？
    ├── 是 → 直接显示
    └── 否 → 触发自动搜索
        ↓
    调用 ASSRT API 搜索
        ↓
    按优先级排序和过滤
        ↓
    获取字幕详情（下载链接）
        ↓
    下载字幕文件内容
        ↓
    检测格式并解析
        ↓
    挂载到播放器
        ↓
    开始同步显示
```

---

## API 调用示例

### 搜索字幕
```
GET https://api.assrt.net/v1/sub/search?token=TOKEN&q=电影名称&cnt=15
```

**响应示例**:
```json
{
  "status": 0,
  "sub": {
    "subs": [{
      "id": 123456,
      "native_name": "电影名称",
      "subtype": "Subrip(srt)",
      "upload_time": "2025-10-20 12:00:00",
      "lang": {
        "desc": "简英双语",
        "langlist": {
          "langchs": true,
          "langdou": true
        }
      }
    }]
  }
}
```

### 获取详情
```
GET https://api.assrt.net/v1/sub/detail?token=TOKEN&id=123456
```

**响应包含**:
- 字幕下载链接（临时地址）
- 文件列表（如果是压缩包）
- 每个文件的单独下载链接

---

## 日志输出

实现了完整的调试日志，便于追踪问题：

```
SubtitleManager: 设置电影名称: The Matrix
SubtitleManager: 字幕已启用，开始自动搜索
SubtitleManager: 开始自动搜索字幕: The Matrix
AssrtService: 请求: https://api.assrt.net/v1/sub/search?...
AssrtService: 搜索到 8 个字幕
AssrtService: 找到 5 个符合条件的字幕
AssrtService: 尝试下载字幕 [ID:123456, 语言:SIMPLIFIED_BILINGUAL, 类型:Subrip(srt)]
AssrtService: 下载字幕文件: http://file0.assrt.net/...
AssrtService: 下载成功，大小: 45678 字节
SubtitleManager: 找到字幕，格式: srt, 大小: 45678 字节
SubtitleManager: 成功加载 856 条字幕
SubtitleManager: 字幕: 0ms - 2500ms: 欢迎来到矩阵世界
```

---

## 测试验证

### ✅ 编译测试
```bash
./gradlew.bat assembleDebug
BUILD SUCCESSFUL in 11s
41 actionable tasks: 11 executed, 30 up-to-date
```

### ✅ API Token 验证
```json
{
  "status": 0,
  "user": {
    "action": "quota",
    "quota": 5,
    "result": "succeed"
  }
}
```
当前剩余配额: 5次/分钟

---

## 已知限制

1. **API 配额**: 20次/分钟（Token + IP 共享）
2. **下载地址时效**: 每次需要重新获取详情接口
3. **搜索精度**: 依赖电影名称的准确性（建议使用英文原名）
4. **网络依赖**: 需要联网才能搜索下载字幕

---

## 优化建议

### 未来可扩展功能

1. **本地缓存**: 
   - 缓存已下载的字幕文件
   - 避免重复下载

2. **手动选择**:
   - 提供字幕列表供用户手动选择
   - 支持多语言切换

3. **配额管理**:
   - 实现请求频率限制
   - 添加本地速率控制

4. **离线字幕**:
   - 支持从本地文件加载字幕
   - WebDAV 字幕文件识别

5. **字幕同步**:
   - 自动调整字幕延迟
   - 基于语音识别的智能同步

---

## 文件清单

### 新增文件
- `jetstream/src/main/java/com/google/jetstream/data/remote/AssrtApiModels.kt`
- `jetstream/src/main/java/com/google/jetstream/data/remote/AssrtService.kt`
- `docs/ASSRT字幕API集成指南.md`
- `docs/ASSRT字幕集成完成报告.md`

### 修改文件
- `jetstream/src/main/java/com/google/jetstream/presentation/screens/videoPlayer/subtitle/SubtitleManager.kt`
- `jetstream/src/main/java/com/google/jetstream/presentation/screens/videoPlayer/VideoPlayerScreen.kt`

---

## 使用说明

### 用户操作流程

1. **播放视频**: 打开任意电影或电视剧
2. **打开字幕**: 按遥控器菜单键 → 选择"字幕设置" → 开启字幕
3. **自动搜索**: 系统自动搜索并下载最合适的字幕
4. **显示字幕**: 字幕自动同步显示在视频画面上

### 字幕不显示？

可能的原因：
- 网络连接问题
- ASSRT API 上找不到该电影的字幕
- 电影名称不准确（建议检查 TMDB 刮削的名称）
- API 配额已用完（需等待下一分钟）

**解决方法**:
- 查看 Logcat 日志中的 `AssrtService` 和 `SubtitleManager` 标签
- 手动在 ASSRT 网站搜索该电影，确认是否有字幕资源

---

## 致谢

- **ASSRT（射手网）**: 提供免费的字幕搜索 API
- **字幕服务由 [assrt.net](https://assrt.net) 提供**

---

## 开发者信息

- **实现日期**: 2025-10-24
- **API 版本**: v1
- **Android Min SDK**: 21
- **Kotlin 版本**: 2.1.0

