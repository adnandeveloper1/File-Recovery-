package com.nexappra.filerecovery.data.repository

import com.nexappra.filerecovery.domain.model.RecoverableFile
import com.nexappra.filerecovery.domain.model.RecoveryFileType

/** Retains identities only; gallery photo and video contents are never read. */
internal class MediaLibraryIndex(private val type: RecoveryFileType, var complete: Boolean) {
    private val identities = HashSet<String>()

    fun add(identity: String?) {
        if (!identity.isNullOrBlank()) identities += identity
    }

    fun check(file: RecoverableFile): RecoverableFile {
        if (file.fileType != type) return file
        val visible = when {
            file.id in identities || file.uriString in identities -> true
            complete -> false
            else -> null
        }
        return when (type) {
            RecoveryFileType.Photo -> file.copy(isInPhotoLibrary = visible)
            RecoveryFileType.Video -> file.copy(isInVideoLibrary = visible)
            else -> file
        }
    }
}
