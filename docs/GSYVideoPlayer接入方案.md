# GSYVideoPlayer 接入方案分析文档

## 一、GSYVideoPlayer 项目概述

### 1.1 核心特性
- **多内核支持**：IJKPlayer、Media3(ExoPlayer2)、MediaPlayer、AliPlayer
- **功能丰富**：
  - 边播边缓存
  - 多种协议支持（h263/4/5、HTTPS、concat、rtsp、hls、rtmp、mpeg等）
  - 滤镜、水印、字幕、弹幕
  - 手势控制（拖动、声音、亮度调节）
  - 全屏/小窗口播放
  - 画面比例调整
  - 支持 16k Page Size
- **架构灵活**：播放内核、管理器、渲染层都可以自定义替换

### 1.2 项目架构（五层设计）
```
┌─────────────────────────────────────┐
│  Video 播放器控件层 (UI + Controller) │
│  GSYVideoPlayer → StandardGSYVideoPlayer │
├─────────────────────────────────────┤
│  Manager 内核管理层                    │
│  GSYVideoManager (GSYVideoViewBridge) │
├─────────────────────────────────────┤
│  Player 播放内核层                     │
│  IjkPlayerManager/Exo2PlayerManager   │
├─────────────────────────────────────┤
│  Cache 缓存层                         │
│  ProxyCacheManager/ExoPlayerCacheManager│
├─────────────────────────────────────┤
│  Render 渲染控件层                     │
│  TextureView/SurfaceView/GLSurfaceView│
└─────────────────────────────────────┘
```

### 1.3 最新版本信息
- **当前版本**：v11.1.0 (2025-08-04)
- **更新内容**：
  - update media3 1.8.0
  - 支持 16k page size
  - FFmpeg 4.1.6 (arm64)
  - openssl 1.1.1w (arm64)
  - minSdk 21，compileSdk 35

---

## 二、当前项目现状分析

### 2.1 技术栈
- **开发语言**：Kotlin
- **UI框架**：Jetpack Compose
- **当前播放器**：Media3 ExoPlayer
- **架构**：MVVM + Hilt
- **目标平台**：Android TV

### 2.2 现有播放器实现
当前 `VideoPlayerScreen.kt` 中使用：
- `androidx.media3.exoplayer.ExoPlayer`
- `androidx.media3.ui.compose.PlayerSurface`
- Compose 自定义 UI 控制层
- 支持 WebDAV、进度保存、字幕等功能

### 2.3 现有功能特性
✅ 已实现：
- ExoPlayer 播放
- 播放进度保存/恢复
- 手势控制（快进/快退/暂停）
- 长按快速定位
- 生命周期管理
- 最近观看记录
- 电视剧分集播放

---

## 三、GSYVideoPlayer 依赖集成方案

### 3.1 推荐依赖方式（MavenCentral）

#### 方案一：使用 GSY 内置的 Media3 模式（推荐）
```groovy
// 在 project 的 build.gradle.kts 或 settings.gradle.kts 中添加
repositories {
    mavenCentral()
    maven { url = uri("https://maven.aliyun.com/repository/public") }
}

// 在 module 的 build.gradle.kts 中添加
dependencies {
    // GSY 基础库
    implementation("io.github.carguo:gsyvideoplayer-java:11.1.0")
    
    // 使用 Media3(ExoPlayer) 模式 - 与现有项目兼容
    implementation("io.github.carguo:gsyvideoplayer-exo2:11.1.0")
    
    // 或者引入完整版（包含 IJK so 文件，体积较大）
    // implementation("io.github.carguo:gsyvideoplayer:11.1.0")
}
```

#### 方案二：按需引入 IJKPlayer 内核
```groovy
dependencies {
    implementation("io.github.carguo:gsyvideoplayer-java:11.1.0")
    implementation("io.github.carguo:gsyvideoplayer-exo2:11.1.0")
    
    // 根据需要引入 IJK so 库（更多编码格式支持）
    implementation("io.github.carguo:gsyvideoplayer-arm64:11.1.0")
    implementation("io.github.carguo:gsyvideoplayer-armv7a:11.1.0")
    // 或使用 ex_so（支持 mpeg、rtsp、crypto 等协议，支持 16k Page Size）
    // implementation("io.github.carguo:gsyvideoplayer-ex_so:11.1.0")
}
```

### 3.2 依赖大小参考
- **gsyvideoplayer-java**：基础库，约 1-2MB
- **gsyvideoplayer-exo2**：Media3 模块，约 500KB-1MB
- **gsyvideoplayer-arm64**：IJK so (单架构)，约 10-15MB
- **gsyvideoplayer-ex_so**：扩展 so (全架构)，约 50MB+

**建议**：使用 ndk abiFilters 过滤不需要的架构

---

## 四、接入方案设计

### 4.1 方案对比

#### 方案 A：保持 Compose + 替换底层为 GSY 的 Manager（推荐）
**优点**：
- 保留现有 Compose UI 和交互逻辑
- 只替换播放内核管理层
- 改动最小，风险最低
- 可以利用 GSY 的内核切换、缓存、配置能力

**缺点**：
- 需要适配 GSY 的 API 到现有代码
- 无法直接使用 GSY 的 UI 控件（如进度条预览等）

**实现思路**：
```kotlin
// 使用 GSY 的播放管理器替代直接使用 ExoPlayer
PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)
// 或切换到 IJK 内核
// PlayerFactory.setPlayManager(IjkPlayerManager::class.java)

// 通过 GSYVideoManager 控制播放
val player = GSYVideoManager.instance().player
// 配置缓存
CacheFactory.setCacheManager(ExoPlayerCacheManager::class.java)
```

#### 方案 B：使用 GSY 的 View 控件（传统 View）
**优点**：
- 直接使用 GSY 的完整功能和 UI
- 开箱即用，无需重新实现控制逻辑
- 可以快速实现滤镜、弹幕等高级功能

**缺点**：
- 需要混用 View 和 Compose（通过 AndroidView）
- 与现有 Compose UI 风格可能不一致
- TV 遥控器焦点处理需要额外适配

