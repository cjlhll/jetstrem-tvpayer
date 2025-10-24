package com.google.jetstream.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.jetstream.data.entities.Movie
import com.google.jetstream.data.repositories.ScrapedTvStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ScrapedTvViewModel @Inject constructor(
    store: ScrapedTvStore
) : ViewModel() {
    // 使用Eagerly立即加载，确保首页数据尽快可用
    val shows: StateFlow<List<Movie>> = store.shows
        .map { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

