package com.muri.tripscout.data

import com.muri.tripscout.model.Event
import com.muri.tripscout.model.GeoPlace
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.LocalDate

/**
 * Real event listings (concerts, sports, theatre, festivals) with photos and ticket links from the
 * Ticketmaster Discovery API. Needs a free key from developer.ticketmaster.com.
 * Coverage is strongest in North America and Europe.
 */
class TicketmasterSource(private val http: Http, private val apiKey: String) {

    suspend fun forPlace(place: GeoPlace, start: LocalDate, end: LocalDate): List<Event> {
        val url = "https://app.ticketmaster.com/discovery/v2/events.json".toHttpUrl().newBuilder()
            .addQueryParameter("apikey", apiKey)
            .addQueryParameter("latlong", "${place.latitude},${place.longitude}")
            .addQueryParameter("radius", "25")
            .addQueryParameter("unit", "km")
            // Padded by a day on each side: the API filters in UTC, events use local time.
            .addQueryParameter("startDateTime", "${start.minusDays(1)}T00:00:00Z")
            .addQueryParameter("endDateTime", "${end.plusDays(1)}T23:59:59Z")
            .addQueryParameter("sort", "date,asc")
            .addQueryParameter("size", "100")
            .build()
        val wanted = start..end
        return parse(http.getJson(url), place.name).filter { it.date == null || it.date in wanted }
    }

    companion object {
        fun parse(json: JsonElement, city: String): List<Event> =
            json["_embedded"]["events"].arr().orEmpty().mapNotNull { e ->
                val venue = e["_embedded"]["venues"].arr()?.firstOrNull()
                val classification = e["classifications"].arr()?.firstOrNull()
                Event(
                    title = e["name"].str() ?: return@mapNotNull null,
                    date = e["dates"]["start"]["localDate"].str()?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    time = e["dates"]["start"]["localTime"].str()?.take(5),
                    venue = listOfNotNull(venue?.get("name").str(), venue?.get("address")?.get("line1").str())
                        .joinToString(", ").ifBlank { null },
                    description = listOfNotNull(e["info"].str(), e["pleaseNote"].str())
                        .joinToString("\n\n").ifBlank { null },
                    url = e["url"].str(),
                    imageUrl = bestImage(e["images"]),
                    category = listOfNotNull(classification?.get("segment")?.get("name").str(),
                        classification?.get("genre")?.get("name").str())
                        .filterNot { it.equals("Undefined", ignoreCase = true) }
                        .joinToString(" · ").ifBlank { null },
                    source = "Ticketmaster",
                    city = city,
                )
            }

        /** Widest 16:9 image; Ticketmaster lists the same picture in many sizes. */
        private fun bestImage(images: JsonElement?): String? {
            val list = images.arr().orEmpty()
            return (list.filter { it["ratio"].str() == "16_9" }.ifEmpty { list })
                .maxByOrNull { it["width"].int() ?: 0 }
                ?.get("url").str()
        }
    }
}
