package com.brianyeh.justnotes.backend.play

import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendEntitlementSource
import com.brianyeh.justnotes.backend.entitlement.EntitlementGrantInputs
import com.brianyeh.justnotes.backend.entitlement.EntitlementGrantPolicy
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord

object PlaySubscriptionMapper {
    fun fromSubscriptionsV2(snapshot: PlaySubscriptionsV2Snapshot): PlaySubscriptionVerification {
        return PlaySubscriptionVerification(
            packageName = snapshot.packageName,
            purchaseTokenHash = snapshot.purchaseTokenHash,
            subscriptionState = snapshot.subscriptionState.toBackendStatus(),
            playSubscriptionState = snapshot.subscriptionState,
            lineItems = snapshot.lineItems,
            acknowledgementState = snapshot.acknowledgementState.name,
            playAcknowledgementState = snapshot.acknowledgementState,
            autoRenewing = snapshot.autoRenewing,
            linkedPurchaseTokenHash = snapshot.linkedPurchaseTokenHash,
            externalAccountIdentifiers = snapshot.externalAccountIdentifiers,
            canceledButActiveUntilExpiry = snapshot.subscriptionState == PlaySubscriptionState.SUBSCRIPTION_STATE_CANCELED,
            purchaseTokenHashVersion = snapshot.purchaseTokenHashVersion,
            purchaseTokenPepperVersion = snapshot.purchaseTokenPepperVersion,
        )
    }

    fun toEntitlement(
        googleSub: String,
        verification: PlaySubscriptionVerification,
        now: Long,
        ownershipVerified: Boolean = false,
        config: BackendConfig = BackendConfig.fromEnvironment(),
    ): EntitlementRecord {
        val primaryLineItem = verification.lineItems.maxByOrNull { it.expiryTime ?: Long.MIN_VALUE }
        val catalogError = config.validateCatalog(
            packageName = verification.packageName,
            productId = primaryLineItem?.productId,
            basePlanId = primaryLineItem?.basePlanId,
            offerId = primaryLineItem?.offerId,
        )
        val rawStatus = when {
            catalogError != null -> BackendSubscriptionStatus.Unknown
            verification.canceledButActiveUntilExpiry -> BackendSubscriptionStatus.CanceledActiveUntilExpiry
            else -> verification.subscriptionState
        }
        val acknowledgementState = verification.toBackendAcknowledgementState()
        val grantInputs = EntitlementGrantInputs(
            status = rawStatus,
            expiryTime = primaryLineItem?.expiryTime,
            acknowledgementState = acknowledgementState,
            ownershipVerified = ownershipVerified,
        )
        val hasPremium = EntitlementGrantPolicy.hasPremium(grantInputs, now)
        val status = if (hasPremium) {
            when (rawStatus) {
                BackendSubscriptionStatus.CanceledActiveUntilExpiry -> BackendSubscriptionStatus.Active
                else -> rawStatus
            }
        } else {
            EntitlementGrantPolicy.nonGrantingStatusFor(grantInputs)
        }
        return EntitlementRecord(
            googleSub = googleSub,
            hasPremium = hasPremium,
            status = status,
            source = BackendEntitlementSource.BackendVerified,
            packageName = verification.packageName,
            productId = primaryLineItem?.productId,
            basePlanId = primaryLineItem?.basePlanId,
            offerId = primaryLineItem?.offerId,
            expiryTime = primaryLineItem?.expiryTime,
            lastVerifiedAt = now,
            purchaseTokenHash = verification.purchaseTokenHash,
            acknowledgementState = acknowledgementState,
        )
    }

    fun PlaySubscriptionState.toBackendStatus(): BackendSubscriptionStatus {
        return when (this) {
            PlaySubscriptionState.SUBSCRIPTION_STATE_ACTIVE -> BackendSubscriptionStatus.Active
            PlaySubscriptionState.SUBSCRIPTION_STATE_IN_GRACE_PERIOD -> BackendSubscriptionStatus.GracePeriod
            PlaySubscriptionState.SUBSCRIPTION_STATE_CANCELED -> BackendSubscriptionStatus.CanceledActiveUntilExpiry
            PlaySubscriptionState.SUBSCRIPTION_STATE_ON_HOLD -> BackendSubscriptionStatus.OnHold
            PlaySubscriptionState.SUBSCRIPTION_STATE_PAUSED -> BackendSubscriptionStatus.Paused
            PlaySubscriptionState.SUBSCRIPTION_STATE_EXPIRED -> BackendSubscriptionStatus.Expired
            PlaySubscriptionState.REVOKED -> BackendSubscriptionStatus.Revoked
            PlaySubscriptionState.SUBSCRIPTION_STATE_PENDING -> BackendSubscriptionStatus.VerificationPending
            PlaySubscriptionState.PENDING_PURCHASE_CANCELED -> BackendSubscriptionStatus.VerificationPending
            PlaySubscriptionState.SUBSCRIPTION_STATE_UNSPECIFIED -> BackendSubscriptionStatus.Unknown
            PlaySubscriptionState.UNKNOWN -> BackendSubscriptionStatus.Unknown
        }
    }

    fun PlaySubscriptionVerification.toBackendAcknowledgementState(): BackendAcknowledgementState {
        playAcknowledgementState?.let { state ->
            return when (state) {
                PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED -> BackendAcknowledgementState.Acknowledged
                PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING -> BackendAcknowledgementState.Pending
                PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_UNSPECIFIED -> BackendAcknowledgementState.Unknown
                PlayAcknowledgementState.UNKNOWN -> BackendAcknowledgementState.Unknown
            }
        }
        return when (acknowledgementState) {
            "ACKNOWLEDGED", "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED" -> BackendAcknowledgementState.Acknowledged
            "PENDING", "ACKNOWLEDGEMENT_STATE_PENDING" -> BackendAcknowledgementState.Pending
            null -> BackendAcknowledgementState.Unknown
            else -> BackendAcknowledgementState.Unknown
        }
    }
}
