package com.niiros.tripplanner.core.weather

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** One hour of forecast at a point. Units: °C, %, km/h, UV index. */
data class HourPoint(
    val time: Instant,
    val temperatureC: Double,
    val apparentC: Double?,
    val precipitationProbability: Int?,
    val weatherCode: Int?,
    val windKmh: Double?,
    val uvIndex: Double?,
) {
    val feelsLikeC: Double get() = apparentC ?: temperatureC
}

data class HourlyForecast(
    val hours: List<HourPoint>,
    val zone: ZoneId?,
    /** Elevation the provider downscaled the forecast to, in meters. */
    val elevationM: Double?,
)

enum class WeatherCondition { CLEAR, MAINLY_CLEAR, PARTLY_CLOUDY, OVERCAST, FOG, DRIZZLE, RAIN, FREEZING_RAIN, SNOW, SHOWERS, SNOW_SHOWERS, THUNDERSTORM, UNKNOWN }

enum class WeatherIcon { CLEAR_DAY, CLEAR_NIGHT, PARTLY_CLOUDY, CLOUDY, RAIN, STORM, SNOW, FOG, WIND }

/** What the timeline shows for one stop at its arrival hour. */
data class StopWeather(
    val time: Instant,
    val icon: WeatherIcon,
    val condition: WeatherCondition,
    val temperatureC: Int,
    /** Shown as "מרגיש N°" only when it differs from the temperature by 3° or more. */
    val feelsLikeC: Int?,
    /** Shown only when strong. */
    val windKmh: Int?,
    /** Shown only when 30% or more. */
    val precipitationProbability: Int?,
    val highUv: Boolean,
    val isNight: Boolean,
)

object WeatherRules {
    const val FEELS_LIKE_DIFF_C = 3.0
    const val STRONG_WIND_KMH = 30.0
    const val SHOW_RAIN_PROBABILITY = 30
    const val HIGH_UV_INDEX = 6.0

    /** Only use a forecast hour this close to the requested time. */
    val MAX_HOUR_DISTANCE: Duration = Duration.ofMinutes(90)

    /** WMO weather interpretation codes, as emitted by Open-Meteo. */
    fun condition(code: Int?): WeatherCondition = when (code) {
        0 -> WeatherCondition.CLEAR
        1 -> WeatherCondition.MAINLY_CLEAR
        2 -> WeatherCondition.PARTLY_CLOUDY
        3 -> WeatherCondition.OVERCAST
        45, 48 -> WeatherCondition.FOG
        51, 53, 55 -> WeatherCondition.DRIZZLE
        56, 57, 66, 67 -> WeatherCondition.FREEZING_RAIN
        61, 63, 65 -> WeatherCondition.RAIN
        71, 73, 75, 77 -> WeatherCondition.SNOW
        80, 81, 82 -> WeatherCondition.SHOWERS
        85, 86 -> WeatherCondition.SNOW_SHOWERS
        95, 96, 99 -> WeatherCondition.THUNDERSTORM
        else -> WeatherCondition.UNKNOWN
    }

    fun isSnow(code: Int?): Boolean = condition(code).let { it == WeatherCondition.SNOW || it == WeatherCondition.SNOW_SHOWERS }

    fun icon(condition: WeatherCondition, windKmh: Double?, isNight: Boolean): WeatherIcon {
        val windy = (windKmh ?: 0.0) >= STRONG_WIND_KMH
        return when (condition) {
            WeatherCondition.THUNDERSTORM -> WeatherIcon.STORM
            WeatherCondition.SNOW, WeatherCondition.SNOW_SHOWERS -> WeatherIcon.SNOW
            WeatherCondition.RAIN, WeatherCondition.DRIZZLE, WeatherCondition.SHOWERS, WeatherCondition.FREEZING_RAIN -> WeatherIcon.RAIN
            WeatherCondition.FOG -> WeatherIcon.FOG
            else -> when {
                windy -> WeatherIcon.WIND
                condition == WeatherCondition.OVERCAST -> WeatherIcon.CLOUDY
                condition == WeatherCondition.PARTLY_CLOUDY -> WeatherIcon.PARTLY_CLOUDY
                isNight -> WeatherIcon.CLEAR_NIGHT
                else -> WeatherIcon.CLEAR_DAY
            }
        }
    }

    fun nearestHour(forecast: HourlyForecast, time: Instant): HourPoint? =
        forecast.hours
            .minByOrNull { abs(Duration.between(it.time, time).seconds) }
            ?.takeIf { abs(Duration.between(it.time, time).seconds) <= MAX_HOUR_DISTANCE.seconds }

    /** Hours inside [start, end], plus the hour nearest to [start] so short stays still count. */
    fun hoursDuring(forecast: HourlyForecast, start: Instant, end: Instant): List<HourPoint> {
        val inside = forecast.hours.filter { !it.time.isBefore(start) && !it.time.isAfter(end) }
        val nearest = nearestHour(forecast, start)
        return (listOfNotNull(nearest) + inside).distinctBy { it.time }.sortedBy { it.time }
    }

    /**
     * @param sunrise and [sunset] at the stop; arrival at or after sunset (or before sunrise) is night.
     */
    fun stopWeather(forecast: HourlyForecast, arrival: Instant, sunrise: Instant?, sunset: Instant?): StopWeather? {
        val hour = nearestHour(forecast, arrival) ?: return null
        val isNight = when {
            sunset != null && !arrival.isBefore(sunset) -> true
            sunrise != null && arrival.isBefore(sunrise) -> true
            else -> false
        }
        val condition = condition(hour.weatherCode)
        val feels = hour.apparentC?.takeIf { abs(it - hour.temperatureC) >= FEELS_LIKE_DIFF_C }
        return StopWeather(
            time = hour.time,
            icon = icon(condition, hour.windKmh, isNight),
            condition = condition,
            temperatureC = hour.temperatureC.roundToInt(),
            feelsLikeC = feels?.roundToInt(),
            windKmh = hour.windKmh?.takeIf { it >= STRONG_WIND_KMH }?.roundToInt(),
            precipitationProbability = hour.precipitationProbability?.takeIf { it >= SHOW_RAIN_PROBABILITY },
            highUv = (hour.uvIndex ?: 0.0) >= HIGH_UV_INDEX,
            isNight = isNight,
        )
    }

    /** Elevation rounded to the nearest 10 m, or null if unknown. */
    fun roundElevation(meters: Double?): Int? =
        meters?.takeIf { !it.isNaN() }?.let { (it / 10.0).roundToInt() * 10 }
}
