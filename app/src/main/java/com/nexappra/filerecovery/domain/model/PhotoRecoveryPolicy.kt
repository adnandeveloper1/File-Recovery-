package com.nexappra.filerecovery.domain.model

/** Photo-specific entry points retained for existing callers. */
object PhotoRecoveryPolicy {
    fun recoveryName(safeName: String): String = VisualRecoveryPolicy.recoveryName(safeName, "recovered_photo")

    fun hiddenReason(name: String, path: String, noMedia: Boolean = false): RecoveryFileHiddenReason? =
        VisualRecoveryPolicy.hiddenReason(name, path, noMedia)

    fun isRecycleLocation(name: String, path: String): Boolean =
        VisualRecoveryPolicy.isRecycleLocation(name, path)

    fun isCandidate(name: String, path: String, trashed: Boolean = false, noMedia: Boolean = false): Boolean =
        VisualRecoveryPolicy.isCandidate(name, path, trashed, noMedia)

    fun accepts(file: RecoverableFile): Boolean = file.fileType != RecoveryFileType.Photo ||
        VisualRecoveryPolicy.accepts(file)

    fun exploreQuickDirectory(path: String, noMedia: Boolean): Boolean =
        VisualRecoveryPolicy.exploreQuickDirectory(path, noMedia)

    fun indexedPathPatterns(): List<String> = VisualRecoveryPolicy.indexedPathPatterns()
}
