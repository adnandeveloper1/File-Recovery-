package com.nexappra.filerecovery.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.nexappra.filerecovery.BuildConfig
import com.nexappra.filerecovery.domain.model.*
import com.nexappra.filerecovery.domain.repository.PremiumRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/** Release entitlements require server verification. Debug previews are process-local and explicitly labelled. */
@Singleton
class PlayBillingRepository @Inject constructor(@ApplicationContext context: Context) : PremiumRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val products = setOf(BuildConfig.PREMIUM_MONTHLY_ID, BuildConfig.PREMIUM_YEARLY_ID).filter { it.isNotBlank() }.toSet()
    private val configured = runCatching {
        val endpoint = URI(BuildConfig.PURCHASE_VERIFICATION_URL)
        endpoint.scheme == "https" && !endpoint.host.isNullOrBlank() &&
            endpoint.userInfo == null && endpoint.fragment == null && products.size == 2
    }.getOrDefault(false)
    private val mutableState = MutableStateFlow(BillingState(configured = configured))
    private val debugPreview = MutableStateFlow(false)
    override val state = combine(mutableState, debugPreview) { billing, preview ->
        if (BuildConfig.DEBUG && preview) billing.copy(
            access = PremiumAccess(true, Long.MAX_VALUE),
            isDebugPreview = true,
            isLoading = false,
            message = "Test access only. No purchase has been made. Access resets when the app process closes.",
        ) else billing
    }.stateIn(scope, SharingStarted.Eagerly, mutableState.value)

    fun setDebugPreview(enabled: Boolean) {
        check(BuildConfig.DEBUG) { "Test access is unavailable in release builds." }
        debugPreview.value = enabled
    }
    private var connecting = false
    private var refreshing = false
    private var verificationJob: Job? = null
    private var connectionTimeout: Job? = null
    private var entitlementExpiry: Job? = null
    private val client = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> verifyPurchases(purchases.orEmpty())
                BillingClient.BillingResponseCode.USER_CANCELED -> message("Purchase cancelled. You can keep scanning for free.")
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
                else -> message("Google Play could not complete this purchase. Please try again.")
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override fun refresh() {
        if (!configured) {
            message("Purchases are not available in this build. Free scanning and previews are available.")
            return
        }
        if (connecting || refreshing) return
        if (client.isReady) {
            loadCatalogAndPurchases()
        } else {
            connecting = true
            mutableState.update { it.copy(isLoading = true, message = null) }
            connectionTimeout?.cancel()
            connectionTimeout = scope.launch {
                delay(30_000)
                connecting = false
                message("Google Play took too long to respond. Tap Restore purchases to retry.")
            }
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connectionTimeout?.cancel()
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) loadCatalogAndPurchases()
                    else message("Google Play is unavailable. Check your Play Store account and connection.")
                }
                override fun onBillingServiceDisconnected() {
                    connectionTimeout?.cancel()
                    connecting = false
                    refreshing = false
                    message("Google Play disconnected. Tap Restore purchases to reconnect.")
                }
            })
        }
    }

    private fun productQuery() = QueryProductDetailsParams.newBuilder().setProductList(products.map { id ->
        QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(BillingClient.ProductType.SUBS).build()
    }).build()

    private fun basePlan(product: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
        val period = when (product.productId) {
            BuildConfig.PREMIUM_MONTHLY_ID -> "P1M"
            BuildConfig.PREMIUM_YEARLY_ID -> "P1Y"
            else -> return null
        }
        // The displayed period and renewal terms must match the exact checkout offer.
        return product.subscriptionOfferDetails?.firstOrNull { offer ->
            val price = offer.pricingPhases.pricingPhaseList.singleOrNull()
            offer.offerId == null && price?.billingPeriod == period &&
                price.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING
        }
    }

    private fun loadCatalogAndPurchases() {
        refreshing = true
        mutableState.update { it.copy(isLoading = true, message = null) }
        client.queryProductDetailsAsync(productQuery()) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val offers = details.productDetailsList.mapNotNull { product ->
                    // Offer-free base plans avoid ambiguous trial/introductory pricing.
                    val offer = basePlan(product) ?: return@mapNotNull null
                    val price = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                    PremiumOffer(product.productId, if (price.billingPeriod == "P1Y") "Yearly" else "Monthly", price.formattedPrice, price.billingPeriod)
                }
                mutableState.update { it.copy(offers = offers) }
            }
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { purchaseResult, purchases ->
                refreshing = false
                if (purchaseResult.responseCode == BillingClient.BillingResponseCode.OK) verifyPurchases(purchases)
                else message("Could not restore purchases. Check your connection and try again.")
            }
        }
    }

    fun purchase(activity: Activity, productId: String) {
        if (!configured || mutableState.value.isLoading || productId !in products) return
        if (!client.isReady) { refresh(); return }
        mutableState.update { it.copy(isLoading = true, message = null) }
        // Fetch fresh ProductDetails immediately before opening checkout.
        client.queryProductDetailsAsync(productQuery()) { result, details ->
            val product = details.productDetailsList.firstOrNull { it.productId == productId }
            val offer = product?.let(::basePlan)
            if (result.responseCode != BillingClient.BillingResponseCode.OK || product == null || offer == null) {
                message("This plan is currently unavailable. Please try again later.")
                return@queryProductDetailsAsync
            }
            scope.launch {
                if (activity.isFinishing || activity.isDestroyed) { message("Open Premium again to continue."); return@launch }
                val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(offer.offerToken).build()
                )).build()
                val launched = client.launchBillingFlow(activity, params)
                if (launched.responseCode != BillingClient.BillingResponseCode.OK) message("Unable to open Google Play checkout.")
            }
        }
    }

    private fun verifyPurchases(purchases: List<Purchase>) {
        verificationJob?.cancel()
        verificationJob = scope.launch {
            val relevant = purchases.filter { purchase -> purchase.products.any { it in products } }
            val purchased = relevant.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            if (purchased.isEmpty()) {
                entitlementExpiry?.cancel()
                mutableState.update { it.copy(access = PremiumAccess(), isLoading = false, message = if (relevant.any { p -> p.purchaseState == Purchase.PurchaseState.PENDING }) "Payment is pending. Premium unlocks after Google Play confirms payment." else if (it.offers.isEmpty()) "No plans are currently available from Google Play." else null) }
                return@launch
            }
            mutableState.update { it.copy(isLoading = true) }
            try {
                val access = purchased.map { verifyOnServer(it) }.maxByOrNull { it.expiresAtMillis } ?: PremiumAccess()
                mutableState.update { it.copy(access = access, isLoading = false, message = if (access.isActive()) "Premium is active. Your recovery tools are ready." else "No active Premium subscription was found.") }
                entitlementExpiry?.cancel()
                if (access.isActive()) entitlementExpiry = scope.launch {
                    delay((access.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(1))
                    mutableState.update { it.copy(access = PremiumAccess()) }
                    refresh()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message("Purchase verification could not finish. Tap Restore purchases to retry.")
            }
        }
    }

    private suspend fun verifyOnServer(purchase: Purchase): PremiumAccess = withContext(Dispatchers.IO) {
        val connection = URI(BuildConfig.PURCHASE_VERIFICATION_URL).toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val payload = JSONObject().put("purchaseToken", purchase.purchaseToken).toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode == 200) { "Verification unavailable" }
            val response = connection.inputStream.use { stream ->
                val buffer = ByteArray(16_385)
                var length = 0
                while (length < buffer.size) {
                    val read = stream.read(buffer, length, buffer.size - length)
                    if (read < 0) break
                    length += read
                }
                check(length <= 16_384) { "Invalid verification response" }
                String(buffer, 0, length, Charsets.UTF_8)
            }
            val json = JSONObject(response)
            val verified = json.optBoolean("verified") && json.optBoolean("acknowledged") &&
                json.optString("productId") in products && json.optString("productId") in purchase.products
            PremiumAccess(verified, minOf(json.optLong("expiresAtMillis"), System.currentTimeMillis() + 15 * 60_000L))
        } finally { connection.disconnect() }
    }

    private fun message(value: String) { mutableState.update { it.copy(isLoading = false, message = value) } }
}
