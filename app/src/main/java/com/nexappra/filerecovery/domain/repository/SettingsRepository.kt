package com.nexappra.filerecovery.domain.repository

import com.nexappra.filerecovery.domain.model.AppThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    val selectedLanguageCode: Flow<String>

    val selectedThemeMode: Flow<AppThemeMode>

    suspend fun setLanguageCode(
        languageCode: String,
    )

    suspend fun setThemeMode(
        themeMode: AppThemeMode,
    )
}