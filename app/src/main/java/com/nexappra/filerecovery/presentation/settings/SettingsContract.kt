package com.nexappra.filerecovery.presentation.settings

import com.nexappra.filerecovery.domain.model.AppLanguage
import com.nexappra.filerecovery.domain.model.AppThemeMode
import com.nexappra.filerecovery.domain.model.SupportedLanguages

data class SettingsUiState(
    val selectedLanguage: AppLanguage = SupportedLanguages.English,
    val selectedTheme: AppThemeMode = AppThemeMode.SYSTEM,
)