package com.nexappra.filerecovery.domain.usecase

import com.nexappra.filerecovery.domain.model.UserPreferences
import com.nexappra.filerecovery.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveUserPreferencesUseCase @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
) {
    operator fun invoke(): Flow<UserPreferences> = preferencesRepository.preferences
}
