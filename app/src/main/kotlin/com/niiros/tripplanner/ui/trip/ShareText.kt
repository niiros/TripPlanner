package com.niiros.tripplanner.ui.trip

import com.niiros.tripplanner.R
import com.niiros.tripplanner.core.plan.DayPlan
import com.niiros.tripplanner.ui.common.Texts

/** Plain Hebrew summary for the Android share sheet. Places and times only. */
object ShareText {
    fun build(plan: DayPlan, t: Texts): String = buildString {
        val s = plan.schedule
        appendLine(t.s(R.string.share_header, plan.trip.name, t.date(plan.trip.date)))
        appendLine(t.s(R.string.share_zone, t.zoneName(s.startZone)))
        plan.start?.let { appendLine(t.s(R.string.share_start, it.place.name, t.time(s.departure, s.startZone))) }
        plan.stops.forEach { sp ->
            val leg = sp.scheduled.legIn
            val legText = t.s(R.string.leg_summary, t.duration(leg.duration), t.km(leg.distanceMeters ?: 0)) +
                if (!leg.hasTraffic) " (" + t.s(R.string.leg_no_traffic) + ")" else ""
            appendLine(t.s(R.string.share_leg, legText))
            val z = sp.scheduled.zone
            append(t.s(R.string.share_stop, sp.scheduled.index + 1, sp.point.place.name, t.time(sp.scheduled.arrival, z), t.time(sp.scheduled.departure, z)))
            sp.point.weather?.let { append(" · ").append(t.weatherLine(it)) }
            sp.point.elevationM?.let { append(" · ").append(t.elevation(it)) }
            if (sp.scheduled.stop.id in plan.prayer.minchaStopIds) append(" · ").append(t.s(R.string.prayer_mincha_here))
            if (sp.scheduled.stop.id in plan.prayer.arvitStopIds) append(" · ").append(t.s(R.string.prayer_arvit_here))
            appendLine()
        }
        appendLine(t.s(R.string.share_end, t.time(s.endOfDay, s.endZone)))
        val z = plan.summaryZmanim
        if (z != null) {
            val zone = plan.summaryZone
            appendLine(
                t.s(
                    R.string.share_zmanim,
                    plan.summaryPlaceName ?: "",
                    t.time(z.chatzot, zone), t.time(z.minchaGedola, zone), t.time(z.minchaKetana, zone),
                    t.time(z.plagHamincha, zone), t.time(z.sunset, zone), z.tzeit?.let { t.time(it, zone) } ?: "-",
                ),
            )
        }
        plan.prayer.minchaMissingDeadline?.let { appendLine(t.s(R.string.prayer_mincha_missing, t.time(it, plan.summaryZone))) }
    }.trim()
}
