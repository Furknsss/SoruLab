package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SoruLabLightColorScheme = lightColorScheme(
    primary = SoruLabCobaltBlue,
    onPrimary = Color.White,
    primaryContainer = SoruLabSkyTint,
    onPrimaryContainer = SoruLabRoyalBlue,
    secondary = SoruLabEmerald,
    onSecondary = Color.White,
    secondaryContainer = SoruLabEmeraldLight,
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = SoruLabViolet,
    onTertiary = Color.White,
    tertiaryContainer = SoruLabVioletLight,
    onTertiaryContainer = Color(0xFF4C1D95),
    background = SoruLabBackground,
    onBackground = SoruLabInkBlack,
    surface = SoruLabSurface,
    onSurface = SoruLabInkBlack,
    surfaceVariant = SoruLabSurfaceVariant,
    onSurfaceVariant = SoruLabSlateMuted,
    outline = SoruLabBorderLight,
    error = SoruLabCoralRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SoruLabLightColorScheme,
        typography = Typography,
        content = content
    )
}
