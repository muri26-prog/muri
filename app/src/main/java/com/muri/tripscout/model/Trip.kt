package com.muri.tripscout.model

import java.time.LocalDate
import java.util.UUID

/** One place on the itinerary, visited from [startDate] to [endDate] (both inclusive). */
data class Stop(
    val id: String = UUID.randomUUID().toString(),
    val place: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    init {
        require(!endDate.isBefore(startDate)) { "endDate must not be before startDate" }
    }

    /** Every calendar day spent at this stop. */
    fun days(): List<LocalDate> =
        generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(endDate) }.toList()
}

data class Trip(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val stops: List<Stop>,
) {
    val startDate: LocalDate? get() = stops.minOfOrNull { it.startDate }
    val endDate: LocalDate? get() = stops.maxOfOrNull { it.endDate }

    /**
     * Calendar of the trip: each date mapped to the stops you are at that day.
     * Travel days between two stops list both places.
     */
    fun calendar(): Map<LocalDate, List<Stop>> {
        val result = sortedMapOf<LocalDate, MutableList<Stop>>()
        for (stop in stops.sortedBy { it.startDate }) {
            for (day in stop.days()) result.getOrPut(day) { mutableListOf() }.add(stop)
        }
        return result
    }
}
