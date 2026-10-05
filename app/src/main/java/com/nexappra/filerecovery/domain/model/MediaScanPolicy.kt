package com.nexappra.filerecovery.domain.model

import java.util.Locale

/** Shared by indexed queries, folder traversal and the final result boundary. */
data class MediaScanPolicy(val category: RecoveryCategory? = null) {
    init {
        require(category == null || category == RecoveryCategory.Photos || category == RecoveryCategory.Videos)
    }

    val types: Set<RecoveryFileType> = when (category) {
        RecoveryCategory.Photos -> setOf(RecoveryFileType.Photo)
        RecoveryCategory.Videos -> setOf(RecoveryFileType.Video)
        else -> setOf(RecoveryFileType.Photo, RecoveryFileType.Video)
    }

    fun accepts(name: String, mime: String?): Boolean = classify(name, mime) in types

    fun shouldReadHeader(name: String): Boolean = name.substringAfterLast('.', "")
        .lowercase(Locale.ROOT) in setOf("", "cache", "tmp", "dat", "bin")

    fun shouldReadHeader(name: String, mime: String?): Boolean = shouldReadHeader(name) &&
        mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty() in
        setOf("", "application/octet-stream", "application/unknown", "chemical/x-cache", "application/x-cache")

    companion object {
        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "dng", "tif", "tiff")
        val videoExtensions = setOf("mp4", "m4v", "mkv", "3gp", "3gpp", "webm", "mov", "avi", "mpeg", "mpg", "ts")

        fun classify(name: String, mime: String?): RecoveryFileType? {
            val normalized = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
            if (normalized?.startsWith("image/") == true) return RecoveryFileType.Photo
            if (normalized?.startsWith("video/") == true) return RecoveryFileType.Video
            // A known non-visual MIME must never be disguised by a filename extension.
            if (!normalized.isNullOrBlank() && normalized !in setOf("application/octet-stream", "application/unknown")) return null
            return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                in imageExtensions -> RecoveryFileType.Photo
                in videoExtensions -> RecoveryFileType.Video
                else -> null
            }
        }

        fun safeFileName(name: String): String = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\p{Cntrl}:*?\"<>|]"), "_").trim().take(180)
            .takeUnless { it.isBlank() || it == "." || it == ".." } ?: "recovered_media"
    }
}
