package com.niiros.tripplanner.core.demo

import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.model.PlaceRef
import com.niiros.tripplanner.core.model.ServiceError
import com.niiros.tripplanner.core.model.Stop
import com.niiros.tripplanner.core.model.Trip
import com.niiros.tripplanner.core.repo.PlaceSearchRepository
import com.niiros.tripplanner.core.repo.PlaceSearchResult
import com.niiros.tripplanner.core.repo.PlaceSuggestion
import com.niiros.tripplanner.core.repo.PointInfo
import com.niiros.tripplanner.core.repo.PointInfoRepository
import com.niiros.tripplanner.core.repo.RouteRepository
import com.niiros.tripplanner.core.repo.RouteResult
import com.niiros.tripplanner.core.repo.WeatherRepository
import com.niiros.tripplanner.core.repo.WeatherResult
import com.niiros.tripplanner.core.weather.HourPoint
import com.niiros.tripplanner.core.weather.HourlyForecast
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.cos

/**
 * Demo data: a day from Marrakech over the High Atlas (Tizi n'Tichka) to Ouarzazate.
 * Coordinates are real; elevations are approximate and the weather is synthetic, built so that
 * the mountain pass is much colder and windier than the valleys. Not a forecast.
 */
object DemoData {
    val MOROCCO: ZoneId = ZoneId.of("Africa/Casablanca")
    val ISRAEL: ZoneId = ZoneId.of("Asia/Jerusalem")

    data class DemoPlace(val place: PlaceRef, val elevationM: Double, val zone: ZoneId, val aliases: List<String>)

    val places: List<DemoPlace> = listOf(
        DemoPlace(PlaceRef("מרקש - כיכר ג'מע אל-פנא", "מרקש, מרוקו", GeoPoint(31.6258, -7.9891), "demo:marrakech"), 470.0, MOROCCO, listOf("marrakech", "מרקש")),
        DemoPlace(PlaceRef("מעבר טיזי נ'טישקה", "הרי האטלס הגבוה, מרוקו", GeoPoint(31.2869, -7.3833), "demo:tichka"), 2260.0, MOROCCO, listOf("tichka", "טישקה", "atlas", "אטלס")),
        DemoPlace(PlaceRef("קסר אית בן חדו", "אית בן חדו, מרוקו", GeoPoint(31.0470, -7.1293), "demo:benhaddou"), 1250.0, MOROCCO, listOf("ait benhaddou", "בן חדו")),
        DemoPlace(PlaceRef("קסבת טאוריירט", "ורזזאת, מרוקו", GeoPoint(30.9200, -6.9050), "demo:taourirt"), 1140.0, MOROCCO, listOf("ouarzazate", "taourirt", "ורזזאת")),
        DemoPlace(PlaceRef("נווה המדבר פינט", "ליד ורזזאת, מרוקו", GeoPoint(30.8810, -6.9930), "demo:fint"), 1100.0, MOROCCO, listOf("fint", "פינט", "oasis")),
        DemoPlace(PlaceRef("ירושלים - שער יפו", "ירושלים", GeoPoint(31.7767, 35.2275), "demo:jaffagate"), 770.0, ISRAEL, listOf("jerusalem", "ירושלים")),
        DemoPlace(PlaceRef("מצדה", "ים המלח", GeoPoint(31.3156, 35.3536), "demo:masada"), 60.0, ISRAEL, listOf("masada", "מצדה")),
        DemoPlace(PlaceRef("עין גדי", "ים המלח", GeoPoint(31.4625, 35.3880), "demo:eingedi"), -350.0, ISRAEL, listOf("ein gedi", "עין גדי")),
    )

    fun byId(id: String): DemoPlace? = places.firstOrNull { it.place.placeId == id }

    /** The sample day. [date] defaults to tomorrow so the demo shows predicted (fake) traffic. */
    fun moroccoTrip(id: String, date: LocalDate): Trip {
        fun p(key: String) = byId("demo:$key")!!
        fun stop(key: String, minutes: Long, daylight: Boolean = false) = Stop(
            id = "$id-$key",
            place = p(key).place,
            dwell = Duration.ofMinutes(minutes),
            needsDaylight = daylight,
            zoneId = p(key).zone.id,
            elevationM = p(key).elevationM,
        )
        return Trip(
            id = id,
            name = "מרקש - האטלס - ורזזאת",
            date = date,
            departureTime = LocalTime.of(9, 30),
            start = p("marrakech").place,
            stops = listOf(
                stop("tichka", 30),
                stop("benhaddou", 180, daylight = true),
                stop("taourirt", 90),
                stop("fint", 45, daylight = true),
            ),
            startZoneId = MOROCCO.id,
            startElevationM = p("marrakech").elevationM,
        )
    }
}

