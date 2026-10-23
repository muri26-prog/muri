package com.muri.tripscout.data

import com.muri.tripscout.model.Article
import com.muri.tripscout.model.GeoPlace
import com.muri.tripscout.model.SourceKind
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Google News RSS search (free, no key). Runs a few targeted searches per place:
 * general news, safety-related news, upcoming events, and local-language news from the country.
 */
class NewsSource(private val http: Http) {

    suspend fun forPlace(place: GeoPlace, start: LocalDate, end: LocalDate): List<Article> {
        val city = "\"${place.name}\""
        val months = listOf(start, end).map { it.format(MONTH_YEAR) }.distinct().joinToString(" OR ")
        val searches = buildList {
            add(Search("$city when:7d", SourceKind.NEWS, ENGLISH))
            add(Search("$city ($SAFETY_TERMS) when:14d", SourceKind.SAFETY_NEWS, ENGLISH))
            add(Search("$city ($EVENT_TERMS) ($months)", SourceKind.EVENTS, ENGLISH))
            localEdition(place.countryCode)?.let { add(Search("$city when:7d", SourceKind.LOCAL_NEWS, it)) }
        }
        return searches.flatMap { search ->
            val url = "https://news.google.com/rss/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", search.query)
                .addQueryParameter("hl", search.edition.hl)
                .addQueryParameter("gl", search.edition.gl)
                .addQueryParameter("ceid", search.edition.ceid)
                .build()
            parseRss(http.get(url), search.kind).take(MAX_PER_SEARCH)
        }.distinctBy { it.title }
    }

    private data class Search(val query: String, val kind: SourceKind, val edition: Edition)

    data class Edition(val hl: String, val gl: String) {
        val ceid: String get() = "$gl:${hl.substringBefore('-')}"
    }

    companion object {
        private const val MAX_PER_SEARCH = 8
        private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
        private val ENGLISH = Edition("en-US", "US")
        private const val SAFETY_TERMS =
            "strike OR protest OR demonstration OR closure OR warning OR crime OR police OR flood OR storm OR evacuation OR scam"
        private const val EVENT_TERMS = "festival OR concert OR event OR fair OR exhibition OR marathon OR match"

        /** Local-language Google News editions for common destinations. Others fall back to English only. */
        private val LOCAL_LANGUAGE = mapOf(
            "AT" to "de", "DE" to "de", "CH" to "de", "FR" to "fr", "BE" to "fr", "IT" to "it",
            "ES" to "es", "MX" to "es", "AR" to "es", "CO" to "es", "CL" to "es", "PE" to "es",
            "PT" to "pt-PT", "BR" to "pt-BR", "NL" to "nl", "PL" to "pl", "CZ" to "cs", "SK" to "sk",
            "HU" to "hu", "SI" to "sl", "HR" to "hr", "RS" to "sr", "GR" to "el", "TR" to "tr",
            "RO" to "ro", "BG" to "bg", "SE" to "sv", "NO" to "no", "DK" to "da", "FI" to "fi",
            "JP" to "ja", "KR" to "ko", "CN" to "zh-CN", "TW" to "zh-TW", "HK" to "zh-HK",
            "TH" to "th", "VN" to "vi", "ID" to "id", "IL" to "he", "EG" to "ar", "MA" to "fr",
            "AE" to "ar", "SA" to "ar", "UA" to "uk", "LT" to "lt", "LV" to "lv", "EE" to "et",
        )

        fun localEdition(countryCode: String): Edition? =
            LOCAL_LANGUAGE[countryCode.uppercase()]?.let { Edition(it, countryCode.uppercase()) }

        private val ITEM = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)

        /** Minimal RSS 2.0 parser: Google News feeds are flat and predictable. */
        fun parseRss(xml: String, kind: SourceKind): List<Article> =
            ITEM.findAll(xml).mapNotNull { match ->
                val item = match.groupValues[1]
                val title = tag(item, "title") ?: return@mapNotNull null
                Article(
                    title = title,
                    url = tag(item, "link") ?: return@mapNotNull null,
                    source = tag(item, "source"),
                    published = tag(item, "pubDate")?.take(16),
                    kind = kind,
                )
            }.toList()

        private fun tag(xml: String, name: String): String? =
            Regex("<$name(?:\\s[^>]*)?>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(xml)
                ?.groupValues?.get(1)
                ?.removePrefix("<![CDATA[")?.removeSuffix("]]>")
                ?.stripHtml()
                ?.takeIf { it.isNotBlank() }
    }
}
