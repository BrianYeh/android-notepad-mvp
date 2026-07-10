package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.entitlement.InMemoryEntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.SubscriptionRecord
import com.brianyeh.justnotes.backend.entitlement.SubscriptionWriteResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EntitlementRepositoryTest {
    @Test
    fun subscriptionOwnershipIsUniqueAndSameOwnerUpdatesAreIdempotent() = runBlocking {
        val repository = InMemoryEntitlementRepository()
        val original = subscription(owner = "sub-a")
        val acknowledged = original.copy(
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
            acknowledgementAttemptCount = 1,
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
        )
    }

    private companion object {
        const val NOW = 1_762_000_000_000L
    }
}
