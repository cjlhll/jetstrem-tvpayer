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

package com.google.jetstream.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.jetstream.data.entities.Movie
import com.google.jetstream.data.entities.MovieList
import com.google.jetstream.data.util.StringConstants
import com.google.jetstream.presentation.common.Error
import com.google.jetstream.presentation.common.Loading
import com.google.jetstream.presentation.common.MoviesRow
import com.google.jetstream.presentation.screens.dashboard.rememberChildPadding
import com.google.jetstream.presentation.screens.movies.MoviesScreenMovieList
import com.google.jetstream.presentation.screens.home.ScrapedTvViewModel

@Composable
fun HomeScreen(
    onMovieClick: (movie: Movie) -> Unit,
    goToVideoPlayer: (movie: Movie) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    onShowAllClick: (movieType: String) -> Unit,
    homeScreeViewModel: HomeScreeViewModel = hiltViewModel(),
    focusRestoreTrigger: Int = 0
) {
    val uiState by homeScreeViewModel.uiState.collectAsStateWithLifecycle()

    when (val s = uiState) {
        is HomeScreenUiState.Ready -> {
            Catalog(
                featuredMovies = s.featuredMovieList,
                trendingMovies = s.trendingMovieList,
                top10Movies = s.top10MovieList,
                nowPlayingMovies = s.nowPlayingMovieList,
                moviesWithLongThumbnail = s.moviesWithLongThumbnail,
                onMovieClick = onMovieClick,
                onScroll = onScroll,
                goToVideoPlayer = goToVideoPlayer,
                isTopBarVisible = isTopBarVisible,
                onShowAllClick = onShowAllClick,
                modifier = Modifier.fillMaxSize(),
                focusRestoreTrigger = focusRestoreTrigger
            )
        }

        is HomeScreenUiState.Loading -> Loading(modifier = Modifier.fillMaxSize())
        is HomeScreenUiState.Error -> Error(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Catalog(
    featuredMovies: MovieList,
    trendingMovies: MovieList,
    top10Movies: MovieList,
    nowPlayingMovies: MovieList,
    moviesWithLongThumbnail: MovieList,
    onMovieClick: (movie: Movie) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    goToVideoPlayer: (movie: Movie) -> Unit,
    onShowAllClick: (movieType: String) -> Unit,
    modifier: Modifier = Modifier,
    isTopBarVisible: Boolean = true,
    focusRestoreTrigger: Int = 0
) {

    // 使用 rememberSaveable 保存滚动状态，这样页面返回时会自动恢复到之前的位置
    val lazyListState = rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    val childPadding = rememberChildPadding()
    var immersiveListHasFocus by remember { mutableStateOf(false) }

    val shouldShowTopBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset < 300
        }
    }

    // WebDAV 刮削的电影（用于替换热门趋势模块）
    val scrapedVm: ScrapedMoviesViewModel = hiltViewModel()
    // WebDAV 刮削的电视剧（以目录为单位）
    val scrapedTvVm: ScrapedTvViewModel = hiltViewModel()
    val scrapedTv by scrapedTvVm.shows.collectAsStateWithLifecycle()
    
    // 最近观看的电影
    val recentlyWatchedVm: RecentlyWatchedViewModel = hiltViewModel()
    val recentlyWatchedMovies by recentlyWatchedVm.recentlyWatchedMovies.collectAsStateWithLifecycle()

    val scraped by scrapedVm.movies.collectAsStateWithLifecycle()
    
    // 从 ViewModel 获取上次聚焦的区域
    val homeViewModel: HomeScreeViewModel = hiltViewModel()
    val lastFocusedSection by homeViewModel.lastFocusedSection.collectAsStateWithLifecycle()
    
    // 为每个区域创建 FocusRequester
    val recentlyWatchedFocusRequester = remember { FocusRequester() }
    val moviesFocusRequester = remember { FocusRequester() }
    val showsFocusRequester = remember { FocusRequester() }
    
    // 跟踪焦点是否已经分配过（避免重复分配）
    var focusAllocated by rememberSaveable { mutableStateOf(false) }
    
    // 统一的焦点管理逻辑
    LaunchedEffect(
        focusRestoreTrigger,
        recentlyWatchedMovies,
        scraped,
        trendingMovies,
        scrapedTv,
        nowPlayingMovies
    ) {
        // 等待组件完全渲染：LazyColumn + LazyRow + Items + FocusRequester 绑定
        kotlinx.coroutines.delay(200)
        
        // 如果所有模块都为空，不需要分配焦点
        val movieList = if (scraped.isNotEmpty()) scraped else trendingMovies
        val tvList = if (scrapedTv.isNotEmpty()) scrapedTv else nowPlayingMovies
        val hasAnyContent = recentlyWatchedMovies.isNotEmpty() || movieList.isNotEmpty() || tvList.isNotEmpty()
        
        if (!hasAnyContent) {
            android.util.Log.d("HomeScreen", "所有模块都为空，无需分配焦点")
            return@LaunchedEffect
        }
        
        if (focusRestoreTrigger > 0) {
            // === 场景1：从其他页面返回，恢复焦点 ===
            // 只恢复到有数据的区域
            val focusRestored = try {
                when {
                    lastFocusedSection == 1 && recentlyWatchedMovies.isNotEmpty() -> {
                        recentlyWatchedFocusRequester.requestFocus()
                        true
                    }
                    lastFocusedSection == 2 && movieList.isNotEmpty() -> {
                        moviesFocusRequester.requestFocus()
                        true
                    }
                    lastFocusedSection == 3 && tvList.isNotEmpty() -> {
                        showsFocusRequester.requestFocus()
                        true
                    }
                    else -> false
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeScreen", "Focus restore failed", e)
                false
            }
            
            // 如果无法恢复，使用默认优先级（选择第一个有数据的模块）
            if (!focusRestored) {
                try {
                    when {
                        recentlyWatchedMovies.isNotEmpty() -> {
                            recentlyWatchedFocusRequester.requestFocus()
                            homeViewModel.updateLastFocusedSection(1)
                            android.util.Log.d("HomeScreen", "返回恢复焦点失败，使用默认：最近观看")
                        }
                        movieList.isNotEmpty() -> {
                            moviesFocusRequester.requestFocus()
                            homeViewModel.updateLastFocusedSection(2)
                            android.util.Log.d("HomeScreen", "返回恢复焦点失败，使用默认：电影")
                        }
                        tvList.isNotEmpty() -> {
                            showsFocusRequester.requestFocus()
                            homeViewModel.updateLastFocusedSection(3)
                            android.util.Log.d("HomeScreen", "返回恢复焦点失败，使用默认：电视剧")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "默认焦点分配失败", e)
                }
            }
        } else if (!focusAllocated) {
            // === 场景2：首次进入应用，按优先级分配焦点 ===
            // 优先级：有数据的最近观看 > 有数据的电影 > 有数据的电视剧
            val focusAssigned = try {
                when {
                    recentlyWatchedMovies.isNotEmpty() -> {
                        recentlyWatchedFocusRequester.requestFocus()
                        homeViewModel.updateLastFocusedSection(1)
                        android.util.Log.d("HomeScreen", "首次焦点分配：最近观看")
                        true
                    }
                    movieList.isNotEmpty() -> {
                        moviesFocusRequester.requestFocus()
                        homeViewModel.updateLastFocusedSection(2)
                        android.util.Log.d("HomeScreen", "首次焦点分配：电影")
                        true
                    }
                    tvList.isNotEmpty() -> {
                        showsFocusRequester.requestFocus()
                        homeViewModel.updateLastFocusedSection(3)
                        android.util.Log.d("HomeScreen", "首次焦点分配：电视剧")
                        true
                    }
                    else -> {
                        android.util.Log.d("HomeScreen", "无可用内容，无法分配焦点")
                        false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeScreen", "焦点分配失败", e)
                false
            }
            
            if (focusAssigned) {
                focusAllocated = true
            }
        }
    }

    LaunchedEffect(shouldShowTopBar) {
        onScroll(shouldShowTopBar)
    }
    
    // 判断是否所有模块都为空
    val movieList = if (scraped.isNotEmpty()) scraped else trendingMovies
    val tvList = if (scrapedTv.isNotEmpty()) scrapedTv else nowPlayingMovies
    val hasAnyContent = recentlyWatchedMovies.isNotEmpty() || movieList.isNotEmpty() || tvList.isNotEmpty()
    
    if (!hasAnyContent) {
        // 所有模块都为空，显示全屏居中的空状态
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = "暂无内容",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "请在设置中配置WebDAV并刮削媒体库",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        // 至少有一个模块有内容，只显示有数据的模块
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 108.dp),
            // Setting overscan margin to bottom to ensure the last row's visibility
            modifier = modifier,
        ) {

            // === 最近观看模块 ===
            if (recentlyWatchedMovies.isNotEmpty()) {
                item(contentType = "RecentlyWatched") {
                    MoviesScreenMovieList(
                        movieList = recentlyWatchedMovies,
                        title = "最近观看",
                        onMovieClick = { movie ->
                            homeViewModel.updateLastFocusedSection(1)
                            goToVideoPlayer(movie)
                        },
                        modifier = Modifier
                            .focusRequester(recentlyWatchedFocusRequester)
                            .onFocusChanged { 
                                if (it.hasFocus || it.isFocused) {
                                    homeViewModel.updateLastFocusedSection(1)
                                }
                            }
                    )
                }
            }
            
            // === 电影模块 ===
            if (movieList.isNotEmpty()) {
                item(contentType = "Movies") {
                    MoviesRow(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .focusRequester(moviesFocusRequester)
                            .onFocusChanged { 
                                if (it.hasFocus || it.isFocused) {
                                    homeViewModel.updateLastFocusedSection(2)
                                }
                            },
                        movieList = movieList,
                        title = "电影",
                        showAllButton = true,
                        onMovieSelected = { movie ->
                            homeViewModel.updateLastFocusedSection(2)
                            onMovieClick(movie)
                        },
                        onShowAllClick = { 
                            homeViewModel.updateLastFocusedSection(2)
                            onShowAllClick("movies") 
                        }
                    )
                }
            }
            
            // === 电视剧模块 ===
            if (tvList.isNotEmpty()) {
                item(contentType = "TVShows") {
                    MoviesRow(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .focusRequester(showsFocusRequester)
                            .onFocusChanged { 
                                if (it.hasFocus || it.isFocused) {
                                    homeViewModel.updateLastFocusedSection(3)
                                }
                            },
                        movieList = tvList,
                        title = StringConstants.Composable.HomeScreenNowPlayingMoviesTitle,
                        showAllButton = true,
                        onMovieSelected = { movie ->
                            homeViewModel.updateLastFocusedSection(3)
                            onMovieClick(movie)
                        },
                        onShowAllClick = { 
                            homeViewModel.updateLastFocusedSection(3)
                            onShowAllClick("shows") 
                        }
                    )
                }
            }
        }
    }
}
