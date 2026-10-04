package com.nexappra.filerecovery.domain.usecase

import com.nexappra.filerecovery.domain.model.RecoveryScanSession
import com.nexappra.filerecovery.domain.model.RecoveryScanSnapshot
import com.nexappra.filerecovery.domain.model.RecoveryCategory
import com.nexappra.filerecovery.domain.model.RecoveryScanMode
import com.nexappra.filerecovery.domain.repository.RecoveryScanRepository
import javax.inject.Inject

class PerformRecoveryScanUseCase @Inject constructor(
    private val repository: RecoveryScanRepository,
) {
    suspend operator fun invoke(
        selectedCategory: RecoveryCategory? = null,
        mode: RecoveryScanMode = RecoveryScanMode.Quick,
        onSnapshot: suspend (RecoveryScanSnapshot) -> Unit,
    ): RecoveryScanSession = repository.performFullDeviceScan(selectedCategory, mode, onSnapshot)
}
