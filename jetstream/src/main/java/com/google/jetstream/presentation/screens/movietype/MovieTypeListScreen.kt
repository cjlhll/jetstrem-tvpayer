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

package com.google.jetstream.presentation.screens.movietype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.google.jetstream.data.entities.Movie
import com.google.jetstream.presentation.common.Error
import com.google.jetstream.presentation.common.ItemDirection
import com.google.jetstream.presentation.common.Loading
import com.google.jetstream.presentation.common.MovieCard
import com.google.jetstream.presentation.common.PosterImage
import com.google.jetstream.presentation.screens.dashboard.rememberChildPadding
import com.google.jetstream.presentation.theme.JetStreamBottomListPadding
import com.google.jetstream.presentation.utils.focusOnInitialVisibility

object MovieTypeListScreen {
    const val MovieTypeKey = "movieType"
}

@Composable
fun MovieTypeListScreen(
    onBackPressed: () -> Unit,
    onMovieSelected: (Movie) -> Unit,
    movieTypeListScreenViewModel: MovieTypeListScreenViewModel = hiltViewModel()
) {
    val uiState by movieTypeListScreenViewModel.uiState.collectAsStateWithLifecycle()

    when (val s = uiState) {
        MovieTypeListScreenUiState.Loading -> {
            Loading(modifier = Modifier.fillMaxSize())
        }

        MovieTypeListScreenUiState.Error -> {
            Error(modifier = Modifier.fillMaxSize())
        }

        is MovieTypeListScreenUiState.Ready -> {
            MovieTypeDetails(
                title = s.title,
                movies = s.movies,
                onBackPressed = onBackPressed,
                onMovieSelected = onMovieSelected
            )
        }
    }
}

@Composable
private fun MovieTypeDetails(
    title: String,
    movies: List<Movie>,
    onBackPressed: () -> Unit,
    onMovieSelected: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    val childPadding = rememberChildPadding()
    val isFirstItemVisible = remember { mutableStateOf(true) }

    BackHandler(onBack = onBackPressed)

    Column(
        modifier = modifier,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(
                start = 24.dp,
                top = childPadding.top.times(2f),
                bottom = 16.dp
            )
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 32.dp,
                bottom = JetStreamBottomListPadding
            )
        ) {
            itemsIndexed(
                movies,
                key = { _, movie ->
                    movie.id
                }
            ) { index, movie ->
                MovieCard(
                    onClick = { onMovieSelected(movie) },
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(ItemDirection.Vertical.aspectRatio)
                        .padding(8.dp)
                        .then(
                            if (index == 0)
                                Modifier.focusOnInitialVisibility(isFirstItemVisible)
                            else Modifier
                        ),
                ) {
                    PosterImage(movie = movie, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

sealed interface MovieTypeListScreenUiState {
    data object Loading : MovieTypeListScreenUiState
    data object Error : MovieTypeListScreenUiState
    data class Ready(
        val title: String,
        val movies: List<Movie>
    ) : MovieTypeListScreenUiState
}
