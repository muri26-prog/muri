package com.muri.tripscout.data

import com.muri.tripscout.model.DayWeather
import com.muri.tripscout.model.GeoPlace
import com.muri.tripscout.model.WeatherKind
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.LocalDate

/**
 * Open-Meteo weather (free, no key).
 * - Days within the 16-day forecast window get a real forecast.
 * - Days further out get a "typical" estimate: the average of the same dates in the last 3 years.
 */
class WeatherSource(private val http: Http, private val today: () -> LocalDate = { LocalDate.now() }) {

    suspend fun forDays(place: GeoPlace, days: List<LocalDate>): Map<LocalDate, DayWeather> {
        if (days.isEmpty()) return emptyMap()
        val horizon = today().plusDays(FORECAST_DAYS - 1)
        val (near, far) = days.partition { !it.isAfter(horizon) && !it.isBefore(today()) }
        val result = mutableMapOf<LocalDate, DayWeather>()
        if (near.isNotEmpty()) result += forecast(place, near.min(), near.max())
        if (far.isNotEmpty()) result += typical(place, far)
        return result
    }

    private suspend fun forecast(place: GeoPlace, from: LocalDate, to: LocalDate): Map<LocalDate, DayWeather> {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .coords(place)
            .addQueryParameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
                    "precipitation_probability_max,wind_speed_10m_max,uv_index_max",
            )
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("start_date", from.toString())
            .addQueryParameter("end_date", to.toString())
            .build()
        return parseDaily(http.getJson(url), WeatherKind.FORECAST).associateBy { it.date }
    }

    private suspend fun typical(place: GeoPlace, days: List<LocalDate>): Map<LocalDate, DayWeather> {
        val from = days.min()
        val to = days.max()
        val samples = (1L..YEARS_OF_HISTORY).map { yearsBack ->
            val url = "https://archive-api.open-meteo.com/v1/archive".toHttpUrl().newBuilder()
                .coords(place)
                .addQueryParameter(
                    "daily",
                    "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max",
                )
                .addQueryParameter("timezone", "auto")
                .addQueryParameter("start_date", from.minusYears(yearsBack).toString())
                .addQueryParameter("end_date", to.minusYears(yearsBack).toString())
                .build()
            // Shift the historic days forward so they line up with the trip dates.
            parseDaily(http.getJson(url), WeatherKind.TYPICAL).map { it.copy(date = it.date.plusYears(yearsBack)) }
        }.flatten()
        return days.associateWith { day -> averageOf(day, samples.filter { it.date == day }) }
            .filterValues { it != null }
            .mapValues { it.value!! }
    }

    private fun okhttp3.HttpUrl.Builder.coords(place: GeoPlace) = this
        .addQueryParameter("latitude", place.latitude.toString())
        .addQueryParameter("longitude", place.longitude.toString())

    companion object {
        const val FORECAST_DAYS = 16L
        const val YEARS_OF_HISTORY = 3L

        fun parseDaily(json: JsonElement, kind: WeatherKind): List<DayWeather> {
            val daily = json["daily"] ?: return emptyList()
            val dates = daily["time"].arr().orEmpty().mapNotNull { it.str() }
            fun column(name: String) = daily[name].arr()
            val codes = column("weather_code")
            val max = column("temperature_2m_max")
            val min = column("temperature_2m_min")
            val rain = column("precipitation_sum")
            val chance = column("precipitation_probability_max")
            val wind = column("wind_speed_10m_max")
            val uv = column("uv_index_max")
            return dates.mapIndexed { i, date ->
                DayWeather(
                    date = LocalDate.parse(date),
                    kind = kind,
                    description = describe(codes?.getOrNull(i).int()),
                    tempMaxC = max?.getOrNull(i).dbl(),
                    tempMinC = min?.getOrNull(i).dbl(),
                    precipitationMm = rain?.getOrNull(i).dbl(),
                    precipitationChancePct = chance?.getOrNull(i).int(),
                    windMaxKmh = wind?.getOrNull(i).dbl(),
                    uvIndexMax = uv?.getOrNull(i).dbl(),
                )
            }
        }

        fun averageOf(day: LocalDate, samples: List<DayWeather>): DayWeather? {
            if (samples.isEmpty()) return null
            fun avg(values: List<Double?>) = values.filterNotNull().takeIf { it.isNotEmpty() }?.average()
            val wetDays = samples.count { (it.precipitationMm ?: 0.0) >= 1.0 }
            return DayWeather(
                date = day,
                kind = WeatherKind.TYPICAL,
                // The most common condition across the sampled years.
                description = samples.groupingBy { it.description }.eachCount().maxBy { it.value }.key,
                tempMaxC = avg(samples.map { it.tempMaxC }),
                tempMinC = avg(samples.map { it.tempMinC }),
                precipitationMm = avg(samples.map { it.precipitationMm }),
                precipitationChancePct = wetDays * 100 / samples.size,
                windMaxKmh = avg(samples.map { it.windMaxKmh }),
                uvIndexMax = null,
            )
        }

        /** WMO weather interpretation codes, as used by Open-Meteo. */
        fun describe(code: Int?): String = when (code) {
            null -> "Unknown"
            0 -> "Clear sky"
            1 -> "Mainly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing drizzle"
            61 -> "Light rain"
            63 -> "Rain"
            65 -> "Heavy rain"
            66, 67 -> "Freezing rain"
            71 -> "Light snow"
            73 -> "Snow"
            75 -> "Heavy snow"
            77 -> "Snow grains"
            80, 81 -> "Rain showers"
            82 -> "Violent rain showers"
            85, 86 -> "Snow showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm with hail"
            else -> "Code $code"
        }
    }
}
