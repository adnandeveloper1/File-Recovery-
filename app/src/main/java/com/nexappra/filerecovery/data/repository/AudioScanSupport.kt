package com.nexappra.filerecovery.data.repository

import com.nexappra.filerecovery.domain.model.RecoveryFileType
import java.util.Locale

internal object AudioScanSupport {
    data class ContainerMetadata(val hasAudio: Boolean, val hasVideo: Boolean, val mimeType: String?)

    fun resolveContainer(type: RecoveryFileType?, mime: String?, metadata: ContainerMetadata?): Pair<RecoveryFileType, String?>? {
        if (metadata == null) return type?.let { it to mime }
        if (metadata.hasVideo) return RecoveryFileType.Video to (metadata.mimeType ?: mime)
        if (!metadata.hasAudio) return type?.let { it to mime }
        val containerMime = (metadata.mimeType ?: mime)?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        val audioMime = when {
            containerMime == "application/ogg" -> "audio/ogg"
            containerMime == "application/mp4" -> "audio/mp4"
            containerMime?.startsWith("audio/") == true -> containerMime
            containerMime?.startsWith("video/") == true -> "audio/" + containerMime.removePrefix("video/")
            else -> "application/octet-stream"
        }
        return RecoveryFileType.Audio to audioMime
    }

    // Zero represents an unknown size only after confirming that source bytes exist.
    // Recovery already verifies non-empty output when the expected size is zero.
    fun readableSize(reportedSize: Long?, hasBytes: () -> Boolean): Long? =
        if (reportedSize != null) reportedSize.takeIf { it > 0 }
        else if (hasBytes()) 0L else null
}
