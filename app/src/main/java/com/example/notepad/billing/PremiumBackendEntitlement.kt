package com.example.notepad.billing

enum class PremiumBackendSubscriptionState {
    PendingPurchase,
    Active,
    GracePeriod,
    OnHold,
    Expired,
    Revoked,
}

enum class PremiumBackendAcknowledgementState {
    NotRequired,
    Pending,
    Acknowledged,
    Failed,
}

enum class PremiumRtdnNotificationType {
    Purchased,
    Renewed,
    Canceled,
    Revoked,
    Expired,
    GracePeriod,
    OnHold,
    Recovered,
    Restarted,
    Deferred,
    PendingPurchaseCanceled,
}

data class PremiumBackendPurchaseVerification(
    val packageName: String,
    val productId: String,
    val basePlanId: String?,
    val offerId: String? = null,
    val purchaseToken: String,
    val linkedPurchaseToken: String? = null,
    val purchaseTime: Long? = null,
    val expiryTime: Long? = null,
    val subscriptionState: PremiumBackendSubscriptionState,
    val acknowledgementState: PremiumBackendAcknowledgementState,
    val acknowledgementAttemptCount: Int = 0,
    val nextAcknowledgementAttemptAt: Long? = null,
    val lastAcknowledgementError: String? = null,
)

data class PremiumRtdnEvent(
    val messageId: String,
    val purchaseToken: String,
    val eventTime: Long,
    val notificationType: PremiumRtdnNotificationType,
)

data class PremiumBackendVerificationResult(
    val snapshot: PremiumSubscriptionSnapshot,
    val accepted: Boolean,
    val rejectionReason: String? = null,
)

data class PremiumRtdnSimulationState(
    val snapshot: PremiumSubscriptionSnapshot = PremiumSubscriptionSnapshot(
        status = PremiumSubscriptionStatus.Free,
        source = PremiumEntitlementSource.None,
    ),
    val processedMessageIds: Set<String> = emptySet(),
)

object PremiumBackendEntitlementMapper {
    fun fromVerification(
        expectedPackageName: String,
        verification: PremiumBackendPurchaseVerification,
        now: Long,
    ): PremiumBackendVerificationResult {
        val rejectionReason = rejectReason(expectedPackageName, verification)
        if (rejectionReason != null) {
            return PremiumBackendVerificationResult(
                accepted = false,
                rejectionReason = rejectionReason,
                snapshot = PremiumSubscriptionSnapshot(
                    status = PremiumSubscriptionStatus.Error,
                    source = PremiumEntitlementSource.None,
                    productId = verification.productId,
                    basePlanId = verification.basePlanId,
                    offerId = verification.offerId,
                    purchaseTokenHash = PremiumEntitlementStore.hashPurchaseToken(verification.purchaseToken),
                    purchaseTime = verification.purchaseTime,
                    lastBackendVerifiedAt = now,
                    lastEntitlementChangeAt = now,
                    acknowledgementStatus = PremiumAcknowledgementStatus.NotRequired,
                    lastAcknowledgementError = rejectionReason,
                ),
            )
        }

        val acknowledgementStatus = verification.acknowledgementState.toSnapshotStatus()
        val verifiedStatus = verification.subscriptionState.toSnapshotStatus()
        val status = when {
            acknowledgementStatus == PremiumAcknowledgementStatus.BackendRequired ||
                acknowledgementStatus == PremiumAcknowledgementStatus.RetryScheduled ->
                PremiumSubscriptionStatus.VerificationPending
            else -> verifiedStatus
        }

        return PremiumBackendVerificationResult(
            accepted = true,
            snapshot = PremiumSubscriptionSnapshot(
                status = status,
                source = PremiumEntitlementSource.BackendVerified,
                productId = verification.productId,
                basePlanId = verification.basePlanId,
                offerId = verification.offerId,
                purchaseTokenHash = PremiumEntitlementStore.hashPurchaseToken(verification.purchaseToken),
                purchaseTime = verification.purchaseTime,
                expiryTime = verification.expiryTime,
                lastBackendVerifiedAt = now,
                lastEntitlementChangeAt = now,
                acknowledgementStatus = acknowledgementStatus,
                acknowledgementAttemptCount = verification.acknowledgementAttemptCount,
                nextAcknowledgementAttemptAt = verification.nextAcknowledgementAttemptAt,
                lastAcknowledgementError = verification.lastAcknowledgementError,
            ),
        )
    }

    fun applyRtdnRequery(
        state: PremiumRtdnSimulationState,
        event: PremiumRtdnEvent,
        requeryResult: PremiumBackendPurchaseVerification,
        expectedPackageName: String,
        now: Long,
    ): PremiumRtdnSimulationState {
        if (event.messageId in state.processedMessageIds) return state
        val verification = fromVerification(
            expectedPackageName = expectedPackageName,
            verification = requeryResult,
            now = now,
        )
        return PremiumRtdnSimulationState(
            snapshot = verification.snapshot,
            processedMessageIds = state.processedMessageIds + event.messageId,
        )
    }

    private fun rejectReason(
        expectedPackageName: String,
        verification: PremiumBackendPurchaseVerification,
    ): String? {
        if (verification.packageName != expectedPackageName) {
            return "Purchase token package does not match this app."
        }
        if (!PremiumCatalog.isPremiumProduct(verification.productId)) {
            return "Purchase token is not for a known Premium product."
        }
        if (!verification.matchesKnownBasePlan()) {
            return "Purchase token base plan does not match the Premium catalog."
        }
        return null
    }

    private fun PremiumBackendPurchaseVerification.matchesKnownBasePlan(): Boolean {
        val basePlan = basePlanId ?: return true
        return PremiumPlan.entries.any { plan ->
            productId in plan.productIdsInPreferenceOrder && plan.basePlanId == basePlan
        }
    }

    private fun PremiumBackendSubscriptionState.toSnapshotStatus(): PremiumSubscriptionStatus {
        return when (this) {
            PremiumBackendSubscriptionState.PendingPurchase -> PremiumSubscriptionStatus.PendingPurchase
            PremiumBackendSubscriptionState.Active -> PremiumSubscriptionStatus.Active
            PremiumBackendSubscriptionState.GracePeriod -> PremiumSubscriptionStatus.GracePeriod
            PremiumBackendSubscriptionState.OnHold -> PremiumSubscriptionStatus.OnHold
            PremiumBackendSubscriptionState.Expired -> PremiumSubscriptionStatus.Expired
            PremiumBackendSubscriptionState.Revoked -> PremiumSubscriptionStatus.Revoked
        }
    }

    private fun PremiumBackendAcknowledgementState.toSnapshotStatus(): PremiumAcknowledgementStatus {
        return when (this) {
            PremiumBackendAcknowledgementState.NotRequired -> PremiumAcknowledgementStatus.NotRequired
            PremiumBackendAcknowledgementState.Pending -> PremiumAcknowledgementStatus.BackendRequired
            PremiumBackendAcknowledgementState.Acknowledged -> PremiumAcknowledgementStatus.Acknowledged
            PremiumBackendAcknowledgementState.Failed -> PremiumAcknowledgementStatus.RetryScheduled
        }
    }
}
