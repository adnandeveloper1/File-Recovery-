package com.nexappra.filerecovery.domain.repository

import com.nexappra.filerecovery.domain.model.RecoveryCopyResult
import com.nexappra.filerecovery.domain.model.RecoveryCategory
import com.nexappra.filerecovery.domain.model.RecoveryScanSession
import com.nexappra.filerecovery.domain.model.RecoveryScanSnapshot
import com.nexappra.filerecovery.domain.model.RecoveryScanMode

interface RecoveryScanRepository {
    suspend fun performFullDeviceScan(
        selectedCategory: RecoveryCategory? = null,
        mode: RecoveryScanMode = RecoveryScanMode.Quick,
        onSnapshot: suspend (RecoveryScanSnapshot) -> Unit,
    ): RecoveryScanSession

    suspend fun getSession(sessionId: String): RecoveryScanSession?

    suspend fun recoverToFolder(
        sessionId: String,
        fileIds: Set<String>,
        treeUriString: String,
    ): RecoveryCopyResult

    suspend fun exportArchive(sessionId: String, fileIds: Set<String>, documentUri: String): RecoveryCopyResult

    suspend fun repairPhoto(sessionId: String, fileId: String, documentUri: String): RecoveryCopyResult
}
