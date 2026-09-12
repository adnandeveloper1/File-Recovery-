package com.nexappra.filerecovery.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val BrandPrimary = Color(0xFF2563EB)
val BrandPrimaryStrong = Color(0xFF1D4ED8)
val BrandSecondary = Color(0xFF3B82F6)
val AccentCyan = Color(0xFF06B6D4)
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceSoft = Color(0xFFF1F5F9)
val DarkBackground = Color(0xFF0F172A)
val DarkSurface = Color(0xFF111827)
val DarkSurfaceSoft = Color(0xFF1E293B)
val TextPrimary = Color(0xFF0F172A)
val TextSecondary = Color(0xFF64748B)
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFF94A3B8)
val BorderLight = Color(0xFFE2E8F0)
val BorderDark = Color(0xFF334155)
val SuccessGreen = Color(0xFF22C55E)
val WarningAmber = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFEF4444)
val PhotoAccent = Color(0xFF2563EB)
val VideoAccent = Color(0xFFA855F7)
val AudioAccent = Color(0xFFEC4899)
val DocumentAccent = Color(0xFFF59E0B)
val WhatsAppAccent = Color(0xFF22C55E)
val DownloadAccent = Color(0xFF06B6D4)
val ScreenshotAccent = Color(0xFF6366F1)
val RecycleBinAccent = Color(0xFFEF4444)
val LargeFileAccent = Color(0xFFF97316)

internal val FileRecoveryLightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandSecondary.copy(alpha = 0.16f),
    onPrimaryContainer = BrandPrimaryStrong,
    secondary = AccentCyan,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = TextPrimary,
    surface = LightSurface,
    onSurface = TextPrimary,
    surfaceVariant = LightSurfaceSoft,
    onSurfaceVariant = TextSecondary,
    outline = BorderLight,
    error = ErrorRed,
)

internal val FileRecoveryDarkColors = darkColorScheme(
    primary = BrandSecondary,
    onPrimary = DarkBackground,
    primaryContainer = BrandPrimaryStrong,
    onPrimaryContainer = TextPrimaryDark,
    secondary = AccentCyan,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceSoft,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    error = ErrorRed,
)
