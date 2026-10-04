package com.nexappra.filerecovery.domain.model

data class RecoveryScanSnapshot(
    val mode: RecoveryScanMode,
    val selectedCategory: RecoveryCategory? = null,
    val status: RecoveryScanStatus,
    val progress: Float?,
    val totalItemsScanned: Int,
    val totalFilesFound: Int,
    val hiddenPhotoCount: Int = 0,
    val hiddenVideoCount: Int = 0,
    val totalBytesScanned: Long,
    val estimatedRemainingMillis: Long?,
    val currentLocation: RecoveryScanLocationState?,
    val locations: List<RecoveryScanLocationState>,
    val locationsChecked: Int,
    val categoryCounts: Map<RecoveryResultFilter, Int>,
    val elapsedMillis: Long,
    val errorMessage: String? = null,
    val sessionId: String = "",
    val previewFiles: List<RecoverableFile> = emptyList(),
)
