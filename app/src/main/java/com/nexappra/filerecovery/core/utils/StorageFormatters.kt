package com.nexappra.filerecovery.core.utils

import kotlin.math.roundToLong

private const val BytesPerGibibyte = 1024.0 * 1024.0 * 1024.0

fun Long.toDisplayGigabytes(): String {
    val value = (this / BytesPerGibibyte).roundToLong()
    return "$value GB"
}
