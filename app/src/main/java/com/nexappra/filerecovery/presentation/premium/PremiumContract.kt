package com.nexappra.filerecovery.presentation.premium

enum class PremiumPlan {
    MONTHLY,
    YEARLY
}

data class PremiumUiState(
    val selectedPlan: PremiumPlan = PremiumPlan.YEARLY,
)