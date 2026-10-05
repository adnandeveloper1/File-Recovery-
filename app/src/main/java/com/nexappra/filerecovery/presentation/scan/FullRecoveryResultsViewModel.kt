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
                    val files = orderedFiles(session)
                    val selected = restoredSelection(files)
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
        if (filter !in setOf(RecoveryResultFilter.All, RecoveryResultFilter.Photos, RecoveryResultFilter.Videos, RecoveryResultFilter.Audio)) return
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
        // Thousands of URI/path IDs can exceed Android's saved-state Binder limit.
        // Positions refer to the immutable, persisted session in the same stable order.
        savedStateHandle["selected_indices"] = uiState.value.allFiles.withIndex()
            .filter { it.value.id in ids }.map { it.index }.toIntArray()
        savedStateHandle.remove<ArrayList<String>>("selected_ids")
        mutableState.update { it.copy(selectedIds = ids, selectedTotalSizeBytes = it.allFiles.filter { f -> f.id in ids }.sumOf { f -> f.sizeBytes }) }
    }
    private fun orderedFiles(session: RecoveryScanSession) = session.files
        .filter { it.fileType in setOf(RecoveryFileType.Photo, RecoveryFileType.Video, RecoveryFileType.Audio) }.sortedByDescending { it.dateModifiedMillis }

    private fun restoredSelection(files: List<RecoverableFile>): Set<String> {
        val indices = savedStateHandle.get<IntArray>("selected_indices")
        return if (indices != null) indices.map { files.getOrNull(it)?.id }.filterNotNull().toSet()
        else savedStateHandle.get<ArrayList<String>>("selected_ids").orEmpty().toSet().intersect(files.map { it.id }.toSet())
    }
    private fun filterResults() {
        mutableState.update { state -> state.copy(visibleFiles = state.allFiles.filter { file ->
            (state.selectedFilter == RecoveryResultFilter.All ||
                (state.selectedFilter == RecoveryResultFilter.Photos && file.fileType == RecoveryFileType.Photo) ||
                (state.selectedFilter == RecoveryResultFilter.Videos && file.fileType == RecoveryFileType.Video) ||
                (state.selectedFilter == RecoveryResultFilter.Audio && file.fileType == RecoveryFileType.Audio)) &&
                file.displayName.contains(state.searchQuery.trim(), ignoreCase = true)
        }) }
    }

    fun recoverSelectedTo(destination: String) = perform { ids -> repository.recoverToFolder(sessionId, ids, destination) }
    fun exportSelectedTo(destination: String) = perform { ids -> repository.exportArchive(sessionId, ids, destination) }
    fun repairSelectedTo(destination: String) = perform { ids -> repository.repairPhoto(sessionId, ids.single(), destination) }
    private fun perform(action: suspend (Set<String>) -> RecoveryCopyResult) {
        if (uiState.value.isRecovering) return
        val currentIds = uiState.value.selectedIds
        if (currentIds.isEmpty() && savedStateHandle.get<IntArray>("selected_indices")?.isNotEmpty() != true &&
            savedStateHandle.get<ArrayList<String>>("selected_ids").isNullOrEmpty()) return
        mutableState.update { it.copy(isRecovering = true) }
        viewModelScope.launch {
            try {
                premium.requirePremium()
                // A document-picker result can arrive before this screen finishes reloading.
                val ids = currentIds.ifEmpty {
                    val session = repository.getSession(sessionId) ?: error("This scan is no longer available. Please scan again.")
                    restoredSelection(orderedFiles(session))
                }
                check(ids.isNotEmpty()) { "Select the files to save and try again." }
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
