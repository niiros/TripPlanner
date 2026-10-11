package com.niiros.tripplanner.core.schedule

import com.niiros.tripplanner.core.model.ServiceError
import com.niiros.tripplanner.core.model.Stop
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

enum class LegSource {
    /** Predicted traffic for the leg's departure time. */
    TRAFFIC,

    /** Departure time already passed: non-traffic duration. */
    NO_TRAFFIC_PAST,

    /** Provider returned identical traffic and static durations: no traffic data for the region. */
    NO_TRAFFIC_REGION,

    /** Traffic-aware request failed, the non-traffic retry worked. */
    NO_TRAFFIC_FALLBACK,

    /** No route at all (or the service failed): rough straight-line estimate so the chain continues. */
    ESTIMATE,
}

data class Leg(
    val duration: Duration,
    val distanceMeters: Int?,
    val source: LegSource,
    val error: ServiceError? = null,
) {
    val hasTraffic: Boolean get() = source == LegSource.TRAFFIC
}

data class ScheduledStop(
    val index: Int,
    val stop: Stop,
    val zone: ZoneId,
    /** True when this stop is in a different zone than the previous point. */
    val zoneChanged: Boolean,
    val legIn: Leg,
    val arrival: Instant,
    val departure: Instant,
) {
    val stay: Duration get() = Duration.between(arrival, departure)
}

data class Schedule(
    val departure: Instant,
    val startZone: ZoneId,
    val stops: List<ScheduledStop>,
) {
    val totalDrive: Duration get() = stops.fold(Duration.ZERO) { acc, s -> acc + s.legIn.duration }
    val totalDwell: Duration get() = stops.fold(Duration.ZERO) { acc, s -> acc + s.stay }
    val endOfDay: Instant get() = stops.lastOrNull()?.departure ?: departure
    val endZone: ZoneId get() = stops.lastOrNull()?.zone ?: startZone
    val totalDistanceMeters: Int get() = stops.sumOf { it.legIn.distanceMeters ?: 0 }
}
