package com.nexappra.filerecovery.presentation.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexappra.filerecovery.domain.model.PhotoResultFilter
import com.nexappra.filerecovery.domain.model.PhotoScanMode
import com.nexappra.filerecovery.domain.model.PhotoScanSession
import com.nexappra.filerecovery.domain.model.RecoverablePhoto
import com.nexappra.filerecovery.domain.usecase.GetPhotoScanSessionUseCase
import com.nexappra.filerecovery.presentation.navigation.AppDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PhotoResultsEvent {
    data object ShowRecoveryUnavailable : PhotoResultsEvent
}

@HiltViewModel
class PhotoResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPhotoScanSessionUseCase: GetPhotoScanSessionUseCase,
) : ViewModel() {

    private val sessionId = savedStateHandle.get<String>(AppDestination.PhotoResults.ArgSessionId)
        .orEmpty()

    private val _uiState = MutableStateFlow(PhotoResultsUiState())
    val uiState = _uiState.asStateFlow()

    private val eventsChannel = Channel<PhotoResultsEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    private var session: PhotoScanSession? = null

    init {
        loadSession()
    }

    fun onFilterSelected(filter: PhotoResultFilter) {
        _uiState.update { state -> state.copy(selectedFilter = filter) }
        refreshVisiblePhotos()
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
        refreshVisiblePhotos()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state -> state.copy(searchQuery = query) }
        refreshVisiblePhotos()
    }

    fun onToggleSelection(photoId: Long) {
        _uiState.update { state ->
            val updatedSelection = state.selectedIds.toMutableSet().apply {
                if (!add(photoId)) remove(photoId)
            }
            state.copy(
                selectedIds = updatedSelection,
                selectedTotalSizeBytes = state.allPhotos
                    .filter { photo -> photo.id in updatedSelection }
                    .sumOf { photo -> photo.sizeBytes },
            )
        }
    }

    fun onRecoverSelected() {
        viewModelScope.launch {
            eventsChannel.send(PhotoResultsEvent.ShowRecoveryUnavailable)
        }
    }

    private fun loadSession() {
        viewModelScope.launch {
            val loadedSession = getPhotoScanSessionUseCase(sessionId)
            session = loadedSession

            if (loadedSession == null) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        hasMissingSession = true,
                    )
                }
                return@launch
            }

            val sortedPhotos = loadedSession.photos.sortedByDescending { photo -> photo.dateModifiedMillis }
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    errorMessage = null,
                    hasMissingSession = false,
                    scanMode = loadedSession.mode,
                    scanDurationMillis = loadedSession.durationMillis,
                    totalFound = sortedPhotos.size,
                    allPhotos = sortedPhotos,
                    visiblePhotos = sortedPhotos,
                    selectedFilter = PhotoResultFilter.All,
                    searchQuery = "",
                    selectedIds = emptySet(),
                    selectedTotalSizeBytes = 0L,
                    isSearchVisible = false,
                )
            }
        }
    }

    private fun refreshVisiblePhotos() {
        val current = _uiState.value
        val filtered = current.allPhotos
            .asSequence()
            .filter { photo ->
                when (current.selectedFilter) {
                    PhotoResultFilter.All -> true
                    else -> photo.filter == current.selectedFilter
                }
            }
            .filter { photo ->
                val query = current.searchQuery.trim()
                if (query.isBlank()) {
                    true
                } else {
                    photo.displayName.contains(query, ignoreCase = true) ||
                        photo.relativePath.orEmpty().contains(query, ignoreCase = true) ||
                        photo.bucketName.orEmpty().contains(query, ignoreCase = true)
                }
            }
            .sortedByDescending { photo -> photo.dateModifiedMillis }
            .toList()

        _uiState.update { state ->
            state.copy(visiblePhotos = filtered)
        }
    }
}
