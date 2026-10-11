package com.niiros.tripplanner.ui.common

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.niiros.tripplanner.R
import com.niiros.tripplanner.core.clothing.ClothingItem
import com.niiros.tripplanner.core.model.ServiceError
import com.niiros.tripplanner.core.model.TzeitMethod
import com.niiros.tripplanner.core.weather.StopWeather
import com.niiros.tripplanner.core.weather.WeatherCondition
import com.niiros.tripplanner.core.weather.WeatherIcon
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * All user-facing formatting in one place, backed by strings.xml. Usable from composables
 * ([rememberTexts]) and from non-UI code such as the share text.
 */
class Texts(private val context: Context) {
    private val hebrew: Locale = Locale.forLanguageTag("he")
    private val hm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(hebrew)
    private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ROOT)

    fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** 24-hour time in the given zone (never the device zone implicitly). */
    fun time(instant: Instant, zone: ZoneId): String = hm.format(instant.atZone(zone))
    fun time(t: LocalTime): String = hm.format(t)
    fun date(d: LocalDate): String = dateFormat.format(d)
    fun shortDate(d: LocalDate): String = shortDate.format(d)

    fun zoneName(zone: ZoneId): String = "${zone.getDisplayName(TextStyle.FULL, hebrew)} (${zone.id})"

    fun duration(d: Duration): String {
        val total = ((d.seconds + 30) / 60).coerceAtLeast(0)
        return if (total >= 60) s(R.string.duration_h_mm, total / 60, total % 60) else s(R.string.duration_min, total)
    }

    /** "H:MM" for the "before sunset" label. */
    fun hMm(d: Duration): String {
        val total = (d.seconds / 60).coerceAtLeast(0)
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
    }

    fun km(meters: Int): String =
        if (meters < 10_000) String.format(Locale.ROOT, "%.1f", meters / 1000.0) else String.format(Locale.ROOT, "%d", (meters + 500) / 1000)

    fun elevation(m: Int): String = s(R.string.elevation_line, String.format(Locale.US, "%,d", m))

    fun error(e: ServiceError): String = s(
        when (e) {
            ServiceError.OFFLINE -> R.string.error_offline
            ServiceError.KEY_MISSING -> R.string.error_key_missing
            ServiceError.KEY_INVALID -> R.string.error_key_invalid
            ServiceError.QUOTA -> R.string.error_quota
            ServiceError.NO_ROUTE -> R.string.error_no_route
            ServiceError.SERVER, ServiceError.UNKNOWN -> R.string.error_server
        },
    )

    fun condition(c: WeatherCondition): String = s(
        when (c) {
            WeatherCondition.CLEAR -> R.string.wx_clear
            WeatherCondition.MAINLY_CLEAR -> R.string.wx_mainly_clear
            WeatherCondition.PARTLY_CLOUDY -> R.string.wx_partly_cloudy
            WeatherCondition.OVERCAST -> R.string.wx_overcast
            WeatherCondition.FOG -> R.string.wx_fog
            WeatherCondition.DRIZZLE -> R.string.wx_drizzle
            WeatherCondition.RAIN -> R.string.wx_rain
            WeatherCondition.FREEZING_RAIN -> R.string.wx_freezing_rain
            WeatherCondition.SNOW -> R.string.wx_snow
            WeatherCondition.SHOWERS -> R.string.wx_showers
            WeatherCondition.SNOW_SHOWERS -> R.string.wx_snow_showers
            WeatherCondition.THUNDERSTORM -> R.string.wx_thunderstorm
            WeatherCondition.UNKNOWN -> R.string.wx_unknown
        },
    )

    fun iconDescription(icon: WeatherIcon): String = s(
        when (icon) {
            WeatherIcon.CLEAR_DAY -> R.string.icon_clear_day
            WeatherIcon.CLEAR_NIGHT -> R.string.icon_clear_night
            WeatherIcon.PARTLY_CLOUDY -> R.string.icon_partly_cloudy
            WeatherIcon.CLOUDY -> R.string.icon_cloudy
            WeatherIcon.RAIN -> R.string.icon_rain
            WeatherIcon.STORM -> R.string.icon_storm
            WeatherIcon.SNOW -> R.string.icon_snow
            WeatherIcon.FOG -> R.string.icon_fog
            WeatherIcon.WIND -> R.string.icon_wind
        },
    )

    /** "18° · בהיר · מרגיש 14° · רוח 38 קמ"ש · גשם 45% · קרינה גבוהה" */
    fun weatherLine(w: StopWeather): String {
        val parts = mutableListOf(s(R.string.weather_temp, w.temperatureC), condition(w.condition))
        w.feelsLikeC?.let { parts += s(R.string.weather_feels_like, it) }
        w.windKmh?.let { parts += s(R.string.weather_wind, it) }
        w.precipitationProbability?.let { parts += s(R.string.weather_rain, it) }
        if (w.highUv) parts += s(R.string.weather_high_uv)
        return parts.joinToString(" · ")
    }

    fun clothingItem(item: ClothingItem): String = s(
        when (item) {
            ClothingItem.LAYERS -> R.string.clothing_layers
            ClothingItem.WARM_LAYER -> R.string.clothing_warm_layer
            ClothingItem.COAT_HAT_GLOVES -> R.string.clothing_coat
            ClothingItem.LIGHT_CLOTHING_WATER -> R.string.clothing_light
            ClothingItem.WINDBREAKER -> R.string.clothing_windbreaker
            ClothingItem.RAIN_JACKET -> R.string.clothing_rain_jacket
            ClothingItem.HAT_SUNSCREEN -> R.string.clothing_hat_sunscreen
            ClothingItem.WATERPROOF_SHOES -> R.string.clothing_waterproof_shoes
        },
    )

    fun tzeitMethod(m: TzeitMethod): String = s(
        when (m) {
            TzeitMethod.DEGREES_8_5 -> R.string.tzeit_8_5
            TzeitMethod.FIXED_18_MIN -> R.string.tzeit_18
            TzeitMethod.RABBEINU_TAM_72 -> R.string.tzeit_72
        },
    )

    companion object {
        @DrawableRes
        fun iconRes(icon: WeatherIcon): Int = when (icon) {
            WeatherIcon.CLEAR_DAY -> R.drawable.ic_weather_clear_day
            WeatherIcon.CLEAR_NIGHT -> R.drawable.ic_weather_clear_night
            WeatherIcon.PARTLY_CLOUDY -> R.drawable.ic_weather_partly_cloudy
            WeatherIcon.CLOUDY -> R.drawable.ic_weather_cloudy
            WeatherIcon.RAIN -> R.drawable.ic_weather_rain
            WeatherIcon.STORM -> R.drawable.ic_weather_storm
            WeatherIcon.SNOW -> R.drawable.ic_weather_snow
            WeatherIcon.FOG -> R.drawable.ic_weather_fog
            WeatherIcon.WIND -> R.drawable.ic_weather_wind
        }
    }
}

@Composable
fun rememberTexts(): Texts {
    val context = LocalContext.current
    return remember(context) { Texts(context) }
}
