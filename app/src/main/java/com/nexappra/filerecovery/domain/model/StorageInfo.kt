package com.nexappra.filerecovery.domain.model

import kotlin.math.roundToInt

data class StorageInfo(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    val usedBytes: Long
        get() = (totalBytes - availableBytes).coerceAtLeast(0L)

    val usedFraction: Float
        get() = if (totalBytes <= 0L) 0f else usedBytes.toFloat() / totalBytes.toFloat()

    val usedPercentage: Int
        get() = (usedFraction * 100f).roundToInt()
}
