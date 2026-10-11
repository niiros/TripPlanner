package com.niiros.tripplanner.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.niiros.tripplanner.R
import com.niiros.tripplanner.core.daylight.DaylightFix
import com.niiros.tripplanner.core.daylight.DaylightLabel
import com.niiros.tripplanner.core.daylight.DaylightStatus
import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.plan.PointPlan
import com.niiros.tripplanner.core.plan.StopPlan
import com.niiros.tripplanner.core.prayer.MinchaProblem
import com.niiros.tripplanner.core.prayer.PrayerPlan
import com.niiros.tripplanner.core.schedule.Leg
import com.niiros.tripplanner.core.schedule.LegSource
import com.niiros.tripplanner.ui.common.SectionCard
import com.niiros.tripplanner.ui.common.StatusChip
import com.niiros.tripplanner.ui.common.Texts
import com.niiros.tripplanner.ui.common.WeatherRow
import com.niiros.tripplanner.ui.common.rememberTexts
import com.niiros.tripplanner.ui.theme.LocalTripColors
import java.time.Instant
import java.time.ZoneId

@Composable
fun StartCard(
    point: PointPlan?,
    departure: Instant,
    zone: ZoneId,
    onChangeStart: () -> Unit,
    onUseLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = rememberTexts()
    val c = LocalTripColors.current
    SectionCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IndexBadge("●", c.ok)
            Column(Modifier.weight(1f)) {
                Text(t.s(R.string.trip_start_label) + " · " + t.time(departure, zone), style = MaterialTheme.typography.labelLarge, color = c.ok)
                Text(point?.place?.name ?: t.s(R.string.trip_no_start), style = MaterialTheme.typography.titleMedium, color = c.text)
                point?.elevationM?.let { Text(t.elevation(it), style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
            }
        }
        if (point != null) WeatherRow(point)
        FlowRow2 {
            TextButton(onClick = onUseLocation, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text(t.s(R.string.trip_set_start_current)) }
            TextButton(onClick = onChangeStart, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text(t.s(R.string.trip_set_start_search)) }
        }
    }
}

@Composable
fun LegRow(leg: Leg, modifier: Modifier = Modifier) {
    val t = rememberTexts()
    val c = LocalTripColors.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(width = 3.dp, height = 28.dp).background(c.cardBorder))
        Column {
            Text(
                t.s(R.string.leg_summary, t.duration(leg.duration), t.km(leg.distanceMeters ?: 0)),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            legNote(leg, t)?.let { (text, color) -> Text(text, style = MaterialTheme.typography.bodySmall, color = color) }
        }
    }
}

