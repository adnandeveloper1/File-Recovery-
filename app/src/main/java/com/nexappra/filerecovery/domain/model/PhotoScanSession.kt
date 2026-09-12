package com.nexappra.filerecovery.domain.model

data class PhotoScanSession(
    val id: String,
    val mode: PhotoScanMode,
    val durationMillis: Long,
    val totalBytesScanned: Long,
    val photos: List<RecoverablePhoto>,
    val locations: List<PhotoScanLocationState>,
    val completedAtMillis: Long,
)
