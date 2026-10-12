package com.nexappra.filerecovery.domain.model

data class RecoverableFile(
    val id: String,
    val uriString: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val dateModifiedMillis: Long,
    val relativePath: String?,
    val fileType: RecoveryFileType,
    val sources: Set<RecoveryFileSource>,
    val isHidden: Boolean,
    val hiddenReason: RecoveryFileHiddenReason?,
    val isTrashed: Boolean,
    val storageVolume: String?,
    val durationMillis: Long? = null,
    val dateExpiresMillis: Long? = null,
    // Null means library visibility could not be verified with the current access.
    val isInPhotoLibrary: Boolean? = null,
)
