package com.brianyeh.justnotes.backend.entitlement

enum class BackendEntitlementSource {
    BackendVerified,
}

enum class BackendSubscriptionStatus {
    Unknown,
    Free,
    Active,
    GracePeriod,
    OnHold,
    Expired,
    Revoked,
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
    val purchaseTokenHash: String? = null,
)

data class SubscriptionBinding(
    val purchaseTokenHash: String,
    val ownerGoogleSub: String,
    val packageName: String,
    val productId: String,
    val basePlanId: String?,
)
