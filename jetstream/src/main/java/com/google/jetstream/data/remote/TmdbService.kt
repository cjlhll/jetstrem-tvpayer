package com.google.jetstream.data.remote

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 轻量 TMDB API 客户端，仅包含电影详情、演职员、相似影片和发行分级。
 */
object TmdbService {
    private const val TAG = "TmdbService"
    private const val BASE = "https://api.themoviedb.org"
    // 与 DashboardViewModel 中一致（已由用户提供）
    private const val BEARER = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJlNWVhMWZmMjJhYzUzOTMzNDAwYmMwMjUxZmZmNTk0MyIsIm5iZiI6MTc1NTI1Nzk4OC4xODU5OTk5LCJzdWIiOiI2ODlmMWM4NGRhOWIyNDRhMzkxYTQ1NzMiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.GRMPv8PEpwrEm-6zCPmSr4uFH5JFkJPMQq9P44IvcvM"

    private val json by lazy { Json { ignoreUnknownKeys = true } }

    const val IMAGE_BASE_W500 = "https://image.tmdb.org/t/p/w500"
    const val IMAGE_BASE_W780 = "https://image.tmdb.org/t/p/w780"

    @Serializable
    data class Genre(
        val id: Int,
        val name: String
    )

    @Serializable
    data class MovieDetailsResponse(
        val id: Int,
        val title: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        val runtime: Int? = null,
        val status: String? = null,
        @SerialName("original_language") val originalLanguage: String? = null,
        val budget: Long? = null,
        val revenue: Long? = null,
        @SerialName("vote_average") val voteAverage: Float? = null,
        val genres: List<Genre> = emptyList()
    )

    @Serializable
    data class CastItem(
        val id: Int,
        val name: String,
        val character: String? = null,
        @SerialName("profile_path") val profilePath: String? = null,
    )

    @Serializable
    data class CrewItem(
        val id: Int,
        val name: String,
        val job: String? = null,
        @SerialName("profile_path") val profilePath: String? = null,
    )

    @Serializable
    data class CreditsResponse(
        val cast: List<CastItem> = emptyList(),
        val crew: List<CrewItem> = emptyList()
    )

    @Serializable
    data class SimilarItem(
        val id: Int,
        val title: String? = null,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("vote_average") val voteAverage: Float? = null,
    )

    @Serializable
    data class PagedResponse<T>(
        val page: Int = 1,
        val results: List<T> = emptyList()
    )

    @Serializable
    data class ReleaseDatesResponse(
        val results: List<CountryRelease> = emptyList()
    )

    @Serializable
    data class CountryRelease(
        @SerialName("iso_3166_1") val country: String,
        @SerialName("release_dates") val releaseDates: List<ReleaseDateItem> = emptyList()
    )

    @Serializable
    data class ReleaseDateItem(
        val certification: String? = null
    )

    suspend fun getMovieDetails(id: String, language: String = "zh-CN"): MovieDetailsResponse? =
        get("/3/movie/${id}", mapOf("language" to language))?.let {
            try { json.decodeFromString(MovieDetailsResponse.serializer(), it) } catch (e: Exception) {
                Log.w(TAG, "decode details failed: ${e.message}"); null
            }
        }

    suspend fun getCredits(id: String, language: String = "zh-CN"): CreditsResponse? =
        get("/3/movie/${id}/credits", mapOf("language" to language))?.let {
            try { json.decodeFromString(CreditsResponse.serializer(), it) } catch (e: Exception) {
                Log.w(TAG, "decode credits failed: ${e.message}"); null
            }
        }

    suspend fun getSimilar(id: String, language: String = "zh-CN"): PagedResponse<SimilarItem>? =
        get("/3/movie/${id}/similar", mapOf("language" to language, "page" to "1"))?.let {
            try { json.decodeFromString(PagedResponse.serializer(SimilarItem.serializer()), it) } catch (e: Exception) {
                Log.w(TAG, "decode similar failed: ${e.message}"); null
            }
        }

    suspend fun getReleaseCertification(id: String): String? =
        get("/3/movie/${id}/release_dates")?.let {
            try {
                val resp = json.decodeFromString(ReleaseDatesResponse.serializer(), it)
                // 优先 CN，其次 US
                val cn = resp.results.firstOrNull { it.country == "CN" }
                val us = resp.results.firstOrNull { it.country == "US" }
                cn?.releaseDates?.firstOrNull { !it.certification.isNullOrBlank() }?.certification
                    ?: us?.releaseDates?.firstOrNull { !it.certification.isNullOrBlank() }?.certification
            } catch (e: Exception) {
                Log.w(TAG, "decode release_dates failed: ${e.message}"); null
            }
        }

    private suspend fun get(path: String, query: Map<String, String> = emptyMap()): String? = withContext(Dispatchers.IO) {
        try {
            val params = if (query.isEmpty()) "" else buildString {
                append("?")
                append(query.entries.joinToString("&") { (k, v) ->
                    k + "=" + URLEncoder.encode(v, "UTF-8")
                })
            }
            val url = URL("$BASE$path$params")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $BEARER")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 10000
            }
            conn.inputStream.bufferedReader().use { r -> r.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "GET $path failed: ${e.message}")
            null
        }
    }
}

