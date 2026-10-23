package com.muri.tripscout.data

import com.muri.tripscout.model.GeoPlace
import com.muri.tripscout.model.PlaceInfo
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.math.cos
import kotlin.math.sqrt

/** A photo and short introduction for the place, from the Wikipedia page summary API (free, no key). */
class WikipediaSource(private val http: Http) {

    suspend fun summary(place: GeoPlace): PlaceInfo? {
        // "Paris, Texas" is its own article; plain "Paris" is the French capital.
        val titles = listOfNotNull(place.region?.let { "${place.name}, $it" }, "${place.name}, ${place.country}", place.name)
        for (title in titles.distinct()) {
            val url = "https://en.wikipedia.org/api/rest_v1/page/summary/".toHttpUrl().newBuilder()
                .addPathSegment(title.replace(' ', '_'))
                .build()
            val json = runCatching { http.getJson(url) }.getOrNull() ?: continue
            parse(json, place)?.let { return it }
        }
        return null
    }

    companion object {
        private const val MAX_DISTANCE_KM = 40.0

        /** Accepts the article only if it is about this place: not a disambiguation page, and located nearby. */
        fun parse(json: JsonElement, place: GeoPlace): PlaceInfo? {
            if (json["type"].str() == "disambiguation") return null
            val lat = json["coordinates"]["lat"].dbl()
            val lon = json["coordinates"]["lon"].dbl()
            if (lat == null || lon == null || distanceKm(lat, lon, place.latitude, place.longitude) > MAX_DISTANCE_KM) return null
            return PlaceInfo(
                summary = json["extract"].str()?.takeIf { it.isNotBlank() } ?: return null,
                imageUrl = json["originalimage"]["source"].str() ?: json["thumbnail"]["source"].str(),
                pageUrl = json["content_urls"]["mobile"]["page"].str()
                    ?: "https://en.wikipedia.org/wiki/${json["title"].str().orEmpty().replace(' ', '_')}",
            )
        }

        /** Equirectangular approximation; accurate enough at city scale. */
        fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val x = Math.toRadians(lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
            val y = Math.toRadians(lat2 - lat1)
            return sqrt(x * x + y * y) * 6371.0
        }
    }
}
