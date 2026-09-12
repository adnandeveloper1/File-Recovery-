package com.nexappra.filerecovery.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexappra.filerecovery.domain.model.AppThemeMode
import com.nexappra.filerecovery.domain.model.SupportedLanguages
import com.nexappra.filerecovery.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "file_recovery_settings",
)

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private object Keys {
        val Language = stringPreferencesKey("selected_language")
        val Theme = stringPreferencesKey("selected_theme")
    }

    private val preferencesFlow: Flow<Preferences>
        get() = context.settingsDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }

    override val selectedLanguageCode: Flow<String> =
        preferencesFlow.map { preferences ->
            preferences[Keys.Language]
                ?: SupportedLanguages.English.code
        }

    override val selectedThemeMode: Flow<AppThemeMode> =
        preferencesFlow.map { preferences ->

            val storedTheme = preferences[Keys.Theme]

            AppThemeMode.entries.firstOrNull {
                it.name == storedTheme
            } ?: AppThemeMode.SYSTEM
        }

    override suspend fun setLanguageCode(
        languageCode: String,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.Language] = languageCode
        }
    }

    override suspend fun setThemeMode(
        themeMode: AppThemeMode,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.Theme] = themeMode.name
        }
    }
}