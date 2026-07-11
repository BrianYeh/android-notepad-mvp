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
        maxStaleMillis: Long = Long.MAX_VALUE,
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
        linkedPurchaseTokenHash = linkedPurchaseTokenHash,
        acknowledgementState = acknowledgementState,
    )
}

internal fun selectReconciledEntitlementRecord(
    existing: EntitlementRecord?,
    candidate: EntitlementRecord,
    now: Long,
    maxStaleMillis: Long = Long.MAX_VALUE,
): EntitlementRecord {
    val safeCandidate = candidate.failClosedAt(now, maxStaleMillis)
    if (existing == null) return safeCandidate
    val safeExisting = existing.failClosedAt(now, maxStaleMillis)
    require(safeExisting.googleSub == safeCandidate.googleSub) { "Entitlement owners must match." }
    val existingTokenHash = safeExisting.purchaseTokenHash
    val candidateTokenHash = safeCandidate.purchaseTokenHash
    val sameToken = existingTokenHash != null && existingTokenHash == candidateTokenHash
    val candidateIsSuccessor = safeCandidate.linkedPurchaseTokenHash != null &&
        safeCandidate.linkedPurchaseTokenHash == existingTokenHash
    val existingIsSuccessor = safeExisting.linkedPurchaseTokenHash != null &&
        safeExisting.linkedPurchaseTokenHash == candidateTokenHash
    val existingVerifiedAt = safeExisting.lastVerifiedAt ?: Long.MIN_VALUE
    val candidateVerifiedAt = safeCandidate.lastVerifiedAt ?: Long.MIN_VALUE
    val candidateIsTerminalDenial = safeCandidate.status.isTerminalDenial()
    val existingIsTerminalDenial = safeExisting.status.isTerminalDenial()
    return when {
        sameToken -> selectEffectiveEntitlement(safeExisting, safeCandidate)
        existingIsTerminalDenial && safeCandidate.status.isTransientReplacement() -> safeExisting
        candidateIsTerminalDenial && safeExisting.status.isTransientReplacement() -> safeCandidate
        existingIsSuccessor && safeExisting.status.isTransientReplacement() && safeCandidate.hasPremium ->
            safeCandidate
        existingIsSuccessor -> safeExisting
        candidateIsSuccessor && safeCandidate.status.isTransientReplacement() && safeExisting.hasPremium ->
            safeExisting
        candidateIsSuccessor -> safeCandidate
        safeExisting.hasPremium && !safeCandidate.hasPremium && !candidateIsTerminalDenial -> safeExisting
        safeExisting.status.isTransientReplacement() && safeCandidate.hasPremium -> safeCandidate
        candidateVerifiedAt > existingVerifiedAt -> safeCandidate
        candidateVerifiedAt < existingVerifiedAt -> safeExisting
        candidateIsTerminalDenial && safeExisting.hasPremium -> safeCandidate
        existingIsTerminalDenial && safeCandidate.hasPremium -> safeExisting
        else -> safeCandidate
    }
}

private fun BackendSubscriptionStatus.isTransientReplacement(): Boolean {
    return this == BackendSubscriptionStatus.Free ||
        this == BackendSubscriptionStatus.PendingPurchase ||
        this == BackendSubscriptionStatus.VerificationPending ||
        this == BackendSubscriptionStatus.Unknown
}

private fun BackendSubscriptionStatus.isTerminalDenial(): Boolean {
    return this == BackendSubscriptionStatus.Revoked ||
        this == BackendSubscriptionStatus.Expired ||
        this == BackendSubscriptionStatus.OnHold ||
        this == BackendSubscriptionStatus.Paused
}

internal fun EntitlementRecord.failClosedAt(
    now: Long,
    maxStaleMillis: Long = Long.MAX_VALUE,
): EntitlementRecord {
    if (!hasPremium) return this
    val grantable = status == BackendSubscriptionStatus.Active ||
        status == BackendSubscriptionStatus.GracePeriod ||
        status == BackendSubscriptionStatus.CanceledActiveUntilExpiry
    val unexpired = expiryTime?.let { it > now } == true
    val acknowledged = acknowledgementState == BackendAcknowledgementState.Acknowledged
    val withinMaxStale = lastVerifiedAt?.let { verifiedAt ->
        verifiedAt > now || now - verifiedAt <= maxStaleMillis
    } == true
    if (
        source == BackendEntitlementSource.BackendVerified &&
        grantable &&
        unexpired &&
        acknowledged &&
        withinMaxStale
    ) {
        return this
    }
    return copy(
        hasPremium = false,
        status = when {
            grantable && !unexpired -> BackendSubscriptionStatus.Expired
            !withinMaxStale -> BackendSubscriptionStatus.Unknown
            grantable -> BackendSubscriptionStatus.VerificationPending
            else -> status
        },
    )
}
