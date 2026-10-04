package com.nexappra.filerecovery.presentation.scan

import com.nexappra.filerecovery.domain.model.RecoveryResultFilter
import com.nexappra.filerecovery.domain.model.RecoveryScanLocationState
import com.nexappra.filerecovery.domain.model.RecoveryScanLocationType
import com.nexappra.filerecovery.domain.model.RecoveryScanStatus
import com.nexappra.filerecovery.domain.model.RecoveryCategory

data class FullDeviceScanUiState(
    val mode: com.nexappra.filerecovery.domain.model.RecoveryScanMode = com.nexappra.filerecovery.domain.model.RecoveryScanMode.Quick,
    val sessionId: String = "",
    val previewFiles: List<com.nexappra.filerecovery.domain.model.RecoverableFile> = emptyList(),
    val isStopping: Boolean = false,
    val isPremium: Boolean = false,
    val selectedCategory: RecoveryCategory? = null,
    val scanStatus: RecoveryScanStatus = RecoveryScanStatus.AccessRequired,
    val permissions: DeviceScanPermissions = DeviceScanPermissions(),
    val accessLevel: DeviceScanAccessLevel = DeviceScanAccessLevel.NoAccess,
    val accessSummary: String? = null,
    val progress: Float? = null,
    val totalItemsScanned: Int = 0,
    val totalFilesFound: Int = 0,
    val hiddenPhotoCount: Int = 0,
    val hiddenVideoCount: Int = 0,
    val totalBytesScanned: Long = 0L,
    val locationsChecked: Int = 0,
    val estimatedRemainingMillis: Long? = null,
    val currentLocation: RecoveryScanLocationState? = null,
    val locations: List<RecoveryScanLocationState> = defaultRecoveryLocationStates(),
    val categoryCounts: Map<RecoveryResultFilter, Int> = emptyRecoveryCategoryCounts(),
    val elapsedMillis: Long = 0L,
    val errorMessage: String? = null,
) {
    val isActiveScan: Boolean
        get() = scanStatus == RecoveryScanStatus.Preparing || scanStatus == RecoveryScanStatus.Scanning

    val hasAuthorizedFolders: Boolean
        get() = permissions.safFolderCount > 0

    val isLimitedAccess: Boolean
        get() = accessLevel == DeviceScanAccessLevel.PartialVisualAccess ||
            accessLevel == DeviceScanAccessLevel.LimitedAccess

    val canStartFullDeviceScan: Boolean
        get() = permissions.canStartFullDeviceScan
}

fun defaultRecoveryLocationStates(): List<RecoveryScanLocationState> = RecoveryScanLocationType.entries
    .map { type -> RecoveryScanLocationState(type = type) }

fun emptyRecoveryCategoryCounts(): Map<RecoveryResultFilter, Int> = RecoveryResultFilter.entries
    .associateWith { 0 }
