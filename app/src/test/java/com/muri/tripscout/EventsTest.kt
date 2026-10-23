package com.muri.tripscout

import com.muri.tripscout.ai.ClaudeResearcher
import com.muri.tripscout.data.GeocodingSource
import com.muri.tripscout.data.PageImageSource
import com.muri.tripscout.data.TicketmasterSource
import com.muri.tripscout.data.TripResearcher
import com.muri.tripscout.data.WikipediaSource
import com.muri.tripscout.model.Event
import com.muri.tripscout.model.GeoPlace
import com.muri.tripscout.model.Stop
import com.muri.tripscout.model.Trip
import com.muri.tripscout.model.TripJson
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EventsTest {
    private val vienna = GeoPlace("Vienna", 48.2085, 16.3721, "AT", "Austria", "Vienna", "Europe/Vienna", 1_691_468)
    private val day = LocalDate.of(2026, 11, 7)

    @Test
    fun pickedPlaceSurvivesSaving() {
        val trips = listOf(Trip(name = "T", stops = listOf(Stop(place = "Vienna", startDate = day, endDate = day, geo = vienna))))
        assertEquals(trips, TripJson.decode(TripJson.encode(trips)))
    }

    @Test
    fun suggestionHintsMatchRegionOrCountry() {
        val texas = vienna.copy(name = "Paris", countryCode = "US", country = "United States", region = "Texas")
        assertTrue(GeocodingSource.matchesHints(texas, listOf("texas")))
        assertTrue(!GeocodingSource.matchesHints(vienna, listOf("texas")))
        assertEquals("Vienna, Austria", vienna.label) // region equal to name is not repeated
    }

    @Test
    fun ticketmasterParsesEventWithBestImage() {
        val json = Json.parseToJsonElement(
            """{"_embedded":{"events":[{"name":"Vienna Philharmonic","url":"https://www.ticketmaster.at/event/1",
               "dates":{"start":{"localDate":"2026-11-07","localTime":"19:30:00"}},
               "classifications":[{"segment":{"name":"Music"},"genre":{"name":"Classical"}}],
               "images":[{"ratio":"16_9","width":640,"url":"https://img/s.jpg"},{"ratio":"16_9","width":2048,"url":"https://img/l.jpg"},{"ratio":"4_3","width":3000,"url":"https://img/x.jpg"}],
               "_embedded":{"venues":[{"name":"Musikverein","address":{"line1":"Musikvereinsplatz 1"}}]}}]}}""",
        )
        val event = TicketmasterSource.parse(json, "Vienna").single()
        assertEquals("19:30", event.time)
        assertEquals("https://img/l.jpg", event.imageUrl)
        assertEquals("Musikverein, Musikvereinsplatz 1", event.venue)
        assertEquals("Music · Classical", event.category)
        assertEquals(day, event.date)
    }

    @Test
    fun wikipediaRejectsFarAwayOrDisambiguation() {
        fun page(lat: Double, type: String = "standard") = Json.parseToJsonElement(
            """{"type":"$type","title":"Vienna","extract":"Vienna is the capital of Austria.",
               "coordinates":{"lat":$lat,"lon":16.37},"thumbnail":{"source":"https://upload/t.jpg"},
               "content_urls":{"mobile":{"page":"https://en.m.wikipedia.org/wiki/Vienna"}}}""",
        )
        assertEquals("https://upload/t.jpg", WikipediaSource.parse(page(48.21), vienna)?.imageUrl)
        assertNull(WikipediaSource.parse(page(38.9), vienna)) // Vienna, Virginia latitude
        assertNull(WikipediaSource.parse(page(48.21, "disambiguation"), vienna))
    }

    @Test
    fun pageImageReadsOgImageInEitherAttributeOrder() {
        val html1 = """<head><meta content="/img/poster.jpg?a=1&amp;b=2" property="og:image"></head>"""
        assertEquals("https://fest.example/img/poster.jpg?a=1&b=2", PageImageSource.extract(html1, "https://fest.example/events/x"))
        val html2 = """<meta name="twitter:image" content="http://insecure.example/p.jpg">"""
        assertNull(PageImageSource.extract(html2, "https://fest.example/"))
        assertNull(PageImageSource.extract("<html></html>", "https://fest.example/"))
    }

    @Test
    fun claudeEventsParseAsObjectsAndStrings() {
        val reply = """{"overview":"","safety_overview":"","days":[{"date":"2026-11-07","safety_level":"low",
            "events":[{"title":"Christmas market opening","time":"10:00","venue":"Rathausplatz","category":"Market",
                       "description":"Opening day.","url":"https://www.wien.info/x"},
                      "Jazz night - Porgy & Bess"]}]}"""
        val events = ClaudeResearcher.parse(reply).days.single().events
        assertEquals(2, events.size)
        assertEquals("https://www.wien.info/x", events[0].url)
        assertEquals(day, events[1].date)
        assertNull(events[1].url)
    }

    @Test
    fun mergeKeepsListedEventAndDropsDuplicate() {
        fun e(title: String, source: String, time: String? = null) =
            Event(title, day, time, null, null, null, null, null, source)
        val merged = TripResearcher.mergeEvents(
            listOf(e("Vienna Philharmonic", "Ticketmaster", "19:30")),
            listOf(e("Vienna Philharmonic!", "Claude"), e("Christmas market", "Claude", "10:00")),
        )
        assertEquals(listOf("Christmas market", "Vienna Philharmonic"), merged.map { it.title })
        assertEquals("Ticketmaster", merged[1].source)
    }
}
