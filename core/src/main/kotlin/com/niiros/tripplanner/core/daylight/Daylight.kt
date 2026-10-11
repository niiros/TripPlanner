package com.niiros.tripplanner.core.daylight

import com.niiros.tripplanner.core.schedule.Schedule
import com.niiros.tripplanner.core.schedule.ScheduledStop
import com.niiros.tripplanner.core.util.TimeMath
import com.niiros.tripplanner.core.zmanim.DayZmanim
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One label per stop, chosen by time of day. */
sealed interface DaylightLabel {
    /** Stay ends before chatzot minus the midday window: no label at all. */
    data object None : DaylightLabel

    /** Stay overlaps chatzot plus/minus the midday window: "סמוך לחצות היום · HH:MM". */
    data class NearMidday(val chatzot: Instant) : DaylightLabel

    /** "H:MM שע' לפני השקיעה". */
    data class BeforeSunset(val remaining: Duration) : DaylightLabel

    /** "N דק' אחרי השקיעה". */
    data class AfterSunset(val late: Duration) : DaylightLabel
}

enum class DaylightStatus {
    OK,

    /** Arrival between sunset minus buffer and sunset. */
    WARNING_NEAR_SUNSET,

    /** Arrival is fine but the stay itself runs past sunset. */
    WARNING_STAY_PAST_SUNSET,

    /** Arrival after sunset. */
    LATE;

    val needsFix: Boolean get() = this != OK
}

sealed interface DaylightFix {
    /** Shift the whole trip start earlier ("לצאת ב-HH:MM"). [newDeparture] is in the start zone. */
    data class LeaveEarlier(val newDeparture: LocalTime, val shift: Duration) : DaylightFix

    /** Shorten the longest earlier dwell ("לקצר שהייה ב-N דק'"). */
    data class ShortenDwell(val stopId: String, val stopName: String, val by: Duration) : DaylightFix
}

data class DaylightCheck(
    val label: DaylightLabel,
    /** Only for stops marked "דורש אור יום". */
    val status: DaylightStatus?,
    val fixes: List<DaylightFix>,
)

object DaylightRules {
    val MIDDAY_WINDOW: Duration = Duration.ofMinutes(60)

    /** Dwell never shrinks below this when offering a fix. */
    val MIN_DWELL: Duration = Duration.ofMinutes(5)
    const val FIX_ROUNDING_MINUTES = 5L

    fun label(arrival: Instant, departure: Instant, z: DayZmanim): DaylightLabel {
        val middayStart = z.chatzot.minus(MIDDAY_WINDOW)
        val middayEnd = z.chatzot.plus(MIDDAY_WINDOW)
        return when {
            departure.isBefore(middayStart) -> DaylightLabel.None
            !arrival.isAfter(middayEnd) -> DaylightLabel.NearMidday(z.chatzot)
            !arrival.isAfter(z.sunset) -> DaylightLabel.BeforeSunset(Duration.between(arrival, z.sunset))
            else -> DaylightLabel.AfterSunset(Duration.between(z.sunset, arrival))
        }
    }

    fun status(arrival: Instant, departure: Instant, z: DayZmanim, buffer: Duration): DaylightStatus = when {
        arrival.isAfter(z.sunset) -> DaylightStatus.LATE
        arrival.isAfter(z.sunset.minus(buffer)) -> DaylightStatus.WARNING_NEAR_SUNSET
        departure.isAfter(z.sunset) -> DaylightStatus.WARNING_STAY_PAST_SUNSET
        else -> DaylightStatus.OK
    }

    /** How much earlier this stop must happen to become OK. */
    fun requiredShift(stop: ScheduledStop, z: DayZmanim, buffer: Duration, status: DaylightStatus): Duration =
        when (status) {
            DaylightStatus.OK -> Duration.ZERO
            DaylightStatus.LATE, DaylightStatus.WARNING_NEAR_SUNSET ->
                Duration.between(z.sunset.minus(buffer), stop.arrival)
            DaylightStatus.WARNING_STAY_PAST_SUNSET -> Duration.between(z.sunset, stop.departure)
        }

    fun fixes(
        schedule: Schedule,
        stop: ScheduledStop,
        z: DayZmanim,
        buffer: Duration,
        status: DaylightStatus,
        tripDate: LocalDate,
    ): List<DaylightFix> {
        if (!status.needsFix) return emptyList()
        val shift = requiredShift(stop, z, buffer, status)
        if (shift <= Duration.ZERO) return emptyList()
        val fixes = ArrayList<DaylightFix>(2)

        // Leave earlier: floor the new start to 5 minutes so the shown time is exact and sufficient.
        val newStart = TimeMath.floorToMinutes(schedule.departure.minus(shift), FIX_ROUNDING_MINUTES)
        val zone: ZoneId = schedule.startZone
        val newLocal = newStart.atZone(zone)
        if (newLocal.toLocalDate() == tripDate) {
            fixes += DaylightFix.LeaveEarlier(newLocal.toLocalTime(), Duration.between(newStart, schedule.departure))
        }

        // Shorten the longest earlier dwell, if it can absorb the shift.
        val by = Duration.ofMinutes(TimeMath.ceilMinutes(shift, FIX_ROUNDING_MINUTES))
        val longest = schedule.stops.take(stop.index).maxByOrNull { it.stop.dwell }
        if (longest != null && longest.stop.dwell.minus(by) >= MIN_DWELL) {
            fixes += DaylightFix.ShortenDwell(longest.stop.id, longest.stop.place.name, by)
        }
        return fixes
    }

    fun check(
        schedule: Schedule,
        stop: ScheduledStop,
        z: DayZmanim,
        buffer: Duration,
        tripDate: LocalDate,
    ): DaylightCheck {
        val label = label(stop.arrival, stop.departure, z)
        if (!stop.stop.needsDaylight) return DaylightCheck(label, null, emptyList())
        val status = status(stop.arrival, stop.departure, z, buffer)
        return DaylightCheck(label, status, fixes(schedule, stop, z, buffer, status, tripDate))
    }
}
