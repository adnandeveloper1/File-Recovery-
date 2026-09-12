package com.nexappra.filerecovery.domain.repository

import com.nexappra.filerecovery.domain.model.PhotoScanMode
import com.nexappra.filerecovery.domain.model.PhotoScanSession
import com.nexappra.filerecovery.domain.model.PhotoScanSnapshot

interface PhotoScanRepository {
    suspend fun performPhotoScan(
        mode: PhotoScanMode,
        onSnapshot: suspend (PhotoScanSnapshot) -> Unit,
    ): PhotoScanSession

    suspend fun getSession(sessionId: String): PhotoScanSession?
}
