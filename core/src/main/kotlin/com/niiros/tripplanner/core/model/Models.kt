package com.niiros.tripplanner.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** WGS84 coordinate. */
data class GeoPoint(val lat: Double, val lng: Double) {
    /** Cache key with ~100 m resolution, stable across tiny GPS jitter. */
    fun key(decimals: Int = 3): String = "%.${decimals}f,%.${decimals}f".format(java.util.Locale.ROOT, lat, lng)

    fun distanceMetersTo(other: GeoPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(other.lat - lat)
        val dLng = Math.toRadians(other.lng - lng)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}

/** A named place picked by the user (or the current location). */
data class PlaceRef(
    val name: String,
    val address: String?,
    val point: GeoPoint,
    val placeId: String? = null,
)

/** One destination in the day, in visiting order. */
data class Stop(
    val id: String,
    val place: PlaceRef,
    val dwell: Duration,
    val needsDaylight: Boolean = false,
    val minchaHere: Boolean = false,
    val arvitHere: Boolean = false,
    /** Set by "יצאתי עכשיו": the real departure from this stop, overriding arrival + dwell. */
    val actualDeparture: Instant? = null,
    /** Cached from the weather provider so the zone and elevation survive offline use. */
    val zoneId: String? = null,
    val elevationM: Double? = null,
)

data class Trip(
    val id: String,
    val name: String,
    val date: LocalDate,
    /** Wall-clock departure in the start point's time zone. */
    val departureTime: LocalTime,
    val start: PlaceRef?,
    val stops: List<Stop>,
    val startZoneId: String? = null,
    val startElevationM: Double? = null,
)

enum class TzeitMethod { DEGREES_8_5, FIXED_18_MIN, RABBEINU_TAM_72 }

data class PlannerSettings(
    val sunsetBuffer: Duration = Duration.ofMinutes(30),
    val tzeitMethod: TzeitMethod = TzeitMethod.DEGREES_8_5,
)

/** Failure classes surfaced to the user (mapped to Hebrew strings in the app). */
enum class ServiceError { OFFLINE, KEY_MISSING, KEY_INVALID, QUOTA, NO_ROUTE, SERVER, UNKNOWN }
