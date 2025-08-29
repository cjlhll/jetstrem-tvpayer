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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
) {

    val lazyListState = rememberLazyListState()
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

    val scraped by scrapedVm.movies.collectAsStateWithLifecycle()

    LaunchedEffect(shouldShowTopBar) {
        onScroll(shouldShowTopBar)
    }
    LaunchedEffect(isTopBarVisible) {
        if (isTopBarVisible) lazyListState.animateScrollToItem(0)
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(bottom = 108.dp),
        // Setting overscan margin to bottom to ensure the last row's visibility
        modifier = modifier,
    ) {

        item(contentType = "MoviesScreenMovieList") {
            MoviesScreenMovieList(
                movieList = moviesWithLongThumbnail,
                title = "最近观看",
                onMovieClick = onMovieClick
            )
        }
        item(contentType = "MoviesRow") {
            MoviesRow(
                modifier = Modifier.padding(top = 16.dp),
                movieList = if (scraped.isNotEmpty()) scraped else trendingMovies,
                title = "电影",
                showAllButton = true,
                onMovieSelected = onMovieClick,
                onShowAllClick = { onShowAllClick("movies") }
            )
        }
        item(contentType = "MoviesRow") {
            MoviesRow(
                modifier = Modifier.padding(top = 16.dp),
                movieList = if (scrapedTv.isNotEmpty()) scrapedTv else nowPlayingMovies,
                title = StringConstants.Composable.HomeScreenNowPlayingMoviesTitle,
                showAllButton = true,
                onMovieSelected = onMovieClick,
                onShowAllClick = { onShowAllClick("shows") }
            )
        }
    }
}
