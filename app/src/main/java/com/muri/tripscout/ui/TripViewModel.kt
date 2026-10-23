package com.muri.tripscout.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.muri.tripscout.ai.ClaudeResearcher
import com.muri.tripscout.data.GeocodingSource
import com.muri.tripscout.data.Http
import com.muri.tripscout.data.TripResearcher
import com.muri.tripscout.model.Event
import com.muri.tripscout.model.GeoPlace
import com.muri.tripscout.model.Trip
import com.muri.tripscout.model.TripReport
import com.muri.tripscout.storage.TripStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Trips : Screen
    data class Edit(val tripId: String?) : Screen
    data class Report(val tripId: String) : Screen
    data object Settings : Screen
    data class EventDetail(val event: Event) : Screen
}

sealed interface ReportState {
    data class Loading(val progress: String) : ReportState
    data class Ready(val report: TripReport) : ReportState
    data class Failed(val message: String) : ReportState
}

class TripViewModel(app: Application) : AndroidViewModel(app) {
    private val store = TripStore(app)
    private val jobs = mutableMapOf<String, Job>()
    private val http = Http()
    private val geocoding = GeocodingSource(http)
    /** Screens to return to with Back. */
    private val history = ArrayDeque<Screen>()

    var trips by mutableStateOf(store.loadTrips().sortedBy { it.startDate })
        private set
    var screen by mutableStateOf<Screen>(Screen.Trips)
        private set
    var apiKey by mutableStateOf(store.apiKey)
        private set
    var model by mutableStateOf(store.model)
        private set
    var ticketmasterKey by mutableStateOf(store.ticketmasterKey)
        private set

    /** Reports are kept in memory for this session; refresh re-runs the research. */
    val reports = mutableStateMapOf<String, ReportState>()

    fun open(target: Screen) {
        history.addLast(screen)
        screen = target
    }

    fun back() {
        screen = history.removeLastOrNull() ?: Screen.Trips
    }

    /** Replaces the whole back stack, e.g. after saving a trip: Back then returns to the trip list. */
    private fun reset(target: Screen) {
        history.clear()
        if (target != Screen.Trips) history.addLast(Screen.Trips)
        screen = target
    }

    /** City suggestions for the trip editor. Errors (e.g. offline) just mean no suggestions. */
    suspend fun suggestPlaces(query: String): List<GeoPlace> =
        runCatching { geocoding.suggest(query) }.getOrDefault(emptyList())

    fun trip(id: String): Trip? = trips.firstOrNull { it.id == id }

    fun saveTrip(trip: Trip) {
        trips = (trips.filterNot { it.id == trip.id } + trip).sortedBy { it.startDate }
        store.saveTrips(trips)
        reports.remove(trip.id) // itinerary changed, old report no longer matches
        reset(Screen.Report(trip.id))
    }

    fun deleteTrip(id: String) {
        jobs.remove(id)?.cancel()
        reports.remove(id)
        trips = trips.filterNot { it.id == id }
        store.saveTrips(trips)
        reset(Screen.Trips)
    }

    fun saveSettings(newKey: String, newModel: String, newTicketmasterKey: String) {
        store.apiKey = newKey
        store.model = newModel
        store.ticketmasterKey = newTicketmasterKey
        apiKey = store.apiKey
        model = store.model
        ticketmasterKey = store.ticketmasterKey
        back()
    }

    fun research(tripId: String) {
        val trip = trip(tripId) ?: return
        jobs.remove(tripId)?.cancel()
        reports[tripId] = ReportState.Loading("Starting…")
        jobs[tripId] = viewModelScope.launch {
            try {
                val claude = apiKey.takeIf { it.isNotBlank() }?.let { ClaudeResearcher(it, model) }
                val researcher = TripResearcher(http = http, claude = claude, ticketmasterKey = ticketmasterKey)
                val report = researcher.research(trip) { progress ->
                    reports[tripId] = ReportState.Loading(progress)
                }
                reports[tripId] = ReportState.Ready(report)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reports[tripId] = ReportState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
