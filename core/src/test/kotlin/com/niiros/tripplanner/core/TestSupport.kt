package com.niiros.tripplanner.core

import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.model.PlaceRef
import com.niiros.tripplanner.core.model.Stop
import com.niiros.tripplanner.core.repo.PointInfo
import com.niiros.tripplanner.core.repo.PointInfoRepository
import com.niiros.tripplanner.core.repo.RouteRepository
import com.niiros.tripplanner.core.repo.RouteResult
import com.niiros.tripplanner.core.repo.WeatherRepository
import com.niiros.tripplanner.core.repo.WeatherResult
import com.niiros.tripplanner.core.schedule.Leg
import com.niiros.tripplanner.core.schedule.LegSource
import com.niiros.tripplanner.core.schedule.Schedule
import com.niiros.tripplanner.core.schedule.ScheduledStop
import com.niiros.tripplanner.core.zmanim.DayZmanim
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

val TEST_ZONE: ZoneId = ZoneId.of("Africa/Casablanca")
val TEST_DATE: LocalDate = LocalDate.of(2026, 10, 20)

fun at(h: Int, m: Int = 0, zone: ZoneId = TEST_ZONE, date: LocalDate = TEST_DATE): Instant =
    ZonedDateTime.of(date, LocalTime.of(h, m), zone).toInstant()

fun minutes(n: Long): Duration = Duration.ofMinutes(n)

/** Fixed, round-number zmanim so rule tests don't depend on astronomy. */
fun fixedZmanim(
    sunrise: Instant = at(7, 0),
    chatzot: Instant = at(13, 0),
    minchaGedola: Instant = at(13, 30),
    minchaKetana: Instant = at(16, 30),
    plag: Instant = at(17, 45),
    sunset: Instant = at(19, 0),
    tzeit: Instant? = at(19, 35),
) = DayZmanim(sunrise, chatzot, minchaGedola, minchaKetana, plag, sunset, tzeit)

fun place(name: String, lat: Double = 31.0, lng: Double = -7.0) = PlaceRef(name, null, GeoPoint(lat, lng))

fun stop(id: String, dwellMinutes: Long = 30, daylight: Boolean = false, mincha: Boolean = false, arvit: Boolean = false, lat: Double = 31.0, lng: Double = -7.0) =
    Stop(id, place(id, lat, lng), minutes(dwellMinutes), daylight, mincha, arvit)

fun scheduled(index: Int, s: Stop, arrival: Instant, departure: Instant = arrival.plus(s.dwell), zone: ZoneId = TEST_ZONE) =
    ScheduledStop(index, s, zone, false, Leg(minutes(60), 50_000, LegSource.TRAFFIC), arrival, departure)

fun schedule(departure: Instant, vararg stops: ScheduledStop) = Schedule(departure, TEST_ZONE, stops.toList())

/** Route fake with a fixed duration per leg; records calls. */
class FixedRoutes(
    private val minutesPerLeg: Long = 60,
    private val trafficExtraMinutes: Long = 10,
) : RouteRepository {
    val calls = mutableListOf<Triple<GeoPoint, Instant?, Boolean>>()
    override suspend fun route(origin: GeoPoint, destination: GeoPoint, departure: Instant?, trafficAware: Boolean): RouteResult {
        calls += Triple(destination, departure, trafficAware)
        val static = minutes(minutesPerLeg)
        val dur = if (trafficAware) static.plus(minutes(trafficExtraMinutes)) else static
        return RouteResult.Found(dur, static, 50_000)
    }
}

class MapPointInfo(private val map: Map<GeoPoint, PointInfo>) : PointInfoRepository {
    override suspend fun pointInfo(point: GeoPoint): PointInfo? = map[point]
}

class NoWeather(private val result: WeatherResult = WeatherResult.BeyondHorizon) : WeatherRepository {
    override suspend fun hourly(point: GeoPoint, date: LocalDate): WeatherResult = result
}
