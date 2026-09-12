package com.nexappra.filerecovery.domain.usecase

import com.nexappra.filerecovery.domain.model.PhotoScanSession
import com.nexappra.filerecovery.domain.repository.PhotoScanRepository
import javax.inject.Inject

class GetPhotoScanSessionUseCase @Inject constructor(
    private val repository: PhotoScanRepository,
) {
    suspend operator fun invoke(sessionId: String): PhotoScanSession? = repository.getSession(sessionId)
}
