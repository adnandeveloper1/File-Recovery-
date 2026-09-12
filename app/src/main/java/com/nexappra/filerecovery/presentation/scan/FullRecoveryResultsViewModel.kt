package com.nexappra.filerecovery.presentation.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.nexappra.filerecovery.core.utils.FILE_RECOVERY_DEBUG_TAG
import com.nexappra.filerecovery.domain.model.RecoverableFile
import com.nexappra.filerecovery.domain.model.RecoveryFileSource
import com.nexappra.filerecovery.domain.model.RecoveryFileType
import com.nexappra.filerecovery.domain.model.RecoveryResultFilter
import com.nexappra.filerecovery.domain.usecase.GetRecoveryScanSessionUseCase
import com.nexappra.filerecovery.domain.usecase.RecoverFilesToFolderUseCase
import com.nexappra.filerecovery.presentation.navigation.AppDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FullRecoveryResultsEvent {
    data class ShowMessage(val message: String) : FullRecoveryResultsEvent
}

@HiltViewModel
class FullRecoveryResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getRecoveryScanSessionUseCase: GetRecoveryScanSessionUseCase,
    private val recoverFilesToFolderUseCase: RecoverFilesToFolderUseCase,
) : ViewModel() {

    private val sessionId = savedStateHandle.get<String>(AppDestination.FullRecoveryResults.ArgSessionId)
        .orEmpty()

    private val _uiState = MutableStateFlow(FullRecoveryResultsUiState())
    val uiState = _uiState.asStateFlow()

    private val eventsChannel = Channel<FullRecoveryResultsEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    init {
        loadSession()
    }

    fun onFilterSelected(filter: RecoveryResultFilter) {
        _uiState.update { state -> state.copy(selectedFilter = filter) }
        refreshVisibleFiles()
    }

    fun onSearchVisibilityToggle() {
        _uiState.update { state ->
            if (state.isSearchVisible) {
                state.copy(
                    isSearchVisible = false,
                    searchQuery = "",
                )
            } else {
                state.copy(isSearchVisible = true)
            }
        }
        refreshVisibleFiles()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state -> state.copy(searchQuery = query) }
        refreshVisibleFiles()
    }

    fun onToggleSelection(fileId: String) {
        _uiState.update { state ->
            val updatedSelection = state.selectedIds.toMutableSet().apply {
                if (!add(fileId)) remove(fileId)
            }
            state.copy(
                selectedIds = updatedSelection,
                selectedTotalSizeBytes = state.allFiles
                    .filter { file -> file.id in updatedSelection }
                    .sumOf { file -> file.sizeBytes },
            )
        }
    }

    fun recoverSelectedTo(treeUriString: String) {
        val selectedIds = uiState.value.selectedIds
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            val result = recoverFilesToFolderUseCase(
                sessionId = sessionId,
                fileIds = selectedIds,
                treeUriString = treeUriString,
            )
            _uiState.update { state ->
                state.copy(
                    selectedIds = emptySet(),
                    selectedTotalSizeBytes = 0L,
                )
            }
            eventsChannel.send(
                FullRecoveryResultsEvent.ShowMessage(
                    message = if (result.skippedCount == 0) {
                        "Recovered ${result.recoveredCount} file(s)."
                    } else {
                        "Recovered ${result.recoveredCount} file(s), skipped ${result.skippedCount}."
                    },
                ),
            )
        }
    }

    fun onRestoreSelectedConfirmed() {
        val restoredIds = uiState.value.selectedIds
        if (restoredIds.isEmpty()) return

        _uiState.update { state ->
            state.copy(
                allFiles = state.allFiles.map { file ->
                    if (file.id in restoredIds) {
                        file.copy(
                            isTrashed = false,
                            dateExpiresMillis = null,
                        )
                    } else {
                        file
                    }
                },
                selectedIds = emptySet(),
                selectedTotalSizeBytes = 0L,
            )
        }
        refreshVisibleFiles()
    }

    private fun loadSession() {
        viewModelScope.launch {
            Log.d(
                FILE_RECOVERY_DEBUG_TAG,
                "Results screen received session=$sessionId",
            )
            val loadedSession = getRecoveryScanSessionUseCase(sessionId)
            if (loadedSession == null) {
                Log.w(
                    FILE_RECOVERY_DEBUG_TAG,
                    "Repository result count for session $sessionId = null",
                )
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        hasMissingSession = true,
                    )
                }
                return@launch
            }

            val sortedFiles = loadedSession.files.sortedByDescending { file -> file.dateModifiedMillis }
            Log.d(
                FILE_RECOVERY_DEBUG_TAG,
                "Repository result count for session ${loadedSession.id} = ${sortedFiles.size}",
            )
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    errorMessage = null,
                    hasMissingSession = false,
                    scanDurationMillis = loadedSession.durationMillis,
                    totalFound = sortedFiles.size,
                    allFiles = sortedFiles,
                    visibleFiles = sortedFiles,
                    selectedFilter = RecoveryResultFilter.All,
                    searchQuery = "",
                    selectedIds = emptySet(),
                    selectedTotalSizeBytes = 0L,
                    isSearchVisible = false,
                )
            }
        }
    }

    private fun refreshVisibleFiles() {
        val current = _uiState.value
        val query = current.searchQuery.trim()
        val filtered = current.allFiles
            .asSequence()
            .filter { file -> file.matchesFilter(current.selectedFilter) }
            .filter { file ->
                if (query.isBlank()) {
                    true
                } else {
                    file.displayName.contains(query, ignoreCase = true) ||
                        file.relativePath.orEmpty().contains(query, ignoreCase = true) ||
                        file.mimeType.orEmpty().contains(query, ignoreCase = true)
                }
            }
            .sortedByDescending { file -> file.dateModifiedMillis }
            .toList()

        Log.d(
            FILE_RECOVERY_DEBUG_TAG,
            "UI filtered result count = ${filtered.size} filter=${current.selectedFilter} query=${if (query.isBlank()) "<blank>" else query}",
        )

        _uiState.update { state -> state.copy(visibleFiles = filtered) }
    }

    private fun RecoverableFile.matchesFilter(filter: RecoveryResultFilter): Boolean {
        return when (filter) {
            RecoveryResultFilter.All -> true
            RecoveryResultFilter.Photos -> fileType == RecoveryFileType.Photo
            RecoveryResultFilter.Videos -> fileType == RecoveryFileType.Video
            RecoveryResultFilter.Audio -> fileType == RecoveryFileType.Audio
            RecoveryResultFilter.Documents -> fileType == RecoveryFileType.Document || fileType == RecoveryFileType.Archive
            RecoveryResultFilter.WhatsApp -> RecoveryFileSource.WhatsApp in sources
            RecoveryResultFilter.Downloads -> RecoveryFileSource.Downloads in sources
            RecoveryResultFilter.Screenshots -> RecoveryFileSource.Screenshots in sources
            RecoveryResultFilter.Hidden -> isHidden
            RecoveryResultFilter.RecycleBin -> isTrashed
            RecoveryResultFilter.LargeFiles -> sizeBytes >= 100L * 1024L * 1024L
            RecoveryResultFilter.Other -> fileType == RecoveryFileType.Other || RecoveryFileSource.Other in sources
        }
    }
}
