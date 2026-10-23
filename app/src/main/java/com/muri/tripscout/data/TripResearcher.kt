package com.muri.tripscout.data

import com.muri.tripscout.ai.ClaudeResearcher
import com.muri.tripscout.model.Advisory
import com.muri.tripscout.model.Article
import com.muri.tripscout.model.DayReport
import com.muri.tripscout.model.DayWeather
import com.muri.tripscout.model.Event
import com.muri.tripscout.model.Holiday
import com.muri.tripscout.model.LocationReport
import com.muri.tripscout.model.Stop
import com.muri.tripscout.model.Trip
import com.muri.tripscout.model.TripReport
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Collects everything for a trip. Free sources run in parallel for each stop; when an
 * Anthropic API key is set, Claude then does a web-search pass per stop and the results
 * are merged into one report per day and per location.
 */
class TripResearcher(
    private val http: Http = Http(),
    private val claude: ClaudeResearcher? = null,
    ticketmasterKey: String? = null,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private val geocoding = GeocodingSource(http)
    private val weather = WeatherSource(http, today)
    private val holidays = HolidaySource(http)
    private val advisories = AdvisorySource(http)
    private val wikivoyage = WikivoyageSource(http)
    private val wikipedia = WikipediaSource(http)
    private val news = NewsSource(http)
    private val reddit = RedditSource(http)
    private val pageImages = PageImageSource(http)
    private val ticketmaster = ticketmasterKey?.takeIf { it.isNotBlank() }?.let { TicketmasterSource(http, it) }

    suspend fun research(trip: Trip, onProgress: (String) -> Unit = {}): TripReport {
        val locations = trip.stops.sortedBy { it.startDate }.map { stop ->
            onProgress("Researching ${stop.place}…")
            researchStop(stop, onProgress)
        }
        return TripReport(
            trip = trip,
            generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
            locations = locations,
        )
    }

    private suspend fun researchStop(stop: Stop, onProgress: (String) -> Unit): LocationReport = coroutineScope {
        val warnings = mutableListOf<String>()
        val days = stop.days()

        // The place picked from suggestions is used as-is; older trips fall back to a name lookup.
        val geo = stop.geo ?: attempt("Location lookup", warnings) { geocoding.find(stop.place) }
        if (geo == null) {
            warnings += "Could not find \"${stop.place}\". Edit the trip and pick the place from the suggestions."
            return@coroutineScope LocationReport(stop, null, null, days.map { DayReport(it, null, emptyList(), null) },
                null, null, emptyList(), null, warnings)
        }

        val weatherJob = async { attempt("Weather", warnings) { weather.forDays(geo, days) }.orEmpty() }
        val holidayJob = async { attempt("Public holidays", warnings) { holidays.forDays(geo.countryCode, days) }.orEmpty() }
        val advisoryJob = async { attempt("Travel advisory", warnings) { advisories.forCountry(geo.country, geo.countryCode) } }
        val staySafeJob = async { attempt("Wikivoyage", warnings) { wikivoyage.staySafe(geo.name) } }
        val placeInfoJob = async { attempt("Wikipedia", warnings) { wikipedia.summary(geo) } }
        val newsJob = async { attempt("News", warnings) { news.forPlace(geo, stop.startDate, stop.endDate) }.orEmpty() }
        val redditJob = async { attempt("Reddit", warnings) { reddit.forPlace(geo) }.orEmpty() }
        val ticketJob = async {
            ticketmaster?.let { tm -> attempt("Ticketmaster", warnings) { tm.forPlace(geo, stop.startDate, stop.endDate) } }.orEmpty()
        }

        val weatherByDay = weatherJob.await()
        val holidayList = holidayJob.await()
        val advisory = advisoryJob.await()
        val staySafe = staySafeJob.await()
        val articles = newsJob.await() + redditJob.await()
        val listedEvents = ticketJob.await()

        val ai = claude?.let { researcher ->
            onProgress("Claude is searching the web for ${geo.name}…")
            attempt("Claude web research", warnings) {
                researcher.research(
                    placeLabel = geo.label,
                    days = days,
                    today = today(),
                    context = buildContext(days, weatherByDay, holidayList, advisory, staySafe, articles, listedEvents),
                )
            }
        }

        onProgress("Loading event pictures for ${geo.name}…")
        val aiEvents = ai?.days.orEmpty().flatMap { it.events }.map { it.copy(city = geo.name) }
        val allEvents = withPictures(mergeEvents(listedEvents, aiEvents))

        val dayReports = days.map { day ->
            DayReport(
                date = day,
                weather = weatherByDay[day],
                holidays = holidayList.filter { it.date == day },
                ai = ai?.days?.firstOrNull { it.date == day },
                events = allEvents.filter { it.date == day },
            )
        }
        LocationReport(stop, geo, placeInfoJob.await(), dayReports, advisory, staySafe, articles, ai, warnings.toList())
    }

    /** Fetches the preview picture from each event page that has no image yet (a few at a time). */
    private suspend fun withPictures(events: List<Event>): List<Event> = coroutineScope {
        val limit = Semaphore(4)
        events.map { event ->
            async {
                if (event.imageUrl != null || event.url == null) return@async event
                val image = limit.withPermit { runCatching { pageImages.imageFor(event.url) }.getOrNull() }
                event.copy(imageUrl = image)
            }
        }.awaitAll()
    }

    /** Runs one source; a failure becomes a visible warning instead of breaking the whole report. */
    private suspend fun <T> attempt(name: String, warnings: MutableList<String>, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(warnings) { warnings += "$name: ${e.message ?: e.javaClass.simpleName}" }
            null
        }

    companion object {
        /**
         * Ticketmaster listings come first (real photos and ticket links); Claude's finds are added
         * unless they are the same event on the same day.
         */
        fun mergeEvents(listed: List<Event>, found: List<Event>): List<Event> {
            fun key(e: Event) = e.date to e.title.lowercase().filter { it.isLetterOrDigit() }.take(24)
            val seen = listed.map(::key).toMutableSet()
            return (listed + found.filter { seen.add(key(it)) })
                .sortedWith(compareBy({ it.date }, { it.time ?: "99:99" }))
        }

        /** Compact text summary of the free-source data, handed to Claude as context. */
        fun buildContext(
            days: List<LocalDate>,
            weather: Map<LocalDate, DayWeather>,
            holidays: List<Holiday>,
            advisory: Advisory?,
            staySafe: String?,
            articles: List<Article>,
            events: List<Event> = emptyList(),
        ): String = buildString {
            appendLine("Weather:")
            for (day in days) {
                val w = weather[day]
                if (w == null) appendLine("- $day: no data")
                else appendLine("- $day (${w.kind.name.lowercase()}): ${w.description}, ${w.tempMinC?.toInt()}–${w.tempMaxC?.toInt()}°C, rain ${w.precipitationMm ?: "?"} mm")
            }
            appendLine("Public holidays on these dates: " +
                holidays.joinToString { "${it.date} ${it.name}" }.ifEmpty { "none" })
            advisory?.let {
                appendLine("UK FCDO advisory (updated ${it.updated}): alerts=${it.alerts.ifEmpty { listOf("none") }}")
                it.safetySummary?.let { s -> appendLine("FCDO safety excerpt: ${s.truncate(800)}") }
            }
            staySafe?.let { appendLine("Wikivoyage 'Stay safe' excerpt: ${it.truncate(800)}") }
            if (events.isNotEmpty()) {
                appendLine("Ticketed events already listed (no need to repeat; find other events):")
                events.take(25).forEach { appendLine("- ${it.date} ${it.time ?: ""} ${it.title} @ ${it.venue ?: "?"}") }
            }
            if (articles.isNotEmpty()) {
                appendLine("Recent headlines (unverified):")
                articles.take(30).forEach { appendLine("- [${it.kind}] ${it.title} (${it.source ?: ""}, ${it.published ?: ""})") }
            }
        }
    }
}
