package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.AcknowledgementCompletionResult
import com.brianyeh.justnotes.backend.entitlement.AcknowledgementClaimResult
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.entitlement.InMemoryEntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.SubscriptionRecord
import com.brianyeh.justnotes.backend.entitlement.SubscriptionWriteResult
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
