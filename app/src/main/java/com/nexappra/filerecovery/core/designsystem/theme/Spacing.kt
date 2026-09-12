package com.nexappra.filerecovery.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class RecoverySpacing(
    val xSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val xLarge: Dp = 20.dp,
    val xxLarge: Dp = 24.dp,
    val section: Dp = 32.dp,
)

val LocalRecoverySpacing = staticCompositionLocalOf { RecoverySpacing() }

val MaterialTheme.spacing: RecoverySpacing
    @Composable
    get() = LocalRecoverySpacing.current
