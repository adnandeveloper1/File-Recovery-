package com.nexappra.filerecovery.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexappra.filerecovery.domain.usecase.GetStorageInfoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getStorageInfoUseCase: GetStorageInfoUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refreshStorage()
    }

    fun refreshStorage() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            runCatching { getStorageInfoUseCase() }
                .onSuccess { storageInfo ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            storageInfo = storageInfo,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            storageInfo = null,
                            errorMessage = throwable.message,
                        )
                    }
                }
        }
    }
}
