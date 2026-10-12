package com.nexappra.filerecovery.domain.model

/** Photos must have recovery context and be absent from the visible photo library. */
object PhotoRecoveryPolicy {
    fun isThumbnailLocation(path: String): Boolean = VisualRecoveryPolicy.isThumbnailLocation(path)

    fun recoveryName(safeName: String): String = VisualRecoveryPolicy.recoveryName(safeName, "recovered_photo")

    fun hiddenReason(name: String, path: String, noMedia: Boolean = false): RecoveryFileHiddenReason? =
        VisualRecoveryPolicy.hiddenReason(name, path, noMedia)

    fun isRecycleLocation(name: String, path: String): Boolean =
        VisualRecoveryPolicy.isRecycleLocation(name, path)

    fun isCandidate(name: String, path: String, trashed: Boolean = false, noMedia: Boolean = false): Boolean =
        !isThumbnailLocation(path) && !name.startsWith(".thumbdata", true) &&
            VisualRecoveryPolicy.isCandidate(name, path, trashed, noMedia)

    fun accepts(file: RecoverableFile): Boolean = file.fileType != RecoveryFileType.Photo ||
        (isCandidate(file.displayName, file.relativePath.orEmpty(), file.isTrashed,
            file.hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory || RecoveryFileSource.NoMedia in file.sources) &&
            (file.isInPhotoLibrary == false || (file.isInPhotoLibrary == null && file.isTrashed)))

    fun exploreQuickDirectory(path: String, noMedia: Boolean): Boolean =
        VisualRecoveryPolicy.exploreQuickDirectory(path, noMedia)

    fun indexedPathPatterns(): List<String> = VisualRecoveryPolicy.indexedPathPatterns()
}
