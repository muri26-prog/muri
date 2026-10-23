package com.muri.tripscout.storage

import android.content.Context
import com.muri.tripscout.ai.ClaudeResearcher
import com.muri.tripscout.model.Trip
import com.muri.tripscout.model.TripJson

/** Trips and settings live in app-private SharedPreferences (not readable by other apps). */
class TripStore(context: Context) {
    private val prefs = context.getSharedPreferences("trip_scout", Context.MODE_PRIVATE)

    fun loadTrips(): List<Trip> =
        runCatching { TripJson.decode(prefs.getString(KEY_TRIPS, "").orEmpty()) }.getOrDefault(emptyList())

    fun saveTrips(trips: List<Trip>) {
        prefs.edit().putString(KEY_TRIPS, TripJson.encode(trips)).apply()
    }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() } ?: ClaudeResearcher.DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    var ticketmasterKey: String
        get() = prefs.getString(KEY_TICKETMASTER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TICKETMASTER, value.trim()).apply()

    private companion object {
        const val KEY_TRIPS = "trips"
        const val KEY_API_KEY = "anthropic_api_key"
        const val KEY_MODEL = "claude_model"
        const val KEY_TICKETMASTER = "ticketmaster_api_key"
    }
}
