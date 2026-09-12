package com.nexappra.filerecovery.presentation.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexappra.filerecovery.domain.model.PhotoScanMode
import com.nexappra.filerecovery.domain.model.PhotoScanSnapshot
import com.nexappra.filerecovery.domain.model.PhotoScanStatus
import com.nexappra.filerecovery.domain.usecase.PerformPhotoScanUseCase
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

sealed interface PhotoScanNavigationEvent {
    data class OpenResults(val sessionId: String) : PhotoScanNavigationEvent
    data object Close : PhotoScanNavigationEvent
}

@HiltViewModel
class PhotoScanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val performPhotoScanUseCase: PerformPhotoScanUseCase,
) : ViewModel() {

    private val scanMode = savedStateHandle.get<String>(AppDestination.PhotoScan.ArgMode)
        ?.let(PhotoScanMode::valueOf)
        ?: PhotoScanMode.Quick

    private val _uiState = MutableStateFlow(
        PhotoScanUiState(
            scanMode = scanMode,
            locations = defaultLocationStatesFor(scanMode),
        ),
    )
    val uiState = _uiState.asStateFlow()

    private val navigationEvents = Channel<PhotoScanNavigationEvent>(Channel.BUFFERED)
    val events = navigationEvents.receiveAsFlow()

    private var scanStarted = false
    private var scanJobId = 0
    private var scanJob: Job? = null

    fun onPermissionStateChanged(isGranted: Boolean) {
        _uiState.update { state ->
            state.copy(
                isPermissionGranted = isGranted,
                scanStatus = when {
                    isGranted && state.scanStatus == PhotoScanStatus.PermissionRequired -> PhotoScanStatus.Preparing
                    !isGranted && state.scanStatus != PhotoScanStatus.Completed -> PhotoScanStatus.PermissionRequired
                    else -> state.scanStatus
                },
                errorMessage = if (isGranted) state.errorMessage else null,
            )
        }

        if (isGranted) {
            startScanIfNeeded()
        }
    }

    fun retryScan() {
        scanStarted = false
        _uiState.update { state ->
            state.copy(
                scanStatus = if (state.isPermissionGranted) PhotoScanStatus.Preparing else PhotoScanStatus.PermissionRequired,
                errorMessage = null,
                progress = null,
                totalFilesFound = 0,
                bytesScanned = 0L,
                estimatedRemainingMillis = null,
                currentLocation = null,
                locations = defaultLocationStatesFor(scanMode),
                elapsedMillis = 0L,
            )
        }
        startScanIfNeeded()
    }

    fun stopScan() {
        scanStarted = false
        scanJobId += 1
        scanJob?.cancel()
        scanJob = null
        _uiState.update { state -> state.copy(scanStatus = PhotoScanStatus.Cancelled) }
        viewModelScope.launch {
            navigationEvents.send(PhotoScanNavigationEvent.Close)
        }
    }

    private fun startScanIfNeeded() {
        if (!_uiState.value.isPermissionGranted || scanStarted) return

        scanStarted = true
        val currentJobId = ++scanJobId

        scanJob = viewModelScope.launch {
            try {
                val session = performPhotoScanUseCase(scanMode) { snapshot ->
                    if (currentJobId == scanJobId) {
                        applySnapshot(snapshot)
                    }
                }

                if (currentJobId == scanJobId) {
                    navigationEvents.send(PhotoScanNavigationEvent.OpenResults(session.id))
                }
            } catch (error: CancellationException) {
                if (currentJobId == scanJobId) {
                    _uiState.update { state -> state.copy(scanStatus = PhotoScanStatus.Cancelled) }
                }
            } catch (error: SecurityException) {
                scanStarted = false
                _uiState.update { state ->
                    state.copy(
                        scanStatus = PhotoScanStatus.PermissionRequired,
                        errorMessage = null,
                    )
                }
            } catch (error: Throwable) {
                scanStarted = false
                _uiState.update { state ->
                    state.copy(
                        scanStatus = PhotoScanStatus.Error,
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

    private fun applySnapshot(snapshot: PhotoScanSnapshot) {
        _uiState.update { state ->
            state.copy(
                scanStatus = snapshot.status,
                progress = snapshot.progress,
                totalFilesFound = snapshot.totalFilesFound,
                bytesScanned = snapshot.bytesScanned,
                estimatedRemainingMillis = snapshot.estimatedRemainingMillis,
                currentLocation = snapshot.currentLocation,
                locations = snapshot.locations,
                elapsedMillis = snapshot.elapsedMillis,
                errorMessage = snapshot.errorMessage,
            )
        }
    }
}
