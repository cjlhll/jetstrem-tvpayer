package com.google.jetstream.data.repositories

import com.google.jetstream.data.entities.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrapedTvStore @Inject constructor() {
    private val _shows = MutableStateFlow<List<Movie>>(emptyList())
    val shows: StateFlow<List<Movie>> = _shows

    fun setShows(list: List<Movie>) {
        _shows.value = list
    }

    fun clear() {
        _shows.value = emptyList()
    }
}

