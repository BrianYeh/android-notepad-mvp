package com.example.notepad.billing

enum class PremiumBackendAcknowledgementState {
    NotRequired,
    Pending,
    Acknowledged,
    Failed,
    Unknown,
}

data class PremiumBackendVerificationResult(
    val snapshot: PremiumSubscriptionSnapshot,
    val accepted: Boolean,
    val rejectionReason: String? = null,
    val shouldApplySnapshot: Boolean = true,
)

data class PremiumBackendEntitlementResponse(
    val hasPremium: Boolean,
    val status: PremiumSubscriptionStatus,
    val source: PremiumEntitlementSource,
    val packageName: String? = null,
    val productId: String? = null,
    val basePlanId: String? = null,
    val offerId: String? = null,
    val expiryTime: Long? = null,
    val lastVerifiedAt: Long? = null,
    val purchaseTokenHash: String? = null,
    val acknowledgementState: PremiumBackendAcknowledgementState? = null,
    val retryable: Boolean = false,
    val retryAfterSeconds: Long? = null,
    val errorCode: String? = null,
    val reason: String? = null,
)

object PremiumBackendEntitlementMapper {
    fun fromEntitlementResponse(
        expectedPackageName: String,
        response: PremiumBackendEntitlementResponse,
        now: Long,
    ): PremiumBackendVerificationResult {
        if (response.isNoBackendRecord()) {
            return PremiumBackendVerificationResult(
                accepted = true,
                shouldApplySnapshot = false,
                snapshot = PremiumSubscriptionSnapshot(
                    status = PremiumSubscriptionStatus.Unknown,
                    source = PremiumEntitlementSource.None,
                    lastBackendVerifiedAt = response.lastVerifiedAt,
                    lastEntitlementChangeAt = now,
                    acknowledgementStatus = PremiumAcknowledgementStatus.NotRequired,
                ),
            )
        }

        val acknowledgementStatus = response.acknowledgementState.toSnapshotStatus()
        val rejectionReason = rejectReason(expectedPackageName, response, now)
        if (rejectionReason != null) {
            return PremiumBackendVerificationResult(
                accepted = false,
                rejectionReason = rejectionReason,
                shouldApplySnapshot = response.errorCode == null,
                snapshot = PremiumSubscriptionSnapshot(
                    status = PremiumSubscriptionStatus.Error,
                    source = PremiumEntitlementSource.None,
                    productId = response.productId,
                    basePlanId = response.basePlanId,
                    offerId = response.offerId,
                    purchaseTokenHash = response.purchaseTokenHash,
                    expiryTime = response.expiryTime,
                    lastBackendVerifiedAt = now,
                    lastEntitlementChangeAt = now,
                    acknowledgementStatus = acknowledgementStatus,
                    nextAcknowledgementAttemptAt = response.nextAcknowledgementAttemptAt(now),
                    lastAcknowledgementError = response.reason ?: response.errorCode ?: rejectionReason,
                ),
            )
        }

        return PremiumBackendVerificationResult(
            accepted = true,
            snapshot = PremiumSubscriptionSnapshot(
                status = response.status,
                source = PremiumEntitlementSource.BackendVerified,
                productId = response.productId,
                basePlanId = response.basePlanId,
                offerId = response.offerId,
                purchaseTokenHash = response.purchaseTokenHash,
                expiryTime = response.expiryTime,
                lastBackendVerifiedAt = response.lastVerifiedAt ?: now,
                lastEntitlementChangeAt = now,
                acknowledgementStatus = acknowledgementStatus,
                nextAcknowledgementAttemptAt = response.nextAcknowledgementAttemptAt(now),
                lastAcknowledgementError = response.reason ?: response.errorCode,
            ),
        )
    }

    private fun rejectReason(
        expectedPackageName: String,
        response: PremiumBackendEntitlementResponse,
        now: Long,
    ): String? {
        if (response.isNoBackendRecord()) return null
        if (
            response.errorCode != null &&
            response.errorCode !in PENDING_VERIFICATION_ERROR_CODES
        ) {
            return "Purchase verification did not complete."
        }
        if (response.source != PremiumEntitlementSource.BackendVerified) {
            return "Entitlement response is not backend verified."
        }

        val shouldHavePremium = response.status.isGrantable() &&
            response.acknowledgementState == PremiumBackendAcknowledgementState.Acknowledged &&
            response.expiryTime?.let { it > now } == true
        if (response.hasPremium != shouldHavePremium) {
            return "Entitlement response premium flag does not match subscription state."
        }

        if (response.productId == null && !response.hasPremium) return null
        if (response.packageName != expectedPackageName) {
            return "Entitlement response package does not match this app."
        }
        if (response.productId == null || !PremiumCatalog.isPremiumProduct(response.productId)) {
            return "Entitlement response product is not for a known Premium product."
        }
        if (!response.matchesKnownCatalogTuple()) {
            return "Entitlement response plan or offer does not match the Premium catalog."
        }
        return null
    }

    private fun PremiumBackendEntitlementResponse.isNoBackendRecord(): Boolean {
        return source == PremiumEntitlementSource.None &&
            !hasPremium &&
            status == PremiumSubscriptionStatus.Unknown &&
            packageName == null &&
            productId == null &&
            basePlanId == null &&
            offerId == null &&
            expiryTime == null &&
            purchaseTokenHash == null &&
            acknowledgementState == null &&
            !retryable &&
            retryAfterSeconds == null &&
            errorCode == null &&
            reason == null
    }

    private fun PremiumBackendEntitlementResponse.matchesKnownCatalogTuple(): Boolean {
        if (productId != PremiumCatalog.PREFERRED_PRODUCT_ID) return false
        return when (basePlanId) {
            "monthly" -> offerId == null || offerId == "trial10d"
            "annual" -> offerId == null
            else -> false
        }
    }

    private fun PremiumSubscriptionStatus.isGrantable(): Boolean {
        return this == PremiumSubscriptionStatus.Active ||
            this == PremiumSubscriptionStatus.GracePeriod ||
            this == PremiumSubscriptionStatus.CanceledActiveUntilExpiry
    }

    private fun PremiumBackendAcknowledgementState?.toSnapshotStatus(): PremiumAcknowledgementStatus {
        return when (this) {
            PremiumBackendAcknowledgementState.NotRequired -> PremiumAcknowledgementStatus.NotRequired
            PremiumBackendAcknowledgementState.Pending -> PremiumAcknowledgementStatus.Pending
            PremiumBackendAcknowledgementState.Acknowledged -> PremiumAcknowledgementStatus.Acknowledged
            PremiumBackendAcknowledgementState.Failed -> PremiumAcknowledgementStatus.Failed
            PremiumBackendAcknowledgementState.Unknown,
            null,
            -> PremiumAcknowledgementStatus.Unknown
        }
    }

    private fun PremiumBackendEntitlementResponse.nextAcknowledgementAttemptAt(now: Long): Long? {
        if (!retryable) return null
        val seconds = retryAfterSeconds?.takeIf { it >= 0L } ?: return null
        return runCatching {
            Math.addExact(now, Math.multiplyExact(seconds, 1_000L))
        }.getOrNull()
    }

    private val PENDING_VERIFICATION_ERROR_CODES = setOf(
        "PURCHASE_PENDING",
        "ACKNOWLEDGEMENT_RETRY",
    )
}
