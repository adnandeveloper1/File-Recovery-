package com.nexappra.filerecovery.presentation.scan

import com.nexappra.filerecovery.domain.model.RecoverableFile
import com.nexappra.filerecovery.domain.model.RecoveryResultFilter

data class FullRecoveryResultsUiState(
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
