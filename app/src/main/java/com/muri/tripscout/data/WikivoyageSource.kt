package com.muri.tripscout.data

import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** The community-written "Stay safe" section from Wikivoyage (free, no key). */
class WikivoyageSource(private val http: Http) {

    suspend fun staySafe(place: String): String? {
        val sections = http.getJson(api(place).addQueryParameter("prop", "sections").build())
        val index = findSection(sections, "Stay safe") ?: return null
        val html = http.getJson(
            api(place).addQueryParameter("prop", "text").addQueryParameter("section", index).build(),
        )["parse"]["text"].str() ?: return null
        return html.stripHtml()
            .lines().filterNot { it.equals("Stay safe", ignoreCase = true) || it.startsWith("[edit") }
            .joinToString("\n")
            .truncate(2000)
            .takeIf { it.isNotBlank() }
    }

    private fun api(page: String): HttpUrl.Builder =
        "https://en.wikivoyage.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "parse")
            .addQueryParameter("page", page)
            .addQueryParameter("redirects", "1")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")

    companion object {
        fun findSection(json: JsonElement, title: String): String? =
            json["parse"]["sections"].arr().orEmpty()
                .firstOrNull { it["line"].str().equals(title, ignoreCase = true) }
                ?.get("index").str()
    }
}
