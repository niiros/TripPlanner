package com.niiros.tripplanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niiros.tripplanner.R

/** Fixed palette (no dynamic color), tuned for outdoor readability. */
@Immutable
data class TripColors(
    val background: Color = Color(0xFFF3F6F9),
    val text: Color = Color(0xFF14213A),
    val textSecondary: Color = Color(0xFF4A5673),
    val card: Color = Color(0xFFFFFFFF),
    val cardBorder: Color = Color(0xFFDDE3EC),
    val primaryAction: Color = Color(0xFFC2410C),
    val structure: Color = Color(0xFF2B3A67),
    val ok: Color = Color(0xFF0B5C56),
    val okBackground: Color = Color(0xFFE0F2EF),
    val midday: Color = Color(0xFF7A4A06),
    val middayBackground: Color = Color(0xFFFDF0DC),
    val late: Color = Color(0xFFB91C1C),
    val lateBackground: Color = Color(0xFFFDE8E8),
    val zmanimCard: Color = Color(0xFF14213A),
    val zmanimText: Color = Color(0xFFF3F6F9),
    val sunsetHighlight: Color = Color(0xFFFDBA74),
    val night: Color = Color(0xFF2B3A67),
)

val LocalTripColors = staticCompositionLocalOf { TripColors() }

@OptIn(ExperimentalTextApi::class)
private fun rubik(weight: Int) = Font(
    R.font.rubik,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Rubik = FontFamily(rubik(400), rubik(500), rubik(600), rubik(700))

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.r() = copy(fontFamily = Rubik)
    return Typography(
        displayLarge = base.displayLarge.r(),
        displayMedium = base.displayMedium.r(),
        displaySmall = base.displaySmall.r(),
        headlineLarge = base.headlineLarge.r(),
        headlineMedium = base.headlineMedium.r(),
        headlineSmall = base.headlineSmall.r().copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.r().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.r().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.r().copy(fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.r(),
        bodyMedium = base.bodyMedium.r().copy(fontSize = 15.sp),
        bodySmall = base.bodySmall.r().copy(fontSize = 13.sp),
        labelLarge = base.labelLarge.r().copy(fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.r(),
        labelSmall = base.labelSmall.r(),
    )
}

@Composable
fun TripPlannerTheme(content: @Composable () -> Unit) {
    val c = TripColors()
    val scheme = lightColorScheme(
        primary = c.primaryAction,
        onPrimary = Color.White,
        secondary = c.structure,
        onSecondary = Color.White,
        tertiary = c.ok,
        background = c.background,
        onBackground = c.text,
        surface = c.card,
        onSurface = c.text,
        surfaceVariant = c.background,
        onSurfaceVariant = c.textSecondary,
        surfaceContainerLow = c.card,
        surfaceContainer = c.card,
        surfaceContainerHigh = c.card,
        outline = c.cardBorder,
        outlineVariant = c.cardBorder,
        error = c.late,
        secondaryContainer = Color(0xFFE3E8F4),
        onSecondaryContainer = c.structure,
        primaryContainer = Color(0xFFFBE4D8),
        onPrimaryContainer = Color(0xFF7C2D12),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalTripColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography(),
            shapes = Shapes(
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp),
            ),
            content = content,
        )
    }
}
