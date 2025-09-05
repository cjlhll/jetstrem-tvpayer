package com.google.jetstream.data.remote

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
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
    data class TvDetailsResponse(
        val id: Int,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("last_air_date") val lastAirDate: String? = null,
        @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
        @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
        @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
        val status: String? = null,
        @SerialName("original_language") val originalLanguage: String? = null,
        @SerialName("vote_average") val voteAverage: Float? = null,
        val genres: List<Genre> = emptyList(),
        @SerialName("created_by") val createdBy: List<Creator> = emptyList()
    )

    @Serializable
    data class Creator(
        val id: Int,
        val name: String
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

    @Serializable
    data class ContentRatingsResponse(
        val results: List<ContentRating> = emptyList()
    )

    @Serializable
    data class ContentRating(
        @SerialName("iso_3166_1") val country: String,
        val rating: String? = null
    )

    // 季详情响应
    @Serializable
    data class SeasonDetailsResponse(
        val id: Int,
        @SerialName("season_number") val seasonNumber: Int,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("air_date") val airDate: String? = null,
        val episodes: List<EpisodeItem> = emptyList()
    )

    // 剧集信息
    @Serializable
    data class EpisodeItem(
        val id: Int,
        @SerialName("episode_number") val episodeNumber: Int,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("still_path") val stillPath: String? = null,
        @SerialName("air_date") val airDate: String? = null,
        @SerialName("vote_average") val voteAverage: Float? = null,
        val runtime: Int? = null
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

    // 电视剧相关API方法
    suspend fun getTvDetails(id: String, language: String = "zh-CN"): TvDetailsResponse? =
        get("/3/tv/${id}", mapOf("language" to language))?.let {
            try { json.decodeFromString(TvDetailsResponse.serializer(), it) } catch (e: Exception) {
                Log.w(TAG, "decode tv details failed: ${e.message}"); null
            }
        }

    suspend fun getTvCredits(id: String, language: String = "zh-CN"): CreditsResponse? =
        get("/3/tv/${id}/credits", mapOf("language" to language))?.let {
            try { json.decodeFromString(CreditsResponse.serializer(), it) } catch (e: Exception) {
                Log.w(TAG, "decode tv credits failed: ${e.message}"); null
            }
        }

    suspend fun getTvSimilar(id: String, language: String = "zh-CN"): PagedResponse<SimilarItem>? =
        get("/3/tv/${id}/similar", mapOf("language" to language, "page" to "1"))?.let {
            try { json.decodeFromString(PagedResponse.serializer(SimilarItem.serializer()), it) } catch (e: Exception) {
                Log.w(TAG, "decode tv similar failed: ${e.message}"); null
            }
        }

    suspend fun getTvContentRating(id: String): String? =
        get("/3/tv/${id}/content_ratings")?.let {
            try {
                val resp = json.decodeFromString(ContentRatingsResponse.serializer(), it)
                // 优先 CN，其次 US
                val cn = resp.results.firstOrNull { it.country == "CN" }
                val us = resp.results.firstOrNull { it.country == "US" }
                cn?.rating ?: us?.rating
            } catch (e: Exception) {
                Log.w(TAG, "decode content_ratings failed: ${e.message}"); null
            }
        }

    // 获取电视剧季详情和剧集列表
    suspend fun getTvSeasonDetails(tvId: String, seasonNumber: Int, language: String = "zh-CN"): SeasonDetailsResponse? =
        get("/3/tv/${tvId}/season/${seasonNumber}", mapOf("language" to language))?.let {
            try { json.decodeFromString(SeasonDetailsResponse.serializer(), it) } catch (e: Exception) {
                Log.w(TAG, "decode season details failed: ${e.message}"); null
            }
        }

    private fun createTrustAllTrustManager(): Array<TrustManager> {
        return arrayOf(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
    }

    private suspend fun get(path: String, query: Map<String, String> = emptyMap(), retryCount: Int = 3): String? = withContext(Dispatchers.IO) {
        repeat(retryCount) { attempt ->
            try {
                val params = if (query.isEmpty()) "" else buildString {
                    append("?")
                    append(query.entries.joinToString("&") { (k, v) ->
                        k + "=" + URLEncoder.encode(v, "UTF-8")
                    })
                }
                val url = URL("$BASE$path$params")
                val conn = url.openConnection() as HttpURLConnection
                
                // 如果是HTTPS连接，配置SSL
                if (conn is HttpsURLConnection) {
                    try {
                        val sslContext = SSLContext.getInstance("TLS")
                        sslContext.init(null, createTrustAllTrustManager(), java.security.SecureRandom())
                        conn.sslSocketFactory = sslContext.socketFactory
                        conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to configure SSL, using default: ${e.message}")
                    }
                }
                
                conn.apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $BEARER")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "JetStream-Android/1.0")
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                
                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    return@withContext conn.inputStream.bufferedReader().use { r -> r.readText() }
                } else if (responseCode == 429) {
                    // Rate limited, wait and retry
                    Log.w(TAG, "Rate limited, retrying in ${(attempt + 1) * 1000}ms")
                    kotlinx.coroutines.delay((attempt + 1) * 1000L)
                } else {
                    Log.w(TAG, "HTTP $responseCode for $path (attempt ${attempt + 1})")
                    conn.errorStream?.bufferedReader()?.use { r -> 
                        Log.w(TAG, "Error response: ${r.readText()}")
                    }
                    if (responseCode in 400..499 && responseCode != 429) {
                        // Client error, no point retrying
                        return@withContext null
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "GET $path failed (attempt ${attempt + 1}): ${e.message}")
                if (attempt == retryCount - 1) {
                    e.printStackTrace()
                    return@withContext null
                }
                // Wait before retry
                kotlinx.coroutines.delay((attempt + 1) * 1000L)
            }
        }
        null
    }
}

