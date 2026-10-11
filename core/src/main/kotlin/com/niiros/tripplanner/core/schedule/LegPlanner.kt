package com.niiros.tripplanner.core.schedule

import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.model.ServiceError
import com.niiros.tripplanner.core.repo.RouteRepository
import com.niiros.tripplanner.core.repo.RouteResult
import com.niiros.tripplanner.core.util.TimeMath
import com.niiros.tripplanner.core.util.TtlCache
import java.time.Duration
import java.time.Instant

/**
 * Computes one driving leg, choosing traffic-aware or non-traffic requests and caching results
 * keyed by origin, destination and departure rounded to [CACHE_ROUNDING_MINUTES].
 */
class LegPlanner(
    private val routes: RouteRepository,
    clock: () -> Instant,
    cacheTtl: Duration = Duration.ofMinutes(30),
) {
    private data class Key(val origin: String, val destination: String, val departureSlot: Instant?)

    private val cache = TtlCache<Key, Leg>(cacheTtl, clock)

    suspend fun leg(origin: GeoPoint, destination: GeoPoint, departure: Instant, now: Instant): Leg {
        // The Routes API rejects traffic-aware requests with a departure time in the past.
        val inFuture = departure.isAfter(now.plus(PAST_MARGIN))
        val key = Key(
            origin.key(5),
            destination.key(5),
            if (inFuture) TimeMath.roundToMinutes(departure, CACHE_ROUNDING_MINUTES) else null,
        )
        cache.get(key)?.let { return it }
        val leg = compute(origin, destination, departure, inFuture)
        // Do not cache transient failures, so a retry after reconnecting gets real data.
        if (leg.source != LegSource.ESTIMATE || leg.error == ServiceError.NO_ROUTE) cache.put(key, leg)
        return leg
    }

    private suspend fun compute(origin: GeoPoint, destination: GeoPoint, departure: Instant, inFuture: Boolean): Leg {
        if (!inFuture) {
            return when (val r = routes.route(origin, destination, null, trafficAware = false)) {
                is RouteResult.Found -> Leg(r.staticDuration, r.distanceMeters, LegSource.NO_TRAFFIC_PAST)
                RouteResult.NotFound -> estimate(origin, destination, ServiceError.NO_ROUTE)
                is RouteResult.Failed -> estimate(origin, destination, r.error)
            }
        }
        return when (val r = routes.route(origin, destination, departure, trafficAware = true)) {
            is RouteResult.Found -> {
                val source = if (r.duration == r.staticDuration) LegSource.NO_TRAFFIC_REGION else LegSource.TRAFFIC
                Leg(r.duration, r.distanceMeters, source)
            }
            RouteResult.NotFound -> estimate(origin, destination, ServiceError.NO_ROUTE)
            is RouteResult.Failed -> when (r.error) {
                ServiceError.OFFLINE, ServiceError.KEY_MISSING, ServiceError.KEY_INVALID, ServiceError.QUOTA ->
                    estimate(origin, destination, r.error)
                else -> when (val retry = routes.route(origin, destination, null, trafficAware = false)) {
                    is RouteResult.Found -> Leg(retry.staticDuration, retry.distanceMeters, LegSource.NO_TRAFFIC_FALLBACK)
                    RouteResult.NotFound -> estimate(origin, destination, ServiceError.NO_ROUTE)
                    is RouteResult.Failed -> estimate(origin, destination, retry.error)
                }
            }
        }
    }

    private fun estimate(origin: GeoPoint, destination: GeoPoint, error: ServiceError): Leg {
        val roadMeters = origin.distanceMetersTo(destination) * ROAD_FACTOR
        val seconds = (roadMeters / ESTIMATE_SPEED_MPS).toLong()
        return Leg(Duration.ofSeconds(seconds), roadMeters.toInt(), LegSource.ESTIMATE, error)
    }

    fun clearCache() = cache.clear()

    companion object {
        const val CACHE_ROUNDING_MINUTES = 15L
        private val PAST_MARGIN: Duration = Duration.ofMinutes(1)
        /** Straight line to road distance, a common rule of thumb for rough estimates. */
        const val ROAD_FACTOR = 1.3
        /** 50 km/h. */
        const val ESTIMATE_SPEED_MPS = 50_000.0 / 3600.0
    }
}
