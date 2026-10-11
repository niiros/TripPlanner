package com.niiros.tripplanner.core.schedule

import com.niiros.tripplanner.core.model.Trip
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Sequential schedule:
 *   depart(i) = arrive(i) + dwell(i)          (or the recorded actual departure)
 *   arrive(i+1) = depart(i) + drive(i, i+1)  (each leg requested with depart(i))
 */
class ScheduleCalculator(private val legs: LegPlanner) {

    fun tripDeparture(trip: Trip, startZone: ZoneId): Instant =
        ZonedDateTime.of(trip.date, trip.departureTime, startZone).toInstant()

    /**
     * @param startZone zone of the start point (the trip's zone).
     * @param zoneOf resolves a stop's zone; null means "same as the previous point".
     */
    suspend fun compute(
        trip: Trip,
        startZone: ZoneId,
        zoneOf: (stopId: String) -> ZoneId?,
        now: Instant,
    ): Schedule {
        val departure = tripDeparture(trip, startZone)
        val start = trip.start ?: return Schedule(departure, startZone, emptyList())
        var previousPoint = start.point
        var previousZone = startZone
        var cursor = departure
        val result = ArrayList<com.niiros.tripplanner.core.schedule.ScheduledStop>(trip.stops.size)
        trip.stops.forEachIndexed { index, stop ->
            val leg = legs.leg(previousPoint, stop.place.point, cursor, now)
            val arrival = cursor.plus(leg.duration)
            val planned = arrival.plus(stop.dwell)
            val actual = stop.actualDeparture
            val stopDeparture = if (actual != null && !actual.isBefore(arrival)) actual else planned
            val zone = zoneOf(stop.id) ?: previousZone
            result += ScheduledStop(
                index = index,
                stop = stop,
                zone = zone,
                zoneChanged = zone.rules != previousZone.rules && zone != previousZone,
                legIn = leg,
                arrival = arrival,
                departure = stopDeparture,
            )
            previousPoint = stop.place.point
            previousZone = zone
            cursor = stopDeparture
        }
        return Schedule(departure, startZone, result)
    }
}
