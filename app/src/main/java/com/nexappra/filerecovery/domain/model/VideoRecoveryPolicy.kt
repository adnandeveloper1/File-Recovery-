package com.nexappra.filerecovery.domain.model

/** Videos need recovery context and verified absence from the visible video library. */
object VideoRecoveryPolicy {
    fun isCandidate(name: String, path: String, trashed: Boolean = false, noMedia: Boolean = false): Boolean =
        !VisualRecoveryPolicy.isThumbnailLocation(path) && !name.startsWith(".thumbdata", true) &&
            VisualRecoveryPolicy.isCandidate(name, path, trashed, noMedia)

    fun accepts(file: RecoverableFile): Boolean = file.fileType != RecoveryFileType.Video ||
        (isCandidate(file.displayName, file.relativePath.orEmpty(), file.isTrashed,
            file.hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory || RecoveryFileSource.NoMedia in file.sources) &&
            (file.isInVideoLibrary == false || (file.isInVideoLibrary == null && file.isTrashed)))
}
