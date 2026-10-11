package com.niiros.tripplanner.core.plan

import com.niiros.tripplanner.core.clothing.ClothingRules
import com.niiros.tripplanner.core.clothing.ClothingSummary
import com.niiros.tripplanner.core.clothing.StayWeather
import com.niiros.tripplanner.core.daylight.DaylightCheck
import com.niiros.tripplanner.core.daylight.DaylightRules
import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.model.PlaceRef
import com.niiros.tripplanner.core.model.PlannerSettings
import com.niiros.tripplanner.core.model.ServiceError
import com.niiros.tripplanner.core.model.Trip
import com.niiros.tripplanner.core.prayer.PrayerPlan
import com.niiros.tripplanner.core.prayer.PrayerRules
import com.niiros.tripplanner.core.prayer.StopZmanim
import com.niiros.tripplanner.core.repo.PointInfo
import com.niiros.tripplanner.core.repo.PointInfoRepository
import com.niiros.tripplanner.core.repo.WeatherRepository
import com.niiros.tripplanner.core.repo.WeatherResult
import com.niiros.tripplanner.core.schedule.Schedule
import com.niiros.tripplanner.core.schedule.ScheduleCalculator
import com.niiros.tripplanner.core.schedule.ScheduledStop
import com.niiros.tripplanner.core.weather.HourlyForecast
import com.niiros.tripplanner.core.weather.StopWeather
import com.niiros.tripplanner.core.weather.WeatherRules
import com.niiros.tripplanner.core.zmanim.DayZmanim
import com.niiros.tripplanner.core.zmanim.ZmanimCalculator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

enum class WeatherState { OK, NOT_YET, FAILED }

data class PointPlan(
    val place: PlaceRef,
    val zone: ZoneId,
    val elevationM: Int?,
    val zmanim: DayZmanim?,
    val weather: StopWeather?,
    val weatherState: WeatherState,
)

data class StopPlan(
    val scheduled: ScheduledStop,
    val point: PointPlan,
    val daylight: DaylightCheck?,
)

data class DayPlan(
    val trip: Trip,
    val schedule: Schedule,
    val start: PointPlan?,
    val stops: List<StopPlan>,
    /** Zmanim shown in the summary card: the last stop of the day (or the start). */
    val summaryZmanim: DayZmanim?,
    val summaryPlaceName: String?,
    val summaryZone: ZoneId,
    val prayer: PrayerPlan,
    val clothing: ClothingSummary?,
    val errors: Set<ServiceError>,
    /** The start zone is a guess (lookup failed and nothing cached). */
    val zoneUncertain: Boolean,
    /** Resolved zone/elevation per point ("start" or stop id), to be cached by the caller. */
    val resolvedPointInfo: Map<String, PointInfo>,
) {
    val multiZone: Boolean get() = stops.any { it.scheduled.zoneChanged }
}

/**
 * Orchestrates one full recalculation of the day: zones, legs (in order), zmanim, weather,
 * daylight, prayer and clothing. Pure Kotlin; repositories are injected.
 */