class FakePointInfoRepository(private val fallback: ZoneId) : PointInfoRepository {
    override suspend fun pointInfo(point: GeoPoint): PointInfo {
        DemoData.places.firstOrNull { it.place.point.distanceMetersTo(point) < 2_000 }?.let {
            return PointInfo(it.zone, it.elevationM)
        }
        val zone = when {
            point.lat in 27.0..36.0 && point.lng in -13.5..-1.0 -> DemoData.MOROCCO
            point.lat in 29.4..33.4 && point.lng in 34.2..35.9 -> DemoData.ISRAEL
            else -> fallback
        }
        return PointInfo(zone, null)
    }
}

/** Deterministic routes: road distance = 1.4 x straight line, slower on mountain legs. */
class FakeRouteRepository(private val pointInfo: FakePointInfoRepository) : RouteRepository {
    override suspend fun route(origin: GeoPoint, destination: GeoPoint, departure: Instant?, trafficAware: Boolean): RouteResult {
        val straight = origin.distanceMetersTo(destination)
        if (straight < 1.0) return RouteResult.Found(Duration.ZERO, Duration.ZERO, 0)
        val road = straight * 1.4
        val high = listOf(origin, destination).any { (pointInfo.pointInfo(it).elevationM ?: 0.0) > 1500 }
        val speedKmh = if (high) 45.0 else 65.0
        val static = Duration.ofSeconds((road / (speedKmh * 1000 / 3600)).toLong())
        val withTraffic = if (trafficAware) static.plus(static.dividedBy(20)) else static
        return RouteResult.Found(withTraffic, static, road.toInt())
    }
}

/** Synthetic hourly weather: a diurnal curve, cooled 6.5 °C per km of elevation. */
class FakeWeatherRepository(private val pointInfo: FakePointInfoRepository) : WeatherRepository {
    override suspend fun hourly(point: GeoPoint, date: LocalDate): WeatherResult {
        val info = pointInfo.pointInfo(point)
        val zone = info.zone ?: return WeatherResult.Failed(ServiceError.UNKNOWN)
        val elevation = info.elevationM ?: 300.0
        val mountain = elevation > 1800
        val start = ZonedDateTime.of(date.minusDays(1), LocalTime.MIDNIGHT, zone).toInstant()
        val hours = (0 until 72).map { h ->
            val time = start.plus(Duration.ofHours(h.toLong()))
            val local = time.atZone(zone).hour
            // Coldest at 06:00, warmest at 15:00.
            val phase = cos((local - 15) / 24.0 * 2 * PI)
            val seaLevel = 25.0 + 8.0 * phase
            val temp = seaLevel - 6.5 * elevation / 1000.0
            val wind = if (mountain) 38.0 else 12.0
            val apparent = temp - (if (wind > 20) (wind - 20) * 0.25 else 0.0) + (if (temp > 27) 2.0 else 0.0)
            val afternoon = local in 13..18
            HourPoint(
                time = time,
                temperatureC = temp,
                apparentC = apparent,
                precipitationProbability = if (mountain && afternoon) 45 else 5,
                weatherCode = when {
                    mountain && afternoon -> 80
                    mountain -> 3
                    local in 7..18 -> 1
                    else -> 0
                },
                windKmh = wind,
                uvIndex = if (local in 10..15) (if (mountain) 8.0 else 6.5) else 1.0,
            )
        }
        return WeatherResult.Ok(HourlyForecast(hours, zone, elevation))
    }
}

class FakePlaceSearchRepository : PlaceSearchRepository {
    override suspend fun autocomplete(query: String, biasCenter: GeoPoint?): PlaceSearchResult {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return PlaceSearchResult.Ok(emptyList())
        val matches = DemoData.places
            .filter { p -> p.place.name.lowercase().contains(q) || p.aliases.any { it.contains(q) } }
            .sortedBy { p -> biasCenter?.let { p.place.point.distanceMetersTo(it) } ?: 0.0 }
            .map { PlaceSuggestion(it.place.placeId!!, it.place.name, it.place.address) }
        return PlaceSearchResult.Ok(matches)
    }

    override suspend fun details(suggestion: PlaceSuggestion): PlaceRef? = DemoData.byId(suggestion.id)?.place
}
