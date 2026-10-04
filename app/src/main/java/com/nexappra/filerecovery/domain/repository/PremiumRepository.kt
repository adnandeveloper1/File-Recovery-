package com.nexappra.filerecovery.domain.repository

import com.nexappra.filerecovery.domain.model.BillingState
import com.nexappra.filerecovery.domain.model.PremiumRequiredException
import kotlinx.coroutines.flow.StateFlow

interface PremiumRepository {
    val state: StateFlow<BillingState>
    fun refresh()
    fun requirePremium() {
        if (!state.value.access.isActive()) throw PremiumRequiredException()
    }
}
