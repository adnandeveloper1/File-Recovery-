package com.nexappra.filerecovery.domain.usecase

import com.nexappra.filerecovery.domain.model.StorageInfo
import com.nexappra.filerecovery.domain.repository.StorageRepository
import javax.inject.Inject

class GetStorageInfoUseCase @Inject constructor(
    private val storageRepository: StorageRepository,
) {
    suspend operator fun invoke(): StorageInfo = storageRepository.getStorageInfo()
}
