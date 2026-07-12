package com.example.notepad.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.notepad.BuildConfig
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

internal data class PendingLaunchMetadata(
    val productId: String,
    val basePlanId: String,
    val offerId: String?,
)

internal fun isValidObfuscatedExternalAccountId(value: String): Boolean {
    return OBFUSCATED_EXTERNAL_ACCOUNT_ID_PATTERN.matches(value)
}

internal data class PremiumPlayPurchaseObservation(
    val purchaseToken: String,
    val products: List<String>,
    val purchaseState: Int,
    val purchaseTime: Long,
    val isSuspended: Boolean,
) {
    override fun toString(): String =
        "PremiumPlayPurchaseObservation(purchaseToken=[REDACTED], products=$products, " +
            "purchaseState=$purchaseState, purchaseTime=$purchaseTime, isSuspended=$isSuspended)"
}

internal fun emitBackendPurchaseCandidates(
    channel: SendChannel<BackendPurchaseCandidate>,
    responseCode: Int,
    purchases: List<PremiumPlayPurchaseObservation>,
    launchMetadata: PendingLaunchMetadata?,
    isRestore: Boolean,
    appVersion: String,
    versionCode: Long,
    deviceLocale: String,
): Int {
    if (responseCode != BillingClient.BillingResponseCode.OK) return 0
    val metadataPurchaseIndex = if (isRestore || launchMetadata == null) {
        null
    } else {
        purchases.indices
            .filter { index ->
                val purchase = purchases[index]
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    launchMetadata.productId in purchase.products
            }
            .maxByOrNull { index -> purchases[index].purchaseTime }
    }
    var emitted = 0
    purchases.forEachIndexed { index, purchase ->
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return@forEachIndexed
        val productId = PremiumCatalog.matchingPremiumProductId(purchase.products) ?: return@forEachIndexed
        val metadata = launchMetadata?.takeIf { index == metadataPurchaseIndex && it.productId == productId }
        val candidate = BackendPurchaseCandidate(
            purchaseToken = purchase.purchaseToken,
            packageName = BuildConfig.APPLICATION_ID,
            productId = productId,
            basePlanId = metadata?.basePlanId,
            offerId = metadata?.offerId,
            appVersion = appVersion,
            versionCode = versionCode,
            deviceLocale = deviceLocale,
        )
        if (channel.trySend(candidate).isSuccess) emitted += 1
    }
    return emitted
}

