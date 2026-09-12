package com.nexappra.filerecovery.presentation.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.nexappra.filerecovery.core.utils.FILE_RECOVERY_DEBUG_TAG
import com.nexappra.filerecovery.domain.model.RecoveryScanSnapshot
import com.nexappra.filerecovery.domain.model.RecoveryScanStatus
import com.nexappra.filerecovery.domain.model.RecoveryCategory
import com.nexappra.filerecovery.domain.usecase.PerformRecoveryScanUseCase
import com.nexappra.filerecovery.presentation.navigation.AppDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FullDeviceScanNavigationEvent {
    data class OpenResults(val sessionId: String) : FullDeviceScanNavigationEvent
    data object Close : FullDeviceScanNavigationEvent
}

@HiltViewModel
class FullDeviceScanViewModel @Inject constructor(
    private val performRecoveryScanUseCase: PerformRecoveryScanUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val selectedCategory = savedStateHandle.get<String>(AppDestination.FullDeviceScan.ArgCategory)
        ?.let { name -> RecoveryCategory.entries.firstOrNull { it.name == name } }

    private val _uiState = MutableStateFlow(FullDeviceScanUiState(selectedCategory = selectedCategory))
    val uiState = _uiState.asStateFlow()

    private val navigationEvents = Channel<FullDeviceScanNavigationEvent>(Channel.BUFFERED)
    val events = navigationEvents.receiveAsFlow()

    private var scanStarted = false
    private var scanJobId = 0
    private var scanJob: Job? = null

    fun onAccessStateChanged(
        accessState: DeviceScanAccessState,
    ) {
        Log.d(
            FILE_RECOVERY_DEBUG_TAG,
            "onAccessStateChanged accessLevel=${accessState.accessLevel} canStartFullDeviceScan=${accessState.canStartFullDeviceScan} summary=${accessState.summary}",
        )
        _uiState.update { state ->
            state.copy(
                permissions = accessState.permissions,
                accessLevel = accessState.accessLevel,
                accessSummary = accessState.summary,
                scanStatus = when {
                    accessState.canStartFullDeviceScan && state.scanStatus == RecoveryScanStatus.AccessRequired ->
                        RecoveryScanStatus.Preparing
                    !accessState.canStartFullDeviceScan && state.scanStatus != RecoveryScanStatus.Completed &&
                        state.scanStatus != RecoveryScanStatus.CompletedEmpty -> RecoveryScanStatus.AccessRequired
                    else -> state.scanStatus
                },
                errorMessage = if (accessState.canStartFullDeviceScan) state.errorMessage else null,
            )
        }

        if (accessState.canStartFullDeviceScan) {
            startScanIfNeeded()
        }
    }

    fun retryScan() {
        scanStarted = false
        _uiState.update { state ->
            state.copy(
                scanStatus = if (state.canStartFullDeviceScan) {
                    RecoveryScanStatus.Preparing
                } else {
                    RecoveryScanStatus.AccessRequired
                },
                progress = null,
                totalItemsScanned = 0,
                totalFilesFound = 0,
                totalBytesScanned = 0L,
                locationsChecked = 0,
                estimatedRemainingMillis = null,
                currentLocation = null,
                locations = defaultRecoveryLocationStates(),
                categoryCounts = emptyRecoveryCategoryCounts(),
                elapsedMillis = 0L,
                errorMessage = null,
            )
        }
        startScanIfNeeded()
    }

    fun stopScan() {
        scanStarted = false
        scanJobId += 1
        scanJob?.cancel()
        scanJob = null
        _uiState.update { state -> state.copy(scanStatus = RecoveryScanStatus.Cancelled) }
        viewModelScope.launch {
            navigationEvents.send(FullDeviceScanNavigationEvent.Close)
        }
    }

    private fun startScanIfNeeded() {
        if (!_uiState.value.canStartFullDeviceScan || scanStarted) return

        scanStarted = true
        val currentJobId = ++scanJobId
        Log.d(
            FILE_RECOVERY_DEBUG_TAG,
            "Starting full device scan jobId=$currentJobId",
        )
        scanJob = viewModelScope.launch {
            try {
                val session = performRecoveryScanUseCase(selectedCategory) { snapshot ->
                    if (currentJobId == scanJobId) {
                        applySnapshot(snapshot)
                    }
                }
                if (currentJobId == scanJobId) {
                    Log.d(
                        FILE_RECOVERY_DEBUG_TAG,
                        "Scan completed session=${session.id} resultCount=${session.files.size}",
                    )
                    navigationEvents.send(FullDeviceScanNavigationEvent.OpenResults(session.id))
                }
            } catch (error: CancellationException) {
                if (currentJobId == scanJobId) {
                    _uiState.update { state -> state.copy(scanStatus = RecoveryScanStatus.Cancelled) }
                }
            } catch (error: SecurityException) {
                Log.e(
                    FILE_RECOVERY_DEBUG_TAG,
                    "Full device scan blocked by SecurityException",
                    error,
                )
                scanStarted = false
                _uiState.update { state ->
                    state.copy(
                        scanStatus = RecoveryScanStatus.AccessRequired,
                        errorMessage = null,
                    )
                }
            } catch (error: Throwable) {
                Log.e(
                    FILE_RECOVERY_DEBUG_TAG,
                    "Full device scan failed",
                    error,
                )
                scanStarted = false
                _uiState.update { state ->
                    state.copy(
                        scanStatus = RecoveryScanStatus.Error,
                        errorMessage = error.message,
                    )
                }
            } finally {
                if (currentJobId == scanJobId) {
                    scanJob = null
                }
            }
        }
    }

    private fun applySnapshot(snapshot: RecoveryScanSnapshot) {
        if (snapshot.totalFilesFound == 0 || snapshot.status == RecoveryScanStatus.Completed || snapshot.status == RecoveryScanStatus.CompletedEmpty) {
            Log.d(
                FILE_RECOVERY_DEBUG_TAG,
                "Snapshot status=${snapshot.status} totalFiles=${snapshot.totalFilesFound} locationsChecked=${snapshot.locationsChecked}",
            )
        }
        _uiState.update { state ->
            state.copy(
                scanStatus = snapshot.status,
                selectedCategory = snapshot.selectedCategory,
                progress = snapshot.progress,
                totalItemsScanned = snapshot.totalItemsScanned,
                totalFilesFound = snapshot.totalFilesFound,
                totalBytesScanned = snapshot.totalBytesScanned,
                locationsChecked = snapshot.locationsChecked,
                estimatedRemainingMillis = snapshot.estimatedRemainingMillis,
                currentLocation = snapshot.currentLocation,
                locations = snapshot.locations,
                categoryCounts = snapshot.categoryCounts,
                elapsedMillis = snapshot.elapsedMillis,
                errorMessage = snapshot.errorMessage,
            )
        }
    }
}