**实现思路**：
```kotlin
@Composable
fun GSYVideoPlayerView() {
    AndroidView(
        factory = { context ->
            StandardGSYVideoPlayer(context).apply {
                // 配置播放器
                setUp(url, cacheWithPlay, title)
                // 设置控制器
                // TV 适配：需要处理焦点
            }
        }
    )
}
```

#### 方案 C：完全重构为 GSY + Compose 自定义 UI
**优点**：
- 充分利用 GSY 的核心能力
- 完全定制化的 Compose UI
- 最佳的 TV 体验

**缺点**：
- 工作量大，需要重新设计和实现
- 需要深入了解 GSY 的架构

---

## 五、推荐接入步骤（方案 A）

### 5.1 第一阶段：添加依赖和基础配置

1. **添加依赖**（见 3.1）

2. **配置混淆规则**
```proguard
# GSYVideoPlayer
-keep class com.shuyu.gsyvideoplayer.video.** { *; }
-dontwarn com.shuyu.gsyvideoplayer.video.**
-keep class com.shuyu.gsyvideoplayer.video.base.** { *; }
-dontwarn com.shuyu.gsyvideoplayer.video.base.**
-keep class com.shuyu.gsyvideoplayer.utils.** { *; }
-dontwarn com.shuyu.gsyvideoplayer.utils.**
-keep class com.shuyu.gsyvideoplayer.player.** {*;}
-dontwarn com.shuyu.gsyvideoplayer.player.**

# IJKPlayer (如果使用)
-keep class tv.danmaku.ijk.** { *; }
-dontwarn tv.danmaku.ijk.**

# Media3 (如果使用)
-keep class androidx.media3.** {*;}
-keep interface androidx.media3.**
```

3. **配置 AndroidManifest.xml**
```xml
<activity
    android:name=".VideoPlayerActivity"
    android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|uiMode"
    android:screenOrientation="landscape"
    android:hardwareAccelerated="true" />
```

### 5.2 第二阶段：创建播放器管理层

创建 `GSYPlayerManager.kt`：
```kotlin
class GSYPlayerManager(context: Context) {
    
    init {
        // 设置播放内核（可动态切换）
        PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)
        // 或使用 IJK
        // PlayerFactory.setPlayManager(IjkPlayerManager::class.java)
        
        // 设置缓存模式
        CacheFactory.setCacheManager(ExoPlayerCacheManager::class.java)
        
        // 设置渲染模式
        GSYVideoType.setRenderType(GSYVideoType.SUFRACE) // TV 推荐 SurfaceView
        
        // 设置显示比例
        GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_DEFAULT)
    }
    
    fun setupPlayer(url: String, headers: Map<String, String>? = null) {
        // 配置播放选项
        val optionList = mutableListOf<VideoOptionModel>()
        
        // 配置 HTTP Headers
        headers?.forEach { (key, value) ->
            optionList.add(
                VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_FORMAT, key, value)
            )
        }
        
        // 其他配置项...
        GSYVideoManager.instance().setOptionModelList(optionList)
    }
    
    fun release() {
        GSYVideoManager.releaseAllVideos()
    }
}
```

### 5.3 第三阶段：适配现有 VideoPlayerScreen

修改 `VideoPlayerScreen.kt`：
```kotlin
// 1. 保留现有 Compose UI
// 2. 使用 GSYVideoManager 替代直接使用 ExoPlayer
// 3. 通过 GSY API 控制播放
```

### 5.4 第四阶段：测试与优化

测试项：
- [ ] 基础播放功能
- [ ] 进度保存/恢复
- [ ] 全屏切换
- [ ] 手势控制
- [ ] 生命周期管理
- [ ] WebDAV 播放
- [ ] TV 遥控器焦点控制
- [ ] 多格式视频兼容性

---

## 六、关键技术点

### 6.1 内核切换
```kotlin
// 全局设置，应用启动时配置
PlayerFactory.setPlayManager(Exo2PlayerManager::class.java) // Media3
PlayerFactory.setPlayManager(IjkPlayerManager::class.java)  // IJKPlayer
PlayerFactory.setPlayManager(SystemPlayerManager::class.java) // 系统播放器
```

### 6.2 播放配置（针对 TV 和网络视频）
```kotlin
val optionList = mutableListOf<VideoOptionModel>()

// 1. 网络优化
optionList.add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 20000))
optionList.add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", -1))

// 2. 解码优化
optionList.add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)) // 硬解

// 3. RTSP/HLS 优化
optionList.add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp"))
optionList.add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", 
    "crypto,file,http,https,tcp,tls,udp"))

// 4. 精准 seek
optionList.add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1))

GSYVideoManager.instance().setOptionModelList(optionList)
```

### 6.3 缓存配置
```kotlin
// ExoPlayer 缓存（支持 m3u8）
CacheFactory.setCacheManager(ExoPlayerCacheManager::class.java)

// 代理缓存（不支持 m3u8，但支持更多格式）
CacheFactory.setCacheManager(ProxyCacheManager::class.java)
```

### 6.4 TV 适配要点
1. **焦点管理**：使用 `requestFocus()` 和 `focusable`
2. **遥控器按键**：处理 D-Pad 和 Back 键
3. **渲染模式**：优先使用 SurfaceView（HDR 支持更好）
4. **大屏显示**：默认 16:9 或填充模式

---

## 七、潜在问题与解决方案

### 7.1 与 Media3 版本冲突
**问题**：GSYVideoPlayer 使用的 Media3 版本可能与项目不一致
**解决**：
```groovy
// 排除冲突的依赖
implementation("io.github.carguo:gsyvideoplayer-exo2:11.1.0") {
    exclude(group = "androidx.media3")
}
// 使用项目统一的 Media3 版本
implementation("androidx.media3:media3-exoplayer:1.8.0")
```

### 7.2 Compose 与 View 混用
**问题**：GSY 的 View 控件在 Compose 中使用需要通过 AndroidView
**解决**：仅使用 GSY 的 Manager 层，保留 Compose UI

