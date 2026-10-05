package com.nexappra.filerecovery.domain.model

data class PremiumAccess(val verified: Boolean = false, val expiresAtMillis: Long = 0L) {
    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean = verified && expiresAtMillis > nowMillis
}

class PremiumRequiredException : IllegalStateException("Premium is required for this feature.")

data class PremiumOffer(val productId: String, val title: String, val price: String, val billingPeriod: String)

data class BillingState(
    val access: PremiumAccess = PremiumAccess(),
    val offers: List<PremiumOffer> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val configured: Boolean = false,
    val isDebugPreview: Boolean = false,
)
