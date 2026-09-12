package com.nexappra.filerecovery.domain.model

data class RecoverablePhoto(
    val id: Long,
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val dateModifiedMillis: Long,
    val relativePath: String?,
    val bucketName: String?,
    val filter: PhotoResultFilter,
    val locationType: PhotoScanLocationType,
)
