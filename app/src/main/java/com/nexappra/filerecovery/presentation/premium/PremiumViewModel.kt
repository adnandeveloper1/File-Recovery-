package com.nexappra.filerecovery.presentation.premium

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PremiumViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        PremiumUiState()
    )

    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    fun selectPlan(plan: PremiumPlan) {
        _uiState.value = _uiState.value.copy(
            selectedPlan = plan
        )
    }
}