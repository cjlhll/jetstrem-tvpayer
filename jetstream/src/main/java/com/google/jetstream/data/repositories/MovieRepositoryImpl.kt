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
import com.google.jetstream.data.entities.MovieReviewsAndRatings
import com.google.jetstream.data.entities.ThumbnailType
import com.google.jetstream.data.remote.TmdbService
import com.google.jetstream.data.util.StringConstants
import com.google.jetstream.data.util.StringConstants.Movie.Reviewer.DefaultCount
import com.google.jetstream.data.util.StringConstants.Movie.Reviewer.DefaultRating
import com.google.jetstream.data.util.StringConstants.Movie.Reviewer.FreshTomatoes
import com.google.jetstream.data.util.StringConstants.Movie.Reviewer.ReviewerName
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
        // 先从本地列表拿到基础封面与视频地址（WebDAV），其余信息用 TMDB 填充
        val movieList = movieDataSource.getMovieList()
        val base = movieList.find { it.id == movieId } ?: movieList.first()

        // TMDB 详情
        val details = TmdbService.getMovieDetails(movieId)
        // 发行分级（PG）
        val certification = TmdbService.getReleaseCertification(movieId) ?: "PG-13"
        // credits
        val credits = TmdbService.getCredits(movieId)
        // 相似影片
        val similar = TmdbService.getSimilar(movieId)?.results ?: emptyList()

        // 类型转中文：TMDB 已根据 language=zh-CN 返回中文
        val categoriesZh = details?.genres?.map { it.name } ?: emptyList()

        // 演员：只取前 10
        val castAndCrew: List<MovieCast> = (credits?.cast ?: emptyList()).take(10).map {
            MovieCast(
                id = it.id.toString(),
                characterName = it.character ?: "",
                realName = it.name,
                avatarUrl = it.profilePath?.let { p -> TmdbService.IMAGE_BASE_W500 + p } ?: ""
            )
        }

        // 导演/编剧/音乐（从 crew 里挑）
        val director = credits?.crew?.firstOrNull { it.job.equals("Director", true) }?.name ?: ""
        val screenplay = credits?.crew?.firstOrNull { it.job?.contains("Writer", true) == true || it.job?.contains("Screenplay", true) == true }?.name ?: ""
        val music = credits?.crew?.firstOrNull { it.job?.contains("Music", true) == true || it.job?.contains("Composer", true) == true }?.name ?: ""

        // 相似影片映射到现有 Movie 实体
        val similarMovies: MovieList = similar.take(10).map {
            Movie(
                id = it.id.toString(),
                videoUri = "", // 这里只展示封面与信息
                subtitleUri = null,
                posterUri = it.posterPath?.let { p -> TmdbService.IMAGE_BASE_W500 + p } ?: base.posterUri,
                name = it.title ?: it.name ?: "",
                description = it.overview ?: "",
                releaseDate = it.releaseDate,
                rating = it.voteAverage
            )
        }

        fun minutesToDuration(mins: Int?): String = if (mins == null || mins <= 0) "" else "${mins / 60}h ${mins % 60}m"
        fun money(v: Long?): String = if (v == null || v <= 0) "-" else "$" + String.format("%,d", v)

        val ratingText = details?.voteAverage?.let { String.format("⭐ %.1f", it) } ?: "⭐ --"

        return MovieDetails(
            id = base.id,
            videoUri = base.videoUri,
            subtitleUri = base.subtitleUri,
            posterUri = details?.posterPath?.let { TmdbService.IMAGE_BASE_W780 + it } ?: base.posterUri,
            backdropUri = details?.backdropPath?.let { TmdbService.IMAGE_BASE_W780 + it }
                ?: details?.posterPath?.let { TmdbService.IMAGE_BASE_W780 + it } ?: base.posterUri,
            name = details?.title ?: base.name,
            description = details?.overview ?: base.description,
            pgRating = ratingText,
            releaseDate = details?.releaseDate ?: (base.releaseDate ?: ""),
            categories = if (categoriesZh.isNotEmpty()) categoriesZh else listOf("电影"),
            duration = minutesToDuration(details?.runtime),
            director = director,
            screenplay = screenplay,
            music = music,
            castAndCrew = castAndCrew,
            status = when (details?.status) {
                "Released" -> "已上映"
                "Post Production" -> "后期制作"
                "In Production" -> "制作中"
                "Planned" -> "计划中"
                "Canceled" -> "已取消"
                else -> details?.status ?: ""
            },
            originalLanguage = languageCodeToChinese(details?.originalLanguage ?: ""),
            budget = money(details?.budget),
            revenue = money(details?.revenue),
            similarMovies = emptyList(),
            reviewsAndRatings = emptyList(),
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
