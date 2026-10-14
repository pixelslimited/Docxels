package com.docscan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Paper and ink": warm off-white paper, near-black ink, and a single vermilion accent that is only used
 * where something is live (shutter, edges, selected tab). No dynamic colour, so the app always looks like itself.
 */
object Ink {
    val Paper = Color(0xFFF7F4EE)
    val Sheet = Color(0xFFFFFFFF)
    val Ink = Color(0xFF16150F)
    val InkSoft = Color(0xFF6B675D)
    val Rule = Color(0xFFE3DED2)
    val Vermilion = Color(0xFFFF5A2B)

    val NightPaper = Color(0xFF12110D)
    val NightSheet = Color(0xFF1C1A15)
    val NightInk = Color(0xFFF3EEE3)
    val NightInkSoft = Color(0xFFA29D90)
    val NightRule = Color(0xFF2E2B24)
}

private val LightColors = lightColorScheme(
    primary = Ink.Ink,
    onPrimary = Ink.Paper,
    primaryContainer = Ink.Rule,
    onPrimaryContainer = Ink.Ink,
    secondary = Ink.Vermilion,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0D6),
    onSecondaryContainer = Color(0xFF5C1500),
    tertiary = Ink.InkSoft,
    background = Ink.Paper,
    onBackground = Ink.Ink,
    surface = Ink.Paper,
    onSurface = Ink.Ink,
    surfaceVariant = Color(0xFFEDE8DC),
    onSurfaceVariant = Ink.InkSoft,
    surfaceContainerLowest = Ink.Sheet,
    surfaceContainerLow = Color(0xFFFBF9F4),
    surfaceContainer = Color(0xFFF1EDE3),
    surfaceContainerHigh = Color(0xFFEDE8DC),
    surfaceContainerHighest = Color(0xFFE7E1D3),
    outline = Ink.InkSoft,
    outlineVariant = Ink.Rule,
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Ink.NightInk,
    onPrimary = Ink.NightPaper,
    primaryContainer = Ink.NightRule,
    onPrimaryContainer = Ink.NightInk,
    secondary = Color(0xFFFF7A50),
    onSecondary = Color(0xFF2A0A00),
    secondaryContainer = Color(0xFF5C2310),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Ink.NightInkSoft,
    background = Ink.NightPaper,
    onBackground = Ink.NightInk,
    surface = Ink.NightPaper,
    onSurface = Ink.NightInk,
    surfaceVariant = Color(0xFF26231D),
    onSurfaceVariant = Ink.NightInkSoft,
    surfaceContainerLowest = Color(0xFF0D0C09),
    surfaceContainerLow = Ink.NightSheet,
    surfaceContainer = Color(0xFF211F19),
    surfaceContainerHigh = Color(0xFF26231D),
    surfaceContainerHighest = Color(0xFF2E2B24),
    outline = Ink.NightInkSoft,
    outlineVariant = Ink.NightRule,
    error = Color(0xFFF2B8B5),
)

/** Headlines in a serif give the app a printed-page voice; everything you tap stays in the clean system sans. */
private val Serif = FontFamily.Serif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun DocScanTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
