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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
        
        if (focusRestoreTrigger > 0) {
            // === 场景1：从其他页面返回，恢复焦点 ===
            // 优先恢复到上次的区域（即使没有数据也恢复，因为有空状态组件）
            val focusRestored = try {
                when (lastFocusedSection) {
                    1 -> {
                        recentlyWatchedFocusRequester.requestFocus()
                        true
                    }
                    2 -> {
                        moviesFocusRequester.requestFocus()
                        true
                    }
                    3 -> {
                        showsFocusRequester.requestFocus()
                        true
                    }
                    else -> false
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeScreen", "Focus restore failed", e)
                false
            }
            
            // 如果无法恢复，使用默认优先级
            if (!focusRestored) {
                try {
                    recentlyWatchedFocusRequester.requestFocus()
                    homeViewModel.updateLastFocusedSection(1)
                    android.util.Log.d("HomeScreen", "返回恢复焦点失败，使用默认：最近观看")
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "默认焦点分配失败", e)
                }
            }
        } else if (!focusAllocated) {
            // === 场景2：首次进入应用，按优先级分配焦点 ===
            // 优先级：有数据的最近观看 > 有数据的电影 > 有数据的电视剧 > 最近观看（空状态）
            val focusAssigned = try {
                when {
                    recentlyWatchedMovies.isNotEmpty() -> {
                        recentlyWatchedFocusRequester.requestFocus()
                        homeViewModel.updateLastFocusedSection(1)
                        android.util.Log.d("HomeScreen", "首次焦点分配：最近观看（有数据）")
                        true
                    }
                    scraped.isNotEmpty() || trendingMovies.isNotEmpty() -> {
                        moviesFocusRequester.requestFocus()
                        homeViewModel.updateLastFocusedSection(2)
                        android.util.Log.d("HomeScreen", "首次焦点分配：电影（有数据）")
                        true
                    }
                    scrapedTv.isNotEmpty() || nowPlayingMovies.isNotEmpty() -> {
                        showsFocusRequester.requestFocus()
                        homeViewModel.updateLastFocusedSection(3)
                        android.util.Log.d("HomeScreen", "首次焦点分配：电视剧（有数据）")
                        true
                    }
                    else -> {
                        // 所有模块都没有数据，默认焦点到最近观看的空状态
                        recentlyWatchedFocusRequester.requestFocus()
                        homeViewModel.updateLastFocusedSection(1)
                        android.util.Log.d("HomeScreen", "首次焦点分配：最近观看（空状态）")
                        true
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
    

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(bottom = 108.dp),
        // Setting overscan margin to bottom to ensure the last row's visibility
        modifier = modifier,
    ) {

        // === 最近观看模块 ===
        item(contentType = "RecentlyWatched") {
            if (recentlyWatchedMovies.isNotEmpty()) {
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
            } else {
                com.google.jetstream.presentation.common.EmptySection(
                    title = "最近观看",
                    modifier = Modifier.focusRequester(recentlyWatchedFocusRequester)
                )
            }
        }
        
        // === 电影模块 ===
        item(contentType = "Movies") {
            val movieList = if (scraped.isNotEmpty()) scraped else trendingMovies
            if (movieList.isNotEmpty()) {
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
            } else {
                com.google.jetstream.presentation.common.EmptySection(
                    title = "电影",
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .focusRequester(moviesFocusRequester)
                )
            }
        }
        
        // === 电视剧模块 ===
        item(contentType = "TVShows") {
            val tvList = if (scrapedTv.isNotEmpty()) scrapedTv else nowPlayingMovies
            if (tvList.isNotEmpty()) {
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
            } else {
                com.google.jetstream.presentation.common.EmptySection(
                    title = StringConstants.Composable.HomeScreenNowPlayingMoviesTitle,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .focusRequester(showsFocusRequester)
                )
            }
        }
    }
}
