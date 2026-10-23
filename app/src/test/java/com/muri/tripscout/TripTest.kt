package com.muri.tripscout

import com.muri.tripscout.model.Stop
import com.muri.tripscout.model.Trip
import com.muri.tripscout.model.TripJson
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TripTest {
    private val d = { day: Int -> LocalDate.of(2026, 11, day) }

    @Test
    fun calendarListsBothPlacesOnTravelDays() {
        val trip = Trip(name = "Balkans", stops = listOf(
            Stop(place = "Kotor", startDate = d(3), endDate = d(5)),
            Stop(place = "Dubrovnik", startDate = d(1), endDate = d(3)),
        ))
        val calendar = trip.calendar()
        assertEquals(listOf(d(1), d(2), d(3), d(4), d(5)), calendar.keys.toList())
        assertEquals(listOf("Dubrovnik", "Kotor"), calendar.getValue(d(3)).map { it.place })
        assertEquals(d(1), trip.startDate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun stopRejectsEndBeforeStart() {
        Stop(place = "X", startDate = d(5), endDate = d(4))
    }

    @Test
    fun jsonRoundTrip() {
        val trips = listOf(Trip(name = "Test \"quoted\"", stops = listOf(Stop(place = "Vienna, Austria", startDate = d(1), endDate = d(2)))))
        assertEquals(trips, TripJson.decode(TripJson.encode(trips)))
        assertEquals(emptyList<Trip>(), TripJson.decode(""))
    }
}
