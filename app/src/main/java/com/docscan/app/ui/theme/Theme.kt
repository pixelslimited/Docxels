package com.docscan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.docscan.app.R
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

/** The chosen accent as the colours the app draws with. */
class BrandColors(val a: Color, val b: Color) {
    val gradient: Brush = Brush.linearGradient(listOf(a, b))
}

val LocalBrand = staticCompositionLocalOf { BrandColors(Brand.Indigo, Brand.Violet) }

object DocxelsTheme {
    val tints: Tints
        @Composable @ReadOnlyComposable get() = LocalTints.current
    val brand: BrandColors
        @Composable @ReadOnlyComposable get() = LocalBrand.current
}

private fun mix(x: Color, y: Color, t: Float) = androidx.compose.ui.graphics.lerp(x, y, t)

private fun lightSchemeFor(a: Color, b: Color) = LightColors.copy(
    primary = a,
    primaryContainer = mix(a, Color.White, 0.86f),
    onPrimaryContainer = mix(a, Color.Black, 0.55f),
    secondary = b,
    secondaryContainer = mix(b, Color.White, 0.86f),
    onSecondaryContainer = mix(b, Color.Black, 0.55f),
    background = mix(Color(0xFFF7F7FA), a, 0.05f),
    surface = mix(Color(0xFFF7F7FA), a, 0.05f),
    surfaceVariant = mix(Color(0xFFEDEDF3), a, 0.07f),
    surfaceContainer = mix(Color(0xFFF1F1F6), a, 0.06f),
    surfaceContainerHigh = mix(Color(0xFFEBEBF2), a, 0.07f),
    surfaceContainerHighest = mix(Color(0xFFE4E4ED), a, 0.08f),
)

private fun darkSchemeFor(a: Color, b: Color) = DarkColors.copy(
    primary = mix(a, Color.White, 0.62f),
    onPrimary = mix(a, Color.Black, 0.6f),
    primaryContainer = mix(a, Color.Black, 0.3f),
    onPrimaryContainer = mix(a, Color.White, 0.86f),
    secondary = mix(b, Color.White, 0.6f),
    secondaryContainer = mix(b, Color.Black, 0.35f),
    onSecondaryContainer = mix(b, Color.White, 0.86f),
)

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

/** The app's own typeface (Inter, bundled, SIL Open Font License) so the interface looks the same on every phone. */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun Typography.withFamily(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
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
).withFamily(InterFamily)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun DocScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: com.docscan.app.security.Accent = com.docscan.app.security.Accent.DOCXELS,
    content: @Composable () -> Unit,
) {
    val a = Color(accent.a)
    val b = Color(accent.b)
    val brand = androidx.compose.runtime.remember(accent) { BrandColors(a, b) }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalTints provides if (darkTheme) DarkTints else LightTints,
        LocalBrand provides brand,
    ) {
        MaterialTheme(
            colorScheme = if (accent == com.docscan.app.security.Accent.DOCXELS) (if (darkTheme) DarkColors else LightColors)
            else if (darkTheme) darkSchemeFor(a, b) else lightSchemeFor(a, b),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
