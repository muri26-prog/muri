package com.muri.tripscout.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpException(val code: Int, url: String) : IOException("HTTP $code for $url")

/** Small shared HTTP helper used by every free data source. */
class Http(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun get(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            // Reddit and Wikimedia reject requests without a descriptive User-Agent.
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, url.host)
            response.body?.string().orEmpty()
        }
    }

    suspend fun getJson(url: HttpUrl): JsonElement = Json.parseToJsonElement(get(url))

    companion object {
        const val USER_AGENT = "android:com.muri.tripscout:1.0 (personal trip research app)"
    }
}

// Lenient JSON accessors: the free APIs omit fields freely, so missing data becomes null.
internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray
internal fun JsonElement?.str(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
internal fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.doubleOrNull
internal fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull
internal operator fun JsonElement?.get(key: String): JsonElement? = this.obj()?.get(key)

/** Strips HTML tags and collapses whitespace; good enough for short summaries. */
internal fun String.stripHtml(): String =
    replace(Regex("<(br|/p|/li|/h\\d)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .lines().map { it.trim() }.filter { it.isNotEmpty() }
        .joinToString("\n")

internal fun String.truncate(max: Int): String =
    if (length <= max) this else take(max).substringBeforeLast(' ') + "…"
