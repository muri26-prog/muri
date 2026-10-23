package com.muri.tripscout.model

import java.time.LocalDate

data class GeoPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val countryCode: String,
    val country: String,
    val region: String?,
    val timezone: String?,
) {
    val label: String get() = listOfNotNull(name, region, country).distinct().joinToString(", ")
}

enum class WeatherKind { FORECAST, TYPICAL }

data class DayWeather(
    val date: LocalDate,
    val kind: WeatherKind,
    val description: String,
    val tempMaxC: Double?,
    val tempMinC: Double?,
    val precipitationMm: Double?,
    val precipitationChancePct: Int?,
    val windMaxKmh: Double?,
    val uvIndexMax: Double?,
)

data class Holiday(val date: LocalDate, val localName: String, val name: String, val nationwide: Boolean)

/** UK FCDO travel advice for the destination country. */
data class Advisory(
    val country: String,
    val alerts: List<String>,
    val safetySummary: String?,
    val updated: String?,
    val url: String,
)

enum class SourceKind { NEWS, LOCAL_NEWS, SAFETY_NEWS, EVENTS, REDDIT, WEB }

data class Article(
    val title: String,
    val url: String,
    val source: String?,
    val published: String?,
    val kind: SourceKind,
)

enum class SafetyLevel { LOW, MODERATE, ELEVATED, HIGH, UNKNOWN;
    companion object {
        fun parse(value: String?): SafetyLevel =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** Claude's research for one day at one location. */
data class AiDay(
    val date: LocalDate,
    val safetyLevel: SafetyLevel,
    val safetyNotes: String,
    val events: List<String>,
    val happenings: String,
    val tips: String,
)

data class AiLocationResearch(
    val overview: String,
    val safetyOverview: String,
    val days: List<AiDay>,
    val sources: List<Article>,
    /** Set when the reply could not be parsed as JSON; the raw text is shown instead. */
    val rawText: String? = null,
)

data class DayReport(
    val date: LocalDate,
    val weather: DayWeather?,
    val holidays: List<Holiday>,
    val ai: AiDay?,
)

data class LocationReport(
    val stop: Stop,
    val geo: GeoPlace?,
    val days: List<DayReport>,
    val advisory: Advisory?,
    val staySafe: String?,
    val articles: List<Article>,
    val ai: AiLocationResearch?,
    /** Per-source problems, e.g. "Reddit: HTTP 403". Shown so gaps are visible, never silent. */
    val warnings: List<String>,
)

data class TripReport(
    val trip: Trip,
    val generatedAt: String,
    val locations: List<LocationReport>,
)
