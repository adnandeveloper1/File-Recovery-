package com.nexappra.filerecovery.presentation.scan

enum class DeviceScanAccessLevel {
    FullAccess,
    PartialVisualAccess,
    LimitedAccess,
    NoAccess,
}

data class DeviceScanPermissions(
    val fullImagesAccess: Boolean = false,
    val fullVideosAccess: Boolean = false,
    val partialVisualAccess: Boolean = false,
    val audioAccess: Boolean = false,
    val legacyReadAccess: Boolean = false,
    val allFilesAccess: Boolean = false,
    val sharedStorageTraversalAccess: Boolean = false,
    val safFolderCount: Int = 0,
) {
    val hasFullVisualAccess: Boolean
        get() = legacyReadAccess || allFilesAccess || (fullImagesAccess && fullVideosAccess)

    val hasAnyDeclaredAccess: Boolean
        get() = hasFullVisualAccess || partialVisualAccess || audioAccess || safFolderCount > 0

    val canStartFullDeviceScan: Boolean
        get() = hasAnyDeclaredAccess

    val accessLevel: DeviceScanAccessLevel
        get() = when {
            sharedStorageTraversalAccess -> DeviceScanAccessLevel.FullAccess
            partialVisualAccess && !hasFullVisualAccess -> DeviceScanAccessLevel.PartialVisualAccess
            hasAnyDeclaredAccess -> DeviceScanAccessLevel.LimitedAccess
            else -> DeviceScanAccessLevel.NoAccess
        }

    val summary: String?
        get() = buildList {
            if (legacyReadAccess) add("Media access (legacy)")
            if (allFilesAccess) add("All files access")
            if (fullImagesAccess) add("Photos")
            if (fullVideosAccess) add("Videos")
            if (partialVisualAccess && !hasFullVisualAccess) add("Selected photos/videos")
            if (audioAccess) add("Audio")
            if (safFolderCount > 0) add("$safFolderCount authorized folder(s)")
        }.joinToString(", ").ifBlank { null }
}

data class DeviceScanAccessState(
    val permissions: DeviceScanPermissions,
) {
    val accessLevel: DeviceScanAccessLevel
        get() = permissions.accessLevel

    val canStartFullDeviceScan: Boolean
        get() = permissions.canStartFullDeviceScan

    val hasAuthorizedFolders: Boolean
        get() = permissions.safFolderCount > 0

    val isLimitedAccess: Boolean
        get() = accessLevel == DeviceScanAccessLevel.PartialVisualAccess ||
            accessLevel == DeviceScanAccessLevel.LimitedAccess

    val summary: String?
        get() = permissions.summary
}
