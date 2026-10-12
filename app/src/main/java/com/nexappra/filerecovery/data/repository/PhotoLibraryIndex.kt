package com.nexappra.filerecovery.data.repository

import com.nexappra.filerecovery.domain.model.RecoverableFile
import com.nexappra.filerecovery.domain.model.RecoveryFileType

/** Only identities are retained; gallery image contents are never read. */
internal class PhotoLibraryIndex(var complete: Boolean) {
    private val identities = HashSet<String>()

    fun add(identity: String?) {
        if (!identity.isNullOrBlank()) identities += identity
    }

    fun check(file: RecoverableFile): RecoverableFile =
        if (file.fileType != RecoveryFileType.Photo) file else file.copy(isInPhotoLibrary = when {
            file.id in identities || file.uriString in identities -> true
            complete -> false
            else -> null
        })
}
