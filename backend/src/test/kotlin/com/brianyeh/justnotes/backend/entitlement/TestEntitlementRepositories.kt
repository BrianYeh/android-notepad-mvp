package com.brianyeh.justnotes.backend.entitlement

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryEntitlementRepository : EntitlementRepository {
    private val mutex = Mutex()
    private val entitlements = mutableMapOf<String, EntitlementRecord>()
    private val subscriptions = mutableMapOf<String, SubscriptionRecord>()

    override suspend fun getEntitlement(googleSub: String): EntitlementRecord? {
        return mutex.withLock { entitlements[googleSub] }
    }

    override suspend fun upsertEntitlement(record: EntitlementRecord): EntitlementRecord {
        return mutex.withLock {
            val effective = selectEffectiveEntitlement(entitlements[record.googleSub], record)
            entitlements[record.googleSub] = effective
            effective
        }
    }

    override suspend fun getSubscription(purchaseTokenHash: String): SubscriptionRecord? {
        return mutex.withLock { subscriptions[purchaseTokenHash] }
    }

    override suspend fun upsertSubscriptionForOwner(record: SubscriptionRecord): SubscriptionWriteResult {
        return mutex.withLock {
            val existing = subscriptions[record.purchaseTokenHash]
            when {
                existing == null -> {
                    subscriptions[record.purchaseTokenHash] = record
                    SubscriptionWriteResult.Created
                }
                existing.ownerGoogleSub != record.ownerGoogleSub -> SubscriptionWriteResult.OwnedByAnotherUser
                else -> {
                    subscriptions[record.purchaseTokenHash] = mergeSubscriptionAxes(existing, record)
                    SubscriptionWriteResult.UpdatedForSameOwner
                }
            }
        }
    }

    override suspend fun claimSubscriptionAcknowledgement(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        now: Long,
        leaseUntil: Long,
    ): AcknowledgementClaimResult {
        require(leaseUntil > now) { "Acknowledgement lease must end after it starts." }
        return mutex.withLock {
            val existing = subscriptions[purchaseTokenHash] ?: return@withLock AcknowledgementClaimResult.Missing
            when {
                existing.ownerGoogleSub != ownerGoogleSub -> AcknowledgementClaimResult.OwnedByAnotherUser
                existing.acknowledgementState == BackendAcknowledgementState.Acknowledged ->
                    AcknowledgementClaimResult.AlreadyAcknowledged(existing)
                existing.acknowledgementState == BackendAcknowledgementState.Failed ->
                    AcknowledgementClaimResult.TerminalFailure(existing)
                !existing.isAcknowledgementEligible(now) -> AcknowledgementClaimResult.NotEligible(existing)
                existing.acknowledgementState != BackendAcknowledgementState.Pending ->
                    AcknowledgementClaimResult.TerminalFailure(existing)
                existing.nextAcknowledgementAttemptAt?.let { it > now } == true ||
                    existing.acknowledgementLeaseUntil?.let { it > now } == true ->
                    AcknowledgementClaimResult.NotDue(existing)
                else -> {
                    val generation = existing.acknowledgementClaimGeneration + 1L
                    val claimed = existing.copy(
                        acknowledgementClaimGeneration = generation,
                        acknowledgementLeaseUntil = leaseUntil,
                    )
                    subscriptions[purchaseTokenHash] = claimed
                    AcknowledgementClaimResult.Claimed(claimed, generation)
                }
            }
        }
    }

    override suspend fun completeSubscriptionAcknowledgement(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        generation: Long,
        acknowledgementState: BackendAcknowledgementState,
        acknowledgementAttemptCount: Int,
        nextAcknowledgementAttemptAt: Long?,
        lastAcknowledgementErrorCode: String?,
    ): AcknowledgementCompletionResult {
        return mutex.withLock {
            val existing = subscriptions[purchaseTokenHash]
                ?: return@withLock AcknowledgementCompletionResult.Missing
            when {
                existing.ownerGoogleSub != ownerGoogleSub -> AcknowledgementCompletionResult.OwnedByAnotherUser
                existing.acknowledgementClaimGeneration != generation ||
                    existing.acknowledgementLeaseUntil == null ->
                    AcknowledgementCompletionResult.Stale(existing)
                else -> {
                    val updated = existing.copy(
                        acknowledgementState = acknowledgementState,
                        acknowledgementAttemptCount = acknowledgementAttemptCount,
                        nextAcknowledgementAttemptAt = nextAcknowledgementAttemptAt,
                        lastAcknowledgementErrorCode = lastAcknowledgementErrorCode,
                        acknowledgementLeaseUntil = null,
                    )
                    subscriptions[purchaseTokenHash] = updated
                    AcknowledgementCompletionResult.Applied(updated)
                }
            }
        }
    }

    override suspend fun reconcileEntitlementFromSubscription(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        now: Long,
        maxStaleMillis: Long,
    ): EntitlementReconciliationResult {
        return mutex.withLock {
            val subscription = subscriptions[purchaseTokenHash]
                ?: return@withLock EntitlementReconciliationResult.Missing
            if (subscription.ownerGoogleSub != ownerGoogleSub) {
                return@withLock EntitlementReconciliationResult.OwnedByAnotherUser
            }
            val candidate = subscription.reconciledEntitlement(now)
            val effective = selectReconciledEntitlementRecord(
                existing = entitlements[ownerGoogleSub],
                candidate = candidate,
                now = now,
                maxStaleMillis = maxStaleMillis,
            )
            entitlements[ownerGoogleSub] = effective
            EntitlementReconciliationResult.Success(effective, subscription)
        }
    }

    private fun mergeSubscriptionAxes(
        existing: SubscriptionRecord,
        incoming: SubscriptionRecord,
    ): SubscriptionRecord {
        val lifecycleBase = selectLifecycleBase(existing, incoming)
        val generation = maxOf(
            existing.acknowledgementClaimGeneration,
            incoming.acknowledgementClaimGeneration,
        )
        if (
            incoming.acknowledgementState == BackendAcknowledgementState.Acknowledged ||
            existing.acknowledgementState == BackendAcknowledgementState.Acknowledged
        ) {
            return lifecycleBase.copy(
                acknowledgementState = BackendAcknowledgementState.Acknowledged,
                acknowledgementAttemptCount = 0,
                nextAcknowledgementAttemptAt = null,
                lastAcknowledgementErrorCode = null,
                acknowledgementClaimGeneration = generation,
                acknowledgementLeaseUntil = null,
            )
        }
        if (
            existing.acknowledgementState == BackendAcknowledgementState.Failed &&
            incoming.acknowledgementState != BackendAcknowledgementState.Acknowledged
        ) {
            return lifecycleBase.copy(
                acknowledgementState = BackendAcknowledgementState.Failed,
                acknowledgementAttemptCount = existing.acknowledgementAttemptCount,
                nextAcknowledgementAttemptAt = null,
                lastAcknowledgementErrorCode = existing.lastAcknowledgementErrorCode,
                acknowledgementClaimGeneration = generation,
                acknowledgementLeaseUntil = null,
            )
        }
        val acknowledgementSource = selectAcknowledgementSource(existing, incoming)
        return lifecycleBase.copy(
            acknowledgementState = acknowledgementSource.acknowledgementState,
            acknowledgementAttemptCount = acknowledgementSource.acknowledgementAttemptCount,
            nextAcknowledgementAttemptAt = acknowledgementSource.nextAcknowledgementAttemptAt,
            lastAcknowledgementErrorCode = acknowledgementSource.lastAcknowledgementErrorCode,
            acknowledgementClaimGeneration = generation,
            acknowledgementLeaseUntil = acknowledgementSource.acknowledgementLeaseUntil,
        )
    }

    private fun selectLifecycleBase(
        existing: SubscriptionRecord,
        incoming: SubscriptionRecord,
    ): SubscriptionRecord {
        if (incoming.lastVerifiedAt > existing.lastVerifiedAt) return incoming
        if (incoming.lastVerifiedAt < existing.lastVerifiedAt) return existing
        val existingGrantable = existing.status.isGrantable()
        val incomingGrantable = incoming.status.isGrantable()
        return when {
            existingGrantable && !incomingGrantable -> incoming
            !existingGrantable && incomingGrantable -> existing
            else -> incoming
        }
    }

    private fun selectAcknowledgementSource(
        existing: SubscriptionRecord,
        incoming: SubscriptionRecord,
    ): SubscriptionRecord {
        val existingNext = existing.nextAcknowledgementAttemptAt ?: Long.MIN_VALUE
        val incomingNext = incoming.nextAcknowledgementAttemptAt ?: Long.MIN_VALUE
        val existingLease = existing.acknowledgementLeaseUntil ?: Long.MIN_VALUE
        val incomingLease = incoming.acknowledgementLeaseUntil ?: Long.MIN_VALUE
        return if (
            existing.acknowledgementClaimGeneration > incoming.acknowledgementClaimGeneration ||
            (
                existing.acknowledgementClaimGeneration == incoming.acknowledgementClaimGeneration &&
                    existing.acknowledgementAttemptCount > incoming.acknowledgementAttemptCount
                ) ||
            (
                existing.acknowledgementClaimGeneration == incoming.acknowledgementClaimGeneration &&
                    existing.acknowledgementAttemptCount == incoming.acknowledgementAttemptCount &&
                    existingNext > incomingNext
                ) ||
            (
                existing.acknowledgementClaimGeneration == incoming.acknowledgementClaimGeneration &&
                    existing.acknowledgementAttemptCount == incoming.acknowledgementAttemptCount &&
                    existingNext == incomingNext &&
                    existingLease > incomingLease
                )
        ) {
            existing
        } else {
            incoming
        }
    }

    private fun SubscriptionRecord.isAcknowledgementEligible(now: Long): Boolean {
        return status.isGrantable() && expiryTime?.let { it > now } == true
    }

    private fun BackendSubscriptionStatus.isGrantable(): Boolean {
        return this == BackendSubscriptionStatus.Active ||
            this == BackendSubscriptionStatus.GracePeriod ||
            this == BackendSubscriptionStatus.CanceledActiveUntilExpiry
    }
}
