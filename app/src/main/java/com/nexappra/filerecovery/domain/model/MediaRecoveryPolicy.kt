package com.nexappra.filerecovery.domain.model

import java.util.Locale

/** Every media category needs a hidden or recycle-bin context before content is inspected. */
object MediaRecoveryPolicy {
    private val types = setOf(RecoveryFileType.Photo, RecoveryFileType.Video, RecoveryFileType.Audio)
    private val audioParents = setOf("music", "recordings", "recording", "records", "recorder", "sounds", "podcasts", "audiobooks", "ringtones", "notifications")

    fun isMedia(type: RecoveryFileType): Boolean = type in types

    fun accepts(file: RecoverableFile): Boolean = isMedia(file.fileType) &&
        when (file.fileType) {
            RecoveryFileType.Photo -> PhotoRecoveryPolicy.accepts(file)
            RecoveryFileType.Video -> VideoRecoveryPolicy.accepts(file)
            RecoveryFileType.Audio -> AudioRecoveryPolicy.accepts(file)
            else -> false
        }

    fun exploreQuickDirectory(path: String, noMedia: Boolean, includesAudio: Boolean): Boolean =
        VisualRecoveryPolicy.exploreQuickDirectory(path, noMedia) ||
            (includesAudio && path.split('/', '\\').firstOrNull { it.isNotEmpty() }?.lowercase(Locale.ROOT) in audioParents)
}
