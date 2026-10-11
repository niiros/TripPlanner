package com.niiros.tripplanner.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.niiros.tripplanner.R
import com.niiros.tripplanner.core.plan.PointPlan
import com.niiros.tripplanner.core.plan.WeatherState
import com.niiros.tripplanner.ui.theme.LocalTripColors

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    container: Color = LocalTripColors.current.card,
    border: Color = LocalTripColors.current.cardBorder,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, border),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

/** Status is never color-only: the chip always carries text. */
@Composable
fun StatusChip(text: String, foreground: Color, background: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = foreground,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .background(background, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

enum class BannerKind { INFO, WARNING, ERROR }

@Composable
fun Banner(text: String, kind: BannerKind, modifier: Modifier = Modifier, title: String? = null) {
    val c = LocalTripColors.current
    val (fg, bg) = when (kind) {
        BannerKind.INFO -> c.structure to Color(0xFFE3E8F4)
        BannerKind.WARNING -> c.midday to c.middayBackground
        BannerKind.ERROR -> c.late to c.lateBackground
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(bg, MaterialTheme.shapes.medium)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (kind == BannerKind.INFO) Icons.Filled.Info else Icons.Filled.Warning,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(22.dp),
        )
        Column {
            if (title != null) Text(title, color = fg, style = MaterialTheme.typography.titleSmall)
            Text(text, color = fg, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Icon plus visible text; never icon-only. */
@Composable
fun WeatherRow(point: PointPlan, modifier: Modifier = Modifier) {
    val t = rememberTexts()
    val c = LocalTripColors.current
    val w = point.weather
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (w != null) {
            val description = t.iconDescription(w.icon)
            Icon(
                painterResource(Texts.iconRes(w.icon)),
                contentDescription = null,
                tint = c.structure,
                modifier = Modifier
                    .size(22.dp)
                    .semantics { contentDescription = description },
            )
            Text(t.weatherLine(w), style = MaterialTheme.typography.bodyMedium, color = c.text)
        } else {
            val text = if (point.weatherState == WeatherState.FAILED) t.s(R.string.weather_failed) else t.s(R.string.weather_not_yet)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
    }
}
