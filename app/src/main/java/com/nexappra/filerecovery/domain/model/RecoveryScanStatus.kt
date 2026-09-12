package com.nexappra.filerecovery.domain.model

enum class RecoveryScanStatus {
    Idle,
    AccessRequired,
    Preparing,
    Scanning,
    Completed,
    CompletedEmpty,
    Cancelled,
    Error,
}
