package com.nexappra.filerecovery.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(

    primary = Color(0xFF2563EB),

    onPrimary = Color.White,

    primaryContainer = Color(0xFFEFF6FF),

    onPrimaryContainer = Color(0xFF1E3A8A),

    secondary = Color(0xFF3B82F6),

    onSecondary = Color.White,

    tertiary = Color(0xFF06B6D4),

    background = Color(0xFFF8FAFC),

    onBackground = Color(0xFF0F172A),

    surface = Color.White,

    onSurface = Color(0xFF0F172A),

    surfaceVariant = Color(0xFFF1F5F9),

    onSurfaceVariant = Color(0xFF64748B),

    outline = Color(0xFFCBD5E1),

    outlineVariant = Color(0xFFE2E8F0),

    error = Color(0xFFEF4444),

    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(

    primary = Color(0xFF60A5FA),

    onPrimary = Color(0xFF082F49),

    primaryContainer = Color(0xFF1E3A8A),

    onPrimaryContainer = Color(0xFFDBEAFE),

    secondary = Color(0xFF60A5FA),

    onSecondary = Color(0xFF0F172A),

    tertiary = Color(0xFF22D3EE),

    background = Color(0xFF0F172A),

    onBackground = Color(0xFFF8FAFC),

    surface = Color(0xFF111827),

    onSurface = Color(0xFFF8FAFC),

    surfaceVariant = Color(0xFF1E293B),

    onSurfaceVariant = Color(0xFF94A3B8),

    outline = Color(0xFF475569),

    outlineVariant = Color(0xFF334155),

    error = Color(0xFFF87171),

    onError = Color(0xFF450A0A),
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
        typography = MaterialTheme.typography,
        content = content,
    )
}