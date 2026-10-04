package com.nexappra.filerecovery.domain.model

data class RecoveryScanSession(
    val id: String,
    val mode: RecoveryScanMode,
    val selectedCategory: RecoveryCategory? = null,
    val durationMillis: Long,
    val totalBytesScanned: Long,
    val files: List<RecoverableFile>,
    val locations: List<RecoveryScanLocationState>,
    val completedAtMillis: Long,
    val isPartial: Boolean = false,
    val warnings: List<String> = emptyList(),
)
