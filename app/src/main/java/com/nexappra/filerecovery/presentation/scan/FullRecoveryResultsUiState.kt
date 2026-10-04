package com.nexappra.filerecovery.presentation.scan

import com.nexappra.filerecovery.domain.model.RecoverableFile
import com.nexappra.filerecovery.domain.model.RecoveryResultFilter

data class FullRecoveryResultsUiState(
    val isPremium: Boolean = false,
    val isRecovering: Boolean = false,
    val isPartial: Boolean = false,
    val warnings: List<String> = emptyList(),
    val selectedCategory: com.nexappra.filerecovery.domain.model.RecoveryCategory? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val hasMissingSession: Boolean = false,
    val scanDurationMillis: Long = 0L,
    val totalFound: Int = 0,
    val allFiles: List<RecoverableFile> = emptyList(),
    val visibleFiles: List<RecoverableFile> = emptyList(),
    val selectedFilter: RecoveryResultFilter = RecoveryResultFilter.All,
    val searchQuery: String = "",
    val selectedIds: Set<String> = emptySet(),
    val selectedTotalSizeBytes: Long = 0L,
    val isSearchVisible: Boolean = false,
) {
    val selectedFiles: List<RecoverableFile>
        get() = allFiles.filter { file -> file.id in selectedIds }

    val selectedFilesAreAllTrashed: Boolean
        get() = selectedFiles.isNotEmpty() && selectedFiles.all { file -> file.isTrashed }
}
