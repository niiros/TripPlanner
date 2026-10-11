package com.niiros.tripplanner.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.niiros.tripplanner.R
import com.niiros.tripplanner.core.clothing.ClothingSummary
import com.niiros.tripplanner.core.daylight.DaylightLabel
import com.niiros.tripplanner.core.daylight.DaylightStatus
import com.niiros.tripplanner.core.plan.DayPlan
import com.niiros.tripplanner.core.zmanim.DayZmanim
import com.niiros.tripplanner.ui.common.SectionCard
import com.niiros.tripplanner.ui.common.StatusChip
import com.niiros.tripplanner.ui.common.rememberTexts
import com.niiros.tripplanner.ui.theme.LocalTripColors
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Composable
fun ZmanimCard(zmanim: DayZmanim?, placeName: String?, zone: ZoneId, modifier: Modifier = Modifier) {
    val t = rememberTexts()
    val c = LocalTripColors.current
    SectionCard(modifier, container = c.zmanimCard, border = c.zmanimCard) {
        Text(t.s(R.string.zmanim_title), color = c.zmanimText, style = MaterialTheme.typography.titleMedium)
        if (placeName != null) {
            Text(t.s(R.string.zmanim_for_place, placeName), color = c.zmanimText.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
        }
        if (zmanim == null) {
            Text(t.s(R.string.zmanim_unavailable), color = c.zmanimText)
            return@SectionCard
        }
        val items = listOf(
            Triple(R.string.zman_chatzot, zmanim.chatzot, false),
            Triple(R.string.zman_mincha_gedola, zmanim.minchaGedola, false),
            Triple(R.string.zman_mincha_ketana, zmanim.minchaKetana, false),
            Triple(R.string.zman_plag, zmanim.plagHamincha, false),
            Triple(R.string.zman_sunset, zmanim.sunset, true),
            Triple(R.string.zman_tzeit, zmanim.tzeit, false),
        )
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, time, highlight) ->
                    val color = if (highlight) c.sunsetHighlight else c.zmanimText
                    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(t.s(label), color = color, style = MaterialTheme.typography.bodySmall)
                        Text(
                            time?.let { t.time(it, zone) } ?: "-",
                            color = color,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClothingCard(summary: ClothingSummary?, modifier: Modifier = Modifier) {
    val t = rememberTexts()
    val c = LocalTripColors.current
    SectionCard(modifier) {
        Text(t.s(R.string.clothing_title), style = MaterialTheme.typography.titleMedium, color = c.text)
        if (summary == null) {
            Text(t.s(R.string.clothing_no_data), color = c.textSecondary)
            return@SectionCard
        }
        Text(t.s(R.string.clothing_range, summary.min.feelsLikeC, summary.max.feelsLikeC, summary.spread), style = MaterialTheme.typography.titleLarge, color = c.text)
        var sentence = t.s(
            R.string.clothing_sentence,
            summary.min.placeName, t.time(summary.min.time, summary.min.zone), summary.min.feelsLikeC,
            summary.max.placeName, t.time(summary.max.time, summary.max.zone), summary.max.feelsLikeC,
        )
        summary.highest?.let { sentence += " " + t.s(R.string.clothing_highest, it.placeName, t.elevation(it.elevationM)) }
        Text(sentence, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            summary.items.forEach { advice ->
                val label = t.clothingItem(advice.item)
                val text = advice.singlePlace?.let { t.s(R.string.clothing_item_with_place, label, it) } ?: label
                StatusChip(text, c.structure, Color(0xFFE3E8F4))
            }
        }
    }
}

/** Horizontal bar from departure to end of day: stops as blocks, sunset marker, night shaded. */
@Composable
fun DaylightBar(plan: DayPlan, modifier: Modifier = Modifier) {
    val t = rememberTexts()
    val c = LocalTripColors.current
    val schedule = plan.schedule
    val z = plan.summaryZmanim ?: return
    if (plan.stops.isEmpty()) return
    val start: Instant = schedule.departure
    val end: Instant = maxOf(schedule.endOfDay, z.sunset.plus(Duration.ofMinutes(30)))
    val total = Duration.between(start, end).seconds.toFloat().coerceAtLeast(1f)
    fun frac(i: Instant) = (Duration.between(start, i).seconds / total).coerceIn(0f, 1f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lateCount = plan.stops.count { it.daylight?.status == DaylightStatus.LATE || it.daylight?.label is DaylightLabel.AfterSunset }
    val zone = schedule.startZone
    val description = t.s(
        R.string.daylight_bar_description,
        t.time(start, zone), t.time(end, zone), t.time(z.sunset, plan.summaryZone), plan.stops.size, lateCount,
    )
    SectionCard(modifier) {
        Text(t.s(R.string.daylight_bar_title), style = MaterialTheme.typography.titleMedium, color = c.text)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .semantics { contentDescription = description },
        ) {
            fun x(f: Float) = if (rtl) size.width * (1f - f) else size.width * f
            val h = size.height
            drawRoundRect(c.background, size = size, cornerRadius = CornerRadius(8f, 8f))
            // Night (before sunrise and after sunset).
            fun shade(a: Instant, b: Instant) {
                if (!b.isAfter(a)) return
                val x1 = x(frac(a)); val x2 = x(frac(b))
                drawRect(c.night.copy(alpha = 0.22f), topLeft = Offset(minOf(x1, x2), 0f), size = Size(kotlin.math.abs(x2 - x1), h))
            }
            if (start.isBefore(z.sunrise)) shade(start, z.sunrise)
            if (end.isAfter(z.sunset)) shade(maxOf(z.sunset, start), end)
            plan.stops.forEach { sp ->
                val late = sp.daylight?.status == DaylightStatus.LATE || sp.daylight?.label is DaylightLabel.AfterSunset
                val x1 = x(frac(sp.scheduled.arrival)); val x2 = x(frac(sp.scheduled.departure))
                drawRoundRect(
                    if (late) c.late else c.structure,
                    topLeft = Offset(minOf(x1, x2), h * 0.22f),
                    size = Size(kotlin.math.abs(x2 - x1).coerceAtLeast(4f), h * 0.56f),
                    cornerRadius = CornerRadius(6f, 6f),
                )
            }
            val sx = x(frac(z.sunset))
            drawLine(c.primaryAction, Offset(sx, 0f), Offset(sx, h), strokeWidth = 5f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(c.structure, t.s(R.string.daylight_bar_legend_stop))
            LegendDot(c.late, t.s(R.string.daylight_bar_legend_late))
            LegendDot(c.night.copy(alpha = 0.4f), t.s(R.string.daylight_bar_legend_night))
        }
        Text(t.s(R.string.daylight_bar_sunset, t.time(z.sunset, plan.summaryZone)), color = c.primaryAction, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalTripColors.current.textSecondary)
    }
}

@Composable
fun FooterTotals(plan: DayPlan, modifier: Modifier = Modifier) {
    val t = rememberTexts()
    val c = LocalTripColors.current
    val s = plan.schedule
    SectionCard(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Total(t.s(R.string.footer_drive), t.duration(s.totalDrive), Modifier.weight(1f))
            Total(t.s(R.string.footer_dwell), t.duration(s.totalDwell), Modifier.weight(1f))
            Total(t.s(R.string.footer_end), t.time(s.endOfDay, s.endZone), Modifier.weight(1f), c.primaryAction)
        }
    }
}

@Composable
private fun Total(label: String, value: String, modifier: Modifier, color: Color = LocalTripColors.current.text) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalTripColors.current.textSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}
