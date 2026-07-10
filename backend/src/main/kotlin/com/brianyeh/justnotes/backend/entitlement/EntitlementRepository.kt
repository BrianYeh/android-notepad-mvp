package com.brianyeh.justnotes.backend.entitlement

sealed class SubscriptionWriteResult {
    data object Created : SubscriptionWriteResult()
    data object UpdatedForSameOwner : SubscriptionWriteResult()
    data object OwnedByAnotherUser : SubscriptionWriteResult()
}

sealed class AcknowledgementClaimResult {
    data class Claimed(
        val record: SubscriptionRecord,
        val generation: Long,
    ) : AcknowledgementClaimResult()
    data class NotDue(val record: SubscriptionRecord) : AcknowledgementClaimResult()
    data class AlreadyAcknowledged(val record: SubscriptionRecord) : AcknowledgementClaimResult()
    data class TerminalFailure(val record: SubscriptionRecord) : AcknowledgementClaimResult()
    data class NotEligible(val record: SubscriptionRecord) : AcknowledgementClaimResult()
    data object Missing : AcknowledgementClaimResult()
    data object OwnedByAnotherUser : AcknowledgementClaimResult()
}

sealed class AcknowledgementCompletionResult {
    data class Applied(val record: SubscriptionRecord) : AcknowledgementCompletionResult()
    data class Stale(val record: SubscriptionRecord) : AcknowledgementCompletionResult()
    data object Missing : AcknowledgementCompletionResult()
    data object OwnedByAnotherUser : AcknowledgementCompletionResult()
}

sealed class EntitlementReconciliationResult {
    data class Success(
        val entitlement: EntitlementRecord,
        val subscription: SubscriptionRecord,
    ) : EntitlementReconciliationResult()
    data object Missing : EntitlementReconciliationResult()
    data object OwnedByAnotherUser : EntitlementReconciliationResult()
}

interface EntitlementRepository {
    suspend fun getEntitlement(googleSub: String): EntitlementRecord?
    suspend fun upsertEntitlement(record: EntitlementRecord): EntitlementRecord
    suspend fun getSubscription(purchaseTokenHash: String): SubscriptionRecord?
    suspend fun upsertSubscriptionForOwner(record: SubscriptionRecord): SubscriptionWriteResult
    suspend fun claimSubscriptionAcknowledgement(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        now: Long,
        leaseUntil: Long,
    ): AcknowledgementClaimResult
    suspend fun completeSubscriptionAcknowledgement(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        generation: Long,
        acknowledgementState: BackendAcknowledgementState,
        acknowledgementAttemptCount: Int,
        nextAcknowledgementAttemptAt: Long?,
        lastAcknowledgementErrorCode: String?,
    ): AcknowledgementCompletionResult
    suspend fun reconcileEntitlementFromSubscription(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        now: Long,
    ): EntitlementReconciliationResult
}

internal fun selectEffectiveEntitlement(
    existing: EntitlementRecord?,
    incoming: EntitlementRecord,
): EntitlementRecord {
    if (existing == null) return incoming
    require(existing.googleSub == incoming.googleSub) { "Entitlement owners must match." }

    val sameToken = existing.purchaseTokenHash != null &&
        existing.purchaseTokenHash == incoming.purchaseTokenHash
    val existingAcknowledgedGrant = existing.hasPremium &&
        existing.acknowledgementState == BackendAcknowledgementState.Acknowledged
    val incomingAcknowledgedGrant = incoming.hasPremium &&
        incoming.acknowledgementState == BackendAcknowledgementState.Acknowledged
    val existingAwaitingAcknowledgement = !existing.hasPremium &&
        existing.status == BackendSubscriptionStatus.VerificationPending
    val incomingAwaitingAcknowledgement = !incoming.hasPremium &&
        incoming.status == BackendSubscriptionStatus.VerificationPending

    if (sameToken && existingAcknowledgedGrant && incomingAwaitingAcknowledgement) {
        return promoteAcknowledgedGrant(existing, incoming)
    }
    if (sameToken && incomingAcknowledgedGrant && existingAwaitingAcknowledgement) {
        return promoteAcknowledgedGrant(incoming, existing)
    }

    val existingVerifiedAt = existing.lastVerifiedAt
    val incomingVerifiedAt = incoming.lastVerifiedAt
    return when {
        existingVerifiedAt == null -> incoming
        incomingVerifiedAt == null -> existing
        incomingVerifiedAt > existingVerifiedAt -> incoming
        incomingVerifiedAt < existingVerifiedAt -> existing
        existing.hasPremium && !incoming.hasPremium -> incoming
        !existing.hasPremium && incoming.hasPremium -> existing
        else -> incoming
    }
}

