package com.nexappra.filerecovery.presentation.home

import com.nexappra.filerecovery.domain.model.RecoveryCategory
import com.nexappra.filerecovery.domain.model.StorageInfo

data class HomeUiState(
    val isLoading: Boolean = true,
    val isPremium: Boolean = false,
    val storageInfo: StorageInfo? = null,
    val categories: List<RecoveryCategory> = listOf(RecoveryCategory.Photos, RecoveryCategory.Videos),
    val errorMessage: String? = null,
)