### 7.3 TV 焦点问题
**问题**：GSY 默认为手机设计，TV 焦点处理需要额外适配
**解决**：自定义控制层，禁用触摸事件，增强遥控器支持

### 7.4 SO 库体积过大
**问题**：IJK 的 so 文件会增加 APK 体积
**解决**：
```groovy
android {
    defaultConfig {
        ndk {
            abiFilters("armeabi-v7a", "arm64-v8a") // 只打包 TV 常用架构
        }
    }
}
```

---

## 八、总结与建议

### 8.1 是否需要接入 GSYVideoPlayer？

**建议接入的场景**：
- ✅ 需要支持多种视频格式（特别是 mpeg、rtsp 等）
- ✅ 需要 IJKPlayer 的高级配置能力
- ✅ 需要方便地切换播放内核
- ✅ 需要边播边缓存功能

**不建议接入的场景**：
- ❌ 只播放常见格式（mp4、m3u8），Media3 已足够
- ❌ 项目对 APK 体积有严格限制
- ❌ 纯 Compose 项目，不希望引入 View 层依赖

### 8.2 推荐方案

基于你的项目现状（Compose + Media3），**推荐使用方案 A（轻量级接入）**：

1. **第一步**：仅引入 `gsyvideoplayer-exo2`，保持使用 Media3 内核
2. **第二步**：通过 GSY 的 API 优化播放配置（如缓存、Headers、播放选项）
3. **第三步**（可选）：如果需要支持更多格式，再引入 IJK 内核

**优点**：
- 改动最小，风险最低
- 保留现有的 Compose UI 和用户体验
- 获得 GSY 的配置和管理能力
- 未来可以无缝切换到 IJK 内核

### 8.3 下一步行动

1. ✅ 添加 GSYVideoPlayer 依赖（gsyvideoplayer-exo2）
2. ⬜ 创建 `GSYPlayerManager` 封装播放逻辑
3. ⬜ 逐步替换 `VideoPlayerScreen` 中的 ExoPlayer 直接调用
4. ⬜ 测试播放功能和兼容性
5. ⬜ 根据需要添加 IJK 内核支持

---

## 九、参考资源

- **GitHub 仓库**：https://github.com/CarGuo/GSYVideoPlayer
- **使用文档**：https://github.com/CarGuo/GSYVideoPlayer/blob/master/doc/USE.md
- **项目架构**：https://github.com/CarGuo/GSYVideoPlayer/blob/master/doc/GSYVIDEO_PLAYER_PROJECT_INFO.md
- **问题集锦**：https://github.com/CarGuo/GSYVideoPlayer/blob/master/doc/QUESTION.md
- **API 文档**：https://github.com/CarGuo/GSYVideoPlayer/wiki

---

## 十、新需求接入方案

### 10.1 需求概述

**核心需求**：除了 UI 层面使用 Compose，其他所有播放控制逻辑都使用 GSYVideoPlayer 的 API。

基于你的新需求，我们需要实现以下功能：
1. ✅ 移除现有的直接使用 `exoPlayer` 的代码
2. ✅ 改用 `GSYVideoManager.instance()` API 控制播放（seekTo、pause、play 等）
3. ✅ 实现内核切换功能（在控制栏添加内核切换按钮）
4. ✅ 保留 Compose UI 层（通过 AndroidView 桥接 GSY 渲染层）

**架构设计**：
```
┌─────────────────────────────┐
│   Compose UI 层（自定义）      │  ← 你的 Compose 控制 UI
│   VideoPlayerControls         │
├─────────────────────────────┤
│   播放控制层（GSY API）        │  ← GSYVideoManager.instance()
│   GSYVideoManager             │     .seekTo() / .pause() / .start()
├─────────────────────────────┤
│   渲染层（AndroidView 桥接）   │  ← StandardGSYVideoPlayer
│   StandardGSYVideoPlayer      │     (隐藏所有自带 UI)
├─────────────────────────────┤
│   播放内核层（可切换）          │  ← Media3 / IJKPlayer / System
│   Exo2PlayerManager           │
└─────────────────────────────┘
```

**说明**：
- 帧预览功能已移除（GSY 的帧预览基于 View，实现复杂）
- 保留现有的 `HoldSeekProgressBar` 进度条显示
- 所有播放控制必须通过 `GSYVideoManager.instance()` API

---

### 10.2 GSYVideoPlayer 播放控制 API 分析

#### 10.2.1 GSYVideoManager 核心 API

GSYVideoPlayer 通过 `GSYVideoManager.instance()` 提供播放控制：

```kotlin
// 基础播放控制
GSYVideoManager.instance().start()                    // 开始播放
GSYVideoManager.instance().pause()                    // 暂停
GSYVideoManager.instance().stop()                     // 停止
GSYVideoManager.instance().releaseMediaPlayer()       // 释放播放器

// 进度控制
GSYVideoManager.instance().seekTo(timeMs)             // 跳转到指定位置（毫秒）
GSYVideoManager.instance().getCurrentPosition()       // 获取当前位置（毫秒）
GSYVideoManager.instance().getDuration()              // 获取总时长（毫秒）

// 播放状态
GSYVideoManager.instance().isPlaying()                // 是否正在播放
GSYVideoManager.instance().getBufferedPercentage()    // 缓冲百分比

// 速度控制
GSYVideoManager.instance().setSpeed(speed, soundTouch) // 设置播放速度
GSYVideoManager.instance().getNetSpeed()              // 获取网络速度

// 静音控制
GSYVideoManager.instance().setNeedMute(true)          // 设置静音
```

#### 10.2.2 Compose 中使用 GSYVideoPlayer 的正确方式

**问题分析**：
- GSYVideoPlayer 的设计是基于 **View 体系**
- 播放控制需要先创建 GSY 的 View 组件（如 `StandardGSYVideoPlayer`）
- 纯 Compose 项目需要通过 `AndroidView` 桥接

**方案：使用 AndroidView + GSYVideoManager API**

