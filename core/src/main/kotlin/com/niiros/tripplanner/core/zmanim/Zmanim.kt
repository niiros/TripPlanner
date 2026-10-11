package com.niiros.tripplanner.core.zmanim

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.model.TzeitMethod
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/** The day's zmanim for one location. All values are absolute instants. */
data class DayZmanim(
    val sunrise: Instant,
    val chatzot: Instant,
    val minchaGedola: Instant,
    val minchaKetana: Instant,
    val plagHamincha: Instant,
    /** Sea-level sunset (shkia). */
    val sunset: Instant,
    /** Null when the sun does not reach the required depression (high latitudes in summer). */
    val tzeit: Instant?,
)

/**
 * Thin adapter over the KosherJava library. Sha'ot zmaniyot follow the Gra (sea-level sunrise to
 * sea-level sunset). Elevation is deliberately not applied, so sunset is the sea-level shkia.
 *
 * The library's API is built on java.util Date/Calendar; they are converted to java.time at this
 * boundary and do not leak into the rest of the app.
 */
class ZmanimCalculator {

    fun compute(point: GeoPoint, date: LocalDate, zone: ZoneId, method: TzeitMethod): DayZmanim? {
        val location = GeoLocation("stop", point.lat, point.lng, 0.0, TimeZone.getTimeZone(zone))
        val calendar = ComplexZmanimCalendar(location)
        calendar.isUseElevation = false
        calendar.calendar.timeZone = TimeZone.getTimeZone(zone)
        calendar.calendar.set(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0, 0)

        val sunrise = calendar.seaLevelSunrise?.toInstant() ?: return null
        val sunset = calendar.seaLevelSunset?.toInstant() ?: return null
        val chatzot = calendar.chatzos?.toInstant() ?: return null
        val gedola = calendar.minchaGedola?.toInstant() ?: return null
        val ketana = calendar.minchaKetana?.toInstant() ?: return null
        val plag = calendar.plagHamincha?.toInstant() ?: return null
        val tzeit = when (method) {
            TzeitMethod.DEGREES_8_5 -> calendar.tzaisGeonim8Point5Degrees?.toInstant()
            TzeitMethod.FIXED_18_MIN -> sunset.plus(Duration.ofMinutes(18))
            TzeitMethod.RABBEINU_TAM_72 -> calendar.tzais72?.toInstant()
        }
        return DayZmanim(sunrise, chatzot, gedola, ketana, plag, sunset, tzeit)
    }
}
