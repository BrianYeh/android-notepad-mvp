package com.brianyeh.justnotes.backend.entitlement

enum class BackendEntitlementSource {
    BackendVerified,
}

enum class BackendSubscriptionStatus {
    Unknown,
    Free,
    VerificationPending,
    PendingPurchase,
    Active,
    GracePeriod,
    CanceledActiveUntilExpiry,
    Paused,
    OnHold,
    Expired,
    Revoked,
}

enum class BackendAcknowledgementState {
    NotRequired,
    Pending,
    Acknowledged,
    Failed,
    Unknown,
}

data class EntitlementRecord(
    val googleSub: String,
    val hasPremium: Boolean,
    val status: BackendSubscriptionStatus,
    val source: BackendEntitlementSource = BackendEntitlementSource.BackendVerified,
    val packageName: String? = null,
    val productId: String? = null,
    val basePlanId: String? = null,
    val offerId: String? = null,
    val expiryTime: Long? = null,
    val lastVerifiedAt: Long? = null,
    val stale: Boolean = false,
    val purchaseTokenHash: String? = null,
    val acknowledgementState: BackendAcknowledgementState? = null,
)

data class SubscriptionBinding(
    val purchaseTokenHash: String,
    val ownerGoogleSub: String,
    val packageName: String,
    val productId: String,
    val basePlanId: String?,
)

data class EntitlementGrantInputs(
    val status: BackendSubscriptionStatus,
    val expiryTime: Long?,
    val acknowledgementState: BackendAcknowledgementState,
    val ownershipVerified: Boolean,
    val acknowledgementSucceededInRequest: Boolean = false,
)

object EntitlementGrantPolicy {
    fun hasPremium(inputs: EntitlementGrantInputs, now: Long): Boolean {
        val grantableState = inputs.status == BackendSubscriptionStatus.Active ||
            inputs.status == BackendSubscriptionStatus.GracePeriod ||
            inputs.status == BackendSubscriptionStatus.CanceledActiveUntilExpiry
        val acknowledged = inputs.acknowledgementState == BackendAcknowledgementState.Acknowledged ||
            inputs.acknowledgementSucceededInRequest
        return grantableState &&
            inputs.ownershipVerified &&
            acknowledged &&
            inputs.expiryTime?.let { it > now } == true
    }

    fun nonGrantingStatusFor(inputs: EntitlementGrantInputs): BackendSubscriptionStatus {
        if (inputs.acknowledgementState == BackendAcknowledgementState.Pending) {
            return BackendSubscriptionStatus.VerificationPending
        }
        if (!inputs.ownershipVerified) {
            return BackendSubscriptionStatus.VerificationPending
        }
        if (
            inputs.status == BackendSubscriptionStatus.Active ||
            inputs.status == BackendSubscriptionStatus.GracePeriod ||
            inputs.status == BackendSubscriptionStatus.CanceledActiveUntilExpiry
        ) {
            return BackendSubscriptionStatus.Expired
        }
        return inputs.status
    }
}
