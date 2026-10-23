package com.muri.tripscout

import com.muri.tripscout.ai.ClaudeResearcher
import com.muri.tripscout.data.AdvisorySource
import com.muri.tripscout.data.GeocodingSource
import com.muri.tripscout.data.NewsSource
import com.muri.tripscout.data.RedditSource
import com.muri.tripscout.data.WeatherSource
import com.muri.tripscout.model.DayWeather
import com.muri.tripscout.model.SafetyLevel
import com.muri.tripscout.model.SourceKind
import com.muri.tripscout.model.WeatherKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ParsingTest {

    @Test
    fun geocodingPrefersCountryHint() {
        val json = Json.parseToJsonElement(
            """{"results":[
              {"name":"Paris","latitude":48.85,"longitude":2.35,"country_code":"FR","country":"France","admin1":"Île-de-France","timezone":"Europe/Paris"},
              {"name":"Paris","latitude":33.66,"longitude":-95.55,"country_code":"US","country":"United States","admin1":"Texas","timezone":"America/Chicago"}
            ]}""",
        )
        val places = GeocodingSource.parse(json)
        assertEquals("FR", GeocodingSource.pickBest(places, emptyList())?.countryCode)
        assertEquals("Texas", GeocodingSource.pickBest(places, listOf("Texas"))?.region)
        assertNull(GeocodingSource.pickBest(emptyList(), listOf("x")))
    }

    @Test
    fun weatherParsesDailyColumnsAndAverages() {
        val json = Json.parseToJsonElement(
            """{"daily":{"time":["2026-11-01","2026-11-02"],"weather_code":[61,0],
               "temperature_2m_max":[12.5,15.0],"temperature_2m_min":[5.0,null],"precipitation_sum":[4.2,0.0]}}""",
        )
        val days = WeatherSource.parseDaily(json, WeatherKind.FORECAST)
        assertEquals(2, days.size)
        assertEquals("Light rain", days[0].description)
        assertNull(days[1].tempMinC)

        val day = LocalDate.of(2026, 11, 1)
        val samples = listOf(10.0, 20.0).map { DayWeather(day, WeatherKind.TYPICAL, "Overcast", it, 1.0, if (it > 15) 3.0 else 0.0, null, null, null) }
        val avg = WeatherSource.averageOf(day, samples)!!
        assertEquals(15.0, avg.tempMaxC!!, 0.001)
        assertEquals(50, avg.precipitationChancePct)
    }

    @Test
    fun rssParsesCdataAndSource() {
        val xml = """<rss><channel><item><title><![CDATA[Metro strike in Vienna &amp; more]]></title>
            <link>https://news.example/a</link><pubDate>Fri, 23 Oct 2026 08:00:00 GMT</pubDate>
            <source url="https://x">Der Standard</source></item><item><title>No link</title></item></channel></rss>"""
        val items = NewsSource.parseRss(xml, SourceKind.SAFETY_NEWS)
        assertEquals(1, items.size)
        assertEquals("Metro strike in Vienna & more", items[0].title)
        assertEquals("Der Standard", items[0].source)
    }

    @Test
    fun redditParsesPosts() {
        val json = Json.parseToJsonElement(
            """{"data":{"children":[{"data":{"title":"Is Naples safe at night?","permalink":"/r/travel/comments/1/x/","subreddit_name_prefixed":"r/travel","created_utc":1792800000.0}}]}}""",
        )
        val posts = RedditSource.parse(json)
        assertEquals("https://www.reddit.com/r/travel/comments/1/x/", posts.single().url)
    }

    @Test
    fun advisorySlugsAndParsing() {
        assertEquals("usa", AdvisorySource.slugFor("United States", "US"))
        assertEquals("new-zealand", AdvisorySource.slugFor("New Zealand", "NZ"))
        assertNull(AdvisorySource.slugFor("United Kingdom", "GB"))
        val json = Json.parseToJsonElement(
            """{"public_updated_at":"2026-10-01T10:00:00Z","details":{"alert_status":["avoid_all_but_essential_travel_to_parts"],
               "parts":[{"slug":"safety-and-security","body":"<p>Pickpocketing is common.</p><p>Stay alert.</p>"}]}}""",
        )
        val advisory = AdvisorySource.parse(json, "Italy", "u")
        assertEquals("2026-10-01", advisory.updated)
        assertEquals("Pickpocketing is common.\nStay alert.", advisory.safetySummary)
        assertTrue(advisory.alerts.single().startsWith("Avoid all"))
    }

    @Test
    fun claudeReplyParsesJsonEvenWithSurroundingText() {
        val reply = """Here is the research:
            {"overview":"Busy","safety_overview":"Mostly safe","days":[
              {"date":"2026-11-01","safety_level":"Elevated","safety_notes":"Protest downtown","events":["Jazz fest"],"happenings":"","tips":"Avoid the square"},
              {"date":"bad-date","safety_level":"low"}],
             "sources":[{"title":"City site","url":"https://city.example"},{"title":"x","url":"javascript:alert(1)"}]}"""
        val research = ClaudeResearcher.parse(reply)
        assertNull(research.rawText)
        assertEquals(SafetyLevel.ELEVATED, research.days.single().safetyLevel)
        assertEquals(listOf("Jazz fest"), research.days.single().events.map { it.title })
        assertEquals(1, research.sources.size)
    }

    @Test
    fun claudeReplyKeepsRawTextWhenNotJson() {
        val research = ClaudeResearcher.parse("Sorry, no JSON here")
        assertNotNull(research.rawText)
        assertTrue(research.days.isEmpty())
    }
}
