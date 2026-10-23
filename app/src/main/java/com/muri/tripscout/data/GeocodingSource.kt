package com.muri.tripscout.data

import com.muri.tripscout.model.GeoPlace
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Open-Meteo geocoding (free, no key): turns "Kotor, Montenegro" into coordinates + country. */
class GeocodingSource(private val http: Http) {

    /** Best single match, used for trips saved without a picked place. */
    suspend fun find(query: String): GeoPlace? {
        val parts = query.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return pickBest(lookup(parts.firstOrNull() ?: return null), hints = parts.drop(1))
    }

    /**
     * Suggestions while typing. "Paris, Texas" ranks places matching the extra words first;
     * otherwise the API's own order (largest places first) is kept.
     */
    suspend fun suggest(query: String): List<GeoPlace> {
        val parts = query.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val name = parts.firstOrNull()?.takeIf { it.length >= 2 } ?: return emptyList()
        val hints = parts.drop(1)
        return lookup(name).sortedByDescending { matchesHints(it, hints) }
    }

    private suspend fun lookup(name: String): List<GeoPlace> {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", name)
            .addQueryParameter("count", "10")
            .addQueryParameter("language", "en")
            .addQueryParameter("format", "json")
            .build()
        return parse(http.getJson(url))
    }

    companion object {
        fun parse(json: JsonElement): List<GeoPlace> =
            json["results"].arr().orEmpty().mapNotNull { r ->
                GeoPlace(
                    name = r["name"].str() ?: return@mapNotNull null,
                    latitude = r["latitude"].dbl() ?: return@mapNotNull null,
                    longitude = r["longitude"].dbl() ?: return@mapNotNull null,
                    countryCode = r["country_code"].str().orEmpty(),
                    country = r["country"].str().orEmpty(),
                    region = r["admin1"].str(),
                    timezone = r["timezone"].str(),
                    population = r["population"].dbl()?.toLong(),
                )
            }

        /**
         * The API only matches the place name, so "Paris, Texas" would return Paris, France first.
         * Extra comma-separated words are matched against country, country code and region.
         */
        fun pickBest(candidates: List<GeoPlace>, hints: List<String>): GeoPlace? {
            if (hints.isEmpty()) return candidates.firstOrNull()
            return candidates.firstOrNull { matchesHints(it, hints) } ?: candidates.firstOrNull()
        }

        fun matchesHints(place: GeoPlace, hints: List<String>): Boolean = hints.all { hint ->
            listOfNotNull(place.country, place.countryCode, place.region)
                .any { it.equals(hint, ignoreCase = true) || it.contains(hint, ignoreCase = true) }
        }
    }
}
