package com.nexappra.filerecovery.domain.model

enum class PhotoScanStatus {
    Idle,
    PermissionRequired,
    Preparing,
    Scanning,
    Completed,
    Cancelled,
    Error,
}
