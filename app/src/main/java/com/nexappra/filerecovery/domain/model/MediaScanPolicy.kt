package com.nexappra.filerecovery.domain.model

import java.util.Locale

/** Shared by indexed queries, folder traversal and the final result boundary. */
data class MediaScanPolicy(val category: RecoveryCategory? = null) {
    init {
        require(category == null || category == RecoveryCategory.Photos || category == RecoveryCategory.Videos || category == RecoveryCategory.Audio)
    }

    val types: Set<RecoveryFileType> = when (category) {
        RecoveryCategory.Photos -> setOf(RecoveryFileType.Photo)
        RecoveryCategory.Videos -> setOf(RecoveryFileType.Video)
        RecoveryCategory.Audio -> setOf(RecoveryFileType.Audio)
        else -> setOf(RecoveryFileType.Photo, RecoveryFileType.Video)
    }

    fun accepts(name: String, mime: String?): Boolean = classify(name, mime) in types

    fun shouldReadHeader(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext in setOf("", "cache", "tmp", "dat", "bin", "thumb", "cnt", "idx", "p", "exo", "0", "1", "2")) return true
        if (ext.all { it.isDigit() }) return true
        if (name.contains("thumb", ignoreCase = true) || name.startsWith(".")) return true
        return false
    }

    fun shouldReadHeader(name: String, mime: String?): Boolean = shouldReadHeader(name) &&
        mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty() in
        setOf("", "application/octet-stream", "application/unknown", "chemical/x-cache", "application/x-cache")

    companion object {
        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "dng", "tif", "tiff", "svg", "ico", "raw")
        val videoExtensions = setOf("mp4", "m4v", "mkv", "3gp", "3gpp", "webm", "mov", "avi", "mpeg", "mpg", "ts", "flv", "wmv", "asf", "3g2", "3gp2", "vob", "ogv", "m2ts", "mts", "divx", "f4v", "m2v")
        val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "mid", "midi", "wma")

        fun classify(name: String, mime: String?): RecoveryFileType? {
            val normalized = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
            if (normalized?.startsWith("image/") == true) return RecoveryFileType.Photo
            if (normalized?.startsWith("video/") == true) return RecoveryFileType.Video
            if (normalized?.startsWith("audio/") == true) return RecoveryFileType.Audio
            // A known non-visual MIME must never be disguised by a filename extension.
            if (!normalized.isNullOrBlank() && normalized !in setOf("application/octet-stream", "application/unknown")) return null
            return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                in imageExtensions -> RecoveryFileType.Photo
                in videoExtensions -> RecoveryFileType.Video
                in audioExtensions -> RecoveryFileType.Audio
                else -> null
            }
        }

        fun safeFileName(name: String): String = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\p{Cntrl}:*?\"<>|]"), "_").trim().take(180)
            .takeUnless { it.isBlank() || it == "." || it == ".." } ?: "recovered_media"
    }
}
