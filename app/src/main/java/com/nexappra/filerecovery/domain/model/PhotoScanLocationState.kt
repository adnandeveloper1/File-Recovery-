package com.nexappra.filerecovery.domain.model

data class PhotoScanLocationState(
    val type: PhotoScanLocationType,
    val status: ScanLocationStatus = ScanLocationStatus.Pending,
    val foundCount: Int = 0,
    val totalCount: Int = 0,
    val displayPath: String? = null,
)
