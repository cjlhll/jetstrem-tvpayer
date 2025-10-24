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

package com.google.jetstream

import android.app.Application
import com.google.jetstream.data.repositories.MovieRepository
import com.google.jetstream.data.repositories.MovieRepositoryImpl
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.cache.CacheFactory
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import com.shuyu.gsyvideoplayer.utils.GSYVideoType
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager
import tv.danmaku.ijk.media.exo2.ExoPlayerCacheManager

@HiltAndroidApp
class JetStreamApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化 GSYVideoPlayer
        initGSYVideoPlayer()
    }
    
    private fun initGSYVideoPlayer() {
        // 设置播放内核为 Media3 (ExoPlayer)
        PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)
        
        // 配置缓存管理器（支持 m3u8）
        CacheFactory.setCacheManager(ExoPlayerCacheManager::class.java)
        
        // 配置渲染模式（TV 推荐 SurfaceView，HDR 支持更好）
        GSYVideoType.setRenderType(GSYVideoType.SUFRACE)
        
        // 配置显示比例（默认比例）
        GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_DEFAULT)
        
        // 初始化 context
        GSYVideoManager.instance().initContext(applicationContext)
        
        // 配置 ExoPlayer 数据源工厂，允许重定向
        tv.danmaku.ijk.media.exo2.ExoSourceManager.setExoMediaSourceInterceptListener(
            object : tv.danmaku.ijk.media.exo2.ExoMediaSourceInterceptListener {
                override fun getMediaSource(
                    dataSource: String?,
                    preview: Boolean,
                    cacheEnable: Boolean,
                    isLooping: Boolean,
                    cacheDir: java.io.File?
                ): androidx.media3.exoplayer.source.MediaSource? {
                    // 返回 null 使用默认实现
                    return null
                }
                
                override fun getHttpDataSourceFactory(
                    userAgent: String?,
                    listener: androidx.media3.datasource.TransferListener?,
                    connectTimeoutMillis: Int,
                    readTimeoutMillis: Int,
                    mapHeadData: MutableMap<String, String>?,
                    allowCrossProtocolRedirects: Boolean
                ): androidx.media3.datasource.DataSource.Factory? {
                    // 创建支持重定向的 HttpDataSource
                    return androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setUserAgent(userAgent)
                        .setConnectTimeoutMs(connectTimeoutMillis)
                        .setReadTimeoutMs(readTimeoutMillis)
                        .setAllowCrossProtocolRedirects(true) // 允许跨协议重定向
                        .apply {
                            if (!mapHeadData.isNullOrEmpty()) {
                                setDefaultRequestProperties(mapHeadData)
                            }
                            if (listener != null) {
                                // Media3 1.8.0 不再支持 setTransferListener
                                // transferListener 通过其他方式注入
                            }
                        }
                }
                
                override fun cacheWriteDataSinkFactory(
                    cachePath: String?,
                    url: String?
                ): androidx.media3.datasource.DataSink.Factory? {
                    return null
                }
            }
        )
    }
}

@InstallIn(SingletonComponent::class)
@Module
abstract class MovieRepositoryModule {

    @Binds
    abstract fun bindMovieRepository(
        movieRepositoryImpl: MovieRepositoryImpl
    ): MovieRepository
}
