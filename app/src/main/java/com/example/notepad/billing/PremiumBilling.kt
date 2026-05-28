package com.example.notepad.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class PremiumPlan(val productId: String) {
    Monthly("just_notes_premium_monthly"),
    Annual("just_notes_premium_annual"),
}

data class PremiumBillingState(
    val isPremium: Boolean = false,
    val debugPremiumOverride: Boolean = false,
    val billingAvailable: Boolean = false,
    val loading: Boolean = true,
    val monthlyPrice: String? = null,
    val annualPrice: String? = null,
    val lastError: String? = null,
) {
    val hasPremiumAccess: Boolean
        get() = isPremium || debugPremiumOverride
}

class PremiumBilling(
    private val application: Application,
) : PurchasesUpdatedListener {
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        PremiumBillingState(
            isPremium = preferences.getBoolean(PREMIUM_ENTITLED_KEY, false),
        ),
    )
    val state: StateFlow<PremiumBillingState> = _state
    private val productDetailsById = mutableMapOf<String, ProductDetails>()

    private val billingClient = BillingClient.newBuilder(application)
        .enablePendingPurchases()
        .setListener(this)
        .build()

    fun start() {
        if (billingClient.isReady) {
            refresh()
            return
        }
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        _state.update { it.copy(billingAvailable = true, loading = false, lastError = null) }
                        refresh()
                    } else {
                        _state.update {
                            it.copy(
                                billingAvailable = false,
                                loading = false,
                                lastError = result.debugMessage.ifBlank { "Billing unavailable" },
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
        if (!billingClient.isReady) {
            start()
            return
        }
        queryProductDetails()
        queryActivePurchases()
    }

    fun launchPurchase(activity: Activity, plan: PremiumPlan): Boolean {
        val productDetails = productDetailsById[plan.productId] ?: return false
        val offerToken = productDetails.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken
            ?: return false
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val result = billingClient.launchBillingFlow(activity, params)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun close() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> updateEntitlement(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _state.update { it.copy(lastError = result.debugMessage) }
        }
    }

    private fun queryProductDetails() {
        val products = PremiumPlan.entries.map { plan ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(plan.productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.update { it.copy(lastError = result.debugMessage) }
                return@queryProductDetailsAsync
            }
            productDetailsById.clear()
            productDetailsList.forEach { details ->
                productDetailsById[details.productId] = details
            }
            _state.update {
                it.copy(
                    billingAvailable = true,
                    loading = false,
                    monthlyPrice = productDetailsById[PremiumPlan.Monthly.productId]?.displayPrice(),
                    annualPrice = productDetailsById[PremiumPlan.Annual.productId]?.displayPrice(),
                    lastError = null,
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
                updateEntitlement(purchases)
            } else {
                _state.update { it.copy(lastError = result.debugMessage) }
            }
        }
    }

    private fun updateEntitlement(purchases: List<Purchase>) {
        val activePurchases = purchases.filter { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.any { productId -> productId in PremiumPlan.entries.map { it.productId } }
        }
        activePurchases
            .filterNot { it.isAcknowledged }
            .forEach(::acknowledgePurchase)
        val entitled = activePurchases.isNotEmpty()
        preferences.edit()
            .putBoolean(PREMIUM_ENTITLED_KEY, entitled)
            .apply()
        _state.update { it.copy(isPremium = entitled, loading = false, lastError = null) }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.update { it.copy(lastError = result.debugMessage) }
            }
        }
    }

    private fun ProductDetails.displayPrice(): String? {
        return subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.lastOrNull()
            ?.formattedPrice
    }

    companion object {
        private const val PREFERENCES_NAME = "billing_entitlement"
        private const val PREMIUM_ENTITLED_KEY = "premium_entitled"
    }
}
