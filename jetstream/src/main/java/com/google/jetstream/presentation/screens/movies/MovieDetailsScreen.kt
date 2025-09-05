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

package com.google.jetstream.presentation.screens.movies

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch

import androidx.compose.ui.focus.FocusRequester

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.google.jetstream.R
import com.google.jetstream.data.entities.Movie
import com.google.jetstream.data.entities.MovieDetails
import com.google.jetstream.data.entities.Episode
import com.google.jetstream.data.util.StringConstants
import com.google.jetstream.presentation.common.Error
import com.google.jetstream.presentation.common.Loading
import com.google.jetstream.presentation.common.MoviesRow
import com.google.jetstream.presentation.screens.dashboard.rememberChildPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

object MovieDetailsScreen {
    const val MovieIdBundleKey = "movieId"
}

@Composable
fun MovieDetailsScreen(
    goToMoviePlayer: (String) -> Unit,
    goToEpisodePlayer: (String, String) -> Unit = { _, _ -> }, // 新增：播放指定剧集的回调
    onBackPressed: () -> Unit,
    refreshScreenWithNewMovie: (Movie) -> Unit,
    movieDetailsScreenViewModel: MovieDetailsScreenViewModel = hiltViewModel()
) {
    val uiState by movieDetailsScreenViewModel.uiState.collectAsStateWithLifecycle()

    when (val s = uiState) {
        is MovieDetailsScreenUiState.Loading -> {
            Loading(modifier = Modifier.fillMaxSize())
        }

        is MovieDetailsScreenUiState.Error -> {
            Error(modifier = Modifier.fillMaxSize())
        }

        is MovieDetailsScreenUiState.Done -> {
            Details(
                movieDetails = s.movieDetails,
                fileSizeBytes = s.fileSizeBytes,
                recentlyWatched = s.recentlyWatched,
                goToMoviePlayer = { 
                    if (s.movieDetails.isTV) {
                        // 电视剧：使用智能播放逻辑
                        movieDetailsScreenViewModel.startTvPlayback(s.movieDetails) { episodeId ->
                            goToEpisodePlayer(s.movieDetails.id, episodeId)
                        }
                    } else {
                        // 电影：直接播放
                        goToMoviePlayer(s.movieDetails.id)
                    }
                },
                goToEpisodePlayer = goToEpisodePlayer,
                onBackPressed = onBackPressed,
                refreshScreenWithNewMovie = refreshScreenWithNewMovie,
                movieDetailsScreenViewModel = movieDetailsScreenViewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .animateContentSize()
            )
        }
    }
}

