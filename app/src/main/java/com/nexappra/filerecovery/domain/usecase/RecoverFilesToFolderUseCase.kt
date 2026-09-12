package com.nexappra.filerecovery.domain.usecase

import com.nexappra.filerecovery.domain.model.RecoveryCopyResult
import com.nexappra.filerecovery.domain.repository.RecoveryScanRepository
import javax.inject.Inject

class RecoverFilesToFolderUseCase @Inject constructor(
    private val repository: RecoveryScanRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        fileIds: Set<String>,
        treeUriString: String,
    ): RecoveryCopyResult = repository.recoverToFolder(
        sessionId = sessionId,
        fileIds = fileIds,
        treeUriString = treeUriString,
    )
}
