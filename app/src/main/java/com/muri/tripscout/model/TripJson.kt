package com.muri.tripscout.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
            )
        },
    )

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
}
