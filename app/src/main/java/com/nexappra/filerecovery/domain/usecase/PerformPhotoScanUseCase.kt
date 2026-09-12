package com.nexappra.filerecovery.domain.usecase

import com.nexappra.filerecovery.domain.model.PhotoScanMode
import com.nexappra.filerecovery.domain.model.PhotoScanSession
import com.nexappra.filerecovery.domain.model.PhotoScanSnapshot
import com.nexappra.filerecovery.domain.repository.PhotoScanRepository
import javax.inject.Inject

class PerformPhotoScanUseCase @Inject constructor(
    private val repository: PhotoScanRepository,
) {
    suspend operator fun invoke(
        mode: PhotoScanMode,
        onSnapshot: suspend (PhotoScanSnapshot) -> Unit,
    ): PhotoScanSession = repository.performPhotoScan(
        mode = mode,
        onSnapshot = onSnapshot,
    )
}
