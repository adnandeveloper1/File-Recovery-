package com.nexappra.filerecovery.domain.model

data class PhotoScanSnapshot(
    val mode: PhotoScanMode,
    val status: PhotoScanStatus,
    val progress: Float?,
    val totalFilesFound: Int,
    val bytesScanned: Long,
    val estimatedRemainingMillis: Long?,
    val currentLocation: PhotoScanLocationState?,
    val locations: List<PhotoScanLocationState>,
    val elapsedMillis: Long,
    val errorMessage: String? = null,
)
