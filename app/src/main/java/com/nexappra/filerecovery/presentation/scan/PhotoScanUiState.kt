package com.nexappra.filerecovery.presentation.scan

import com.nexappra.filerecovery.domain.model.PhotoScanLocationState
import com.nexappra.filerecovery.domain.model.PhotoScanMode
import com.nexappra.filerecovery.domain.model.PhotoScanStatus

data class PhotoScanUiState(
    val scanMode: PhotoScanMode = PhotoScanMode.Quick,
    val scanStatus: PhotoScanStatus = PhotoScanStatus.Idle,
    val progress: Float? = null,
    val totalFilesFound: Int = 0,
    val bytesScanned: Long = 0L,
    val estimatedRemainingMillis: Long? = null,
    val currentLocation: PhotoScanLocationState? = null,
    val locations: List<PhotoScanLocationState> = emptyList(),
    val elapsedMillis: Long = 0L,
    val errorMessage: String? = null,
    val isPermissionGranted: Boolean = false,
) {
    val isActiveScan: Boolean
        get() = scanStatus == PhotoScanStatus.Preparing || scanStatus == PhotoScanStatus.Scanning
}
