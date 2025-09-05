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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.jetstream.data.repositories.ScrapedMoviesStore
import com.google.jetstream.data.repositories.ScrapedTvStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MovieTypeListScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    scrapedMoviesStore: ScrapedMoviesStore,
    scrapedTvStore: ScrapedTvStore
) : ViewModel() {

    val uiState = savedStateHandle.getStateFlow<String?>(
        MovieTypeListScreen.MovieTypeKey,
        null
    ).flatMapLatest { movieType ->
        when (movieType) {
            "movies" -> {
                scrapedMoviesStore.movies.map { movieList ->
                    MovieTypeListScreenUiState.Ready(
                        title = "电影",
                        movies = movieList
                    )
                }
            }
            "shows" -> {
                scrapedTvStore.shows.map { movieList ->
                    MovieTypeListScreenUiState.Ready(
                        title = "电视剧",
                        movies = movieList
                    )
                }
            }
            else -> {
                kotlinx.coroutines.flow.flowOf(MovieTypeListScreenUiState.Error)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MovieTypeListScreenUiState.Loading
    )
}
