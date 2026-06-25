package com.example.notepad.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class PremiumBilling(
    private val application: Application,
    private val clock: () -> Long = System::currentTimeMillis,
    private val connectToPlay: Boolean = true,
) : PurchasesUpdatedListener {
    private val store = PremiumEntitlementStore(
        application.getSharedPreferences(PremiumEntitlementStore.PREFERENCES_NAME, Context.MODE_PRIVATE),
    )
    private val _state = MutableStateFlow(
        PremiumBillingState(
            subscription = store.loadSubscription(),
        ),
    )
    val state: StateFlow<PremiumBillingState> = _state
    private val billingOffersByPlan = mutableMapOf<PremiumPlan, BillingOffer>()
    private var productStatusError: String? = null
    private var purchaseStatusError: String? = null
    private val acknowledgementInFlightTokens = mutableSetOf<String>()

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
                            it.copy(
                                billingAvailable = true,
                                loading = false,
                                lastError = visibleBillingError(),
                            )
                        }
                        refresh()
                    } else {
                        val now = clock()
                        val snapshot = _state.value.subscription.withTransientBillingUnavailable(now)
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

    fun launchPurchase(activity: Activity, plan: PremiumPlan): Boolean {
        if (!BuildConfig.ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT) {
            purchaseStatusError = PRODUCTION_BACKEND_REQUIRED_MESSAGE
            _state.update {
                it.copy(lastError = visibleBillingError())
            }
            return false
        }
        val billingOffer = billingOffersByPlan[plan] ?: return false
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(billingOffer.productDetails)
            .setOfferToken(billingOffer.offerDetails.offerToken)
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val result = billingClient.launchBillingFlow(activity, params)
        val launchSucceeded = result.responseCode == BillingClient.BillingResponseCode.OK
        if (!launchSucceeded) {
            purchaseStatusError = billingMessage(result, "Unable to launch purchase")
            _state.update { it.copy(lastError = visibleBillingError()) }
        }
        return launchSucceeded
    }

    fun close() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> updateEntitlementFromPurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> {
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
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        billingClient.queryProductDetailsAsync(params) { result, productDetailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                productStatusError = billingMessage(result, "Unable to load premium products")
                _state.update { it.copy(lastError = visibleBillingError()) }
                return@queryProductDetailsAsync
            }
            val productDetailsList = productDetailsResult.productDetailsList
            val selectedOffers = selectBillingOffers(productDetailsList)
            billingOffersByPlan.clear()
            billingOffersByPlan.putAll(selectedOffers)
            val unfetchedProductSummary = productDetailsResult.unfetchedProductList.joinToString { product ->
                "${product.productId}:${product.statusCode}"
            }
            val configurationMessage = when {
                selectedOffers.isNotEmpty() && selectedOffers.size < PremiumPlan.entries.size -> PRODUCT_CONFIGURATION_MESSAGE
                selectedOffers.isNotEmpty() -> null
                productDetailsList.isNotEmpty() -> PRODUCT_CONFIGURATION_MESSAGE
                unfetchedProductSummary.isNotBlank() ->
                    "Premium products are not available from Google Play yet ($unfetchedProductSummary)."
                else -> null
            }
            productStatusError = configurationMessage
            _state.update {
                it.copy(
                    billingAvailable = true,
                    loading = false,
                    monthlyPrice = selectedOffers[PremiumPlan.Monthly]?.displayPrice,
                    annualPrice = selectedOffers[PremiumPlan.Annual]?.displayPrice,
                    lastError = visibleBillingError(),
                )
            }
        }
    }

    private fun queryActivePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                updateEntitlementFromPurchases(purchases)
            } else {
                purchaseStatusError = billingMessage(result, "Unable to refresh purchase status")
                _state.update { it.copy(lastError = visibleBillingError()) }
            }
        }
    }

    private fun updateEntitlementFromPurchases(purchases: List<Purchase>) {
        val now = clock()
        val premiumPurchases = purchases.filter { purchase ->
            purchase.products.any(PremiumCatalog::isPremiumProduct)
        }
        val purchasedPremiumPurchases = premiumPurchases.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        val activePurchasedPurchases = purchasedPremiumPurchases.filter { !it.isSuspended }
        val newestPurchased = activePurchasedPurchases.maxByOrNull { it.purchaseTime }
        val acknowledgedPurchased = activePurchasedPurchases
            .filter { it.isAcknowledged }
            .maxByOrNull { it.purchaseTime }
        val pendingAcknowledgementPurchases = purchasedPremiumPurchases
            .filter { !it.isAcknowledged }
            .sortedByDescending { it.purchaseTime }
        val purchased = if (BuildConfig.ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT && acknowledgedPurchased != null) {
            acknowledgedPurchased
        } else {
            newestPurchased
        }
        val suspendedPurchase = premiumPurchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.isSuspended }
            .maxByOrNull { it.purchaseTime }
        val pendingPurchase = premiumPurchases
            .filter { it.purchaseState == Purchase.PurchaseState.PENDING }
            .maxByOrNull { it.purchaseTime }
        if (preserveBackendVerifiedEntitlement(now, purchased, pendingAcknowledgementPurchases)) return
        pendingAcknowledgementPurchases.forEach { purchase ->
            store.recordPendingAcknowledgement(
                purchase.purchaseToken,
                purchase.premiumProductId(),
            )
        }
        if (pendingPurchase != null && purchased == null) {
            val productId = pendingPurchase.premiumProductId()
            val snapshot = PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.PendingPurchase,
                source = PremiumEntitlementSource.ClientObserved,
                productId = productId,
                purchaseTokenHash = PremiumEntitlementStore.hashPurchaseToken(pendingPurchase.purchaseToken),
                purchaseTime = pendingPurchase.purchaseTime,
                lastPlayQueryAt = now,
                lastEntitlementChangeAt = now,
                acknowledgementStatus = PremiumAcknowledgementStatus.NotRequired,
            )
            saveSubscription(
                snapshot = snapshot,
                preservePendingAcknowledgement = pendingAcknowledgementPurchases.isNotEmpty(),
            )
            purchaseStatusError = null
            _state.update { it.copy(loading = false, lastError = visibleBillingError()) }
            pendingAcknowledgementPurchases.forEach(::retryPendingAcknowledgementIfAllowed)
            return
        }

        if (suspendedPurchase != null && purchased == null) {
            val productId = suspendedPurchase.premiumProductId()
            val pendingAcknowledgement = store.loadPendingAcknowledgement(suspendedPurchase.purchaseToken)
            val snapshot = PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.VerificationPending,
                source = PremiumEntitlementSource.ClientObserved,
                productId = productId,
                purchaseTokenHash = PremiumEntitlementStore.hashPurchaseToken(suspendedPurchase.purchaseToken),
                purchaseTime = suspendedPurchase.purchaseTime,
                lastPlayQueryAt = now,
                lastEntitlementChangeAt = now,
                acknowledgementStatus = when {
                    suspendedPurchase.isAcknowledged -> PremiumAcknowledgementStatus.Acknowledged
                    !BuildConfig.ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT -> PremiumAcknowledgementStatus.BackendRequired
                    pendingAcknowledgement?.nextAttemptAt?.let { it > now } == true ->
                        PremiumAcknowledgementStatus.RetryScheduled
                    else -> PremiumAcknowledgementStatus.Pending
                },
                acknowledgementAttemptCount = pendingAcknowledgement?.attemptCount ?: 0,
                nextAcknowledgementAttemptAt = pendingAcknowledgement?.nextAttemptAt,
                lastAcknowledgementError = pendingAcknowledgement?.lastError,
            )
            saveSubscription(
                snapshot = snapshot,
                preservePendingAcknowledgement = pendingAcknowledgementPurchases.any { pendingPurchase ->
                    pendingPurchase.purchaseToken != suspendedPurchase.purchaseToken
                },
            )
            purchaseStatusError = SUSPENDED_SUBSCRIPTION_MESSAGE
            _state.update { it.copy(loading = false, lastError = visibleBillingError()) }
            pendingAcknowledgementPurchases.forEach(::retryPendingAcknowledgementIfAllowed)
            return
        }

        if (purchased == null) {
            val snapshot = PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.Free,
                source = PremiumEntitlementSource.None,
                lastPlayQueryAt = now,
                lastEntitlementChangeAt = now,
                acknowledgementStatus = PremiumAcknowledgementStatus.NotRequired,
            )
            saveSubscription(snapshot)
            purchaseStatusError = null
            _state.update { it.copy(loading = false, lastError = visibleBillingError()) }
            return
        }

        val productId = purchased.premiumProductId()
        val tokenHash = PremiumEntitlementStore.hashPurchaseToken(purchased.purchaseToken)
        if (pendingAcknowledgementPurchases.isEmpty()) {
            store.markAcknowledgementSucceeded(purchased.purchaseToken)
        }
        val pendingAcknowledgement = store.loadPendingAcknowledgement(purchased.purchaseToken)
        val ackStatus = when {
            purchased.isAcknowledged -> PremiumAcknowledgementStatus.Acknowledged
            !BuildConfig.ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT -> PremiumAcknowledgementStatus.BackendRequired
            pendingAcknowledgement?.nextAttemptAt?.let { it > now } == true -> PremiumAcknowledgementStatus.RetryScheduled
            else -> PremiumAcknowledgementStatus.Pending
        }
        val status = when {
            BuildConfig.ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT && ackStatus == PremiumAcknowledgementStatus.Acknowledged ->
                PremiumSubscriptionStatus.Active
            else -> PremiumSubscriptionStatus.VerificationPending
        }
        val snapshot = PremiumSubscriptionSnapshot(
            status = status,
            source = PremiumEntitlementSource.ClientObserved,
            productId = productId,
            purchaseTokenHash = tokenHash,
            purchaseTime = purchased.purchaseTime,
            lastPlayQueryAt = now,
            lastEntitlementChangeAt = now,
            acknowledgementStatus = ackStatus,
            acknowledgementAttemptCount = pendingAcknowledgement?.attemptCount ?: 0,
            nextAcknowledgementAttemptAt = pendingAcknowledgement?.nextAttemptAt,
            lastAcknowledgementError = pendingAcknowledgement?.lastError,
        )
        val hasSeparatePendingAcknowledgement = pendingAcknowledgementPurchases.any { pendingPurchase ->
            pendingPurchase.purchaseToken != purchased.purchaseToken
        }
        saveSubscription(
            snapshot = snapshot,
            preservePendingAcknowledgement = hasSeparatePendingAcknowledgement,
        )
        purchaseStatusError = if (BuildConfig.ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT) {
            null
        } else {
            PRODUCTION_BACKEND_REQUIRED_MESSAGE
        }
        _state.update {
            it.copy(
                loading = false,
                lastError = visibleBillingError(),
            )
        }
        pendingAcknowledgementPurchases.forEach(::retryPendingAcknowledgementIfAllowed)
    }

    private fun retryPendingAcknowledgementIfAllowed(purchase: Purchase) {
        if (!BuildConfig.ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT || !billingClient.isReady) return
        val pendingAcknowledgement = store.loadPendingAcknowledgement(purchase.purchaseToken) ?: return
        val now = clock()
        if (pendingAcknowledgement.purchaseToken != purchase.purchaseToken || pendingAcknowledgement.nextAttemptAt > now) return
        if (purchase.purchaseToken in acknowledgementInFlightTokens) return
        acknowledgementInFlightTokens += purchase.purchaseToken
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            val callbackNow = clock()
            if (purchase.purchaseToken !in acknowledgementInFlightTokens) return@acknowledgePurchase
            acknowledgementInFlightTokens -= purchase.purchaseToken
            val currentPendingAcknowledgement = store.loadPendingAcknowledgement(purchase.purchaseToken)
            if (currentPendingAcknowledgement?.purchaseToken != purchase.purchaseToken) return@acknowledgePurchase
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                store.markAcknowledgementSucceeded(purchase.purchaseToken)
                val hasRemainingPendingAcknowledgement = store.loadPendingAcknowledgement() != null
                val currentSubscription = _state.value.subscription
                val acknowledgedTokenHash = PremiumEntitlementStore.hashPurchaseToken(purchase.purchaseToken)
                if (currentSubscription.purchaseTokenHash == acknowledgedTokenHash) {
                    val snapshot = currentSubscription.copy(
                        status = if (purchase.isSuspended) {
                            PremiumSubscriptionStatus.VerificationPending
                        } else {
                            PremiumSubscriptionStatus.Active
                        },
                        acknowledgementStatus = PremiumAcknowledgementStatus.Acknowledged,
                        acknowledgementAttemptCount = 0,
                        nextAcknowledgementAttemptAt = null,
                        lastAcknowledgementError = null,
                        lastEntitlementChangeAt = callbackNow,
                    )
                    saveSubscription(
                        snapshot = snapshot,
                        preservePendingAcknowledgement = hasRemainingPendingAcknowledgement,
                    )
                }
                purchaseStatusError = if (purchase.isSuspended) SUSPENDED_SUBSCRIPTION_MESSAGE else null
                _state.update { it.copy(loading = false, lastError = visibleBillingError()) }
            } else {
                val acknowledgementError = billingMessage(result, "Acknowledgement failed")
                val failedAck = store.markAcknowledgementFailed(
                    purchaseToken = purchase.purchaseToken,
                    error = acknowledgementError,
                    now = callbackNow,
                )
                val matchingFailedAck = failedAck ?: return@acknowledgePurchase
                if (matchingFailedAck.purchaseToken != purchase.purchaseToken) return@acknowledgePurchase
                purchaseStatusError = matchingFailedAck.lastError ?: acknowledgementError
                val currentSubscription = _state.value.subscription
                val failedAckMatchesEntitlement = currentSubscription.purchaseTokenHash == matchingFailedAck.tokenHash
                if (!failedAckMatchesEntitlement &&
                    currentSubscription.hasPremiumAccess(BuildConfig.ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT)
                ) {
                    _state.update { it.copy(loading = false, lastError = visibleBillingError()) }
                    return@acknowledgePurchase
                }
                val snapshot = currentSubscription.copy(
                    status = PremiumSubscriptionStatus.VerificationPending,
                    acknowledgementStatus = PremiumAcknowledgementStatus.RetryScheduled,
                    acknowledgementAttemptCount = matchingFailedAck.attemptCount,
                    nextAcknowledgementAttemptAt = matchingFailedAck.nextAttemptAt,
                    lastAcknowledgementError = matchingFailedAck.lastError,
                    lastEntitlementChangeAt = callbackNow,
                )
                saveSubscription(snapshot)
                _state.update { it.copy(loading = false, lastError = visibleBillingError()) }
            }
        }
    }

    private fun selectBillingOffers(productDetailsList: List<ProductDetails>): Map<PremiumPlan, BillingOffer> {
        val billingOffers = productDetailsList.flatMap { productDetails ->
            productDetails.subscriptionOfferDetails.orEmpty().map { offerDetails ->
                val candidate = PremiumOfferCandidate(
                    productId = productDetails.productId,
                    basePlanId = offerDetails.basePlanId,
                    offerId = offerDetails.offerId,
                    offerToken = offerDetails.offerToken,
                    formattedPrice = offerDetails.displayPrice(),
                )
                candidate to BillingOffer(
                    productDetails = productDetails,
                    offerDetails = offerDetails,
                    displayPrice = candidate.formattedPrice,
                )
            }
        }
        return PremiumPlan.entries.mapNotNull { plan ->
            val selected = PremiumCatalog.selectBasePlanOffer(plan, billingOffers.map { it.first })
            val billingOffer = billingOffers.singleOrNull { it.first == selected }?.second
            if (billingOffer == null) null else plan to billingOffer
        }.toMap()
    }

    private fun ProductDetails.SubscriptionOfferDetails.displayPrice(): String? {
        return pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice
    }

    private fun Purchase.premiumProductId(): String? {
        return PremiumCatalog.matchingPremiumProductId(products)
    }

    private fun PremiumSubscriptionSnapshot.withTransientBillingUnavailable(now: Long): PremiumSubscriptionSnapshot {
        val shouldShowUnavailableStatus = status == PremiumSubscriptionStatus.Unknown ||
            status == PremiumSubscriptionStatus.Free ||
            status == PremiumSubscriptionStatus.BillingUnavailable
        return if (shouldShowUnavailableStatus) {
            copy(
                status = PremiumSubscriptionStatus.BillingUnavailable,
                lastPlayQueryAt = now,
                lastEntitlementChangeAt = now,
            )
        } else {
            copy(lastPlayQueryAt = now)
        }
    }

    private fun preserveBackendVerifiedEntitlement(
        now: Long,
        purchased: Purchase?,
        pendingAcknowledgementPurchases: List<Purchase>,
    ): Boolean {
        val current = _state.value.subscription
        if (current.source != PremiumEntitlementSource.BackendVerified) return false
        pendingAcknowledgementPurchases.forEach { purchase ->
            store.recordPendingAcknowledgement(
                purchase.purchaseToken,
                purchase.premiumProductId(),
            )
        }
        val pendingAcknowledgementPurchase = pendingAcknowledgementPurchases.firstOrNull()
        val acknowledgementPurchase = pendingAcknowledgementPurchase ?: purchased?.takeIf { !it.isAcknowledged }
        val pendingAcknowledgement = acknowledgementPurchase?.let { purchase ->
            store.loadPendingAcknowledgement(purchase.purchaseToken)
        } ?: store.loadPendingAcknowledgement()
        val snapshot = if (acknowledgementPurchase != null) {
            current.copy(
                productId = acknowledgementPurchase.premiumProductId() ?: current.productId,
                purchaseTokenHash = PremiumEntitlementStore.hashPurchaseToken(acknowledgementPurchase.purchaseToken),
                purchaseTime = acknowledgementPurchase.purchaseTime,
                lastPlayQueryAt = now,
                acknowledgementStatus = PremiumAcknowledgementStatus.BackendRequired,
                acknowledgementAttemptCount = pendingAcknowledgement?.attemptCount ?: current.acknowledgementAttemptCount,
                nextAcknowledgementAttemptAt = pendingAcknowledgement?.nextAttemptAt ?: current.nextAcknowledgementAttemptAt,
                lastAcknowledgementError = pendingAcknowledgement?.lastError ?: current.lastAcknowledgementError,
            )
        } else {
            current.copy(lastPlayQueryAt = now)
        }
        saveSubscription(snapshot)
        purchaseStatusError = null
        _state.update { it.copy(loading = false, lastError = visibleBillingError()) }
        return true
    }

    private fun saveSubscription(
        snapshot: PremiumSubscriptionSnapshot,
        preservePendingAcknowledgement: Boolean = false,
    ) {
        store.saveSubscription(
            snapshot = snapshot,
            preservePendingAcknowledgement = preservePendingAcknowledgement,
        )
        _state.update { it.copy(subscription = snapshot) }
    }

    private fun billingMessage(result: BillingResult, fallback: String): String {
        val debugMessage = result.debugMessage.ifBlank { fallback }
        val subResponseCode = result.onPurchasesUpdatedSubResponseCode
        return if (subResponseCode != BillingClient.OnPurchasesUpdatedSubResponseCode.NO_APPLICABLE_SUB_RESPONSE_CODE) {
            "$debugMessage (${result.responseCode}/$subResponseCode)"
        } else {
            "$debugMessage (${result.responseCode})"
        }
    }

    private fun visibleBillingError(): String? {
        return purchaseStatusError ?: productStatusError
    }

    private data class BillingOffer(
        val productDetails: ProductDetails,
        val offerDetails: ProductDetails.SubscriptionOfferDetails,
        val displayPrice: String?,
    )

    companion object {
        private const val PRODUCT_CONFIGURATION_MESSAGE =
            "Premium products were found, but expected monthly/annual base plans without offers are missing."
        private const val SUSPENDED_SUBSCRIPTION_MESSAGE =
            "Google Play reports this subscription is not active. Backend verification is required before unlocking Premium."
        private const val PRODUCTION_BACKEND_REQUIRED_MESSAGE =
            "Production billing is blocked until backend verification, acknowledgement, and RTDN are configured."
    }
}
