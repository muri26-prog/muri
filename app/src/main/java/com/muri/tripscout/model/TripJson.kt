package com.muri.tripscout.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.LocalDate

/** Saves trips as plain JSON so the app needs no database. */
object TripJson {

    fun encode(trips: List<Trip>): String = buildJsonArray {
        for (trip in trips) add(buildJsonObject {
            put("id", trip.id)
            put("name", trip.name)
            put("stops", buildJsonArray {
                for (stop in trip.stops) add(buildJsonObject {
                    put("id", stop.id)
                    put("place", stop.place)
                    put("start", stop.startDate.toString())
                    put("end", stop.endDate.toString())
                    stop.geo?.let { put("geo", encodeGeo(it)) }
                })
            })
        })
    }.toString()

    fun decode(text: String): List<Trip> {
        if (text.isBlank()) return emptyList()
        return Json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
            runCatching { decodeTrip(element.jsonObject) }.getOrNull()
        }
    }

    private fun decodeTrip(obj: JsonObject): Trip = Trip(
        id = obj.string("id"),
        name = obj.string("name"),
        stops = (obj["stops"] as? JsonArray).orEmpty().map { s ->
            val stop = s.jsonObject
            Stop(
                id = stop.string("id"),
                place = stop.string("place"),
                startDate = LocalDate.parse(stop.string("start")),
                endDate = LocalDate.parse(stop.string("end")),
                // Trips saved before place picking existed have no "geo"; they fall back to a name lookup.
                geo = (stop["geo"] as? JsonObject)?.let { runCatching { decodeGeo(it) }.getOrNull() },
            )
        },
    )

    private fun encodeGeo(geo: GeoPlace) = buildJsonObject {
        put("name", geo.name)
        put("lat", geo.latitude)
        put("lon", geo.longitude)
        put("countryCode", geo.countryCode)
        put("country", geo.country)
        geo.region?.let { put("region", it) }
        geo.timezone?.let { put("timezone", it) }
        geo.population?.let { put("population", it) }
    }

    private fun decodeGeo(obj: JsonObject) = GeoPlace(
        name = obj.string("name"),
        latitude = obj.getValue("lat").jsonPrimitive.double,
        longitude = obj.getValue("lon").jsonPrimitive.double,
        countryCode = obj.string("countryCode"),
        country = obj.string("country"),
        region = obj.optString("region"),
        timezone = obj.optString("timezone"),
        population = obj["population"]?.jsonPrimitive?.longOrNull,
    )

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
    private fun JsonObject.optString(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
