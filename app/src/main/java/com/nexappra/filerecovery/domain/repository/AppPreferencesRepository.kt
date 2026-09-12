package com.nexappra.filerecovery.domain.repository

import com.nexappra.filerecovery.core.common.AppThemeMode
import com.nexappra.filerecovery.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setThemeMode(themeMode: AppThemeMode)
}
