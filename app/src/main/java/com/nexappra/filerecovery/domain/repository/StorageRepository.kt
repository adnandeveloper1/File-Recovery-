package com.nexappra.filerecovery.domain.repository

import com.nexappra.filerecovery.domain.model.StorageInfo

interface StorageRepository {
    suspend fun getStorageInfo(): StorageInfo
}
