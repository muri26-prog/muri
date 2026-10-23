package com.muri.tripscout.data

import com.muri.tripscout.model.Advisory
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Official travel advice from the UK Foreign, Commonwealth & Development Office (GOV.UK content API,
 * free, no key). It is written for UK travellers but covers almost every country and is
 * updated often, which makes it a solid neutral baseline for safety.
 */
class AdvisorySource(private val http: Http) {

    suspend fun forCountry(country: String, countryCode: String): Advisory? {
        val slug = slugFor(country, countryCode) ?: return null
        val url = "https://www.gov.uk/api/content/foreign-travel-advice/$slug".toHttpUrl()
        return parse(http.getJson(url), country, "https://www.gov.uk/foreign-travel-advice/$slug")
    }

    companion object {
        /** GOV.UK slugs differ from Open-Meteo country names for a handful of countries. */
        private val SLUG_OVERRIDES = mapOf(
            "US" to "usa",
            "CZ" to "czech-republic",
            "CI" to "cote-d-ivoire",
            "CD" to "democratic-republic-of-the-congo",
            "CG" to "congo",
            "KR" to "south-korea",
            "KP" to "north-korea",
            "RU" to "russia",
            "VN" to "vietnam",
            "LA" to "laos",
            "MM" to "myanmar",
            "TR" to "turkey",
            "PS" to "the-occupied-palestinian-territories",
            "VA" to "italy",
            "MK" to "north-macedonia",
            "BA" to "bosnia-and-herzegovina",
        )

        fun slugFor(country: String, countryCode: String): String? {
            if (countryCode.equals("GB", ignoreCase = true)) return null // no advice for the UK itself
            SLUG_OVERRIDES[countryCode.uppercase()]?.let { return it }
            if (country.isBlank()) return null
            return country.lowercase()
                .replace(Regex("[’'.]"), "")
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
        }

        fun parse(json: JsonElement, country: String, url: String): Advisory {
            val details = json["details"]
            val alerts = details["alert_status"].arr().orEmpty().mapNotNull { it.str() }
                .map { it.replace('_', ' ').replaceFirstChar(Char::uppercase) }
            val parts = details["parts"].arr().orEmpty()
            val safety = parts.firstOrNull { it["slug"].str() == "safety-and-security" }
                ?: parts.firstOrNull { it["slug"].str() == "warnings-and-insurance" }
            return Advisory(
                country = country,
                alerts = alerts,
                safetySummary = safety?.get("body").str()?.stripHtml()?.truncate(1800),
                updated = json["public_updated_at"].str()?.take(10),
                url = url,
            )
        }
    }
}
