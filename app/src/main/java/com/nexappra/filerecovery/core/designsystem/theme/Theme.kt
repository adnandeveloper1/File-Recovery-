package com.nexappra.filerecovery.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = LightSurface,
    primaryContainer = BrandPrimary.copy(alpha = 0.12f),
    onPrimaryContainer = BrandPrimaryStrong,
    secondary = AccentCyan,
    onSecondary = LightSurface,
    secondaryContainer = LightSurfaceSoft,
    onSecondaryContainer = BrandPrimaryStrong,
    tertiary = VideoAccent,
    background = LightBackground,
    onBackground = TextPrimary,
    surface = LightSurface,
    onSurface = TextPrimary,
    surfaceVariant = LightSurfaceSoft,
    onSurfaceVariant = TextSecondary,
    outline = BorderLight,
    outlineVariant = BorderLight.copy(alpha = 0.5f),
    error = ErrorRed,
    onError = LightSurface,
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandSecondary,
    onPrimary = DarkBackground,
    primaryContainer = BrandPrimaryStrong,
    onPrimaryContainer = TextPrimaryDark,
    secondary = AccentCyan,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceSoft,
    onSecondaryContainer = BrandSecondary,
    tertiary = VideoAccent,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceSoft,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    outlineVariant = BorderDark.copy(alpha = 0.5f),
    error = ErrorRed,
    onError = DarkBackground,
)

@Composable
fun FileRecoveryTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = FileRecoveryShapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}