```kotlin
@Composable
fun VideoPlayerWithGSY(
    videoUrl: String,
    headers: Map<String, String>,
    modifier: Modifier = Modifier,
    onPlayerReady: () -> Unit = {}
) {
    val context = LocalContext.current
    var gsyPlayer by remember { mutableStateOf<StandardGSYVideoPlayer?>(null) }
    
    // 使用 AndroidView 包装 GSY 的渲染层
    AndroidView(
        factory = { ctx ->
            StandardGSYVideoPlayer(ctx).apply {
                // 配置播放器
                setUp(videoUrl, true, "")
                
                // 禁用 GSY 自带的控制 UI（因为我们用 Compose UI）
                titleTextView.visibility = View.GONE
                backButton.visibility = View.GONE
                fullscreenButton.visibility = View.GONE
                
                // 禁用触摸手势（TV 不需要）
                setIsTouchWiget(false)
                
                // 保存引用
                gsyPlayer = this
                
                // 开始播放
                startPlayLogic()
                onPlayerReady()
            }
        },
        modifier = modifier,
        update = { player ->
            // 更新配置
        }
    )
    
    // 通过 GSYVideoManager API 控制播放
    // 例如：快进快退
}
```

#### 10.2.3 快进快退功能改造（使用 GSY API）

**移除的代码**：
```kotlin
// ❌ 不再直接使用 exoPlayer
exoPlayer.seekTo(targetPositionMs)
exoPlayer.play()
exoPlayer.pause()
```

**改为使用 GSY API**：
```kotlin
// ✅ 使用 GSYVideoManager API
fun startHold(direction: Int) {
    if (holdSeekJob != null) return
    holdSeekDirection.value = direction
    isHoldSeeking.value = true
    
    // 使用 GSY API 获取当前位置
    holdSeekPreviewMs.value = GSYVideoManager.instance().currentPosition
    val duration = GSYVideoManager.instance().duration.coerceAtLeast(0L)
    
    holdSeekJob = coroutineScope.launch {
        val startAt = System.currentTimeMillis()
        val intervalMs = 220L
        while (isActive) {
            val elapsed = System.currentTimeMillis() - startAt
            val stepMs = when {
                elapsed < 1000L -> 6000L
                elapsed < 3000L -> 14000L
                elapsed < 6000L -> 24000L
                else -> 40000L
            }
            val next = (holdSeekPreviewMs.value + direction * stepMs)
                .coerceIn(0L, if (duration > 0) duration else 0L)
            holdSeekPreviewMs.value = next
            
            // ✅ 使用 GSY API 进行 seek
            try { 
                GSYVideoManager.instance().seekTo(next) 
            } catch (_: Throwable) {}
            
            delay(intervalMs)
        }
    }
}
```

#### 10.2.4 完整的 Compose + GSY 集成方案

由于你的需求是"除了 UI，其他都用 GSYVideoPlayer"，我们需要：

1. **使用 AndroidView 渲染视频**（GSY 的渲染层）
2. **使用 Compose 自定义控制 UI**（保留你的 UI）
3. **通过 GSYVideoManager API 控制播放**（使用 GSY 的逻辑）

