package com.nexappra.filerecovery.data.repository

import com.nexappra.filerecovery.domain.model.RecoverableFile
import com.nexappra.filerecovery.domain.model.RecoveryFileType

/** Retains identities only; ordinary library media contents are never read. */
internal class MediaLibraryIndex(private val type: RecoveryFileType, var complete: Boolean) {
    private val identities = HashSet<String>()

    fun add(identity: String?) {
        if (!identity.isNullOrBlank()) identities += identity
    }

    fun contains(identity: String, uri: String): Boolean = identity in identities || uri in identities

    fun check(file: RecoverableFile): RecoverableFile {
        if (file.fileType != type) return file
        val visible = when {
            contains(file.id, file.uriString) -> true
            complete -> false
            else -> null
        }
        return when (type) {
            RecoveryFileType.Photo -> file.copy(isInPhotoLibrary = visible)
            RecoveryFileType.Video -> file.copy(isInVideoLibrary = visible)
            RecoveryFileType.Audio -> file.copy(isInAudioLibrary = visible)
            else -> file
        }
    }
}