class DayPlanner(
    private val pointInfo: PointInfoRepository,
    private val weather: WeatherRepository,
    private val scheduleCalculator: ScheduleCalculator,
    private val zmanim: ZmanimCalculator = ZmanimCalculator(),
) {
    suspend fun plan(trip: Trip, settings: PlannerSettings, now: Instant, fallbackZone: ZoneId): DayPlan = coroutineScope {
        val errors = LinkedHashSet<ServiceError>()
        val resolved = java.util.concurrent.ConcurrentHashMap<String, PointInfo>()

        // 1. Zones and elevations, cached on the trip when known.
        suspend fun info(key: String, point: GeoPoint, zoneId: String?, elevation: Double?): PointInfo? {
            val cachedZone = zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            if (cachedZone != null && elevation != null) return PointInfo(cachedZone, elevation)
            val fetched = pointInfo.pointInfo(point)
            val merged = PointInfo(cachedZone ?: fetched?.zone, elevation ?: fetched?.elevationM)
            if (fetched != null) resolved[key] = merged
            return merged
        }

        val start = trip.start
        val startInfo = start?.let { info(START_KEY, it.point, trip.startZoneId, trip.startElevationM) }
        val stopInfos = trip.stops.map { s ->
            async { s.id to info(s.id, s.place.point, s.zoneId, s.elevationM) }
        }.awaitAll().toMap()

        val startZone = startInfo?.zone ?: fallbackZone
        val zoneUncertain = start != null && startInfo?.zone == null

        // 2. Schedule, legs computed in order.
        val schedule = scheduleCalculator.compute(trip, startZone, { id -> stopInfos[id]?.zone }, now)
        schedule.stops.forEach { s -> s.legIn.error?.let { errors += it } }

        // 3. Weather for every point in parallel.
        val points: List<Pair<String, GeoPoint>> =
            listOfNotNull(start?.let { START_KEY to it.point }) + trip.stops.map { it.id to it.place.point }
        val forecasts: Map<String, WeatherResult> = points.map { (key, point) ->
            async { key to weather.hourly(point, trip.date) }
        }.awaitAll().toMap()
        forecasts.values.filterIsInstance<WeatherResult.Failed>().forEach { errors += it.error }

        fun pointPlan(key: String, place: PlaceRef, zone: ZoneId, elevation: Double?, at: Instant): PointPlan {
            val z = zmanim.compute(place.point, trip.date, zone, settings.tzeitMethod)
            val result = forecasts[key]
            val forecast = (result as? WeatherResult.Ok)?.forecast
            val stopWeather = forecast?.let { WeatherRules.stopWeather(it, at, z?.sunrise, z?.sunset) }
            val state = when {
                stopWeather != null -> WeatherState.OK
                result is WeatherResult.Failed -> WeatherState.FAILED
                else -> WeatherState.NOT_YET
            }
            val elev = elevation ?: forecast?.elevationM
            return PointPlan(place, zone, WeatherRules.roundElevation(elev), z, stopWeather, state)
        }

        val startPlan = start?.let { pointPlan(START_KEY, it, startZone, startInfo?.elevationM, schedule.departure) }
        val stopPlans = schedule.stops.map { s ->
            val p = pointPlan(s.stop.id, s.stop.place, s.zone, stopInfos[s.stop.id]?.elevationM, s.arrival)
            val check = p.zmanim?.let { DaylightRules.check(schedule, s, it, settings.sunsetBuffer, trip.date) }
            StopPlan(s, p, check)
        }

        // 4. Prayer.
        val prayer = PrayerRules.plan(stopPlans.mapNotNull { sp -> sp.point.zmanim?.let { StopZmanim(sp.scheduled, it) } })

        // 5. Clothing: the start at departure plus every stop over its stay.
        val stays = ArrayList<StayWeather>()
        if (startPlan != null) {
            (forecasts[START_KEY] as? WeatherResult.Ok)?.forecast?.let { f ->
                stays += StayWeather(startPlan.place.name, startPlan.zone, startPlan.elevationM,
                    WeatherRules.hoursDuring(f, schedule.departure, schedule.departure))
            }
        }
        stopPlans.forEach { sp ->
            val f: HourlyForecast? = (forecasts[sp.scheduled.stop.id] as? WeatherResult.Ok)?.forecast
            if (f != null) {
                stays += StayWeather(sp.point.place.name, sp.point.zone, sp.point.elevationM,
                    WeatherRules.hoursDuring(f, sp.scheduled.arrival, sp.scheduled.departure))
            } else if (sp.point.elevationM != null) {
                // Keep elevation in the high-point check even without a forecast.
                stays += StayWeather(sp.point.place.name, sp.point.zone, sp.point.elevationM, emptyList())
            }
        }
        val clothing = ClothingRules.summarize(stays)

        val summary = stopPlans.lastOrNull()?.point ?: startPlan
        DayPlan(
            trip = trip,
            schedule = schedule,
            start = startPlan,
            stops = stopPlans,
            summaryZmanim = summary?.zmanim,
            summaryPlaceName = summary?.place?.name,
            summaryZone = summary?.zone ?: startZone,
            prayer = prayer,
            clothing = clothing,
            errors = errors,
            zoneUncertain = zoneUncertain,
            resolvedPointInfo = resolved,
        )
    }

    companion object {
        const val START_KEY = "start"

        /** Which stop is "current" for "יצאתי עכשיו": the last one already reached, or -1 for the start. */
        fun currentStopIndex(schedule: Schedule, now: Instant): Int =
            schedule.stops.indexOfLast { !it.arrival.isAfter(now) }

        val DEFAULT_DWELL: Duration = Duration.ofMinutes(30)
    }
}
