package com.google.jetstream.data.subs

import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder

@Serializable
@Keep
private data class AssrtSearchResponse(
    val status: Int,
    val sub: AssrtSearchSub
)

@Serializable
@Keep
private data class AssrtSearchSub(
    val subs: List<AssrtSubItem> = emptyList(),
    val action: String? = null,
    val keyword: String? = null,
    val result: String? = null
)

@Serializable
@Keep
private data class AssrtSubItem(
    val id: Int,
    @SerialName("native_name") val nativeName: String? = null,
    val videoname: String? = null,
    val subtype: String? = null
)

@Serializable
@Keep
private data class AssrtDetailResponse(
    val status: Int,
    val sub: AssrtDetailSub
)

@Serializable
@Keep
private data class AssrtDetailSub(
    val result: String? = null,
    val action: String? = null,
    val subs: List<AssrtDetailItem> = emptyList()
)

@Serializable
@Keep
private data class AssrtDetailItem(
    val id: Int,
    val url: String? = null,
    val filelist: List<AssrtFileItem>? = null
)

@Serializable
@Keep
private data class AssrtFileItem(
    val url: String? = null,
    @SerialName("f") val fileName: String? = null,
    @SerialName("s") val size: String? = null
)

class AssrtApi(
    private val token: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val base = "https://api.assrt.net/v1"

    suspend fun searchOne(keyword: String): Int? = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(keyword, "UTF-8")
            val url = "$base/sub/search?token=${token}&q=${q}"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w("AssrtApi", "search http ${resp.code}")
                    return@withContext null
                }
                val body = resp.body?.string() ?: return@withContext null
                val parsed = json.decodeFromString(AssrtSearchResponse.serializer(), body)
                if (parsed.status != 0) return@withContext null
                val subs = parsed.sub.subs
                val kwLower = keyword.lowercase()
                val hasSubtypeAndNameMatch = subs.firstOrNull { it.subtype?.isNotBlank() == true && (it.videoname?.lowercase()?.contains(kwLower) == true) }
                if (hasSubtypeAndNameMatch != null) {
                    Log.d("AssrtApi", "pick by subtype+videoname contains: id=${hasSubtypeAndNameMatch.id} videoname=${hasSubtypeAndNameMatch.videoname}")
                    return@withContext hasSubtypeAndNameMatch.id
                }
                val hasSubtype = subs.firstOrNull { it.subtype?.isNotBlank() == true }
                if (hasSubtype != null) {
                    Log.d("AssrtApi", "pick by subtype only: id=${hasSubtype.id} videoname=${hasSubtype.videoname}")
                    return@withContext hasSubtype.id
                }
                val fallback = subs.firstOrNull()
                Log.d("AssrtApi", "fallback first id=${fallback?.id} videoname=${fallback?.videoname}")
                return@withContext fallback?.id
            }
        } catch (e: Throwable) {
            Log.w("AssrtApi", "search error", e)
            null
        }
    }

    suspend fun detail(id: Int): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$base/sub/detail?token=${token}&id=${id}"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w("AssrtApi", "detail http ${resp.code}")
                    return@withContext emptyList()
                }
                val body = resp.body?.string() ?: return@withContext emptyList()
                val parsed = json.decodeFromString(AssrtDetailResponse.serializer(), body)
                if (parsed.status != 0) return@withContext emptyList()
                val items = parsed.sub.subs
                val urls = mutableListOf<String>()
                for (it in items) {
                    it.url?.let { u -> urls.add(u) }
                    it.filelist?.forEach { f -> f.url?.let { u -> urls.add(u) } }
                }
                return@withContext urls.map { try { URLDecoder.decode(it, "UTF-8") } catch (_: Throwable) { it } }
            }
        } catch (e: Throwable) {
            Log.w("AssrtApi", "detail error", e)
            emptyList()
        }
    }
}
