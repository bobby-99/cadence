package com.bobby.cadence.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bobby.cadence.R

object C {
    val Bg = Color(0xFF0A0A0A)
    val Surface = Color(0xFF141412)
    val SurfaceHi = Color(0xFF1A1917)
    val SurfaceTop = Color(0xFF24221F)
    val Stroke = Color(0xFF2B2A27)
    val Rail = Color(0xFF2B2A27)
    val Text = Color(0xFFF4F1EA)
    val Dim = Color(0xFFA8A397)
    val Faint = Color(0xFF8A857A)
    val Accent = Color(0xFFF2B04A)
    val AccentSoft = Color(0x26F2B04A)
    val AccentCard = Color(0xFF1A160D)
    val AccentLine = Color(0xFF4A3A1C)
    val AccentOn = Color(0xFF1A1405)
    val High = Color(0xFFFF8F7A)
    val Medium = Color(0xFFF2B04A)
    val Low = Color(0xFF8FC7B8)
    val Done = Color(0xFF9BD3A5)
    val Danger = Color(0xFFFF8F7A)
    val Scrim = Color(0x99000000)
}

fun priorityColor(p: Int): Color = when (p) {
    3 -> C.High
    2 -> C.Medium
    1 -> C.Low
    else -> C.Faint
}

fun priorityLabel(p: Int): String = when (p) {
    3 -> "High"
    2 -> "Medium"
    1 -> "Low"
    else -> "None"
}

private val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private val GoogleSans = FontFamily(
    Font(R.font.googlesans_regular, FontWeight.Normal),
    Font(R.font.googlesans_medium, FontWeight.Medium),
    Font(R.font.googlesans_semibold, FontWeight.SemiBold),
    Font(R.font.googlesans_bold, FontWeight.Bold),
    Font(R.font.googlesans_bold, FontWeight.ExtraBold),
)

/** 0 = Manrope, 1 = Google Sans, anything else = the system sans-serif. */
private fun familyFor(choice: Int): FontFamily = when (choice) {
    0 -> Manrope
    1 -> GoogleSans
    else -> FontFamily.SansSerif
}

private fun typographyFor(f: FontFamily): Typography {
    fun ts(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) = TextStyle(
        fontFamily = f,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = spacing.sp,
    )
    return Typography(
        displayLarge = ts(40, FontWeight.ExtraBold, 42, -1.2),
        headlineLarge = ts(32, FontWeight.ExtraBold, 36, -0.9),
        headlineMedium = ts(28, FontWeight.ExtraBold, 32, -0.8),
        headlineSmall = ts(22, FontWeight.Bold, 28, -0.4),
        titleLarge = ts(20, FontWeight.Bold, 26, -0.2),
        titleMedium = ts(16, FontWeight.SemiBold, 20),
        titleSmall = ts(14, FontWeight.SemiBold, 20),
        bodyLarge = ts(16, FontWeight.Medium, 23),
        bodyMedium = ts(14, FontWeight.Medium, 20),
        bodySmall = ts(12, FontWeight.Medium, 17),
        labelLarge = ts(14, FontWeight.SemiBold, 20, 0.1),
        labelMedium = ts(12, FontWeight.SemiBold, 16, 0.3),
        labelSmall = ts(11, FontWeight.Bold, 14, 1.4),
    )
}

private val DarkScheme = darkColorScheme(
    primary = C.Accent,
    onPrimary = C.AccentOn,
    primaryContainer = C.AccentSoft,
    onPrimaryContainer = C.Accent,
    secondary = C.Accent,
    onSecondary = C.AccentOn,
    background = C.Bg,
    onBackground = C.Text,
    surface = C.Surface,
    onSurface = C.Text,
    surfaceVariant = C.SurfaceHi,
    onSurfaceVariant = C.Dim,
    surfaceContainer = C.Surface,
    surfaceContainerHigh = C.SurfaceHi,
    surfaceContainerHighest = C.SurfaceTop,
    surfaceContainerLow = C.Surface,
    surfaceContainerLowest = C.Bg,
    outline = C.Stroke,
    outlineVariant = C.Stroke,
    error = C.Danger,
    scrim = C.Scrim,
)

/** The app is dark only, whatever the system theme is. */
@Composable
fun CadenceTheme(font: Int = 0, content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val ignored = isSystemInDarkTheme()
    MaterialTheme(colorScheme = DarkScheme, typography = typographyFor(familyFor(font)), content = content)
}