**关键实现**：
```kotlin
@Composable
fun VideoPlayerScreenContent(
    movieDetails: MovieDetails,
    startPositionMs: Long? = null,
    onBackPressed: () -> Unit,
    headers: Map<String, String>,
    currentCore: PlayerCore,
    onSwitchCore: (PlayerCore) -> Unit,
    onVideoStarted: () -> Unit = {},
    onSaveProgress: (currentPositionMs: Long, durationMs: Long) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var gsyPlayer by remember { mutableStateOf<StandardGSYVideoPlayer?>(null) }
    var playerReady by remember { mutableStateOf(false) }
    
    // Hold-seek states
    val isHoldSeeking = remember { mutableStateOf(false) }
    val holdSeekPreviewMs = remember { mutableStateOf(0L) }
    val holdSeekDirection = remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    var holdSeekJob by remember { mutableStateOf<Job?>(null) }
    var holdStarterJob by remember { mutableStateOf<Job?>(null) }
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }
    
    // 使用 GSY API 的快进快退
    fun startHold(direction: Int) {
        if (holdSeekJob != null) return
        holdSeekDirection.value = direction
        isHoldSeeking.value = true
        holdSeekPreviewMs.value = GSYVideoManager.instance().currentPosition
        val duration = GSYVideoManager.instance().duration.coerceAtLeast(0L)
        
        holdSeekJob = coroutineScope.launch {
            val startAt = System.currentTimeMillis()
            val intervalMs = 220L
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startAt
                val stepMs = when {
                    elapsed < 1000L -> 6000L
                    elapsed < 3000L -> 14000L
                    elapsed < 6000L -> 24000L
                    else -> 40000L
                }
                val next = (holdSeekPreviewMs.value + direction * stepMs)
                    .coerceIn(0L, if (duration > 0) duration else 0L)
                holdSeekPreviewMs.value = next
                
                // 使用 GSY API
                try { GSYVideoManager.instance().seekTo(next) } catch (_: Throwable) {}
                delay(intervalMs)
            }
        }
    }
    
    fun stopHold() {
        holdStarterJob?.cancel()
        holdStarterJob = null
        holdSeekJob?.cancel()
        holdSeekJob = null
        isHoldSeeking.value = false
        holdSeekDirection.value = 0
    }
    
    val videoPlayerState = rememberVideoPlayerState(hideSeconds = 4)
    val pulseState = rememberVideoPlayerPulseState()
    
    // 释放资源
    DisposableEffect(Unit) {
        onDispose {
            try {
                // 使用 GSY API 保存进度
                val currentPosition = GSYVideoManager.instance().currentPosition
                val duration = GSYVideoManager.instance().duration
                if (currentPosition > 0 && duration > 0) {
                    onSaveProgress(currentPosition, duration)
                }
                GSYVideoManager.releaseAllVideos()
            } catch (_: Throwable) {}
        }
    }
    
    Box(
        Modifier
            .onPreviewKeyEvent {
                // 长按快进快退逻辑（同现有实现，但调用 GSY API）
                if (!videoPlayerState.isControlsVisible) {
                    when (it.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                leftPressed = true
                                if (holdStarterJob == null && holdSeekJob == null) {
                                    holdStarterJob = coroutineScope.launch {
                                        delay(300)
                                        if (leftPressed && holdSeekJob == null) startHold(-1)
                                    }
                                }
                                return@onPreviewKeyEvent false
                            } else if (it.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                leftPressed = false
                                holdStarterJob?.cancel(); holdStarterJob = null
                                if (holdSeekJob != null) {
                                    stopHold()
                                    return@onPreviewKeyEvent true
                                }
                                return@onPreviewKeyEvent false
                            }
                        }
                        // ... 右键同理
                    }
                }
                false
            }
            .dPadEvents(videoPlayerState, pulseState)
            .focusable()
    ) {
        // AndroidView 渲染 GSY 播放器
        AndroidView(
            factory = { ctx ->
                StandardGSYVideoPlayer(ctx).apply {
                    // 设置视频 URL 和 Headers
                    val mapHeaders = hashMapOf<String, String>()
                    headers.forEach { (k, v) -> mapHeaders[k] = v }
                    setUp(movieDetails.videoUri, true, mapHeaders, "")
                    
                    // 隐藏所有 GSY 自带的控制 UI
                    titleTextView.visibility = View.GONE
                    backButton.visibility = View.GONE
                    fullscreenButton.visibility = View.GONE
                    bottomContainer.visibility = View.GONE // 隐藏底部控制栏
                    topContainer.visibility = View.GONE    // 隐藏顶部控制栏
                    startButton.visibility = View.GONE     // 隐藏中间播放按钮
                    
                    // 禁用触摸手势
                    setIsTouchWiget(false)
                    
                    // 设置起始位置
                    if (startPositionMs != null && startPositionMs > 0) {
                        seekOnStart = startPositionMs
                    }
                    
                    gsyPlayer = this
                    
                    // 开始播放
                    startPlayLogic()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Compose 自定义控制 UI（保留现有实现）
        VideoPlayerOverlay(
            modifier = Modifier.align(Alignment.BottomCenter),
            // ... 其他参数
        )
        
        // 长按快进快退进度条
        if (isHoldSeeking.value) {
            HoldSeekProgressBar(
                progress = if (GSYVideoManager.instance().duration > 0) 
                    holdSeekPreviewMs.value.toFloat() / GSYVideoManager.instance().duration.toFloat() 
                    else 0f,
                currentPositionMs = holdSeekPreviewMs.value,
                durationMs = GSYVideoManager.instance().duration,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
            )
        }
    }
}

// 修改 dPadEvents 使用 GSY API
private fun Modifier.dPadEvents(
    videoPlayerState: VideoPlayerState,
    pulseState: VideoPlayerPulseState
): Modifier = this.handleDPadKeyEvents(
    onLeft = {
        if (!videoPlayerState.isControlsVisible) {
            // ❌ exoPlayer.seekBack()
            // ✅ 使用 GSY API
            val currentPos = GSYVideoManager.instance().currentPosition
            GSYVideoManager.instance().seekTo((currentPos - 10000).coerceAtLeast(0))
            pulseState.setType(BACK)
        }
    },
    onRight = {
        if (!videoPlayerState.isControlsVisible) {
            // ❌ exoPlayer.seekForward()
            // ✅ 使用 GSY API
            val currentPos = GSYVideoManager.instance().currentPosition
            val duration = GSYVideoManager.instance().duration
            GSYVideoManager.instance().seekTo((currentPos + 10000).coerceAtMost(duration))
            pulseState.setType(FORWARD)
        }
    },
    onUp = { videoPlayerState.showControls() },
    onDown = { videoPlayerState.showControls() },
    onEnter = {
        // ❌ exoPlayer.pause()
        // ✅ 使用 GSY API
        GSYVideoManager.instance().pause()
        videoPlayerState.showControls()
    }
)
```

#### 10.2.3 需要移除的现有代码

**移除直接使用 ExoPlayer 的代码**：

```kotlin
// ❌ 移除 rememberPlayer 创建 ExoPlayer 的方式
val exoPlayer = rememberPlayer(context, headers)

// ❌ 移除 PlayerSurface
PlayerSurface(
    player = exoPlayer,
    surfaceType = SURFACE_TYPE_SURFACE_VIEW,
    modifier = Modifier.resizeWithContentScale(...)
)

// ❌ 移除所有 exoPlayer.xxx() 调用
exoPlayer.seekTo()
exoPlayer.play()
exoPlayer.pause()
exoPlayer.currentPosition
exoPlayer.duration
```

**改为使用 GSYVideoPlayer + GSYVideoManager API**：

```kotlin
// ✅ 使用 AndroidView + StandardGSYVideoPlayer
AndroidView(
    factory = { ctx ->
        StandardGSYVideoPlayer(ctx).apply {
            setUp(videoUrl, cacheWithPlay, headers, title)
            // 配置...
        }
    }
)

// ✅ 使用 GSYVideoManager API 控制
GSYVideoManager.instance().seekTo()
GSYVideoManager.instance().start()
GSYVideoManager.instance().pause()
GSYVideoManager.instance().currentPosition
GSYVideoManager.instance().duration
```

---

### 10.3 内核切换功能实现

#### 10.3.1 内核切换原理

GSY 支持动态切换播放内核：
```kotlin
// 切换到 Media3 (ExoPlayer)
PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)

// 切换到 IJKPlayer
PlayerFactory.setPlayManager(IjkPlayerManager::class.java)

// 切换到系统播放器
PlayerFactory.setPlayManager(SystemPlayerManager::class.java)
```

**重要提示**：
- 内核切换是**全局生效**的，会影响整个应用
- 切换后需要**重新初始化播放器**才能生效
- 不能在播放过程中动态切换，需要停止当前播放

