package com.muri.tripscout.data

import com.muri.tripscout.model.Article
import com.muri.tripscout.model.GeoPlace
import com.muri.tripscout.model.SourceKind
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.time.ZoneOffset

/**
 * Recent Reddit threads about the place, via Reddit's public JSON search.
 * Reddit rate-limits or blocks anonymous clients at times; a failure here is reported, not fatal.
 */
class RedditSource(private val http: Http) {

    suspend fun forPlace(place: GeoPlace): List<Article> {
        val queries = listOf("\"${place.name}\" (travel OR visiting OR safe OR tourist)", "\"${place.name}\"")
        return queries.flatMap { q ->
            val url = "https://www.reddit.com/search.json".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("sort", "new")
                .addQueryParameter("t", "month")
                .addQueryParameter("limit", "10")
                .build()
            parse(http.getJson(url))
        }.distinctBy { it.url }.take(12)
    }

    companion object {
        fun parse(json: JsonElement): List<Article> =
            json["data"]["children"].arr().orEmpty().mapNotNull { child ->
                val post = child["data"]
                Article(
                    title = post["title"].str() ?: return@mapNotNull null,
                    url = "https://www.reddit.com" + (post["permalink"].str() ?: return@mapNotNull null),
                    source = post["subreddit_name_prefixed"].str(),
                    published = post["created_utc"].dbl()?.let {
                        Instant.ofEpochSecond(it.toLong()).atOffset(ZoneOffset.UTC).toLocalDate().toString()
                    },
                    kind = SourceKind.REDDIT,
                )
            }
    }
}
