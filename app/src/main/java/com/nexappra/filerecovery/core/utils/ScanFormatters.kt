package com.nexappra.filerecovery.core.utils

import java.text.DecimalFormat
import java.text.NumberFormat
import kotlin.math.abs

private val countFormatter: NumberFormat = NumberFormat.getIntegerInstance()
private val decimalFormatter = DecimalFormat("#.#")

fun Int.toFormattedCount(): String = countFormatter.format(this)

fun Long.toReadableFileSize(): String {
    val bytes = abs(toDouble())
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0

    return when {
        bytes >= gb -> "${decimalFormatter.format(this / gb)} GB"
        bytes >= mb -> "${decimalFormatter.format(this / mb)} MB"
        bytes >= kb -> "${decimalFormatter.format(this / kb)} KB"
        else -> "$this B"
    }
}

fun Long.toDetailedDurationLabel(): String {
    val totalSeconds = (this / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L

    return when {
        minutes > 0L -> "${minutes} min ${seconds} sec"
        else -> "${seconds} sec"
    }
}

fun Long?.toCompactMediaDurationLabel(): String? {
    if (this == null || this <= 0L) return null

    val totalSeconds = this / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return when {
        hours > 0L -> "%d:%02d:%02d".format(hours, minutes, seconds)
        else -> "%d:%02d".format(minutes, seconds)
    }
}

fun Long?.toRemainingDurationLabel(): String = when {
    this == null -> "--"
    this <= 0L -> "~0 sec"
    else -> {
        val totalSeconds = this / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L

        when {
            minutes >= 60L -> "~${minutes / 60L} hr"
            minutes > 0L -> "~$minutes min"
            else -> "~$seconds sec"
        }
    }
}
