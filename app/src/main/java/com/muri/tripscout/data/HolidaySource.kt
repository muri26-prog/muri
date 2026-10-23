package com.muri.tripscout.data

import com.muri.tripscout.model.Holiday
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.LocalDate

/** Public holidays from Nager.Date (free, no key). Holidays mean closures, crowds and changed transit. */
class HolidaySource(private val http: Http) {

    suspend fun forDays(countryCode: String, days: List<LocalDate>): List<Holiday> {
        if (countryCode.isBlank() || days.isEmpty()) return emptyList()
        val wanted = days.toSet()
        return days.map { it.year }.distinct().flatMap { year ->
            val url = "https://date.nager.at/api/v3/PublicHolidays/$year/${countryCode.uppercase()}".toHttpUrl()
            parse(http.getJson(url))
        }.filter { it.date in wanted }
    }

    companion object {
        fun parse(json: JsonElement): List<Holiday> = json.arr().orEmpty().mapNotNull { h ->
            Holiday(
                date = LocalDate.parse(h["date"].str() ?: return@mapNotNull null),
                localName = h["localName"].str().orEmpty(),
                name = h["name"].str().orEmpty(),
                nationwide = h["global"].str()?.toBooleanStrictOrNull() ?: true,
            )
        }
    }
}
