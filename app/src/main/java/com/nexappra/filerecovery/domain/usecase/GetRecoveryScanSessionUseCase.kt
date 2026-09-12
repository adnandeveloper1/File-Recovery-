package com.nexappra.filerecovery.domain.usecase

import com.nexappra.filerecovery.domain.model.RecoveryScanSession
import com.nexappra.filerecovery.domain.repository.RecoveryScanRepository
import javax.inject.Inject

class GetRecoveryScanSessionUseCase @Inject constructor(
    private val repository: RecoveryScanRepository,
) {
    suspend operator fun invoke(sessionId: String): RecoveryScanSession? = repository.getSession(sessionId)
}
