package com.niiros.tripplanner.data.net

import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.model.ServiceError
import com.niiros.tripplanner.core.repo.PointInfo
import com.niiros.tripplanner.core.repo.PointInfoRepository
import com.niiros.tripplanner.core.repo.WeatherRepository
import com.niiros.tripplanner.core.repo.WeatherResult
import com.niiros.tripplanner.core.util.TtlCache
import com.niiros.tripplanner.core.weather.HourPoint
import com.niiros.tripplanner.core.weather.HourlyForecast
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.HttpException
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Open-Meteo forecast API (free for non-commercial use, no key). Only coordinates and dates are
 * sent. `timeformat=unixtime` returns absolute timestamps; `timezone=auto` returns the IANA zone
 * of the coordinates, which java.time then applies with correct DST.
 */
interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("timezone") timezone: String = "auto",
        @Query("timeformat") timeFormat: String = "unixtime",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
    ): ForecastResponse

    @GET("v1/forecast")
    suspend fun pointInfo(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "temperature_2m",
        @Query("forecast_days") forecastDays: Int = 1,
        @Query("timezone") timezone: String = "auto",
        @Query("timeformat") timeFormat: String = "unixtime",
    ): ForecastResponse

    @GET("v1/elevation")
    suspend fun elevation(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
    ): ElevationResponse
}

@Serializable
data class ForecastResponse(
    val elevation: Double? = null,
    val timezone: String? = null,
    val hourly: HourlyDto? = null,
)

@Serializable
data class HourlyDto(
    val time: List<Long> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("apparent_temperature") val apparent: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("uv_index") val uvIndex: List<Double?> = emptyList(),
)

@Serializable
data class ElevationResponse(val elevation: List<Double?> = emptyList())

internal fun Throwable.toServiceError(): ServiceError = when (this) {
    is IOException -> ServiceError.OFFLINE
    is HttpException -> when (code()) {
        401, 403 -> ServiceError.KEY_INVALID
        429 -> ServiceError.QUOTA
        in 500..599 -> ServiceError.SERVER
        else -> ServiceError.UNKNOWN
    }
    else -> ServiceError.UNKNOWN
}

class OpenMeteoWeatherRepository(
    private val api: OpenMeteoApi,
    private val clock: () -> Instant,
) : WeatherRepository {
    private val cache = TtlCache<String, HourlyForecast>(Duration.ofHours(1), clock)

    override suspend fun hourly(point: GeoPoint, date: LocalDate): WeatherResult {
        val today = clock().atZone(ZoneOffset.UTC).toLocalDate()
        // The forecast covers 16 days including today; past days beyond a short window are not forecasts.
        val horizon = today.plusDays(HORIZON_DAYS - 1)
        if (date.isAfter(horizon) || date.isBefore(today.minusDays(PAST_DAYS))) return WeatherResult.BeyondHorizon
        val key = "${point.key()}|$date"
        cache.get(key)?.let { return WeatherResult.Ok(it) }
        val start = date.minusDays(1)
        val end = minOf(date.plusDays(1), horizon)
        return try {
            val r = api.forecast(point.lat, point.lng, HOURLY_VARIABLES, start.toString(), end.toString())
            val forecast = r.toForecast() ?: return WeatherResult.BeyondHorizon
            cache.put(key, forecast)
            WeatherResult.Ok(forecast)
        } catch (e: HttpException) {
            // Open-Meteo answers 400 for dates outside the allowed range.
            if (e.code() == 400) WeatherResult.BeyondHorizon else WeatherResult.Failed(e.toServiceError())
        } catch (e: IOException) {
            WeatherResult.Failed(ServiceError.OFFLINE)
        } catch (e: kotlinx.serialization.SerializationException) {
            WeatherResult.Failed(ServiceError.SERVER)
        }
    }

    private fun ForecastResponse.toForecast(): HourlyForecast? {
        val h = hourly ?: return null
        if (h.time.isEmpty()) return null
        val hours = h.time.indices.mapNotNull { i ->
            val temp = h.temperature.getOrNull(i) ?: return@mapNotNull null
            HourPoint(
                time = Instant.ofEpochSecond(h.time[i]),
                temperatureC = temp,
                apparentC = h.apparent.getOrNull(i),
                precipitationProbability = h.precipitationProbability.getOrNull(i),
                weatherCode = h.weatherCode.getOrNull(i),
                windKmh = h.windSpeed.getOrNull(i),
                uvIndex = h.uvIndex.getOrNull(i),
            )
        }
        if (hours.isEmpty()) return null
        return HourlyForecast(hours, timezone?.toZoneOrNull(), elevation?.takeIf { !it.isNaN() })
    }

    companion object {
        const val HOURLY_VARIABLES =
            "temperature_2m,apparent_temperature,precipitation_probability,weather_code,wind_speed_10m,uv_index"
        const val HORIZON_DAYS = 16L
        const val PAST_DAYS = 2L
    }
}

class OpenMeteoPointInfoRepository(private val api: OpenMeteoApi) : PointInfoRepository {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, PointInfo>()

    override suspend fun pointInfo(point: GeoPoint): PointInfo? {
        cache[point.key(4)]?.let { return it }
        return try {
            val r = api.pointInfo(point.lat, point.lng)
            val elevation = r.elevation?.takeIf { !it.isNaN() } ?: fallbackElevation(point)
            PointInfo(r.timezone?.toZoneOrNull(), elevation).also { cache[point.key(4)] = it }
        } catch (e: IOException) {
            null
        } catch (e: HttpException) {
            null
        } catch (e: kotlinx.serialization.SerializationException) {
            null
        }
    }

    /** Dedicated elevation endpoint, used only if the forecast response lacks elevation. */
    private suspend fun fallbackElevation(point: GeoPoint): Double? = try {
        api.elevation(point.lat, point.lng).elevation.firstOrNull()?.takeIf { !it.isNaN() }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        null
    }
}

private fun String.toZoneOrNull(): ZoneId? = try {
    if (this == "GMT" || isBlank()) null else ZoneId.of(this)
} catch (e: java.time.DateTimeException) {
    null
}
