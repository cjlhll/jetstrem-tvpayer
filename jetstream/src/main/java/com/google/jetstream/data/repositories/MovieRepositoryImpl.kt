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

package com.google.jetstream.data.repositories

import com.google.jetstream.data.entities.Movie
import com.google.jetstream.data.entities.MovieCategoryDetails
import com.google.jetstream.data.entities.MovieCast
import com.google.jetstream.data.entities.MovieDetails
import com.google.jetstream.data.entities.MovieList
import com.google.jetstream.data.entities.TvSeason
import com.google.jetstream.data.entities.MovieReviewsAndRatings
import com.google.jetstream.data.entities.ThumbnailType
import com.google.jetstream.data.remote.TmdbService
import com.google.jetstream.data.util.StringConstants
import com.google.jetstream.data.util.StringConstants.Movie.Reviewer.DefaultCount
import com.google.jetstream.data.util.StringConstants.Movie.Reviewer.DefaultRating
import com.google.jetstream.data.util.StringConstants.Movie.Reviewer.FreshTomatoes
import com.google.jetstream.data.util.StringConstants.Movie.Reviewer.ReviewerName
import com.google.jetstream.data.database.dao.ScrapedItemDao
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val movieDataSource: MovieDataSource,
    private val tvDataSource: TvDataSource,
    private val movieCastDataSource: MovieCastDataSource,
    private val movieCategoryDataSource: MovieCategoryDataSource,
    private val scrapedItemDao: ScrapedItemDao,
) : MovieRepository {

    override fun getFeaturedMovies() = flow {
        val list = movieDataSource.getFeaturedMovieList()
        emit(list)
    }

    override fun getTrendingMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getTrendingMovieList()
        emit(list)
    }

    override fun getTop10Movies(): Flow<MovieList> = flow {
        val list = movieDataSource.getTop10MovieList()
        emit(list)
    }

    override fun getNowPlayingMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getNowPlayingMovieList()
        emit(list)
    }

    override fun getMovieCategories() = flow {
        val list = movieCategoryDataSource.getMovieCategoryList()
        emit(list)
    }

    override suspend fun getMovieCategoryDetails(categoryId: String): MovieCategoryDetails {
        val categoryList = movieCategoryDataSource.getMovieCategoryList()
        val category = categoryList.find { categoryId == it.id } ?: categoryList.first()

        val movieList = movieDataSource.getMovieList().shuffled().subList(0, 20)

        return MovieCategoryDetails(
            id = category.id,
            name = category.name,
            movies = movieList
        )
    }

    override suspend fun getMovieDetails(movieId: String): MovieDetails {
        // 优先从数据库获取已刮削的完整数据
        val scrapedItem = scrapedItemDao.getById(movieId)
        
        if (scrapedItem != null) {
            // 从数据库中获取完整数据
            return createMovieDetailsFromScrapedItem(scrapedItem)
        } else {
            // 如果数据库中没有，回退到本地数据源（兼容性处理）
            val movieList = movieDataSource.getMovieList()
            val base = movieList.find { it.id == movieId } ?: movieList.first()
            
            return MovieDetails(
                id = base.id,
                videoUri = base.videoUri,
                subtitleUri = base.subtitleUri,
                posterUri = base.posterUri,
                backdropUri = base.posterUri, // 使用poster作为backdrop的后备
                name = base.name,
                description = base.description,
                pgRating = base.rating?.let { String.format("⭐ %.1f", it) } ?: "⭐ --",
                releaseDate = base.releaseDate ?: "",
                categories = listOf("电影"),
                duration = "",
                director = "",
                screenplay = "",
                music = "",
                castAndCrew = emptyList(),
            )
        }
    }

    /**
     * 从ScrapedItemEntity创建MovieDetails对象
     */
    private fun createMovieDetailsFromScrapedItem(scrapedItem: com.google.jetstream.data.database.entities.ScrapedItemEntity): MovieDetails {
        val json = Json { ignoreUnknownKeys = true }
        
        // 解析JSON字段
        val categories = scrapedItem.categories?.let {
            try { json.decodeFromString<List<String>>(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()
        
        val castAndCrew = scrapedItem.castAndCrew?.let {
            try { json.decodeFromString<List<MovieCast>>(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()
        
        val availableSeasons = scrapedItem.availableSeasons?.let {
            try { json.decodeFromString<List<TvSeason>>(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()
        
        // 根据类型设置默认类别
        val defaultCategory = when (scrapedItem.type) {
            "tv" -> "电视剧"
            "movie" -> "电影"
            else -> "影片"
        }
        
        return MovieDetails(
            id = scrapedItem.id,
            videoUri = scrapedItem.sourcePath ?: "",
            subtitleUri = null, // 暂时不支持字幕
            posterUri = scrapedItem.posterUri,
            backdropUri = scrapedItem.backdropUri ?: scrapedItem.posterUri,
            name = scrapedItem.title,
            description = scrapedItem.description,
            pgRating = scrapedItem.pgRating ?: "⭐ --",
            releaseDate = scrapedItem.releaseDate ?: "",
            categories = if (categories.isNotEmpty()) categories else listOf(defaultCategory),
            duration = scrapedItem.duration ?: "",
            director = scrapedItem.director ?: "",
            screenplay = scrapedItem.screenplay ?: "",
            music = scrapedItem.music ?: "",
            castAndCrew = castAndCrew,
            availableSeasons = availableSeasons,
            isTV = scrapedItem.type == "tv"
        )
    }
    
    private fun languageCodeToChinese(code: String): String = when (code.lowercase()) {
        "en" -> "英语"
        "zh" -> "中文"
        "ja" -> "日语"
        "ko" -> "韩语"
        "fr" -> "法语"
        "de" -> "德语"
        "es" -> "西班牙语"
        "it" -> "意大利语"
        "ru" -> "俄语"
        "hi" -> "印地语"
        else -> code
    }

    override suspend fun searchMovies(query: String): MovieList {
        return movieDataSource.getMovieList().filter {
            it.name.contains(other = query, ignoreCase = true)
        }
    }

    override fun getMoviesWithLongThumbnail() = flow {
        val list = movieDataSource.getMovieList(ThumbnailType.Long)
        emit(list)
    }

    override fun getMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getMovieList()
        emit(list)
    }

    override fun getPopularFilmsThisWeek(): Flow<MovieList> = flow {
        val list = movieDataSource.getPopularFilmThisWeek()
        emit(list)
    }

    override fun getTVShows(): Flow<MovieList> = flow {
        val list = tvDataSource.getTvShowList()
        emit(list)
    }

    override fun getBingeWatchDramas(): Flow<MovieList> = flow {
        val list = tvDataSource.getBingeWatchDramaList()
        emit(list)
    }

    override fun getFavouriteMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getFavoriteMovieList()
        emit(list)
    }
}