private fun promoteAcknowledgedGrant(
    acknowledgedGrant: EntitlementRecord,
    verificationPending: EntitlementRecord,
): EntitlementRecord {
    val latestLifecycle = if (
        (verificationPending.lastVerifiedAt ?: Long.MIN_VALUE) >
        (acknowledgedGrant.lastVerifiedAt ?: Long.MIN_VALUE)
    ) {
        verificationPending
    } else {
        acknowledgedGrant
    }
    return latestLifecycle.copy(
        hasPremium = true,
        status = acknowledgedGrant.status,
        acknowledgementState = BackendAcknowledgementState.Acknowledged,
    )
}

internal fun SubscriptionRecord.reconciledEntitlement(now: Long): EntitlementRecord {
    val grantable = status == BackendSubscriptionStatus.Active ||
        status == BackendSubscriptionStatus.GracePeriod ||
        status == BackendSubscriptionStatus.CanceledActiveUntilExpiry
    val unexpired = expiryTime?.let { it > now } == true
    val hasPremium = grantable &&
        unexpired &&
        acknowledgementState == BackendAcknowledgementState.Acknowledged
    val effectiveStatus = when {
        hasPremium -> status
        status == BackendSubscriptionStatus.PendingPurchase -> BackendSubscriptionStatus.PendingPurchase
        grantable && !unexpired -> BackendSubscriptionStatus.Expired
        grantable -> BackendSubscriptionStatus.VerificationPending
        else -> status
    }
    return EntitlementRecord(
        googleSub = ownerGoogleSub,
        hasPremium = hasPremium,
        status = effectiveStatus,
        source = BackendEntitlementSource.BackendVerified,
        packageName = packageName,
        productId = productId,
        basePlanId = basePlanId,
        offerId = offerId,
        expiryTime = expiryTime,
        lastVerifiedAt = lastVerifiedAt,
        stale = false,
        purchaseTokenHash = purchaseTokenHash,
        acknowledgementState = acknowledgementState,
    )
}

internal fun selectReconciledEntitlementRecord(
    existing: EntitlementRecord?,
    candidate: EntitlementRecord,
    now: Long,
): EntitlementRecord {
    if (existing == null) return candidate
    val safeExisting = existing.failClosedAt(now)
    require(safeExisting.googleSub == candidate.googleSub) { "Entitlement owners must match." }
    if (safeExisting.purchaseTokenHash == candidate.purchaseTokenHash) return candidate
    val existingVerifiedAt = safeExisting.lastVerifiedAt ?: Long.MIN_VALUE
    val candidateVerifiedAt = candidate.lastVerifiedAt ?: Long.MIN_VALUE
    return when {
        candidateVerifiedAt > existingVerifiedAt -> candidate
        candidateVerifiedAt < existingVerifiedAt -> safeExisting
        candidate.hasPremium && !safeExisting.hasPremium -> safeExisting
        else -> candidate
    }
}

internal fun EntitlementRecord.failClosedAt(now: Long): EntitlementRecord {
    if (!hasPremium) return this
    val grantable = status == BackendSubscriptionStatus.Active ||
        status == BackendSubscriptionStatus.GracePeriod ||
        status == BackendSubscriptionStatus.CanceledActiveUntilExpiry
    val unexpired = expiryTime?.let { it > now } == true
    val acknowledged = acknowledgementState == BackendAcknowledgementState.Acknowledged
    if (source == BackendEntitlementSource.BackendVerified && grantable && unexpired && acknowledged) {
        return this
    }
    return copy(
        hasPremium = false,
        status = when {
            grantable && !unexpired -> BackendSubscriptionStatus.Expired
            grantable -> BackendSubscriptionStatus.VerificationPending
            else -> status
        },
    )
}
