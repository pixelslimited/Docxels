package com.docscan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Docxels look: a soft lavender canvas, white cards, one indigo-to-violet brand gradient, and a small set of
 * pastel tints that colour-code the three kinds of work (scan = violet, write = blue, PDF = pink, convert = green,
 * sign = orange, text = teal). No dynamic colour, so the app always looks like itself.
 */
object Brand {
    val Indigo = Color(0xFF4F46E5)
    val Violet = Color(0xFF7C3AED)
    val Gradient = Brush.linearGradient(listOf(Indigo, Violet))
    val GradientPink = Brush.linearGradient(listOf(Color(0xFFDB2777), Violet))
    val GradientBlue = Brush.linearGradient(listOf(Color(0xFF2563EB), Indigo))
    val GradientGreen = Brush.linearGradient(listOf(Color(0xFF16A34A), Color(0xFF0F766E)))
    val GradientOrange = Brush.linearGradient(listOf(Color(0xFFEA580C), Color(0xFFDB2777)))
}

/** A pastel container with the strong colour used for its icon and label. */
data class Tint(val container: Color, val strong: Color)

data class Tints(
    val violet: Tint, val blue: Tint, val pink: Tint, val green: Tint,
    val orange: Tint, val teal: Tint, val yellow: Tint, val indigo: Tint, val red: Tint,
)

private val LightTints = Tints(
    violet = Tint(Color(0xFFE9E3FF), Color(0xFF6D28D9)),
    blue = Tint(Color(0xFFDBE7FF), Color(0xFF1D4ED8)),
    pink = Tint(Color(0xFFFFDDE8), Color(0xFFBE185D)),
    green = Tint(Color(0xFFD7F3E3), Color(0xFF15803D)),
    orange = Tint(Color(0xFFFFE3D1), Color(0xFFC2410C)),
    teal = Tint(Color(0xFFD0F2EA), Color(0xFF0F766E)),
    yellow = Tint(Color(0xFFFFF0BA), Color(0xFFA16207)),
    indigo = Tint(Color(0xFFE0E0FF), Color(0xFF4338CA)),
    red = Tint(Color(0xFFFFDAD6), Color(0xFFB3261E)),
)

private val DarkTints = Tints(
    violet = Tint(Color(0xFF332A5C), Color(0xFFC9B8FF)),
    blue = Tint(Color(0xFF1E3358), Color(0xFFA9C4FF)),
    pink = Tint(Color(0xFF55223A), Color(0xFFFFB0CC)),
    green = Tint(Color(0xFF1B4330), Color(0xFF9FE3BC)),
    orange = Tint(Color(0xFF55301C), Color(0xFFFFBC93)),
    teal = Tint(Color(0xFF17413A), Color(0xFF8FE3D2)),
    yellow = Tint(Color(0xFF4E4116), Color(0xFFFFDE7A)),
    indigo = Tint(Color(0xFF2B2B66), Color(0xFFB9B9FF)),
    red = Tint(Color(0xFF5A2420), Color(0xFFFFB4AB)),
)

val LocalTints = staticCompositionLocalOf { LightTints }

object DocxelsTheme {
    val tints: Tints
        @Composable @ReadOnlyComposable get() = LocalTints.current
}

private val LightColors = lightColorScheme(
    primary = Brand.Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF1B1A6B),
    secondary = Brand.Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E3FF),
    onSecondaryContainer = Color(0xFF3B1A7A),
    tertiary = Color(0xFF0F766E),
    background = Color(0xFFF2F2FA),
    onBackground = Color(0xFF16152B),
    surface = Color(0xFFF2F2FA),
    onSurface = Color(0xFF16152B),
    surfaceVariant = Color(0xFFE6E6F4),
    onSurfaceVariant = Color(0xFF63627E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFF),
    surfaceContainer = Color(0xFFECECF8),
    surfaceContainerHigh = Color(0xFFE6E6F4),
    surfaceContainerHighest = Color(0xFFDFDFF0),
    outline = Color(0xFF8D8CA8),
    outlineVariant = Color(0xFFE0DFEF),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4B1FF),
    onPrimary = Color(0xFF1B1A6B),
    primaryContainer = Color(0xFF3A38A8),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFFCDB8FF),
    onSecondary = Color(0xFF3B1A7A),
    secondaryContainer = Color(0xFF4A2A93),
    onSecondaryContainer = Color(0xFFE9E3FF),
    tertiary = Color(0xFF8FE3D2),
    background = Color(0xFF111120),
    onBackground = Color(0xFFEDECFA),
    surface = Color(0xFF111120),
    onSurface = Color(0xFFEDECFA),
    surfaceVariant = Color(0xFF26263A),
    onSurfaceVariant = Color(0xFFA9A8C4),
    surfaceContainerLowest = Color(0xFF0B0B17),
    surfaceContainerLow = Color(0xFF1A1A2C),
    surfaceContainer = Color(0xFF1E1E32),
    surfaceContainerHigh = Color(0xFF26263A),
    surfaceContainerHighest = Color(0xFF2F2F46),
    outline = Color(0xFF8D8CA8),
    outlineVariant = Color(0xFF33334C),
    error = Color(0xFFF2B8B5),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun DocScanTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalTints provides if (darkTheme) DarkTints else LightTints) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
