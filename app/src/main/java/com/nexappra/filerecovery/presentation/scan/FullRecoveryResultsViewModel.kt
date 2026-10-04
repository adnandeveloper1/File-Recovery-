package com.nexappra.filerecovery.presentation.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexappra.filerecovery.domain.model.*
import com.nexappra.filerecovery.domain.repository.PremiumRepository
import com.nexappra.filerecovery.domain.repository.RecoveryScanRepository
import com.nexappra.filerecovery.presentation.navigation.AppDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FullRecoveryResultsEvent {
    data class ShowMessage(val message: String) : FullRecoveryResultsEvent
    data object OpenPremium : FullRecoveryResultsEvent
}

@HiltViewModel
class FullRecoveryResultsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: RecoveryScanRepository,
    private val premium: PremiumRepository,
) : ViewModel() {
    private val sessionId = savedStateHandle.get<String>(AppDestination.FullRecoveryResults.ArgSessionId).orEmpty()
    private val mutableState = MutableStateFlow(FullRecoveryResultsUiState())
    val uiState = mutableState.asStateFlow()
    private val messages = Channel<FullRecoveryResultsEvent>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    init {
        viewModelScope.launch { premium.state.collect { billing -> mutableState.update { it.copy(isPremium = billing.access.isActive()) } } }
        viewModelScope.launch {
            try {
                val session = repository.getSession(sessionId)
                if (session == null) mutableState.update { it.copy(isLoading = false, hasMissingSession = true) }
                else {
                    val files = session.files.filter { it.fileType in setOf(RecoveryFileType.Photo, RecoveryFileType.Video) }.sortedByDescending { it.dateModifiedMillis }
                    val selected = savedStateHandle.get<ArrayList<String>>("selected_ids").orEmpty().toSet().intersect(files.map { it.id }.toSet())
                    mutableState.update { it.copy(isLoading = false, allFiles = files, visibleFiles = files, totalFound = files.size,
                        selectedIds = selected, selectedTotalSizeBytes = files.filter { it.id in selected }.sumOf { it.sizeBytes },
                        selectedCategory = session.selectedCategory, scanDurationMillis = session.durationMillis,
                        isPartial = session.isPartial, warnings = session.warnings) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(isLoading = false, errorMessage = "Unable to load this scan. Please try again.") } }
        }
    }

    fun onFilterSelected(filter: RecoveryResultFilter) {
        if (filter !in setOf(RecoveryResultFilter.All, RecoveryResultFilter.Photos, RecoveryResultFilter.Videos)) return
        mutableState.update { it.copy(selectedFilter = filter) }
        filterResults()
    }
    fun onSearchQueryChange(query: String) { mutableState.update { it.copy(searchQuery = query) }; filterResults() }
    fun onToggleSelection(id: String) {
        if (uiState.value.isRecovering || uiState.value.allFiles.none { it.id == id }) return
        select(uiState.value.selectedIds.toMutableSet().apply { if (!add(id)) remove(id) })
    }
    fun selectVisible() {
        if (uiState.value.isRecovering) return
        val visible = uiState.value.visibleFiles.map { it.id }.toSet()
        select(if (uiState.value.selectedIds.containsAll(visible)) uiState.value.selectedIds - visible else uiState.value.selectedIds + visible)
    }
    private fun select(ids: Set<String>) {
        savedStateHandle["selected_ids"] = ArrayList(ids)
        mutableState.update { it.copy(selectedIds = ids, selectedTotalSizeBytes = it.allFiles.filter { f -> f.id in ids }.sumOf { f -> f.sizeBytes }) }
    }
    private fun filterResults() {
        mutableState.update { state -> state.copy(visibleFiles = state.allFiles.filter { file ->
            (state.selectedFilter == RecoveryResultFilter.All ||
                (state.selectedFilter == RecoveryResultFilter.Photos && file.fileType == RecoveryFileType.Photo) ||
                (state.selectedFilter == RecoveryResultFilter.Videos && file.fileType == RecoveryFileType.Video)) &&
                file.displayName.contains(state.searchQuery.trim(), ignoreCase = true)
        }) }
    }

    fun recoverSelectedTo(destination: String) = perform { ids -> repository.recoverToFolder(sessionId, ids, destination) }
    fun exportSelectedTo(destination: String) = perform { ids -> repository.exportArchive(sessionId, ids, destination) }
    fun repairSelectedTo(destination: String) = perform { ids -> repository.repairPhoto(sessionId, ids.single(), destination) }
    private fun perform(action: suspend (Set<String>) -> RecoveryCopyResult) {
        if (uiState.value.isRecovering) return
        val ids = uiState.value.selectedIds.ifEmpty { savedStateHandle.get<ArrayList<String>>("selected_ids").orEmpty().toSet() }
        if (ids.isEmpty()) return
        mutableState.update { it.copy(isRecovering = true) }
        viewModelScope.launch {
            try {
                premium.requirePremium()
                val result = action(ids)
                if (result.skippedCount == 0) select(emptySet())
                messages.send(FullRecoveryResultsEvent.ShowMessage("Saved ${result.recoveredCount} file(s)." + if (result.skippedCount > 0) " ${result.skippedCount} could not be read; your selection is kept for retry." else ""))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: PremiumRequiredException) { messages.send(FullRecoveryResultsEvent.OpenPremium) }
            catch (error: Exception) { messages.send(FullRecoveryResultsEvent.ShowMessage(error.message ?: "Recovery could not finish. Your selected files are still available for retry.")) }
            finally { mutableState.update { it.copy(isRecovering = false) } }
        }
    }
}
