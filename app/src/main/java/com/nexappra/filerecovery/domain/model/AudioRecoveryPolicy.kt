package com.nexappra.filerecovery.domain.model

/** Audio needs recovery context and verified absence from the ordinary audio library. */
object AudioRecoveryPolicy {
    fun isCandidate(name: String, path: String, trashed: Boolean = false, noMedia: Boolean = false): Boolean =
        VisualRecoveryPolicy.isCandidate(name, path, trashed, noMedia)

    fun accepts(file: RecoverableFile): Boolean = file.fileType != RecoveryFileType.Audio ||
        (isCandidate(file.displayName, file.relativePath.orEmpty(), file.isTrashed,
            file.hiddenReason == RecoveryFileHiddenReason.NoMediaDirectory || RecoveryFileSource.NoMedia in file.sources) &&
            (file.isInAudioLibrary == false || (file.isInAudioLibrary == null && file.isTrashed)))
}