#### 10.3.2 UI 实现方案

**在 VideoPlayerControls 中添加内核切换按钮**：

1. **修改 ViewModel 添加内核状态**：
```kotlin
// VideoPlayerScreenViewModel.kt
enum class PlayerCore {
    MEDIA3,   // ExoPlayer (默认)
    IJK,      // IJKPlayer
    SYSTEM    // 系统播放器
}

data class VideoPlayerState(
    val currentCore: PlayerCore = PlayerCore.MEDIA3,
    // ... 其他状态
)

private val _playerCore = MutableStateFlow(PlayerCore.MEDIA3)
val playerCore: StateFlow<PlayerCore> = _playerCore.asStateFlow()

fun switchPlayerCore(newCore: PlayerCore) {
    viewModelScope.launch {
        _playerCore.value = newCore
        // 保存到 SharedPreferences
        savePlayerCorePreference(newCore)
    }
}
```

2. **在 Application 中初始化内核**：
```kotlin
class JetstreamApplication : Application() {
    
    @Inject
    lateinit var sharedPreferences: SharedPreferences
    
    override fun onCreate() {
        super.onCreate()
        
        // 从配置中读取用户选择的内核
        val savedCore = sharedPreferences.getString("player_core", "MEDIA3")
        when (savedCore) {
            "MEDIA3" -> PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)
            "IJK" -> PlayerFactory.setPlayManager(IjkPlayerManager::class.java)
            "SYSTEM" -> PlayerFactory.setPlayManager(SystemPlayerManager::class.java)
        }
        
        // 配置缓存
        CacheFactory.setCacheManager(ExoPlayerCacheManager::class.java)
        
        // 配置渲染模式（TV 推荐 SurfaceView）
        GSYVideoType.setRenderType(GSYVideoType.SUFRACE)
    }
}
```

3. **在控制栏添加内核切换按钮**：
```kotlin
// VideoPlayerControls.kt 中添加
@Composable
fun VideoPlayerControls(
    // ... 现有参数
    currentCore: PlayerCore,
    onSwitchCore: (PlayerCore) -> Unit,
) {
    var showCoreDialog by remember { mutableStateOf(false) }
    
    Row(
        // ... 现有布局
    ) {
        // ... 字幕按钮
        
        // ... 音轨按钮
        
        // 内核切换按钮（添加在音轨按钮右边）
        PlayerControlButton(
            icon = Icons.Default.Settings, // 或自定义图标
            contentDescription = "切换内核",
            onClick = { showCoreDialog = true },
            focusRequester = focusRequester,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
    
    // 内核选择对话框
    if (showCoreDialog) {
        CoreSelectorDialog(
            currentCore = currentCore,
            onDismiss = { showCoreDialog = false },
            onCoreSelected = { newCore ->
                onSwitchCore(newCore)
                showCoreDialog = false
            }
        )
    }
}

@Composable
fun CoreSelectorDialog(
    currentCore: PlayerCore,
    onDismiss: () -> Unit,
    onCoreSelected: (PlayerCore) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择播放内核") },
        text = {
            Column {
                RadioButtonOption(
                    text = "Media3 (ExoPlayer) - 推荐",
                    selected = currentCore == PlayerCore.MEDIA3,
                    onClick = { onCoreSelected(PlayerCore.MEDIA3) }
                )
                RadioButtonOption(
                    text = "IJKPlayer - 支持更多格式",
                    selected = currentCore == PlayerCore.IJK,
                    onClick = { onCoreSelected(PlayerCore.IJK) }
                )
                RadioButtonOption(
                    text = "系统播放器 - 轻量级",
                    selected = currentCore == PlayerCore.SYSTEM,
                    onClick = { onCoreSelected(PlayerCore.SYSTEM) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun RadioButtonOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}
```

4. **处理内核切换后的重新加载**：
```kotlin
// VideoPlayerScreen.kt
LaunchedEffect(playerCore) {
    // 当内核切换时，重新初始化播放器
    try {
        val currentPosition = exoPlayer.currentPosition
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.release()
        
        // 重新创建 ExoPlayer（rememberPlayer 会处理）
        // 设置到之前的播放位置
        delay(100)
        val mediaItem = MediaItem.Builder()
            .setUri(movieDetails.videoUri)
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (currentPosition > 0) {
            exoPlayer.seekTo(currentPosition)
        }
        exoPlayer.playWhenReady = true
    } catch (e: Exception) {
        Log.e("VideoPlayer", "Failed to switch core", e)
    }
}
```

#### 10.3.3 内核对比说明

| 内核 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **Media3 (ExoPlayer)** | • 官方支持<br>• 功能完善<br>• HLS/DASH 支持好<br>• 缓存机制完善 | • APK 体积较大 | 大多数场景（推荐） |
| **IJKPlayer** | • 支持更多格式（mpeg等）<br>• RTSP 支持更好<br>• 可自定义编译 | • SO 文件体积大<br>• 维护较慢 | 需要特殊格式支持 |
| **系统播放器** | • 体积最小<br>• 兼容性好 | • 功能有限<br>• 格式支持少 | 简单场景 |

---

### 10.4 完整代码示例

#### 10.4.1 需要添加的依赖

```groovy
// build.gradle.kts (Module)
dependencies {
    // GSY 基础库
    implementation("io.github.carguo:gsyvideoplayer-java:11.1.0")
    implementation("io.github.carguo:gsyvideoplayer-exo2:11.1.0")
    
    // 如果需要 IJK 内核
    implementation("io.github.carguo:gsyvideoplayer-arm64:11.1.0")
    // 或使用支持更多格式的 ex_so
    // implementation("io.github.carguo:gsyvideoplayer-ex_so:11.1.0")
}
```

#### 10.4.2 VideoPlayerControls 修改（添加播放状态更新）

由于使用 GSYVideoManager API，需要通过轮询或监听器获取播放状态：

