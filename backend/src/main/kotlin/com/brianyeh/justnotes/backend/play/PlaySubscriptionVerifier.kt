package com.brianyeh.justnotes.backend.play

import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus

enum class PlaySubscriptionState {
    SUBSCRIPTION_STATE_UNSPECIFIED,
    SUBSCRIPTION_STATE_PENDING,
    SUBSCRIPTION_STATE_ACTIVE,
    SUBSCRIPTION_STATE_PAUSED,
    SUBSCRIPTION_STATE_IN_GRACE_PERIOD,
    SUBSCRIPTION_STATE_ON_HOLD,
    SUBSCRIPTION_STATE_CANCELED,
    SUBSCRIPTION_STATE_EXPIRED,
    PENDING_PURCHASE_CANCELED,
    REVOKED,
    UNKNOWN,
}

enum class PlayAcknowledgementState {
    ACKNOWLEDGEMENT_STATE_UNSPECIFIED,
    ACKNOWLEDGEMENT_STATE_PENDING,
    ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED,
    UNKNOWN,
}

data class PlaySubscriptionLineItem(
    val productId: String,
    val basePlanId: String?,
    val offerId: String?,
    val expiryTime: Long?,
)

data class PlayExternalAccountIdentifiers(
    val obfuscatedExternalAccountId: String?,
    val obfuscatedExternalProfileId: String? = null,
)

data class PlaySubscriptionVerification(
    val packageName: String,
    val purchaseTokenHash: String,
    val subscriptionState: BackendSubscriptionStatus,
    val playSubscriptionState: PlaySubscriptionState? = null,
    val lineItems: List<PlaySubscriptionLineItem>,
    val acknowledgementState: String?,
    val playAcknowledgementState: PlayAcknowledgementState? = null,
    val autoRenewing: Boolean?,
    val linkedPurchaseTokenHash: String?,
    val externalAccountIdentifiers: PlayExternalAccountIdentifiers? = null,
    val canceledButActiveUntilExpiry: Boolean = false,
    val purchaseTokenHashVersion: String? = null,
    val purchaseTokenPepperVersion: String? = null,
)

data class PlaySubscriptionsV2Snapshot(
    val packageName: String,
    val purchaseTokenHash: String,
    val subscriptionState: PlaySubscriptionState,
    val acknowledgementState: PlayAcknowledgementState,
    val lineItems: List<PlaySubscriptionLineItem>,
    val autoRenewing: Boolean?,
    val linkedPurchaseTokenHash: String?,
    val externalAccountIdentifiers: PlayExternalAccountIdentifiers?,
    val purchaseTokenHashVersion: String? = null,
    val purchaseTokenPepperVersion: String? = null,
)

sealed class PlaySubscriptionVerificationResult {
    data class Success(val verification: PlaySubscriptionVerification) : PlaySubscriptionVerificationResult()
    data class Failure(val reason: String) : PlaySubscriptionVerificationResult()
}

interface PlaySubscriptionVerifier {
    suspend fun verify(packageName: String, purchaseToken: String): PlaySubscriptionVerificationResult
}

sealed class PlaySubscriptionAcknowledgementResult {
    data object Acknowledged : PlaySubscriptionAcknowledgementResult()
    data class Failure(
        val reason: String,
        val retryable: Boolean,
    ) : PlaySubscriptionAcknowledgementResult()
}

interface PlaySubscriptionAcknowledger {
    suspend fun acknowledge(
        packageName: String,
        productId: String,
        purchaseToken: String,
    ): PlaySubscriptionAcknowledgementResult
}
