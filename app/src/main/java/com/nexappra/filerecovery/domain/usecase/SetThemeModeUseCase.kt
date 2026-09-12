package com.nexappra.filerecovery.domain.usecase

import com.nexappra.filerecovery.core.common.AppThemeMode
import com.nexappra.filerecovery.domain.repository.AppPreferencesRepository
import javax.inject.Inject

class SetThemeModeUseCase @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
) {
    suspend operator fun invoke(themeMode: AppThemeMode) {
        preferencesRepository.setThemeMode(themeMode)
    }
}
