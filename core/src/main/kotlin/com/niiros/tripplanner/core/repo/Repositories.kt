package com.niiros.tripplanner.core.repo

import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.model.PlaceRef
import com.niiros.tripplanner.core.model.ServiceError
import com.niiros.tripplanner.core.weather.HourlyForecast
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface RouteResult {
    data class Found(val duration: Duration, val staticDuration: Duration, val distanceMeters: Int) : RouteResult
    data object NotFound : RouteResult
    data class Failed(val error: ServiceError) : RouteResult
}

/** Driving route between two points. [departure] null means "no departure time, no traffic". */
interface RouteRepository {
    suspend fun route(origin: GeoPoint, destination: GeoPoint, departure: Instant?, trafficAware: Boolean): RouteResult
}

/** Static facts about a coordinate: its IANA time zone and terrain elevation. */
data class PointInfo(val zone: ZoneId?, val elevationM: Double?)

interface PointInfoRepository {
    /** Returns null when the lookup failed (offline etc.). */
    suspend fun pointInfo(point: GeoPoint): PointInfo?
}

sealed interface WeatherResult {
    data class Ok(val forecast: HourlyForecast) : WeatherResult
    data object BeyondHorizon : WeatherResult
    data class Failed(val error: ServiceError) : WeatherResult
}

interface WeatherRepository {
    /** Hourly forecast covering [date] (local date at the point, with a margin on both sides). */
    suspend fun hourly(point: GeoPoint, date: LocalDate): WeatherResult
}

data class PlaceSuggestion(val id: String, val primary: String, val secondary: String?)

sealed interface PlaceSearchResult {
    data class Ok(val suggestions: List<PlaceSuggestion>) : PlaceSearchResult
    data class Failed(val error: ServiceError) : PlaceSearchResult
}

interface PlaceSearchRepository {
    suspend fun autocomplete(query: String, biasCenter: GeoPoint?): PlaceSearchResult
    suspend fun details(suggestion: PlaceSuggestion): PlaceRef?
}
