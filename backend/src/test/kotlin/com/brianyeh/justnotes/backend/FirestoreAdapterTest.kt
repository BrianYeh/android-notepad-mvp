package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.entitlement.FirestoreEntitlementDocumentStore
import com.brianyeh.justnotes.backend.entitlement.FirestoreEntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.SubscriptionRecord
import com.brianyeh.justnotes.backend.entitlement.SubscriptionWriteResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FirestoreAdapterTest {
    @Test
    fun entitlementRoundTripsThroughFirestoreDocumentShape() = runBlocking {
        val store = RecordingFirestoreStore()
        val repository = FirestoreEntitlementRepository(store)
        val record = EntitlementRecord(
            googleSub = "sub-a",
            hasPremium = true,
            status = BackendSubscriptionStatus.Active,
            packageName = "com.brianyeh.justnotes",
            productId = "just_notes_premium",
            basePlanId = "monthly",
            offerId = "trial10d",
            expiryTime = NOW + 1_000L,
            lastVerifiedAt = NOW,
            purchaseTokenHash = "token-hash",
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
        )

        repository.upsertEntitlement(record)

        assertEquals(record, repository.getEntitlement("sub-a"))
        assertNull(repository.getEntitlement("sub-b"))
    }

    @Test
    fun subscriptionRoundTripsEveryEncryptedAndRetryFieldWithoutRawSecrets() = runBlocking {
        val store = RecordingFirestoreStore()
        val repository = FirestoreEntitlementRepository(store)
        val record = subscription(owner = "sub-a")

        assertEquals(SubscriptionWriteResult.Created, repository.upsertSubscriptionForOwner(record))
        assertEquals(record, repository.getSubscription("token-hash"))

        val stored = requireNotNull(store.subscriptionDocuments["token-hash"])
        assertEquals("ciphertext", stored["tokenCiphertext"])
        assertEquals("hmac-sha256-v1", stored["hashVersion"])
        assertEquals("1", stored["pepperVersion"])
        assertEquals("key-version", stored["keyVersion"])
        assertEquals(NOW, stored["encryptedAt"])
        assertEquals("GOOGLE_SYMMETRIC_ENCRYPTION", stored["encryptionAlgorithm"])
        assertEquals("trial10d", stored["offerId"])
        assertEquals("linked-token-hash", stored["linkedPurchaseTokenHash"])
        assertEquals(BackendAcknowledgementState.Pending.name, stored["acknowledgementState"])
        assertEquals(1, stored["acknowledgementAttemptCount"])
        assertEquals(NOW + 900_000L, stored["nextAcknowledgementAttemptAt"])
        assertEquals("PLAY_ACK_UNAVAILABLE", stored["lastAcknowledgementErrorCode"])
        assertEquals(NOW, stored["lastVerifiedAt"])
        setOf("purchaseToken", "rawPurchaseToken", "idToken", "email").forEach { forbidden ->
            assertFalse(stored.containsKey(forbidden), "Firestore must not contain $forbidden")
        }
    }

    @Test
    fun sameOwnerUpdatesButDifferentOwnerCannotWriteOrReadOwnerFromResult() = runBlocking {
        val store = RecordingFirestoreStore()
        val repository = FirestoreEntitlementRepository(store)
        val original = subscription(owner = "sub-a")
        val updated = original.copy(
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
            acknowledgementAttemptCount = 2,
            nextAcknowledgementAttemptAt = null,
            lastAcknowledgementErrorCode = null,
            lastVerifiedAt = NOW + 1,
        )

        assertEquals(SubscriptionWriteResult.Created, repository.upsertSubscriptionForOwner(original))
        assertEquals(
            SubscriptionWriteResult.UpdatedForSameOwner,
            repository.upsertSubscriptionForOwner(updated),
        )
        assertEquals(updated, repository.getSubscription("token-hash"))
        assertEquals(
            SubscriptionWriteResult.OwnedByAnotherUser,
            repository.upsertSubscriptionForOwner(updated.copy(ownerGoogleSub = "sub-b", lastVerifiedAt = NOW + 2)),
        )
        assertEquals(updated, repository.getSubscription("token-hash"))
    }

    @Test
    fun olderEntitlementAndSubscriptionCannotOverwriteNewerState() = runBlocking {
        val store = RecordingFirestoreStore()
        val repository = FirestoreEntitlementRepository(store)
        val newerEntitlement = EntitlementRecord(
            googleSub = "sub-a",
            hasPremium = false,
            status = BackendSubscriptionStatus.Revoked,
            lastVerifiedAt = NOW,
        )
        val newerSubscription = subscription(owner = "sub-a").copy(
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
            lastVerifiedAt = NOW,
        )

        repository.upsertEntitlement(newerEntitlement)
        repository.upsertEntitlement(
            newerEntitlement.copy(
                hasPremium = true,
                status = BackendSubscriptionStatus.Active,
                lastVerifiedAt = NOW - 1,
            ),
        )
        repository.upsertSubscriptionForOwner(newerSubscription)
        repository.upsertSubscriptionForOwner(
            newerSubscription.copy(
                acknowledgementState = BackendAcknowledgementState.Pending,
                lastVerifiedAt = NOW - 1,
            ),
        )

        assertEquals(newerEntitlement, repository.getEntitlement("sub-a"))
        assertEquals(newerSubscription, repository.getSubscription("token-hash"))
    }

    @Test
    fun mismatchedDocumentIdentityFailsClosed() = runBlocking {
        val entitlementStore = RecordingFirestoreStore().apply {
            entitlementDocuments["sub-a"] = mapOf(
                "googleSub" to "sub-b",
                "hasPremium" to true,
                "status" to BackendSubscriptionStatus.Active.name,
            )
        }
        val subscriptionStore = RecordingFirestoreStore().apply {
            subscriptionDocuments["token-hash"] = subscription("sub-a")
                .toExpectedDocumentFields()
                .toMutableMap()
                .apply { put("purchaseTokenHash", "different-hash") }
        }

        assertFailsWith<IllegalStateException> {
            FirestoreEntitlementRepository(entitlementStore).getEntitlement("sub-a")
        }
        assertFailsWith<IllegalStateException> {
            FirestoreEntitlementRepository(subscriptionStore).getSubscription("token-hash")
        }
        Unit
    }

    @Test
    fun invalidFirestoreDocumentIdsFailBeforeStoreAccess() = runBlocking {
        val repository = FirestoreEntitlementRepository(RecordingFirestoreStore())

        assertFailsWith<IllegalArgumentException> { repository.getEntitlement("sub/escape") }
        assertFailsWith<IllegalArgumentException> { repository.getSubscription("hash/escape") }
        assertFailsWith<IllegalArgumentException> {
            repository.upsertSubscriptionForOwner(
                subscription(owner = "sub-a").copy(purchaseTokenHash = "hash/escape"),
            )
        }
        Unit
    }

    private fun subscription(owner: String): SubscriptionRecord {
        return SubscriptionRecord(
            purchaseTokenHash = "token-hash",
            hashVersion = "hmac-sha256-v1",
            pepperVersion = "1",
            ownerGoogleSub = owner,
            packageName = "com.brianyeh.justnotes",
            productId = "just_notes_premium",
            basePlanId = "monthly",
            offerId = "trial10d",
            linkedPurchaseTokenHash = "linked-token-hash",
            tokenCiphertext = "ciphertext",
            keyVersion = "key-version",
            encryptedAt = NOW,
            encryptionAlgorithm = "GOOGLE_SYMMETRIC_ENCRYPTION",
            acknowledgementState = BackendAcknowledgementState.Pending,
            acknowledgementAttemptCount = 1,
            nextAcknowledgementAttemptAt = NOW + 900_000L,
            lastAcknowledgementErrorCode = "PLAY_ACK_UNAVAILABLE",
            lastVerifiedAt = NOW,
        )
    }

    private fun SubscriptionRecord.toExpectedDocumentFields(): Map<String, Any?> {
        return mapOf(
            "purchaseTokenHash" to purchaseTokenHash,
            "hashVersion" to hashVersion,
            "pepperVersion" to pepperVersion,
            "ownerGoogleSub" to ownerGoogleSub,
            "packageName" to packageName,
            "productId" to productId,
            "basePlanId" to basePlanId,
            "offerId" to offerId,
            "linkedPurchaseTokenHash" to linkedPurchaseTokenHash,
            "tokenCiphertext" to tokenCiphertext,
            "keyVersion" to keyVersion,
            "encryptedAt" to encryptedAt,
            "encryptionAlgorithm" to encryptionAlgorithm,
            "acknowledgementState" to acknowledgementState.name,
            "acknowledgementAttemptCount" to acknowledgementAttemptCount,
            "nextAcknowledgementAttemptAt" to nextAcknowledgementAttemptAt,
            "lastAcknowledgementErrorCode" to lastAcknowledgementErrorCode,
            "lastVerifiedAt" to lastVerifiedAt,
        )
    }

    private class RecordingFirestoreStore : FirestoreEntitlementDocumentStore {
        val entitlementDocuments = mutableMapOf<String, Map<String, Any?>>()
        val subscriptionDocuments = mutableMapOf<String, Map<String, Any?>>()

        override suspend fun getEntitlementDocument(documentId: String): Map<String, Any?>? {
            return entitlementDocuments[documentId]
        }

        override suspend fun getSubscriptionDocument(documentId: String): Map<String, Any?>? {
            return subscriptionDocuments[documentId]
        }

        override suspend fun upsertEntitlementDocumentIfNotOlder(
            documentId: String,
            lastVerifiedAt: Long?,
            fields: Map<String, Any?>,
        ) {
            val existingLastVerifiedAt = entitlementDocuments[documentId]?.get("lastVerifiedAt") as? Number
            if (
                existingLastVerifiedAt == null ||
                (lastVerifiedAt != null && lastVerifiedAt >= existingLastVerifiedAt.toLong())
            ) {
                entitlementDocuments[documentId] = fields
            }
        }

        override suspend fun upsertSubscriptionDocumentForOwner(
            documentId: String,
            ownerGoogleSub: String,
            lastVerifiedAt: Long,
            fields: Map<String, Any?>,
        ): SubscriptionWriteResult {
            val existing = subscriptionDocuments[documentId]
            val existingOwner = existing?.get("ownerGoogleSub") as? String
            return when {
                existing == null -> {
                    subscriptionDocuments[documentId] = fields
                    SubscriptionWriteResult.Created
                }
                existingOwner != ownerGoogleSub -> SubscriptionWriteResult.OwnedByAnotherUser
                else -> {
                    val existingLastVerifiedAt = (existing["lastVerifiedAt"] as? Number)?.toLong()
                    if (existingLastVerifiedAt == null || lastVerifiedAt >= existingLastVerifiedAt) {
                        subscriptionDocuments[documentId] = fields
                    }
                    SubscriptionWriteResult.UpdatedForSameOwner
                }
            }
        }
    }

    private companion object {
        const val NOW = 1_762_000_000_000L
    }
}