```kotlin
@Composable
fun VideoPlayerControls(
    movieDetails: MovieDetails,
    focusRequester: FocusRequester,
    currentCore: PlayerCore,
    onSwitchCore: (PlayerCore) -> Unit,
    onShowControls: () -> Unit,
    onClickSubtitles: () -> Unit,
    onClickAudio: () -> Unit
) {
    // 轮询获取播放状态（从 GSYVideoManager）
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    
    // 定期更新播放状态
    LaunchedEffect(Unit) {
        while (isActive) {
            try {
                isPlaying = GSYVideoManager.instance().isPlaying
                currentPosition = GSYVideoManager.instance().currentPosition
                duration = GSYVideoManager.instance().duration
            } catch (_: Throwable) {}
            delay(500) // 每500ms更新一次
        }
    }
    
    // UI 实现
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 播放/暂停按钮
        IconButton(
            onClick = {
                if (isPlaying) {
                    GSYVideoManager.instance().pause()
                } else {
                    GSYVideoManager.instance().start()
                }
            }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放"
            )
        }
        
        // 进度条
        Slider(
            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
            onValueChange = { progress ->
                val targetPos = (duration * progress).toLong()
                GSYVideoManager.instance().seekTo(targetPos)
            }
        )
        
        // 字幕按钮
        IconButton(onClick = onClickSubtitles) {
            Icon(Icons.Default.Subtitles, "字幕")
        }
        
        // 音轨按钮
        IconButton(onClick = onClickAudio) {
            Icon(Icons.Default.AudioTrack, "音轨")
        }
        
        // 内核切换按钮
        IconButton(onClick = { /* 显示内核选择对话框 */ }) {
            Icon(Icons.Default.Settings, "切换内核")
        }
    }
}
```

---

### 10.5 完整实施方案总结

#### 10.5.1 改造清单

**需要修改的文件**：
1. `VideoPlayerScreen.kt` - 主播放器页面
2. `VideoPlayerControls.kt` - 控制栏组件
3. `Application.kt` - 初始化 GSY
4. `build.gradle.kts` - 添加依赖
5. `proguard-rules.pro` - 添加混淆规则

**核心改动**：
```diff
// VideoPlayerScreen.kt

- val exoPlayer = rememberPlayer(context, headers)
+ // 使用 AndroidView + StandardGSYVideoPlayer

- PlayerSurface(player = exoPlayer, ...)
+ AndroidView(factory = { StandardGSYVideoPlayer(it) })

- exoPlayer.seekTo(position)
+ GSYVideoManager.instance().seekTo(position)

- exoPlayer.pause()
+ GSYVideoManager.instance().pause()

- exoPlayer.play()
+ GSYVideoManager.instance().start()

- exoPlayer.currentPosition
+ GSYVideoManager.instance().currentPosition

- exoPlayer.duration
+ GSYVideoManager.instance().duration

- exoPlayer.isPlaying
+ GSYVideoManager.instance().isPlaying
```

#### 10.5.2 实施步骤

**阶段一：依赖和基础配置（1小时）**
1. ✅ 添加 GSY 依赖到 `build.gradle.kts`
2. ✅ 配置混淆规则到 `proguard-rules.pro`
3. ✅ 在 `Application.kt` 中初始化 GSY 内核
4. ✅ Sync 项目，确保依赖正常

**阶段二：替换播放控制层（2-3小时）**
1. ✅ 在 `VideoPlayerScreen.kt` 中用 `AndroidView` 替换 `PlayerSurface`
2. ✅ 隐藏 GSY 自带的所有 UI 控件
3. ✅ 将所有 `exoPlayer.xxx()` 改为 `GSYVideoManager.instance().xxx()`
4. ✅ 修改快进快退逻辑使用 GSY API
5. ✅ 修改播放/暂停逻辑使用 GSY API
6. ✅ 修改进度获取逻辑使用 GSY API

**阶段三：内核切换功能（1-2小时）**
1. ✅ 添加 `PlayerCore` 枚举类
2. ✅ 在 ViewModel 中添加内核状态管理
3. ✅ 实现内核切换 UI（对话框）
4. ✅ 处理内核切换后的播放器重启逻辑
5. ✅ 在控制栏添加内核切换按钮

**阶段四：测试和优化（1-2小时）**
1. ⬜ 测试播放基本功能
2. ⬜ 测试长按快进快退
3. ⬜ 测试内核切换（Media3 ↔ IJK ↔ System）
4. ⬜ 测试生命周期管理
5. ⬜ 测试进度保存/恢复
6. ⬜ 优化性能和体验

**预计总时长**：5-8 小时

---

### 10.6 内核切换注意事项

**1. 内核切换是全局的**：
- 调用 `PlayerFactory.setPlayManager()` 会影响整个应用
- 不能在播放过程中动态切换
- 需要先释放当前播放器，再创建新的

**2. 切换流程**：
```kotlin
// 1. 保存当前播放位置
val currentPos = GSYVideoManager.instance().currentPosition

// 2. 释放旧播放器
GSYVideoManager.releaseAllVideos()

// 3. 切换内核
PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)

// 4. 重新创建播放器（通过 AndroidView 重组）
// 5. 恢复播放位置
```

**3. IJK 内核特殊配置**：
```kotlin
// 如果使用 IJK 内核，需要额外配置
IjkPlayerManager.setLogLevel(IjkMediaPlayer.IJK_LOG_SILENT) // 关闭日志

// 配置精准 seek
val optionList = mutableListOf<VideoOptionModel>()
optionList.add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1))
GSYVideoManager.instance().setOptionModelList(optionList)
```

**4. 常见问题**：

**Q1：AndroidView 中的 GSY 播放器无法自适应大小**
```kotlin
AndroidView(
    factory = { ctx ->
        StandardGSYVideoPlayer(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    },
    modifier = Modifier.fillMaxSize()
)
```

**Q2：如何监听播放状态变化**
```kotlin
gsyPlayer.setVideoAllCallBack(object : GSYSampleCallBack() {
    override fun onPrepared(url: String, vararg objects: Any) {
        onPlayerReady()
    }
    
    override fun onPlayError(url: String, vararg objects: Any) {
        Log.e("Player", "Play error")
    }
    
    override fun onAutoComplete(url: String, vararg objects: Any) {
        onVideoCompleted()
    }
})
```

