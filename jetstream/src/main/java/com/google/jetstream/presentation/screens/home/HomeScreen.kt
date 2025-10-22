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
    
    // 焦点管理 - 简化版本，依赖 focusRestorer() 自动恢复内部焦点
    val recentlyWatchedFocusRequester = remember { FocusRequester() }
    val moviesFocusRequester = remember { FocusRequester() }
    val showsFocusRequester = remember { FocusRequester() }
    
    // 焦点恢复逻辑
    LaunchedEffect(focusRestoreTrigger, recentlyWatchedMovies, scraped, scrapedTv) {
        if (focusRestoreTrigger > 0) {
            // 从其他页面返回时，等待滚动状态完全稳定
            // 给 LazyListState 时间通过 Saver 恢复位置
            kotlinx.coroutines.delay(100)
            
            // 等待滚动状态稳定
            while (lazyListState.isScrollInProgress) {
                kotlinx.coroutines.delay(50)
            }
            
            // 额外延迟确保渲染完成
            kotlinx.coroutines.delay(200)
            
            // 恢复焦点到上次的区域
            val focusRestored = when (lastFocusedSection) {
                1 -> if (recentlyWatchedMovies.isNotEmpty()) {
                    recentlyWatchedFocusRequester.requestFocus()
                    true
                } else false
                2 -> if (scraped.isNotEmpty() || trendingMovies.isNotEmpty()) {
                    moviesFocusRequester.requestFocus()
                    true
                } else false
                3 -> if (scrapedTv.isNotEmpty() || nowPlayingMovies.isNotEmpty()) {
                    showsFocusRequester.requestFocus()
                    true
                } else false
                else -> false
            }
            
            // 如果无法恢复到上次位置，使用默认优先级
            if (!focusRestored) {
                when {
                    recentlyWatchedMovies.isNotEmpty() -> {
                        recentlyWatchedFocusRequester.requestFocus()
                        homeViewModel.updateLastFocusedSection(1)
                    }
                    scraped.isNotEmpty() || trendingMovies.isNotEmpty() -> {
                        moviesFocusRequester.requestFocus()
                        homeViewModel.updateLastFocusedSection(2)
                    }
                    scrapedTv.isNotEmpty() || nowPlayingMovies.isNotEmpty() -> {
                        showsFocusRequester.requestFocus()
                        homeViewModel.updateLastFocusedSection(3)
                    }
                }
            }
        } else {
            // 首次加载时设置初始焦点
            kotlinx.coroutines.delay(200)
            when {
                recentlyWatchedMovies.isNotEmpty() -> {
                    recentlyWatchedFocusRequester.requestFocus()
                    homeViewModel.updateLastFocusedSection(1)
                }
                scraped.isNotEmpty() || trendingMovies.isNotEmpty() -> {
                    moviesFocusRequester.requestFocus()
                    homeViewModel.updateLastFocusedSection(2)
                }
                scrapedTv.isNotEmpty() || nowPlayingMovies.isNotEmpty() -> {
                    showsFocusRequester.requestFocus()
                    homeViewModel.updateLastFocusedSection(3)
                }
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

        // 只有当有最近观看记录时才显示最近观看部分
        if (recentlyWatchedMovies.isNotEmpty()) {
            item(contentType = "MoviesScreenMovieList") {
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
        item(contentType = "MoviesRow") {
            MoviesRow(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .focusRequester(moviesFocusRequester)
                    .onFocusChanged { 
                        if (it.hasFocus || it.isFocused) {
                            homeViewModel.updateLastFocusedSection(2)
                        }
                    },
                movieList = if (scraped.isNotEmpty()) scraped else trendingMovies,
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
        item(contentType = "MoviesRow") {
            MoviesRow(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .focusRequester(showsFocusRequester)
                    .onFocusChanged { 
                        if (it.hasFocus || it.isFocused) {
                            homeViewModel.updateLastFocusedSection(3)
                        }
                    },
                movieList = if (scrapedTv.isNotEmpty()) scrapedTv else nowPlayingMovies,
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
