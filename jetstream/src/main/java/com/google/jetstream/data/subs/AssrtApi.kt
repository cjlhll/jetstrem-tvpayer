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
    val subtype: String? = null,
    val lang: AssrtLang? = null
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
    val filelist: List<AssrtFileItem>? = null,
    val lang: AssrtLang? = null
)

@Serializable
@Keep
private data class AssrtFileItem(
    val url: String? = null,
    @SerialName("f") val fileName: String? = null,
    @SerialName("s") val size: String? = null
)

@Serializable
@Keep
private data class AssrtLang(
    val desc: String? = null,
    val langlist: AssrtLangList? = null
)

@Serializable
@Keep
private data class AssrtLangList(
    val langdou: Boolean? = null,
    val langeng: Boolean? = null,
    val langchs: Boolean? = null,
    val langcht: Boolean? = null
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

    @Serializable
    @Keep
    data class AssrtSelected(val id: Int)

    suspend fun searchBest(keyword: String): AssrtSelected? = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(keyword, "UTF-8")
            val url = "$base/sub/search?token=${token}&q=${q}"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val parsed = json.decodeFromString(AssrtSearchResponse.serializer(), body)
                if (parsed.status != 0) return@withContext null
                val subs = parsed.sub.subs
                val kwLower = keyword.lowercase()
                fun containsKw(v: String?) = v?.lowercase()?.contains(kwLower) == true

                // 选择策略只负责挑选候选条目，不再产出语言标签
                val primary = subs.filter { it.subtype?.isNotBlank() == true && containsKw(it.videoname) }
                val pool = if (primary.isNotEmpty()) primary else subs.filter { it.subtype?.isNotBlank() == true }
                val best = pool.firstOrNull() ?: subs.firstOrNull() ?: return@withContext null
                Log.d("AssrtApi", "searchBest pick id=${best.id} videoname=${best.videoname}")
                return@withContext AssrtSelected(best.id)
            }
        } catch (e: Throwable) {
            Log.w("AssrtApi", "searchBest error", e)
            null
        }
    }

    @Serializable
    @Keep
    data class DetailResult(val urls: List<String>, val labels: List<String>)

    suspend fun detail(id: Int): DetailResult = withContext(Dispatchers.IO) {
        try {
            val url = "$base/sub/detail?token=${token}&id=${id}"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w("AssrtApi", "detail http ${resp.code}")
                    return@withContext DetailResult(emptyList(), emptyList())
                }
                val body = resp.body?.string() ?: return@withContext DetailResult(emptyList(), emptyList())
                val parsed = json.decodeFromString(AssrtDetailResponse.serializer(), body)
                if (parsed.status != 0) return@withContext DetailResult(emptyList(), emptyList())
                val items = parsed.sub.subs

                val urls = mutableListOf<String>()
                for (it in items) {
                    it.url?.let { u -> urls.add(u) }
                    it.filelist?.forEach { f -> f.url?.let { u -> urls.add(u) } }
                }
                val decodedUrls = urls.map { try { URLDecoder.decode(it, "UTF-8") } catch (_: Throwable) { it } }

                // Prefer labels from first item's langlist if present
                val fromLangList = items.firstOrNull()?.lang?.langlist
                val labels = if (fromLangList != null) {
                    val res = mutableListOf<String>()
                    if (fromLangList.langdou == true) res.add("双语")
                    if (fromLangList.langchs == true) res.add("简体中文")
                    if (fromLangList.langcht == true) res.add("繁体中文")
                    if (fromLangList.langeng == true) res.add("英语")
                    res
                } else {
                    // Fallback: infer from filenames in filelist
                    val set = linkedSetOf<String>()
                    items.firstOrNull()?.filelist?.forEach { f ->
                        val name = (f.fileName ?: "").lowercase()
                        if (name.contains("chs&eng") || name.contains("eng&chs") || name.contains("双语")) set.add("双语")
                        if (name.contains("chs")) set.add("简体中文")
                        if (name.contains("cht")) set.add("繁体中文")
                        if (name.contains("eng")) set.add("英语")
                    }
                    set.toList()
                }

                return@withContext DetailResult(decodedUrls, labels)
            }
        } catch (e: Throwable) {
            Log.w("AssrtApi", "detail error", e)
            DetailResult(emptyList(), emptyList())
        }
    }
}
