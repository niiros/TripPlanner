package com.niiros.tripplanner.core

import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.model.ServiceError
import com.niiros.tripplanner.core.model.Trip
import com.niiros.tripplanner.core.repo.RouteRepository
import com.niiros.tripplanner.core.repo.RouteResult
import com.niiros.tripplanner.core.schedule.LegPlanner
import com.niiros.tripplanner.core.schedule.LegSource
import com.niiros.tripplanner.core.schedule.ScheduleCalculator
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleTest {
    private val now = at(6, 0)

    private fun trip(vararg stops: com.niiros.tripplanner.core.model.Stop, departure: LocalTime = LocalTime.of(8, 0)) =
        Trip("t", "test", TEST_DATE, departure, place("start", 31.6, -8.0), stops.toList())

    @Test
    fun `sequential chain uses each departure for the next leg`() = runTest {
        val routes = FixedRoutes(minutesPerLeg = 60, trafficExtraMinutes = 10)
        val calc = ScheduleCalculator(LegPlanner(routes, { now }))
        val s = calc.compute(trip(stop("a", 30, lat = 31.1), stop("b", 45, lat = 31.2)), TEST_ZONE, { null }, now)

        assertEquals(at(9, 10), s.stops[0].arrival)
        assertEquals(at(9, 40), s.stops[0].departure)
        assertEquals(at(10, 50), s.stops[1].arrival)
        assertEquals(at(11, 35), s.stops[1].departure)
        // The second leg was requested with the first stop's departure time, with traffic.
        assertEquals(at(9, 40), routes.calls[1].second)
        assertTrue(routes.calls.all { it.third })
        assertTrue(s.stops.all { it.legIn.source == LegSource.TRAFFIC })
        assertEquals(Duration.ofMinutes(140), s.totalDrive)
        assertEquals(Duration.ofMinutes(75), s.totalDwell)
        assertEquals(at(11, 35), s.endOfDay)
    }

    @Test
    fun `past departure time falls back to non-traffic durations`() = runTest {
        val routes = FixedRoutes(minutesPerLeg = 60, trafficExtraMinutes = 10)
        val lateNow = at(12, 0)
        val calc = ScheduleCalculator(LegPlanner(routes, { lateNow }))
        val s = calc.compute(trip(stop("a", 30, lat = 31.1)), TEST_ZONE, { null }, lateNow)
        assertEquals(LegSource.NO_TRAFFIC_PAST, s.stops[0].legIn.source)
        assertEquals(at(9, 0), s.stops[0].arrival)
        assertEquals(null, routes.calls[0].second)
        assertEquals(false, routes.calls[0].third)
    }

    @Test
    fun `identical traffic and static durations mean no traffic data for the region`() = runTest {
        val calc = ScheduleCalculator(LegPlanner(FixedRoutes(trafficExtraMinutes = 0), { now }))
        val s = calc.compute(trip(stop("a", lat = 31.1)), TEST_ZONE, { null }, now)
        assertEquals(LegSource.NO_TRAFFIC_REGION, s.stops[0].legIn.source)
        assertTrue(!s.stops[0].legIn.hasTraffic)
    }

    @Test
    fun `reordering stops recalculates the whole chain`() = runTest {
        val routes = object : RouteRepository {
            override suspend fun route(origin: GeoPoint, destination: GeoPoint, departure: Instant?, trafficAware: Boolean): RouteResult {
                // Duration proportional to distance so order matters.
                val km = origin.distanceMetersTo(destination) / 1000
                val d = Duration.ofMinutes(km.toLong())
                return RouteResult.Found(d, d, (km * 1000).toInt())
            }
        }
        val calc = ScheduleCalculator(LegPlanner(routes, { now }))
        val near = stop("near", 30, lat = 31.7)
        val far = stop("far", 30, lat = 32.6)
        val forward = calc.compute(trip(near, far), TEST_ZONE, { null }, now)
        val reversed = calc.compute(trip(far, near), TEST_ZONE, { null }, now)

        assertEquals(listOf("near", "far"), forward.stops.map { it.stop.id })
        assertEquals(listOf("far", "near"), reversed.stops.map { it.stop.id })
        assertTrue(reversed.totalDrive > forward.totalDrive)
        assertTrue(reversed.stops.last().arrival.isAfter(forward.stops.first().arrival))
    }

    @Test
    fun `actual departure from a stop re-anchors the rest of the chain`() = runTest {
        val calc = ScheduleCalculator(LegPlanner(FixedRoutes(60, 0), { now }))
        val a = stop("a", 30, lat = 31.1).copy(actualDeparture = at(10, 30))
        val s = calc.compute(trip(a, stop("b", 30, lat = 31.2)), TEST_ZONE, { null }, now)
        assertEquals(at(10, 30), s.stops[0].departure)
        assertEquals(at(11, 30), s.stops[1].arrival)
    }

    @Test
    fun `no route keeps the chain going with a flagged estimate`() = runTest {
        val routes = object : RouteRepository {
            override suspend fun route(origin: GeoPoint, destination: GeoPoint, departure: Instant?, trafficAware: Boolean) = RouteResult.NotFound
        }
        val calc = ScheduleCalculator(LegPlanner(routes, { now }))
        val s = calc.compute(trip(stop("a", lat = 31.1)), TEST_ZONE, { null }, now)
        assertEquals(LegSource.ESTIMATE, s.stops[0].legIn.source)
        assertEquals(ServiceError.NO_ROUTE, s.stops[0].legIn.error)
        assertTrue(s.stops[0].arrival.isAfter(s.departure))
    }

    @Test
    fun `legs are cached by origin, destination and departure rounded to 15 minutes`() = runTest {
        val routes = FixedRoutes()
        val planner = LegPlanner(routes, { now })
        val o = GeoPoint(31.0, -7.0)
        val d = GeoPoint(31.5, -7.5)
        planner.leg(o, d, at(9, 2), now)
        planner.leg(o, d, at(9, 6), now)
        assertEquals(1, routes.calls.size)
        planner.leg(o, d, at(9, 10), now)
        assertEquals(2, routes.calls.size)
    }

    @Test
    fun `device time zone different from trip time zone does not shift trip times`() = runTest {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jerusalem"))
            val calc = ScheduleCalculator(LegPlanner(FixedRoutes(60, 0), { now }))
            val s = calc.compute(trip(stop("a", lat = 31.1)), TEST_ZONE, { null }, now)
            // 08:00 Morocco (UTC+1 in October) is 07:00 UTC regardless of the device zone.
            assertEquals(Instant.parse("2026-10-20T07:00:00Z"), s.departure)
            assertEquals(LocalTime.of(9, 0), s.stops[0].arrival.atZone(s.stops[0].zone).toLocalTime())
            assertEquals(LocalTime.of(11, 0), s.stops[0].arrival.atZone(ZoneId.of("Asia/Jerusalem")).toLocalTime())
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `stops in another zone are flagged as a zone change`() = runTest {
        val calc = ScheduleCalculator(LegPlanner(FixedRoutes(60, 0), { now }))
        val s = calc.compute(trip(stop("a", lat = 31.1), stop("b", lat = 31.2)), TEST_ZONE,
            { id -> if (id == "b") ZoneId.of("Europe/Madrid") else null }, now)
        assertEquals(false, s.stops[0].zoneChanged)
        assertEquals(true, s.stops[1].zoneChanged)
        assertEquals(ZoneId.of("Europe/Madrid"), s.stops[1].zone)
    }
}
