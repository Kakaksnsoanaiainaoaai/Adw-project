package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VdlmsDarkColorScheme = darkColorScheme(
    primary = LcdCyan,
    onPrimary = ChassisBlack,
    secondary = LcdAmber,
    onSecondary = ChassisBlack,
    tertiary = LedGreen,
    background = ChassisBlack,
    onBackground = TextPrimary,
    surface = FaceplateDark,
    onSurface = TextPrimary,
    surfaceVariant = FaceplateElevated,
    onSurfaceVariant = TextSecondary,
    outline = RackBorder,
    error = LedRed,
    onError = TextPrimary
)

@Composable
fun AdwVdlmsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VdlmsDarkColorScheme,
        typography = VdlmsTypography,
        content = content
    )
}
