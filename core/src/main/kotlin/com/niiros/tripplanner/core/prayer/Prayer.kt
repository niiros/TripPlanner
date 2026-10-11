package com.niiros.tripplanner.core.prayer

import com.niiros.tripplanner.core.schedule.ScheduledStop
import com.niiros.tripplanner.core.util.TimeMath
import com.niiros.tripplanner.core.zmanim.DayZmanim
import java.time.Instant

enum class MinchaProblem {
    /** Manually chosen stop ends before mincha gedola. */
    ENDS_BEFORE_GEDOLA,

    /** Manually chosen stop starts after sunset. */
    STARTS_AFTER_SUNSET,
}

data class PrayerPlan(
    /** Stops where mincha is planned (manual choices, or the single suggestion). */
    val minchaStopIds: List<String>,
    /** True when [minchaStopIds] is an automatic suggestion rather than the user's choice. */
    val minchaSuggested: Boolean,
    /** Set when no stay overlaps the mincha window: stop for mincha by this time. */
    val minchaMissingDeadline: Instant?,
    val minchaDeadlineZoneStopId: String?,
    val minchaProblems: Map<String, MinchaProblem>,
    val arvitStopIds: List<String>,
    val arvitSuggested: Boolean,
    /** Set when no stay extends past tzeit: tzeit after the last arrival. */
    val arvitTzeitNote: Instant?,
)

/** A stop's stay paired with the zmanim at its own location. */
data class StopZmanim(val stop: ScheduledStop, val zmanim: DayZmanim)

object PrayerRules {

    fun plan(stops: List<StopZmanim>): PrayerPlan {
        val (minchaIds, minchaSuggested, deadline, deadlineStop) = mincha(stops)
        val problems = stops
            .filter { it.stop.stop.minchaHere }
            .mapNotNull { s -> minchaProblem(s)?.let { s.stop.stop.id to it } }
            .toMap()
        val manualArvit = stops.filter { it.stop.stop.arvitHere }.map { it.stop.stop.id }
        val (arvitIds, arvitSuggested, tzeitNote) = if (manualArvit.isNotEmpty()) {
            Triple(manualArvit, false, null)
        } else {
            val first = stops.firstOrNull { s -> s.zmanim.tzeit?.let { s.stop.departure.isAfter(it) } == true }
            if (first != null) {
                Triple(listOf(first.stop.stop.id), true, null)
            } else {
                Triple(emptyList(), false, stops.lastOrNull()?.zmanim?.tzeit)
            }
        }
        return PrayerPlan(
            minchaStopIds = minchaIds,
            minchaSuggested = minchaSuggested,
            minchaMissingDeadline = deadline,
            minchaDeadlineZoneStopId = deadlineStop,
            minchaProblems = problems,
            arvitStopIds = arvitIds,
            arvitSuggested = arvitSuggested,
            arvitTzeitNote = tzeitNote,
        )
    }

    private data class MinchaChoice(
        val ids: List<String>,
        val suggested: Boolean,
        val deadline: Instant?,
        val deadlineStopId: String?,
    )

    private fun mincha(stops: List<StopZmanim>): MinchaChoice {
        val manual = stops.filter { it.stop.stop.minchaHere }.map { it.stop.stop.id }
        if (manual.isNotEmpty()) return MinchaChoice(manual, false, null, null)

        val inWindow = stops.filter { overlapsWindow(it) }
        if (inWindow.isEmpty()) {
            if (stops.isEmpty()) return MinchaChoice(emptyList(), false, null, null)
            // Where will the traveller be around sunset? The first stop reached after its own
            // sunset, otherwise the last stop of the day.
            val reference = stops.firstOrNull { it.stop.arrival.isAfter(it.zmanim.sunset) } ?: stops.last()
            return MinchaChoice(emptyList(), false, reference.zmanim.sunset, reference.stop.stop.id)
        }
        val preferred = inWindow.lastOrNull { overlapsLateWindow(it) } ?: inWindow.last()
        return MinchaChoice(listOf(preferred.stop.stop.id), true, null, null)
    }

    /** Mincha window at a stop = mincha gedola to sunset at that stop's location. */
    fun overlapsWindow(s: StopZmanim): Boolean =
        TimeMath.overlaps(s.stop.arrival, s.stop.departure, s.zmanim.minchaGedola, s.zmanim.sunset)

    /** The preferred part of the window: after mincha ketana. */
    fun overlapsLateWindow(s: StopZmanim): Boolean =
        TimeMath.overlaps(s.stop.arrival, s.stop.departure, s.zmanim.minchaKetana, s.zmanim.sunset)

    fun minchaProblem(s: StopZmanim): MinchaProblem? = when {
        s.stop.departure.isBefore(s.zmanim.minchaGedola) -> MinchaProblem.ENDS_BEFORE_GEDOLA
        s.stop.arrival.isAfter(s.zmanim.sunset) -> MinchaProblem.STARTS_AFTER_SUNSET
        else -> null
    }
}
