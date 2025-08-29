package com.google.jetstream.data.repositories

import com.google.jetstream.data.entities.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrapedMoviesStore @Inject constructor() {
    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies: StateFlow<List<Movie>> = _movies

    fun setMovies(list: List<Movie>) {
        _movies.value = list
    }

    fun clear() {
        _movies.value = emptyList()
    }
}
