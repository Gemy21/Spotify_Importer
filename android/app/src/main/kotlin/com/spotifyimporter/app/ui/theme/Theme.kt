package com.spotifyimporter.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// OLED canvas
// ---------------------------------------------------------------------------
val OledBlack = Color(0xFF000000)
val DarkBackground = OledBlack
val SurfaceDark = Color(0xFF050706)
val SurfaceVariantDark = Color(0xFF0B0F0D)

// ---------------------------------------------------------------------------
// Accents
// ---------------------------------------------------------------------------
val SpotifyGreen = Color(0xFF1ED760)
val AccentBright = Color(0xFF5BF29A)
val SpotifyGreenDark = Color(0xFF0E9A4A)
val AquaGlow = Color(0xFF17E0C0)

// ---------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------
val TextPrimary = Color(0xFFF3F6F4)
val TextSecondary = Color(0xFF9AA6A0)
val TextMuted = Color(0xFF5E6A64)
val ErrorRed = Color(0xFFFF5D63)

// ---------------------------------------------------------------------------
// Liquid glass: white at very low alpha over black reads as smoked glass.
// ---------------------------------------------------------------------------
val GlassFillTop = Color(0x14FFFFFF)
val GlassFillBottom = Color(0x06FFFFFF)
val GlassBorderTop = Color(0x2BFFFFFF)
val GlassBorderBottom = Color(0x08FFFFFF)
val GlassSheen = Color(0x0AFFFFFF)
val GlassSelectedTop = Color(0x2E1ED760)
val GlassSelectedBottom = Color(0x0A1ED760)

private val DarkColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.Black,
    primaryContainer = SpotifyGreenDark,
    onPrimaryContainer = Color.Black,
    secondary = AquaGlow,
    onSecondary = Color.Black,
    background = DarkBackground,
    surface = OledBlack,
    surfaceVariant = SurfaceVariantDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = Color(0x33FFFFFF),
    error = ErrorRed,
    onError = Color.Black
)

/**
 * Deliberate type scale: tight tracking on display text, wide tracking on the
 * small uppercase labels that anchor each section.
 */
private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-1.0).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp
    )
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun SpotifyImporterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
