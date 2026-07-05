package com.brianyeh.justnotes.backend.play

import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus

data class PlaySubscriptionLineItem(
    val productId: String,
    val basePlanId: String?,
    val offerId: String?,
    val expiryTime: Long?,
)

data class PlaySubscriptionVerification(
    val packageName: String,
    val purchaseTokenHash: String,
    val subscriptionState: BackendSubscriptionStatus,
    val lineItems: List<PlaySubscriptionLineItem>,
    val acknowledgementState: String?,
    val autoRenewing: Boolean?,
    val linkedPurchaseTokenHash: String?,
    val canceledButActiveUntilExpiry: Boolean = false,
)

sealed class PlaySubscriptionVerificationResult {
    data class Success(val verification: PlaySubscriptionVerification) : PlaySubscriptionVerificationResult()
    data class Failure(val reason: String) : PlaySubscriptionVerificationResult()
}

interface PlaySubscriptionVerifier {
    suspend fun verify(packageName: String, purchaseToken: String): PlaySubscriptionVerificationResult
}

object NoopPlaySubscriptionVerifier : PlaySubscriptionVerifier {
    override suspend fun verify(packageName: String, purchaseToken: String): PlaySubscriptionVerificationResult {
        return PlaySubscriptionVerificationResult.Failure("Google Play Developer API is not configured.")
    }
}
