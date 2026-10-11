package com.niiros.tripplanner.data

import com.niiros.tripplanner.core.demo.DemoData
import com.niiros.tripplanner.data.json.TripJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TripJsonTest {
    @Test
    fun roundTripKeepsPlacesAndTimesWithFreshIds() {
        val trip = DemoData.moroccoTrip("t1", LocalDate.of(2026, 10, 20))
        val text = TripJson.encode(trip)
        val back = TripJson.decode(text)
        assertNotNull(back)
        back!!
        assertEquals(trip.name, back.name)
        assertEquals(trip.date, back.date)
        assertEquals(trip.departureTime, back.departureTime)
        assertEquals(trip.start, back.start)
        assertEquals(trip.stops.map { it.place }, back.stops.map { it.place })
        assertEquals(trip.stops.map { it.dwell }, back.stops.map { it.dwell })
        assertEquals(trip.stops.map { it.needsDaylight }, back.stops.map { it.needsDaylight })
        assertNotEquals(trip.id, back.id)
    }

    @Test
    fun exportNeverContainsKeyMaterial() {
        val text = TripJson.encode(DemoData.moroccoTrip("t1", LocalDate.of(2026, 10, 20)))
        assertFalse(text.contains("AIza"))
        assertFalse(text.contains("key", ignoreCase = true))
    }

    @Test
    fun rejectsForeignJson() {
        assertNull(TripJson.decode("{\"hello\":1}"))
        assertNull(TripJson.decode("not json"))
    }
}
