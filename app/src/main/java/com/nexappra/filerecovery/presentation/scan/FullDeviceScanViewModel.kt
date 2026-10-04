package com.nexappra.filerecovery.presentation.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexappra.filerecovery.domain.model.*
import com.nexappra.filerecovery.domain.repository.PremiumRepository
import com.nexappra.filerecovery.domain.repository.RecoveryScanRepository
import com.nexappra.filerecovery.presentation.navigation.AppDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

sealed interface FullDeviceScanNavigationEvent {
    data class OpenResults(val sessionId: String) : FullDeviceScanNavigationEvent
    data object Close : FullDeviceScanNavigationEvent
}

@HiltViewModel
class FullDeviceScanViewModel @Inject constructor(
    private val repository: RecoveryScanRepository,
    private val premium: PremiumRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val category = savedStateHandle.get<String>(AppDestination.FullDeviceScan.ArgCategory)
        ?.let { name -> listOf(RecoveryCategory.Photos, RecoveryCategory.Videos).firstOrNull { it.name == name } }
    private val mode = if (savedStateHandle.get<String>(AppDestination.FullDeviceScan.ArgMode) == "Deep") RecoveryScanMode.Deep else RecoveryScanMode.Quick
    private val mutableState = MutableStateFlow(FullDeviceScanUiState(selectedCategory = category, mode = mode))
    val uiState = mutableState.asStateFlow()
    private val navigation = Channel<FullDeviceScanNavigationEvent>(Channel.BUFFERED)
    val events = navigation.receiveAsFlow()
    private var scanJob: Job? = null
    private var started = false

    init {
        viewModelScope.launch { premium.state.collect { state -> mutableState.update { it.copy(isPremium = state.access.isActive()) } } }
    }

    fun onAccessStateChanged(access: DeviceScanAccessState) {
        mutableState.update { it.copy(permissions = access.permissions, accessLevel = access.accessLevel, accessSummary = access.summary) }
        if (!started && mode == RecoveryScanMode.Quick) startScan()
    }

    private fun hasAccess(): Boolean = with(uiState.value.permissions) {
        legacyReadAccess || allFilesAccess || partialVisualAccess ||
            (category != RecoveryCategory.Videos && fullImagesAccess) ||
            (category != RecoveryCategory.Photos && fullVideosAccess) ||
            (mode == RecoveryScanMode.Deep && safFolderCount > 0)
    }

    fun startScan() {
        if (scanJob?.isActive == true || started || uiState.value.isStopping) return
        if (mode == RecoveryScanMode.Deep && !premium.state.value.access.isActive()) return
        if (!hasAccess()) { mutableState.update { it.copy(scanStatus = RecoveryScanStatus.AccessRequired) }; return }
        started = true
        mutableState.update { it.copy(scanStatus = RecoveryScanStatus.Preparing, errorMessage = null) }
        scanJob = viewModelScope.launch {
            try {
                val session = repository.performFullDeviceScan(category, mode) { snapshot ->
                    mutableState.update { it.copy(scanStatus = snapshot.status, sessionId = snapshot.sessionId,
                        previewFiles = snapshot.previewFiles, totalItemsScanned = snapshot.totalItemsScanned,
                        totalFilesFound = snapshot.totalFilesFound, totalBytesScanned = snapshot.totalBytesScanned,
                        elapsedMillis = snapshot.elapsedMillis, progress = snapshot.progress,
                        currentLocation = snapshot.currentLocation, locations = snapshot.locations,
                        categoryCounts = snapshot.categoryCounts) }
                }
                navigation.send(FullDeviceScanNavigationEvent.OpenResults(session.id))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                started = false
                mutableState.update { it.copy(scanStatus = RecoveryScanStatus.Error, errorMessage = error.message ?: "Unable to read this storage. Please try again.") }
            }
        }
    }

    fun retryScan() { if (scanJob?.isActive != true) { started = false; startScan() } }

    fun finishEarly(showResults: Boolean = true) {
        if (uiState.value.isStopping) return
        mutableState.update { it.copy(isStopping = true) }
        viewModelScope.launch {
            scanJob?.cancelAndJoin()
            val id = uiState.value.sessionId
            if (showResults && id.isNotBlank() && repository.getSession(id) != null) navigation.send(FullDeviceScanNavigationEvent.OpenResults(id))
            else navigation.send(FullDeviceScanNavigationEvent.Close)
        }
    }

    fun stopScan() = finishEarly(false)
}