@Composable
private fun Details(
    movieDetails: MovieDetails,
    fileSizeBytes: Long?,
    recentlyWatched: com.google.jetstream.data.database.entities.RecentlyWatchedEntity?,
    goToMoviePlayer: () -> Unit,
    goToEpisodePlayer: (String, String) -> Unit, // 新增：播放指定剧集的回调
    onBackPressed: () -> Unit,
    refreshScreenWithNewMovie: (Movie) -> Unit,
    modifier: Modifier = Modifier,
    movieDetailsScreenViewModel: MovieDetailsScreenViewModel = hiltViewModel()
) {
    // 剧集状态
    val episodesState by movieDetailsScreenViewModel.episodesState.collectAsStateWithLifecycle()
    val sourceInfoEpisode by movieDetailsScreenViewModel.sourceInfoEpisode.collectAsStateWithLifecycle()

    // 当前选中的季
    var selectedSeasonNumber by remember { mutableStateOf(1) }
    val childPadding = rememberChildPadding()

    val playButtonFocusRequester = FocusRequester()

    BackHandler(onBack = onBackPressed)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 35.dp),
        modifier = modifier,
    ) {
        item {
            MovieDetails(
                movieDetails = movieDetails,
                fileSizeBytes = fileSizeBytes,
                recentlyWatched = recentlyWatched,
                goToMoviePlayer = goToMoviePlayer,
                focusRequester = playButtonFocusRequester
            )
        }

        // 电视剧季选择器 - 只要是电视剧就显示，包括只有一季的情况
        if (movieDetails.isTV) {
            item {
                val seasons = if (movieDetails.availableSeasons.isNotEmpty()) {
                    movieDetails.availableSeasons.map { tvSeason ->
                        Season(
                            number = tvSeason.number,
                            displayName = tvSeason.name,
                            episodeCount = tvSeason.episodeCount
                        )
                    }
                } else {
                    // 如果没有季信息，默认显示第1季
                    listOf(
                        Season(
                            number = 1,
                            displayName = "第1季",
                            episodeCount = 0
                        )
                    )
                }
                
                // 初始化时加载第一季的剧集
                LaunchedEffect(movieDetails.id) {
                    if (seasons.isNotEmpty()) {
                        selectedSeasonNumber = seasons.first().number
                        movieDetailsScreenViewModel.loadEpisodes(movieDetails.id, selectedSeasonNumber)
                    }
                }
                
                SeasonSelector(
                    seasons = seasons,
                    selectedSeason = seasons.find { it.number == selectedSeasonNumber },
                    onSeasonSelected = { season ->
                        selectedSeasonNumber = season.number
                        movieDetailsScreenViewModel.loadEpisodes(movieDetails.id, season.number)
                    }
                )
            }
            
            // 剧集列表
            item {
                val currentEpisodesState = episodesState
                when (currentEpisodesState) {
                    is EpisodesUiState.Loading -> {
                        // 可以显示加载状态，这里暂时不显示
                    }
                    is EpisodesUiState.Success -> {
                        if (currentEpisodesState.episodes.isNotEmpty()) {
                            EpisodeList(
                                episodeList = currentEpisodesState.episodes,
                                title = "第${selectedSeasonNumber}季剧集",
                                onEpisodeClick = { episode ->
                                    // 智能播放逻辑：根据是否有播放记录决定播放方式
                                    if (episode.watchProgress != null && episode.currentPositionMs != null) {
                                        // 有播放记录，从历史时间点续播
                                        android.util.Log.d("MovieDetailsScreen", "续播剧集: 第${episode.seasonNumber}季第${episode.episodeNumber}集, 从${episode.currentPositionMs}ms开始")
                                    } else {
                                        // 无播放记录，从0开始播放
                                        android.util.Log.d("MovieDetailsScreen", "首次播放剧集: 第${episode.seasonNumber}季第${episode.episodeNumber}集")
                                    }
                                    // 跳转到播放页面
                                    goToEpisodePlayer(movieDetails.id, episode.id)
                                }
                            )
                        }
                    }
                    is EpisodesUiState.Error -> {
                        // 可以显示错误状态，这里暂时不显示
                    }
                }
            }
        }

        // 添加间距以改善视觉效果
        item {
            Box(modifier = Modifier.padding(top = 24.dp))
        }

        item {
            CastAndCrewList(
                castAndCrew = movieDetails.castAndCrew
            )
        }

        // 恢复底部分割线，并将底部改为“源信息 + 影片规格”
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = childPadding.start)
                    .padding(BottomDividerPadding)
                    .fillMaxWidth()
                    .height(1.dp)
                    .alpha(0.15f)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }
        item {
            SourceInfoAndSpecs(
                movieDetails = movieDetails,
                fileSizeBytes = fileSizeBytes,
                episode = if (movieDetails.isTV) sourceInfoEpisode else null
            )
        }
        item {
            // 底部返回顶部按钮（居中）
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 44.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                androidx.tv.material3.Button(onClick = {
                    // 平滑滚动到顶部，并将焦点设置到“播放”按钮
                    scope.launch {
                        listState.animateScrollToItem(0)
                        playButtonFocusRequester.requestFocus()
                    }
                }) {
                    androidx.tv.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Outlined.KeyboardArrowUp,
                        contentDescription = "返回顶部"
                    )
                    androidx.tv.material3.Text("返回顶部", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

private val BottomDividerPadding = PaddingValues(vertical = 48.dp)
