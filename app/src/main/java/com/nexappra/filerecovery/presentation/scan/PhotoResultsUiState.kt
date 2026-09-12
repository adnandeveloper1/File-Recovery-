package com.nexappra.filerecovery.presentation.scan

import com.nexappra.filerecovery.domain.model.PhotoResultFilter
import com.nexappra.filerecovery.domain.model.PhotoScanMode
import com.nexappra.filerecovery.domain.model.RecoverablePhoto

data class PhotoResultsUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val hasMissingSession: Boolean = false,
    val scanMode: PhotoScanMode = PhotoScanMode.Quick,
    val scanDurationMillis: Long = 0L,
    val totalFound: Int = 0,
    val allPhotos: List<RecoverablePhoto> = emptyList(),
    val visiblePhotos: List<RecoverablePhoto> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val selectedTotalSizeBytes: Long = 0L,
    val selectedFilter: PhotoResultFilter = PhotoResultFilter.All,
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
)