class PremiumBilling(
    private val application: Application,
    private val clock: () -> Long = System::currentTimeMillis,
    private val connectToPlay: Boolean = true,
) : PurchasesUpdatedListener {
    private val store = PremiumEntitlementStore(
        application.getSharedPreferences(PremiumEntitlementStore.PREFERENCES_NAME, Context.MODE_PRIVATE),
    )
    private val _state = MutableStateFlow(
        PremiumBillingState(subscription = store.loadSubscription()),
    )
    val state: StateFlow<PremiumBillingState> = _state
    private val purchaseCandidateChannel = Channel<BackendPurchaseCandidate>(Channel.BUFFERED)
    internal val purchaseCandidates: Flow<BackendPurchaseCandidate> = purchaseCandidateChannel.receiveAsFlow()
    private val billingOffersByPlan = mutableMapOf<PremiumPlan, BillingOffer>()
    private var productStatusError: String? = null
    private var purchaseStatusError: String? = null
    private var pendingLaunchMetadata: PendingLaunchMetadata? = null

    private val billingClient = BillingClient.newBuilder(application)
        .setListener(this)
        .enableAutoServiceReconnection()
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build(),
        )
        .build()

    fun start() {
        if (!connectToPlay) {
            _state.update { it.copy(billingAvailable = false, loading = false, lastError = null) }
            return
        }
        if (billingClient.isReady) {
            refresh()
            return
        }
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        productStatusError = null
                        _state.update {
                            it.copy(billingAvailable = true, loading = false, lastError = visibleBillingError())
                        }
                        refresh()
                    } else {
                        val snapshot = _state.value.subscription.withTransientBillingUnavailable(clock())
                        productStatusError = billingMessage(result, "Billing unavailable")
                        _state.update {
                            it.copy(
                                subscription = snapshot,
                                billingAvailable = false,
                                loading = false,
                                lastError = visibleBillingError(),
                            )
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _state.update { it.copy(billingAvailable = false, loading = false) }
                }
            },
        )
    }

    fun refresh() {
        if (!connectToPlay) {
            _state.update { it.copy(billingAvailable = false, loading = false, lastError = null) }
            return
        }
        if (!billingClient.isReady) {
            start()
            return
        }
        queryProductDetails()
        queryActivePurchases()
    }

    fun applyBackendEntitlement(response: PremiumBackendEntitlementResponse): Boolean {
        val result = PremiumBackendEntitlementMapper.fromEntitlementResponse(
            expectedPackageName = BuildConfig.APPLICATION_ID,
            response = response,
            now = clock(),
        )
        if (shouldPersistBackendEntitlementResult(_state.value.subscription, result)) {
            saveSubscription(result.snapshot)
        }
        purchaseStatusError = result.rejectionReason
        _state.update { it.copy(loading = false, lastError = visibleBillingError()) }
        return result.accepted
    }

    fun clearBackendEntitlement() {
        val current = _state.value.subscription
        if (!shouldClearBackendEntitlement(current)) return
        saveSubscription(
            PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.Free,
                source = PremiumEntitlementSource.None,
                lastBackendVerifiedAt = current.lastBackendVerifiedAt,
                lastEntitlementChangeAt = clock(),
                acknowledgementStatus = PremiumAcknowledgementStatus.NotRequired,
            ),
        )
    }

    fun launchPurchase(activity: Activity, plan: PremiumPlan, obfuscatedExternalAccountId: String): Boolean {
        if (!BuildConfig.ENABLE_BACKEND_PURCHASE_FLOW) {
            purchaseStatusError = PRODUCTION_BACKEND_REQUIRED_MESSAGE
            _state.update { it.copy(lastError = visibleBillingError()) }
            return false
        }
        if (!isValidObfuscatedExternalAccountId(obfuscatedExternalAccountId)) {
            purchaseStatusError = INVALID_ACCOUNT_ID_MESSAGE
            _state.update { it.copy(lastError = visibleBillingError()) }
            return false
        }
        val billingOffer = billingOffersByPlan[plan] ?: return false
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(billingOffer.productDetails)
            .setOfferToken(billingOffer.offerDetails.offerToken)
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .setObfuscatedAccountId(obfuscatedExternalAccountId)
            .build()
        val result = billingClient.launchBillingFlow(activity, params)
        val launchSucceeded = result.responseCode == BillingClient.BillingResponseCode.OK
        if (launchSucceeded) {
            pendingLaunchMetadata = PendingLaunchMetadata(
                productId = billingOffer.productDetails.productId,
                basePlanId = billingOffer.offerDetails.basePlanId,
                offerId = billingOffer.offerDetails.offerId,
            )
        } else {
            pendingLaunchMetadata = null
            purchaseStatusError = billingMessage(result, "Unable to launch purchase")
            _state.update { it.copy(lastError = visibleBillingError()) }
        }
        return launchSucceeded
    }

    fun setBackendPurchaseReady(ready: Boolean) {
        _state.update { it.copy(backendPurchaseReady = ready) }
    }

    fun setPurchaseLaunching(launching: Boolean) {
        _state.update { it.copy(purchaseLaunching = launching) }
    }

    fun setPurchaseVerificationInFlight(inFlight: Boolean) {
        _state.update { it.copy(purchaseVerificationInFlight = inFlight) }
    }

    fun reportBackendPurchaseError(message: String?) {
        purchaseStatusError = message
        _state.update { it.copy(lastError = visibleBillingError()) }
    }

    fun close() {
        purchaseCandidateChannel.close()
        if (billingClient.isReady) billingClient.endConnection()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> updateEntitlementFromPurchases(
                purchases = purchases.orEmpty(),
                isRestore = false,
            )
            BillingClient.BillingResponseCode.USER_CANCELED -> pendingLaunchMetadata = null
            else -> {
                pendingLaunchMetadata = null
                purchaseStatusError = billingMessage(result, "Purchase update failed")
                _state.update { it.copy(lastError = visibleBillingError()) }
            }
        }
    }

    private fun queryProductDetails() {
        val products = PremiumCatalog.productIdsToQuery.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        billingClient.queryProductDetailsAsync(params) { result, productDetailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                productStatusError = billingMessage(result, "Unable to load premium products")
                _state.update { it.copy(lastError = visibleBillingError()) }
                return@queryProductDetailsAsync
            }
            val details = productDetailsResult.productDetailsList
            val offers = collectBillingOffers(details)
            val selected = selectBillingOffers(offers)
            val prices = selectLaunchDisplayPrices(offers.map { it.first })
            billingOffersByPlan.clear()
            billingOffersByPlan.putAll(selected)
            val unfetched = productDetailsResult.unfetchedProductList.joinToString { product ->
                "${product.productId}:${product.statusCode}"
            }
            productStatusError = when {
                prices.isNotEmpty() && prices.size < PremiumPlan.entries.size -> PRODUCT_CONFIGURATION_MESSAGE
                prices.isNotEmpty() -> null
                details.isNotEmpty() -> PRODUCT_CONFIGURATION_MESSAGE
                unfetched.isNotBlank() -> "Premium products are not available from Google Play yet ($unfetched)."
                else -> null
            }
            _state.update {
                it.copy(
                    billingAvailable = true,
                    loading = false,
                    monthlyPrice = prices[PremiumPlan.Monthly],
                    annualPrice = prices[PremiumPlan.Annual],
                    monthlyTrialAvailable = billingOffersByPlan[PremiumPlan.Monthly]?.offerDetails?.offerId ==
                        PremiumCatalog.TRIAL_OFFER_ID,
                    lastError = visibleBillingError(),
                )
            }
        }
    }

    private fun queryActivePurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                updateEntitlementFromPurchases(purchases = purchases, isRestore = true)
            } else {
                purchaseStatusError = billingMessage(result, "Unable to refresh purchase status")
                _state.update { it.copy(lastError = visibleBillingError()) }
            }
        }
    }

    private fun updateEntitlementFromPurchases(purchases: List<Purchase>, isRestore: Boolean) {
        val now = clock()
        val observations = purchases.map { purchase -> purchase.toObservation() }
        val emittedCandidates = emitBackendPurchaseCandidates(
            channel = purchaseCandidateChannel,
            responseCode = BillingClient.BillingResponseCode.OK,
            purchases = observations,
            launchMetadata = pendingLaunchMetadata,
            isRestore = isRestore,
            appVersion = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            deviceLocale = Locale.getDefault().toLanguageTag(),
        )
        if (!isRestore && emittedCandidates > 0) pendingLaunchMetadata = null

        val premiumPurchases = purchases.filter { purchase ->
            purchase.products.any(PremiumCatalog::isPremiumProduct)
        }
        if (preserveBackendVerifiedEntitlement(now)) return

        val purchased = premiumPurchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .maxByOrNull { it.purchaseTime }
        val pending = premiumPurchases
            .filter { it.purchaseState == Purchase.PurchaseState.PENDING }
            .maxByOrNull { it.purchaseTime }
        val snapshot = when {
            purchased != null -> PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.VerificationPending,
                source = PremiumEntitlementSource.ClientObserved,
                productId = purchased.premiumProductId(),
                purchaseTime = purchased.purchaseTime,
                lastPlayQueryAt = now,
                lastEntitlementChangeAt = now,
                acknowledgementStatus = PremiumAcknowledgementStatus.BackendRequired,
            )
            pending != null -> PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.PendingPurchase,
                source = PremiumEntitlementSource.ClientObserved,
                productId = pending.premiumProductId(),
                purchaseTime = pending.purchaseTime,
                lastPlayQueryAt = now,
                lastEntitlementChangeAt = now,
                acknowledgementStatus = PremiumAcknowledgementStatus.NotRequired,
            )
            else -> PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.Free,
                source = PremiumEntitlementSource.None,
                lastPlayQueryAt = now,
                lastEntitlementChangeAt = now,
                acknowledgementStatus = PremiumAcknowledgementStatus.NotRequired,
            )
        }
        saveSubscription(snapshot)
        purchaseStatusError = if (purchased != null) PRODUCTION_BACKEND_REQUIRED_MESSAGE else null
        _state.update { it.copy(loading = false, lastError = visibleBillingError()) }
    }

    private fun collectBillingOffers(details: List<ProductDetails>): List<Pair<PremiumOfferCandidate, BillingOffer>> {
        return details.flatMap { productDetails ->
            productDetails.subscriptionOfferDetails.orEmpty().map { offerDetails ->
                val candidate = PremiumOfferCandidate(
                    productId = productDetails.productId,
                    basePlanId = offerDetails.basePlanId,
                    offerId = offerDetails.offerId,
                    offerToken = offerDetails.offerToken,
                    formattedPrice = offerDetails.displayPrice(),
                )
                candidate to BillingOffer(productDetails, offerDetails, candidate.formattedPrice)
            }
        }
    }

    private fun selectBillingOffers(
        offers: List<Pair<PremiumOfferCandidate, BillingOffer>>,
    ): Map<PremiumPlan, BillingOffer> {
        return PremiumPlan.entries.mapNotNull { plan ->
            val selected = PremiumCatalog.selectBasePlanOffer(plan, offers.map { it.first })
            offers.singleOrNull { it.first == selected }?.second?.let { plan to it }
        }.toMap()
    }

    private fun selectLaunchDisplayPrices(candidates: List<PremiumOfferCandidate>): Map<PremiumPlan, String> {
        return PremiumPlan.entries.mapNotNull { plan ->
            PremiumCatalog.selectLaunchDisplayPrice(plan, candidates)?.let { plan to it }
        }.toMap()
    }

    private fun ProductDetails.SubscriptionOfferDetails.displayPrice(): String? =
        pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice

    private fun Purchase.premiumProductId(): String? = PremiumCatalog.matchingPremiumProductId(products)

    private fun Purchase.toObservation(): PremiumPlayPurchaseObservation {
        return PremiumPlayPurchaseObservation(
            purchaseToken = purchaseToken,
            products = products,
            purchaseState = purchaseState,
            purchaseTime = purchaseTime,
            isSuspended = isSuspended,
        )
    }

    private fun PremiumSubscriptionSnapshot.withTransientBillingUnavailable(now: Long): PremiumSubscriptionSnapshot {
        val unavailable = status == PremiumSubscriptionStatus.Unknown ||
            status == PremiumSubscriptionStatus.Free ||
            status == PremiumSubscriptionStatus.BillingUnavailable
        return if (unavailable) {
            copy(
                status = PremiumSubscriptionStatus.BillingUnavailable,
                lastPlayQueryAt = now,
                lastEntitlementChangeAt = now,
            )
        } else {
            copy(lastPlayQueryAt = now)
        }
    }

    private fun preserveBackendVerifiedEntitlement(now: Long): Boolean {
        val current = _state.value.subscription
        if (!current.source.isBackendAuthoritative()) return false
        saveSubscription(current.copy(lastPlayQueryAt = now))
        purchaseStatusError = null
        _state.update { it.copy(loading = false, lastError = visibleBillingError()) }
        return true
    }

    private fun saveSubscription(snapshot: PremiumSubscriptionSnapshot) {
        store.saveSubscription(snapshot)
        _state.update { it.copy(subscription = snapshot) }
    }

    private fun billingMessage(result: BillingResult, fallback: String): String {
        val debugMessage = result.debugMessage.ifBlank { fallback }
        val subCode = result.onPurchasesUpdatedSubResponseCode
        return if (subCode != BillingClient.OnPurchasesUpdatedSubResponseCode.NO_APPLICABLE_SUB_RESPONSE_CODE) {
            "$debugMessage (${result.responseCode}/$subCode)"
        } else {
            "$debugMessage (${result.responseCode})"
        }
    }

    private fun visibleBillingError(): String? = purchaseStatusError ?: productStatusError

    private data class BillingOffer(
        val productDetails: ProductDetails,
        val offerDetails: ProductDetails.SubscriptionOfferDetails,
        val displayPrice: String?,
    )

    companion object {
        private const val PRODUCT_CONFIGURATION_MESSAGE =
            "Premium products were found, but expected monthly/annual base plans without offers are missing."
        private const val PRODUCTION_BACKEND_REQUIRED_MESSAGE =
            "Production billing is blocked until backend verification completes."
        private const val INVALID_ACCOUNT_ID_MESSAGE = "Purchase account verification is unavailable."
    }
}

private val OBFUSCATED_EXTERNAL_ACCOUNT_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")

internal fun shouldPersistBackendEntitlementResult(
    current: PremiumSubscriptionSnapshot,
    result: PremiumBackendVerificationResult,
): Boolean {
    if (result.shouldApplySnapshot) return true
    return result.accepted && current.source.isBackendAuthoritative()
}

internal fun shouldClearBackendEntitlement(snapshot: PremiumSubscriptionSnapshot): Boolean =
    snapshot.source.isBackendAuthoritative()

private fun PremiumEntitlementSource.isBackendAuthoritative(): Boolean =
    this == PremiumEntitlementSource.BackendVerified || this == PremiumEntitlementSource.ReviewerGrant
