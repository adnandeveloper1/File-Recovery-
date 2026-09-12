package com.nexappra.filerecovery.presentation.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexappra.filerecovery.domain.model.AppThemeMode
import com.nexappra.filerecovery.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<AppThemeMode> =
        settingsRepository.selectedThemeMode.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000L,
            ),
            initialValue = AppThemeMode.SYSTEM,
        )
}