private fun legNote(leg: Leg, t: Texts): Pair<String, Color>? = when (leg.source) {
    LegSource.TRAFFIC -> null
    LegSource.NO_TRAFFIC_PAST, LegSource.NO_TRAFFIC_REGION, LegSource.NO_TRAFFIC_FALLBACK -> t.s(R.string.leg_no_traffic) to Color(0xFF4A5673)
    LegSource.ESTIMATE ->
        (if (leg.error == com.niiros.tripplanner.core.model.ServiceError.NO_ROUTE) t.s(R.string.leg_estimate_no_route) else t.s(R.string.leg_estimate_error)) to Color(0xFFB91C1C)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow2(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
fun IndexBadge(text: String, color: Color) {
    Box(
        Modifier
            .size(30.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** One stop on the timeline. [dragHandle] is supplied by the reorderable list. */
@Composable
fun StopCard(
    sp: StopPlan,
    prayer: PrayerPlan,
    startZone: ZoneId,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onFix: (DaylightFix) -> Unit,
    onNavigate: (GeoPoint, String, Boolean) -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = rememberTexts()
    val c = LocalTripColors.current
    val s = sp.scheduled
    val zone = s.zone
    val daylight = sp.daylight
    val late = daylight?.status == DaylightStatus.LATE
    SectionCard(modifier, border = if (late) c.late else c.cardBorder) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IndexBadge("${s.index + 1}", if (late) c.late else c.structure)
            Column(Modifier.weight(1f)) {
                Text(sp.point.place.name, style = MaterialTheme.typography.titleMedium, color = c.text)
                sp.point.elevationM?.let { Text(t.elevation(it), style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
            }
            dragHandle()
        }
        Text(
            t.s(R.string.stop_arrival_departure, t.time(s.arrival, zone), t.time(s.departure, zone)) + " · " +
                t.s(R.string.stop_dwell, t.duration(s.stay)),
            style = MaterialTheme.typography.bodyLarge,
            color = c.text,
        )
        if (s.zoneChanged) StatusChip(t.s(R.string.stop_zone_changed, t.zoneName(zone)), c.midday, c.middayBackground)
        WeatherRow(sp.point)

        FlowRow2 {
            daylight?.label?.let { label ->
                when (label) {
                    DaylightLabel.None -> Unit
                    is DaylightLabel.NearMidday -> StatusChip(t.s(R.string.label_midday, t.time(label.chatzot, zone)), c.midday, c.middayBackground)
                    is DaylightLabel.BeforeSunset -> StatusChip(t.s(R.string.label_before_sunset, t.hMm(label.remaining)), c.textSecondary, c.background)
                    is DaylightLabel.AfterSunset -> StatusChip(t.s(R.string.label_after_sunset, label.late.toMinutes().toInt()), c.late, c.lateBackground)
                }
            }
            when (daylight?.status) {
                DaylightStatus.OK -> StatusChip(t.s(R.string.status_ok), c.ok, c.okBackground)
                DaylightStatus.WARNING_NEAR_SUNSET -> StatusChip(t.s(R.string.status_near_sunset), c.midday, c.middayBackground)
                DaylightStatus.WARNING_STAY_PAST_SUNSET -> StatusChip(t.s(R.string.status_stay_past_sunset), c.midday, c.middayBackground)
                DaylightStatus.LATE -> StatusChip(t.s(R.string.status_late), Color.White, c.late)
                null -> Unit
            }
            val id = s.stop.id
            if (id in prayer.minchaStopIds) {
                StatusChip(t.s(if (prayer.minchaSuggested) R.string.prayer_mincha_suggested else R.string.prayer_mincha_here), c.structure, Color(0xFFE3E8F4))
            }
            if (id in prayer.arvitStopIds) {
                StatusChip(t.s(if (prayer.arvitSuggested) R.string.prayer_arvit_suggested else R.string.prayer_arvit_here), c.structure, Color(0xFFE3E8F4))
            }
            prayer.minchaProblems[id]?.let { problem ->
                val z = sp.point.zmanim
                val text = when (problem) {
                    MinchaProblem.ENDS_BEFORE_GEDOLA -> t.s(R.string.prayer_problem_before_gedola, z?.let { t.time(it.minchaGedola, zone) } ?: "")
                    MinchaProblem.STARTS_AFTER_SUNSET -> t.s(R.string.prayer_problem_after_sunset, z?.let { t.time(it.sunset, zone) } ?: "")
                }
                StatusChip(text, c.late, c.lateBackground)
            }
        }

        daylight?.fixes?.takeIf { it.isNotEmpty() }?.let { fixes ->
            FlowRow2 {
                fixes.forEach { fix ->
                    val label = when (fix) {
                        is DaylightFix.LeaveEarlier -> t.s(R.string.fix_leave_at, t.time(fix.newDeparture))
                        is DaylightFix.ShortenDwell -> t.s(R.string.fix_shorten, fix.by.toMinutes().toInt()) + " " + t.s(R.string.fix_shorten_detail, fix.stopName)
                    }
                    OutlinedButton(onClick = { onFix(fix) }, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text(label) }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            var navOpen by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { navOpen = true }, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                    Icon(painterResource(R.drawable.ic_navigation), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" " + t.s(R.string.nav_title))
                }
                DropdownMenu(expanded = navOpen, onDismissRequest = { navOpen = false }) {
                    DropdownMenuItem(text = { Text(t.s(R.string.nav_google_maps)) }, onClick = { navOpen = false; onNavigate(sp.point.place.point, sp.point.place.name, false) })
                    DropdownMenuItem(text = { Text(t.s(R.string.nav_waze)) }, onClick = { navOpen = false; onNavigate(sp.point.place.point, sp.point.place.name, true) })
                }
            }
            Box(Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = t.s(R.string.action_edit)) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = t.s(R.string.action_delete)) }
            var moreOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { moreOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = t.s(R.string.action_more)) }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    if (!isFirst) {
                        DropdownMenuItem(
                            text = { Text(t.s(R.string.stop_move_up)) },
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                            onClick = { moreOpen = false; onMove(-1) },
                        )
                    }
                    if (!isLast) {
                        DropdownMenuItem(
                            text = { Text(t.s(R.string.stop_move_down)) },
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                            onClick = { moreOpen = false; onMove(1) },
                        )
                    }
                }
            }
        }
    }
}
