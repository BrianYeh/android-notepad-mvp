package com.brianyeh.justnotes.backend.play

import com.brianyeh.justnotes.backend.entitlement.BackendEntitlementSource
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord

object PlaySubscriptionMapper {
    fun toEntitlement(
        googleSub: String,
        verification: PlaySubscriptionVerification,
        now: Long,
    ): EntitlementRecord {
        val primaryLineItem = verification.lineItems.maxByOrNull { it.expiryTime ?: Long.MIN_VALUE }
        val status = verification.subscriptionState
        val hasPremium = status == BackendSubscriptionStatus.Active ||
            status == BackendSubscriptionStatus.GracePeriod ||
            verification.canceledButActiveUntilExpiry &&
            primaryLineItem?.expiryTime?.let { it > now } == true
        return EntitlementRecord(
            googleSub = googleSub,
            hasPremium = hasPremium,
            status = if (hasPremium && status == BackendSubscriptionStatus.Expired) {
                BackendSubscriptionStatus.Active
            } else {
                status
            },
            source = BackendEntitlementSource.BackendVerified,
            packageName = verification.packageName,
            productId = primaryLineItem?.productId,
            basePlanId = primaryLineItem?.basePlanId,
            offerId = primaryLineItem?.offerId,
            expiryTime = primaryLineItem?.expiryTime,
            lastVerifiedAt = now,
            purchaseTokenHash = verification.purchaseTokenHash,
        )
    }
}
