package com.nexappra.filerecovery.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexappra.filerecovery.core.common.AppThemeMode
import com.nexappra.filerecovery.domain.model.UserPreferences
import com.nexappra.filerecovery.domain.repository.AppPreferencesRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreAppPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppPreferencesRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            val themeMode = preferences[PreferencesKeys.ThemeMode]
                ?.let(AppThemeMode::valueOf)
                ?: AppThemeMode.System

            UserPreferences(themeMode = themeMode)
        }

    override suspend fun setThemeMode(themeMode: AppThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ThemeMode] = themeMode.name
        }
    }

    private object PreferencesKeys {
        val ThemeMode = stringPreferencesKey("theme_mode")
    }
}
