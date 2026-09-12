package com.nexappra.filerecovery.domain.model

import com.nexappra.filerecovery.core.common.AppThemeMode

data class UserPreferences(
    val themeMode: AppThemeMode = AppThemeMode.System,
)
