package com.nexappra.filerecovery.domain.model

data class RecoveryScanLocationState(
    val type: RecoveryScanLocationType,
    val status: ScanLocationStatus = ScanLocationStatus.Pending,
    val foundCount: Int = 0,
    val totalCount: Int = 0,
    val displayPath: String? = null,
)
