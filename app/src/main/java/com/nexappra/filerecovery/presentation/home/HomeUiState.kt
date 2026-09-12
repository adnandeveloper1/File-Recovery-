package com.nexappra.filerecovery.presentation.home

import com.nexappra.filerecovery.domain.model.RecoveryCategory
import com.nexappra.filerecovery.domain.model.StorageInfo

data class HomeUiState(
    val isLoading: Boolean = true,
    val storageInfo: StorageInfo? = null,
    val categories: List<RecoveryCategory> = RecoveryCategory.entries,
    val errorMessage: String? = null,
)
