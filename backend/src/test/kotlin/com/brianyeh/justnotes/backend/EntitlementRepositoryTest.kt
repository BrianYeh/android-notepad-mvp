package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.AcknowledgementCompletionResult
import com.brianyeh.justnotes.backend.entitlement.AcknowledgementClaimResult
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.entitlement.InMemoryEntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.SubscriptionRecord
import com.brianyeh.justnotes.backend.entitlement.SubscriptionWriteResult
import com.brianyeh.justnotes.backend.entitlement.selectReconciledEntitlementRecord
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntitlementRepositoryTest {
    @Test
    fun subscriptionOwnershipIsUniqueAndSameOwnerUpdatesAreIdempotent() = runBlocking {
        val repository = InMemoryEntitlementRepository()
        val original = subscription(owner = "sub-a")
        val acknowledged = original.copy(
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
            acknowledgementAttemptCount = 0,
            nextAcknowledgementAttemptAt = null,
            lastAcknowledgementErrorCode = null,
            lastVerifiedAt = NOW + 1,
        )

        assertEquals(SubscriptionWriteResult.Created, repository.upsertSubscriptionForOwner(original))
        assertEquals(
            SubscriptionWriteResult.UpdatedForSameOwner,
            repository.upsertSubscriptionForOwner(acknowledged),
        )
        assertEquals(acknowledged, repository.getSubscription("hash"))
        assertEquals(
            SubscriptionWriteResult.OwnedByAnotherUser,
            repository.upsertSubscriptionForOwner(original.copy(ownerGoogleSub = "sub-b")),
        )
        assertEquals(acknowledged, repository.getSubscription("hash"))
    }

    @Test
    fun sameOwnerRetryPreservesExistingCiphertextMetadata() = runBlocking {
        val repository = InMemoryEntitlementRepository()
        val original = subscription(owner = "sub-a")
        repository.upsertSubscriptionForOwner(original)

        repository.upsertSubscriptionForOwner(
            original.copy(
                acknowledgementAttemptCount = 2,
                nextAcknowledgementAttemptAt = NOW + 900_000,
                lastAcknowledgementErrorCode = "PLAY_ACK_UNAVAILABLE",
                lastVerifiedAt = NOW + 1,
            ),
        )

        val stored = requireNotNull(repository.getSubscription("hash"))
        assertEquals(original.tokenCiphertext, stored.tokenCiphertext)
        assertEquals(original.keyVersion, stored.keyVersion)
        assertEquals(original.encryptedAt, stored.encryptedAt)
        assertEquals(2, stored.acknowledgementAttemptCount)
    }

    @Test
    fun acknowledgedAndTerminalFailureStatesCannotBeDowngradedByStalePendingWrites() = runBlocking {
        val repository = InMemoryEntitlementRepository()
        val acknowledged = subscription(owner = "sub-a").copy(
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
            lastVerifiedAt = NOW,
        )
        repository.upsertSubscriptionForOwner(acknowledged)
        repository.upsertSubscriptionForOwner(
            acknowledged.copy(
                acknowledgementState = BackendAcknowledgementState.Pending,
                lastVerifiedAt = NOW + 1,
            ),
        )
        assertEquals(
            BackendAcknowledgementState.Acknowledged,
            repository.getSubscription("hash")?.acknowledgementState,
        )

        val failed = subscription(owner = "sub-a").copy(
            purchaseTokenHash = "failed-hash",
            acknowledgementState = BackendAcknowledgementState.Failed,
            lastAcknowledgementErrorCode = "PLAY_ACK_REJECTED",
            lastVerifiedAt = NOW,
        )
        repository.upsertSubscriptionForOwner(failed)
        repository.upsertSubscriptionForOwner(
            failed.copy(
                acknowledgementState = BackendAcknowledgementState.Pending,
                lastAcknowledgementErrorCode = null,
                lastVerifiedAt = NOW + 1,
            ),
        )
        assertEquals(
            BackendAcknowledgementState.Failed,
            repository.getSubscription("failed-hash")?.acknowledgementState,
        )
    }

    @Test
    fun acknowledgementClaimIsAtomicAndUsesLease() = runBlocking {
        val repository = InMemoryEntitlementRepository()
        repository.upsertSubscriptionForOwner(subscription(owner = "sub-a"))

        val first = repository.claimSubscriptionAcknowledgement(
            purchaseTokenHash = "hash",
            ownerGoogleSub = "sub-a",
            now = NOW,
            leaseUntil = NOW + 60_000L,
        )
        val second = repository.claimSubscriptionAcknowledgement(
            purchaseTokenHash = "hash",
            ownerGoogleSub = "sub-a",
            now = NOW,
            leaseUntil = NOW + 60_000L,
        )

        assertTrue(first is AcknowledgementClaimResult.Claimed)
        assertTrue(second is AcknowledgementClaimResult.NotDue)
        assertEquals(NOW + 60_000L, repository.getSubscription("hash")?.acknowledgementLeaseUntil)
    }

    @Test
    fun staleClaimGenerationCannotCompleteAfterLeaseTakeover() = runBlocking {
        val repository = InMemoryEntitlementRepository()
        repository.upsertSubscriptionForOwner(subscription(owner = "sub-a"))
        val first = repository.claimSubscriptionAcknowledgement(
            purchaseTokenHash = "hash",
            ownerGoogleSub = "sub-a",
            now = NOW,
            leaseUntil = NOW + 60_000L,
        ) as AcknowledgementClaimResult.Claimed
        val second = repository.claimSubscriptionAcknowledgement(
            purchaseTokenHash = "hash",
            ownerGoogleSub = "sub-a",
            now = NOW + 60_000L,
            leaseUntil = NOW + 120_000L,
        ) as AcknowledgementClaimResult.Claimed

        val stale = repository.completeSubscriptionAcknowledgement(
            purchaseTokenHash = "hash",
            ownerGoogleSub = "sub-a",
            generation = first.generation,
            acknowledgementState = BackendAcknowledgementState.Failed,
            acknowledgementAttemptCount = 1,
            nextAcknowledgementAttemptAt = null,
            lastAcknowledgementErrorCode = "PLAY_ACK_REJECTED",
        )
        val applied = repository.completeSubscriptionAcknowledgement(
            purchaseTokenHash = "hash",
            ownerGoogleSub = "sub-a",
            generation = second.generation,
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
            acknowledgementAttemptCount = 0,
            nextAcknowledgementAttemptAt = null,
            lastAcknowledgementErrorCode = null,
        )

        assertTrue(stale is AcknowledgementCompletionResult.Stale)
        assertTrue(applied is AcknowledgementCompletionResult.Applied)
        assertEquals(BackendAcknowledgementState.Acknowledged, repository.getSubscription("hash")?.acknowledgementState)
    }

    @Test
    fun equalTimestampRevocationWinsOverGrantableLifecycle() = runBlocking {
        val repository = InMemoryEntitlementRepository()
        val active = subscription(owner = "sub-a").copy(
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
            acknowledgementAttemptCount = 0,
            nextAcknowledgementAttemptAt = null,
        )
        repository.upsertSubscriptionForOwner(active)
        repository.upsertSubscriptionForOwner(active.copy(status = BackendSubscriptionStatus.Revoked))

        val reconciled = repository.reconcileEntitlementFromSubscription("hash", "sub-a", NOW)
            as com.brianyeh.justnotes.backend.entitlement.EntitlementReconciliationResult.Success

        assertEquals(BackendSubscriptionStatus.Revoked, reconciled.entitlement.status)
        assertFalse(reconciled.entitlement.hasPremium)
    }

    @Test
    fun newerExistingDifferentTokenCannotRemainPremiumPastExpiry() = runBlocking {
        val repository = InMemoryEntitlementRepository()
        repository.upsertSubscriptionForOwner(
            subscription(owner = "sub-a").copy(
                status = BackendSubscriptionStatus.Revoked,
                lastVerifiedAt = NOW,
            ),
        )
        repository.upsertEntitlement(
            EntitlementRecord(
                googleSub = "sub-a",
                hasPremium = true,
                status = BackendSubscriptionStatus.Active,
                expiryTime = NOW,
                lastVerifiedAt = NOW + 10L,
                purchaseTokenHash = "newer-other-token",
                acknowledgementState = BackendAcknowledgementState.Acknowledged,
            ),
        )

        val reconciled = repository.reconcileEntitlementFromSubscription("hash", "sub-a", NOW)
            as com.brianyeh.justnotes.backend.entitlement.EntitlementReconciliationResult.Success

        assertFalse(reconciled.entitlement.hasPremium)
        assertEquals(BackendSubscriptionStatus.Expired, reconciled.entitlement.status)
    }

    @Test
    fun newerTransientDifferentTokenCannotReplaceValidPremiumUnlessItLinksTheOldToken() = runBlocking {
        val repository = InMemoryEntitlementRepository()
        val active = subscription(owner = "sub-a").copy(
            purchaseTokenHash = "active-hash",
            linkedPurchaseTokenHash = null,
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
        )
        repository.upsertSubscriptionForOwner(active)
        repository.reconcileEntitlementFromSubscription("active-hash", "sub-a", NOW)

        val unrelatedPending = active.copy(
            purchaseTokenHash = "pending-hash",
            linkedPurchaseTokenHash = null,
            status = BackendSubscriptionStatus.PendingPurchase,
            lastVerifiedAt = NOW + 10L,
        )
        repository.upsertSubscriptionForOwner(unrelatedPending)
        val preserved = repository.reconcileEntitlementFromSubscription("pending-hash", "sub-a", NOW)
            as com.brianyeh.justnotes.backend.entitlement.EntitlementReconciliationResult.Success

        assertTrue(preserved.entitlement.hasPremium)
        assertEquals("active-hash", preserved.entitlement.purchaseTokenHash)

        repository.upsertSubscriptionForOwner(
            unrelatedPending.copy(
                purchaseTokenHash = "replacement-hash",
                linkedPurchaseTokenHash = "active-hash",
                status = BackendSubscriptionStatus.Revoked,
                expiryTime = NOW - 1L,
                lastVerifiedAt = NOW + 20L,
            ),
        )
        val replaced = repository.reconcileEntitlementFromSubscription("replacement-hash", "sub-a", NOW)
            as com.brianyeh.justnotes.backend.entitlement.EntitlementReconciliationResult.Success

        assertFalse(replaced.entitlement.hasPremium)
        assertEquals(BackendSubscriptionStatus.Revoked, replaced.entitlement.status)
        assertEquals("replacement-hash", replaced.entitlement.purchaseTokenHash)
    }

    @Test
    fun canceledReplacementPreservesTheLinkedActiveEntitlement() = runBlocking {
        val repository = InMemoryEntitlementRepository()
        val active = subscription(owner = "sub-a").copy(
            purchaseTokenHash = "active-hash",
            linkedPurchaseTokenHash = null,
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
        )
        repository.upsertSubscriptionForOwner(active)
        repository.reconcileEntitlementFromSubscription("active-hash", "sub-a", NOW)

        repository.upsertSubscriptionForOwner(
            active.copy(
                purchaseTokenHash = "canceled-replacement-hash",
                linkedPurchaseTokenHash = "active-hash",
                status = BackendSubscriptionStatus.Free,
                expiryTime = null,
                lastVerifiedAt = NOW + 10L,
            ),
        )
        val reconciled = repository.reconcileEntitlementFromSubscription(
            "canceled-replacement-hash",
            "sub-a",
            NOW,
        ) as com.brianyeh.justnotes.backend.entitlement.EntitlementReconciliationResult.Success

        assertTrue(reconciled.entitlement.hasPremium)
        assertEquals(BackendSubscriptionStatus.Active, reconciled.entitlement.status)
        assertEquals("active-hash", reconciled.entitlement.purchaseTokenHash)
    }

    @Test
    fun activePredecessorRecoversWhenCanceledReplacementWasReconciledFirst() = runBlocking {
        val repository = InMemoryEntitlementRepository()
        val active = subscription(owner = "sub-a").copy(
            purchaseTokenHash = "active-hash",
            linkedPurchaseTokenHash = null,
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
            lastVerifiedAt = NOW + 10L,
        )
        repository.upsertSubscriptionForOwner(
            active.copy(
                purchaseTokenHash = "canceled-replacement-hash",
                linkedPurchaseTokenHash = "active-hash",
                status = BackendSubscriptionStatus.Free,
                expiryTime = null,
                lastVerifiedAt = NOW,
            ),
        )
        repository.reconcileEntitlementFromSubscription("canceled-replacement-hash", "sub-a", NOW)
        repository.upsertSubscriptionForOwner(active)

        val reconciled = repository.reconcileEntitlementFromSubscription("active-hash", "sub-a", NOW)
            as com.brianyeh.justnotes.backend.entitlement.EntitlementReconciliationResult.Success

        assertTrue(reconciled.entitlement.hasPremium)
        assertEquals(BackendSubscriptionStatus.Active, reconciled.entitlement.status)
        assertEquals("active-hash", reconciled.entitlement.purchaseTokenHash)
    }

    @Test
    fun linkedPendingReplacementPreservesActivePredecessorInEitherReconciliationOrder() = runBlocking {
        val active = subscription(owner = "sub-a").copy(
            purchaseTokenHash = "active-hash",
            linkedPurchaseTokenHash = null,
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
            lastVerifiedAt = NOW,
        )
        val pendingReplacement = active.copy(
            purchaseTokenHash = "pending-replacement-hash",
            linkedPurchaseTokenHash = "active-hash",
            status = BackendSubscriptionStatus.PendingPurchase,
            acknowledgementState = BackendAcknowledgementState.NotRequired,
            lastVerifiedAt = NOW + 10L,
        )

        val activeFirst = InMemoryEntitlementRepository()
        activeFirst.upsertSubscriptionForOwner(active)
        activeFirst.reconcileEntitlementFromSubscription("active-hash", "sub-a", NOW)
        activeFirst.upsertSubscriptionForOwner(pendingReplacement)
        val afterPending = activeFirst.reconcileEntitlementFromSubscription(
            "pending-replacement-hash",
            "sub-a",
            NOW,
        ) as com.brianyeh.justnotes.backend.entitlement.EntitlementReconciliationResult.Success

        val pendingFirst = InMemoryEntitlementRepository()
        pendingFirst.upsertSubscriptionForOwner(pendingReplacement)
        pendingFirst.reconcileEntitlementFromSubscription("pending-replacement-hash", "sub-a", NOW)
        pendingFirst.upsertSubscriptionForOwner(active)
        val afterActive = pendingFirst.reconcileEntitlementFromSubscription("active-hash", "sub-a", NOW)
            as com.brianyeh.justnotes.backend.entitlement.EntitlementReconciliationResult.Success

        assertTrue(afterPending.entitlement.hasPremium)
        assertEquals("active-hash", afterPending.entitlement.purchaseTokenHash)
        assertTrue(afterActive.entitlement.hasPremium)
        assertEquals("active-hash", afterActive.entitlement.purchaseTokenHash)
    }

    @Test
    fun staleOldTokenCannotRestorePremiumAfterItsNewerSuccessorWasRevoked() {
        val staleOldToken = EntitlementRecord(
            googleSub = "sub-a",
            hasPremium = true,
            status = BackendSubscriptionStatus.Active,
            expiryTime = NOW + 30L * 24L * 60L * 60L * 1_000L,
            lastVerifiedAt = NOW,
            purchaseTokenHash = "old-hash",
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
        )
        val newerRevokedSuccessor = EntitlementRecord(
            googleSub = "sub-a",
            hasPremium = false,
            status = BackendSubscriptionStatus.Revoked,
            expiryTime = NOW - 1L,
            lastVerifiedAt = NOW + 10L,
            purchaseTokenHash = "successor-hash",
            linkedPurchaseTokenHash = "old-hash",
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
        )

        val reconciled = selectReconciledEntitlementRecord(
            existing = newerRevokedSuccessor,
            candidate = staleOldToken,
            now = NOW,
        )

        assertFalse(reconciled.hasPremium)
        assertEquals(BackendSubscriptionStatus.Revoked, reconciled.status)
        assertEquals("successor-hash", reconciled.purchaseTokenHash)
    }

    @Test
    fun nullLinkedHashDoesNotMatchAnExistingLegacyEntitlementWithoutATokenHash() {
        val newerExisting = EntitlementRecord(
            googleSub = "sub-a",
            hasPremium = false,
            status = BackendSubscriptionStatus.Revoked,
            lastVerifiedAt = NOW + 10L,
            purchaseTokenHash = null,
        )
        val olderCandidate = newerExisting.copy(
            status = BackendSubscriptionStatus.Free,
            lastVerifiedAt = NOW,
            purchaseTokenHash = "unrelated-hash",
        )

        val reconciled = selectReconciledEntitlementRecord(
            existing = newerExisting,
            candidate = olderCandidate,
            now = NOW,
        )

        assertEquals(newerExisting, reconciled)
    }

    @Test
    fun newerUnlinkedTerminalDenialBeatsAnOlderPremiumObservation() {
        val olderPremium = EntitlementRecord(
            googleSub = "sub-a",
            hasPremium = true,
            status = BackendSubscriptionStatus.Active,
            expiryTime = NOW + 30L * 24L * 60L * 60L * 1_000L,
            lastVerifiedAt = NOW,
            purchaseTokenHash = "old-hash",
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
        )
        val newerRevoked = olderPremium.copy(
            hasPremium = false,
            status = BackendSubscriptionStatus.Revoked,
            expiryTime = NOW - 1L,
            lastVerifiedAt = NOW + 10L,
            purchaseTokenHash = "unlinked-new-hash",
        )

        val reconciled = selectReconciledEntitlementRecord(
            existing = newerRevoked,
            candidate = olderPremium,
            now = NOW,
        )

        assertEquals(newerRevoked, reconciled)
    }

    @Test
    fun existingTerminalDenialWinsAnEqualTimestampTieAgainstPremium() {
        val terminal = EntitlementRecord(
            googleSub = "sub-a",
            hasPremium = false,
            status = BackendSubscriptionStatus.Revoked,
            expiryTime = NOW - 1L,
            lastVerifiedAt = NOW,
            purchaseTokenHash = "revoked-hash",
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
        )
        val premium = terminal.copy(
            hasPremium = true,
            status = BackendSubscriptionStatus.Active,
            expiryTime = NOW + 30L * 24L * 60L * 60L * 1_000L,
            purchaseTokenHash = "active-hash",
        )

        val reconciled = selectReconciledEntitlementRecord(
            existing = terminal,
            candidate = premium,
            now = NOW,
        )

        assertEquals(terminal, reconciled)
    }

    @Test
    fun premiumOlderThanTheMaximumStaleWindowIsNotPreserved() {
        val stalePremium = EntitlementRecord(
            googleSub = "sub-a",
            hasPremium = true,
            status = BackendSubscriptionStatus.Active,
            expiryTime = NOW + 30L * 24L * 60L * 60L * 1_000L,
            lastVerifiedAt = NOW - 60_001L,
            purchaseTokenHash = "active-hash",
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
        )
        val pendingCandidate = stalePremium.copy(
            hasPremium = false,
            status = BackendSubscriptionStatus.PendingPurchase,
            lastVerifiedAt = NOW,
            purchaseTokenHash = "pending-hash",
            acknowledgementState = BackendAcknowledgementState.NotRequired,
        )

        val reconciled = selectReconciledEntitlementRecord(
            existing = stalePremium,
            candidate = pendingCandidate,
            now = NOW,
            maxStaleMillis = 60_000L,
        )

        assertFalse(reconciled.hasPremium)
        assertEquals("pending-hash", reconciled.purchaseTokenHash)
    }

    @Test
    fun validPremiumWithinTheStaleHorizonBeatsANewerUnrelatedTransientState() {
        val premium = EntitlementRecord(
            googleSub = "sub-a",
            hasPremium = true,
            status = BackendSubscriptionStatus.Active,
            expiryTime = NOW + 30L * 24L * 60L * 60L * 1_000L,
            lastVerifiedAt = NOW - 1L,
            purchaseTokenHash = "active-hash",
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
        )
        val newerPending = premium.copy(
            hasPremium = false,
            status = BackendSubscriptionStatus.PendingPurchase,
            lastVerifiedAt = NOW,
            purchaseTokenHash = "pending-hash",
            acknowledgementState = BackendAcknowledgementState.NotRequired,
        )

        val reconciled = selectReconciledEntitlementRecord(
            existing = newerPending,
            candidate = premium,
            now = NOW,
            maxStaleMillis = 60_000L,
        )

        assertEquals(premium, reconciled)
    }

    @Test
    fun transientStateCannotDisplaceTerminalHistoryAndEnableOlderPremiumToReturn() {
        val terminal = EntitlementRecord(
            googleSub = "sub-a",
            hasPremium = false,
            status = BackendSubscriptionStatus.Revoked,
            expiryTime = NOW - 1L,
            lastVerifiedAt = NOW,
            purchaseTokenHash = "revoked-hash",
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
        )
        val newerPending = terminal.copy(
            status = BackendSubscriptionStatus.PendingPurchase,
            lastVerifiedAt = NOW + 10L,
            purchaseTokenHash = "pending-hash",
            acknowledgementState = BackendAcknowledgementState.NotRequired,
        )
        val olderPremium = terminal.copy(
            hasPremium = true,
            status = BackendSubscriptionStatus.Active,
            expiryTime = NOW + 30L * 24L * 60L * 60L * 1_000L,
            lastVerifiedAt = NOW - 10L,
            purchaseTokenHash = "active-hash",
        )

        val afterPending = selectReconciledEntitlementRecord(terminal, newerPending, NOW)
        val afterDelayedPremium = selectReconciledEntitlementRecord(afterPending, olderPremium, NOW)

        assertEquals(terminal, afterPending)
        assertEquals(terminal, afterDelayedPremium)
    }

    @Test
    fun olderTerminalObservationReplacesNewerTransientAndBlocksEvenOlderPremium() {
        val newerPending = EntitlementRecord(
            googleSub = "sub-a",
            hasPremium = false,
            status = BackendSubscriptionStatus.PendingPurchase,
            lastVerifiedAt = NOW + 10L,
            purchaseTokenHash = "pending-hash",
            acknowledgementState = BackendAcknowledgementState.NotRequired,
        )
        val terminal = newerPending.copy(
            status = BackendSubscriptionStatus.Revoked,
            expiryTime = NOW - 1L,
            lastVerifiedAt = NOW,
            purchaseTokenHash = "revoked-hash",
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
        )
        val olderPremium = terminal.copy(
            hasPremium = true,
            status = BackendSubscriptionStatus.Active,
            expiryTime = NOW + 30L * 24L * 60L * 60L * 1_000L,
            lastVerifiedAt = NOW - 10L,
            purchaseTokenHash = "active-hash",
        )

        val afterTerminal = selectReconciledEntitlementRecord(newerPending, terminal, NOW)
        val afterDelayedPremium = selectReconciledEntitlementRecord(afterTerminal, olderPremium, NOW)

        assertEquals(terminal, afterTerminal)
        assertEquals(terminal, afterDelayedPremium)
    }

    @Test
    fun olderAcknowledgementCompletionUpgradesStateWithoutReplacingNewerPlayFields() = runBlocking {
        val repository = InMemoryEntitlementRepository()
        val newerPending = subscription(owner = "sub-a").copy(
            offerId = null,
            acknowledgementAttemptCount = 2,
            nextAcknowledgementAttemptAt = NOW + 900_000L,
            lastVerifiedAt = NOW + 10L,
        )
        repository.upsertSubscriptionForOwner(newerPending)

        repository.upsertSubscriptionForOwner(
            newerPending.copy(
                offerId = "stale-offer",
                acknowledgementState = BackendAcknowledgementState.Acknowledged,
                lastVerifiedAt = NOW,
            ),
        )

        val stored = requireNotNull(repository.getSubscription("hash"))
        assertEquals(BackendAcknowledgementState.Acknowledged, stored.acknowledgementState)
        assertEquals(0, stored.acknowledgementAttemptCount)
        assertNull(stored.nextAcknowledgementAttemptAt)
        assertNull(stored.offerId)
        assertEquals(NOW + 10L, stored.lastVerifiedAt)
    }

    @Test
    fun entitlementRecordsAreMonotonicAndReadableByOwner() = runBlocking {
        val repository = InMemoryEntitlementRepository()
        val newer = EntitlementRecord(
            googleSub = "sub-a",
            hasPremium = false,
            status = BackendSubscriptionStatus.Revoked,
            lastVerifiedAt = NOW,
        )
        val older = newer.copy(
            hasPremium = true,
            status = BackendSubscriptionStatus.Active,
            lastVerifiedAt = NOW - 1,
        )

        repository.upsertEntitlement(newer)
        repository.upsertEntitlement(older)

        assertEquals(newer, repository.getEntitlement("sub-a"))
        assertNull(repository.getEntitlement("sub-b"))
    }

    private fun subscription(owner: String): SubscriptionRecord {
        return SubscriptionRecord(
            purchaseTokenHash = "hash",
            hashVersion = "hmac-sha256-v1",
            pepperVersion = "1",
            ownerGoogleSub = owner,
            packageName = "com.brianyeh.justnotes",
            productId = "just_notes_premium",
            basePlanId = "monthly",
            offerId = "trial10d",
            linkedPurchaseTokenHash = "linked-hash",
            tokenCiphertext = "ciphertext",
            keyVersion = "key-version-1",
            encryptedAt = NOW,
            encryptionAlgorithm = "GOOGLE_SYMMETRIC_ENCRYPTION",
            acknowledgementState = BackendAcknowledgementState.Pending,
            acknowledgementAttemptCount = 0,
            nextAcknowledgementAttemptAt = null,
            lastAcknowledgementErrorCode = null,
            lastVerifiedAt = NOW,
            status = BackendSubscriptionStatus.Active,
            expiryTime = NOW + 30L * 24L * 60L * 60L * 1_000L,
        )
    }

    private companion object {
        const val NOW = 1_762_000_000_000L
    }
}
