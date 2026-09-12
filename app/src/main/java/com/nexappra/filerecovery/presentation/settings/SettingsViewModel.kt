package com.nexappra.filerecovery.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexappra.filerecovery.domain.model.AppLanguage
import com.nexappra.filerecovery.domain.model.AppThemeMode
import com.nexappra.filerecovery.domain.model.SupportedLanguages
import com.nexappra.filerecovery.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState = combine(
        settingsRepository.selectedLanguageCode,
        settingsRepository.selectedThemeMode,
    ) { languageCode, themeMode ->

        SettingsUiState(
            selectedLanguage =
                SupportedLanguages.findByCode(
                    languageCode
                ),
            selectedTheme = themeMode,
        )

    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = 5_000L,
        ),
        initialValue = SettingsUiState(),
    )

    fun selectLanguage(
        language: AppLanguage,
    ) {
        viewModelScope.launch {

            settingsRepository.setLanguageCode(
                language.code
            )
        }
    }

    fun selectTheme(
        themeMode: AppThemeMode,
    ) {
        viewModelScope.launch {

            settingsRepository.setThemeMode(
                themeMode
            )
        }
    }
}