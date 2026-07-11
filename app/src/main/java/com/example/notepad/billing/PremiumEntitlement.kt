package com.example.notepad.billing

import com.example.notepad.BuildConfig

enum class PremiumSubscriptionStatus {
    Unknown,
    Free,
    VerificationPending,
    Active,
    GracePeriod,
    CanceledActiveUntilExpiry,
    Paused,
    OnHold,
    Expired,
    Revoked,
    PendingPurchase,
    BillingUnavailable,
    Error,
}

enum class PremiumEntitlementSource {
    None,
    BackendVerified,
    ClientObserved,
}

enum class PremiumAcknowledgementStatus {
    NotRequired,
    Pending,
    Acknowledged,
    RetryScheduled,
    Failed,
    BackendRequired,
    Unknown,
}

data class PremiumSubscriptionSnapshot(
    val status: PremiumSubscriptionStatus = PremiumSubscriptionStatus.Unknown,
    val source: PremiumEntitlementSource = PremiumEntitlementSource.None,
    val productId: String? = null,
    val basePlanId: String? = null,
    val offerId: String? = null,
    val purchaseTokenHash: String? = null,
    val purchaseTime: Long? = null,
    val expiryTime: Long? = null,
    val lastPlayQueryAt: Long? = null,
    val lastBackendVerifiedAt: Long? = null,
    val lastEntitlementChangeAt: Long? = null,
    val acknowledgementStatus: PremiumAcknowledgementStatus = PremiumAcknowledgementStatus.NotRequired,
    val acknowledgementAttemptCount: Int = 0,
    val nextAcknowledgementAttemptAt: Long? = null,
    val lastAcknowledgementError: String? = null,
) {
    fun hasPremiumAccess(
        allowClientObservedAccess: Boolean,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        return when (source) {
            PremiumEntitlementSource.BackendVerified ->
                (status == PremiumSubscriptionStatus.Active ||
                    status == PremiumSubscriptionStatus.GracePeriod ||
                    status == PremiumSubscriptionStatus.CanceledActiveUntilExpiry) &&
                    acknowledgementStatus == PremiumAcknowledgementStatus.Acknowledged &&
                    expiryTime?.let { it > now } == true
            PremiumEntitlementSource.ClientObserved ->
                allowClientObservedAccess &&
                    status == PremiumSubscriptionStatus.Active &&
                    acknowledgementStatus == PremiumAcknowledgementStatus.Acknowledged
            PremiumEntitlementSource.None -> false
        }
    }

    fun canLaunchPurchase(allowClientOnlyBilling: Boolean): Boolean {
        if (!allowClientOnlyBilling) return false
        return status != PremiumSubscriptionStatus.PendingPurchase &&
            status != PremiumSubscriptionStatus.VerificationPending
    }
}

data class PremiumBillingState(
    val subscription: PremiumSubscriptionSnapshot = PremiumSubscriptionSnapshot(),
    val debugPremiumOverride: Boolean = false,
    val billingAvailable: Boolean = false,
    val loading: Boolean = true,
    val monthlyPrice: String? = null,
    val annualPrice: String? = null,
    val lastError: String? = null,
) {
    val isPremium: Boolean
        get() = subscription.hasPremiumAccess(BuildConfig.ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT)

    val hasPremiumAccess: Boolean
        get() = isPremium || debugPremiumOverride

    val canLaunchPurchase: Boolean
        get() = subscription.canLaunchPurchase(BuildConfig.ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT)
}