**Q3：Headers 如何传递给 GSYVideoPlayer**
```kotlin
val mapHeaders = hashMapOf<String, String>()
headers.forEach { (k, v) -> mapHeaders[k] = v }
gsyPlayer.setUp(url, cacheWithPlay, mapHeaders, title)
```

---

### 10.7 代码迁移对照表

#### 完整的 API 替换清单

```kotlin
// ========== 播放器初始化 ==========
// ❌ 移除
val exoPlayer = rememberPlayer(context, headers)

// ✅ 替换为
AndroidView(
    factory = { ctx ->
        StandardGSYVideoPlayer(ctx).apply {
            setUp(videoUrl, true, headers, "")
            // 隐藏所有自带 UI
            titleTextView.visibility = View.GONE
            backButton.visibility = View.GONE
            fullscreenButton.visibility = View.GONE
            bottomContainer.visibility = View.GONE
            topContainer.visibility = View.GONE
            startButton.visibility = View.GONE
            setIsTouchWiget(false)
            startPlayLogic()
        }
    }
)

// ========== 播放控制 ==========
// ❌ exoPlayer.play() / exoPlayer.playWhenReady = true
// ✅ GSYVideoManager.instance().start()

// ❌ exoPlayer.pause()
// ✅ GSYVideoManager.instance().pause()

// ❌ exoPlayer.stop()
// ✅ GSYVideoManager.instance().stop()

// ========== 进度控制 ==========
// ❌ exoPlayer.seekTo(positionMs)
// ✅ GSYVideoManager.instance().seekTo(positionMs)

// ❌ val pos = exoPlayer.currentPosition
// ✅ val pos = GSYVideoManager.instance().currentPosition

// ❌ val dur = exoPlayer.duration
// ✅ val dur = GSYVideoManager.instance().duration

// ❌ exoPlayer.seekBack() / exoPlayer.seekForward()
// ✅ 
val currentPos = GSYVideoManager.instance().currentPosition
GSYVideoManager.instance().seekTo(currentPos - 10000) // 后退10秒
GSYVideoManager.instance().seekTo(currentPos + 10000) // 前进10秒

// ========== 播放状态 ==========
// ❌ exoPlayer.isPlaying
// ✅ GSYVideoManager.instance().isPlaying

// ❌ exoPlayer.playbackState == Player.STATE_READY
// ✅ 通过 GSYVideoPlayer 的回调监听

// ========== 资源释放 ==========
// ❌ exoPlayer.release()
// ✅ GSYVideoManager.releaseAllVideos()

// ========== 生命周期 ==========
// ❌ 在 onPause/onResume 中手动控制
exoPlayer.pause()
exoPlayer.play()

// ✅ 使用 GSY 的静态方法
override fun onPause() {
    super.onPause()
    GSYVideoManager.onPause()
}

override fun onResume() {
    super.onResume()
    GSYVideoManager.onResume()
}
```

#### 10.5.3 实施步骤详解

**步骤 1：添加依赖（5分钟）**
```groovy
// build.gradle.kts (Module: jetstream)
dependencies {
    implementation("io.github.carguo:gsyvideoplayer-java:11.1.0")
    implementation("io.github.carguo:gsyvideoplayer-exo2:11.1.0")
    
    // 可选：IJK 内核（如需支持更多格式）
    implementation("io.github.carguo:gsyvideoplayer-arm64:11.1.0")
}
```

**步骤 2：配置 Application（10分钟）**
```kotlin
// JetstreamApplication.kt
class JetstreamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 初始化 GSY 播放内核
        PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)
        
        // 配置缓存
        CacheFactory.setCacheManager(ExoPlayerCacheManager::class.java)
        
        // 配置渲染模式（TV 推荐 SurfaceView）
        GSYVideoType.setRenderType(GSYVideoType.SUFRACE)
        
        // 配置显示比例
        GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_DEFAULT)
    }
}
```

**步骤 3：修改 VideoPlayerScreen.kt（2小时）**
- 替换 `rememberPlayer` 为 `AndroidView`
- 替换所有 `exoPlayer.xxx()` 为 `GSYVideoManager.instance().xxx()`
- 保留 Compose UI 层不变

**步骤 4：添加内核切换功能（1小时）**
- 实现 `CoreSelectorDialog`
- 在 VideoPlayerControls 添加切换按钮
- 处理内核切换逻辑

**步骤 5：测试（1-2小时）**
- 测试播放功能
- 测试快进快退
- 测试内核切换
- 测试进度保存

---

### 10.8 预期效果

改造完成后，你的项目将：

✅ **UI 层**：完全使用 Compose 自定义 UI（保持原有体验）
✅ **播放控制**：完全使用 GSYVideoPlayer API（内核可切换）
✅ **渲染层**：使用 GSY 的 StandardGSYVideoPlayer（通过 AndroidView）
✅ **内核管理**：可以动态切换 Media3、IJKPlayer、System 三种内核
✅ **配置能力**：可以使用 GSY 的所有高级配置（缓存、Headers、播放选项等）

**代码对比**：

| 层级 | 改造前 | 改造后 |
|------|--------|--------|
| **UI层** | Compose 自定义 | Compose 自定义（不变） |
| **播放控制** | 直接使用 ExoPlayer | 使用 GSYVideoManager API |
| **渲染层** | PlayerSurface | AndroidView(StandardGSYVideoPlayer) |
| **播放内核** | 固定 Media3 | 可切换（Media3/IJK/System） |
| **配置能力** | 有限 | 完整的 GSY 配置能力 |

---

**文档版本**：v3.0  
**更新日期**：2025-10-24  
**适用于**：GSYVideoPlayer 11.1.0 + Android TV + Jetpack Compose  
**方案特点**：UI 用 Compose，播放控制用 GSYVideoPlayer API  
**改造范围**：播放器层完全替换，UI 层保留

