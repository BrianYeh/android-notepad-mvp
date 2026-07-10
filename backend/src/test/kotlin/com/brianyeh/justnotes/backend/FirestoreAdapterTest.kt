package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.entitlement.FirestoreEntitlementDocumentStore
import com.brianyeh.justnotes.backend.entitlement.FirestoreEntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.SubscriptionBinding
import com.brianyeh.justnotes.backend.entitlement.TokenBindingResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

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
            expiryTime = NOW + 1_000L,
            lastVerifiedAt = NOW,
            purchaseTokenHash = "token-hash",
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
        )

        repository.upsertEntitlement(record)

        assertEquals(record, repository.getEntitlement("sub-a"))
        assertEquals(null, repository.getEntitlement("sub-b"))
    }

    @Test
    fun subscriptionBindingPersistsCiphertextMetadataAndNeverRawToken() = runBlocking {
        val store = RecordingFirestoreStore()
        val repository = FirestoreEntitlementRepository(store)
        val binding = binding(owner = "sub-a")

        assertEquals(TokenBindingResult.Bound, repository.bindSubscriptionTokenHash(binding))
        assertEquals(
            TokenBindingResult.AlreadyOwnedBySameUser,
            repository.bindSubscriptionTokenHash(binding),
        )
        assertEquals(
            TokenBindingResult.AlreadyOwnedByAnotherUser("sub-a"),
            repository.bindSubscriptionTokenHash(binding(owner = "sub-b")),
        )
        val stored = requireNotNull(store.subscriptionDocuments["token-hash"])
        assertEquals("ciphertext", stored["tokenCiphertext"])
        assertEquals("hmac-sha256-v1", stored["hashVersion"])
        assertEquals("1", stored["pepperVersion"])
        assertEquals("key-version", stored["keyVersion"])
        assertEquals("GOOGLE_SYMMETRIC_ENCRYPTION", stored["encryptionAlgorithm"])
        assertFalse(stored.containsKey("purchaseToken"))
    }

    @Test
    fun olderEntitlementVerificationCannotOverwriteNewerState() = runBlocking {
        val store = RecordingFirestoreStore()
        val repository = FirestoreEntitlementRepository(store)
        val newer = EntitlementRecord(
            googleSub = "sub-a",
            hasPremium = false,
            status = BackendSubscriptionStatus.Revoked,
            lastVerifiedAt = NOW,
        )
        val older = newer.copy(
            hasPremium = true,
            status = BackendSubscriptionStatus.Active,
            lastVerifiedAt = NOW - 1L,
        )

        repository.upsertEntitlement(newer)
        repository.upsertEntitlement(older)

        assertEquals(newer, repository.getEntitlement("sub-a"))
    }

    @Test
    fun mismatchedEntitlementOwnerFailsClosed() = runBlocking {
        val store = RecordingFirestoreStore().apply {
            entitlementDocuments["sub-a"] = mapOf(
                "googleSub" to "sub-b",
                "hasPremium" to true,
                "status" to BackendSubscriptionStatus.Active.name,
            )
        }

        assertFailsWith<IllegalStateException> {
            FirestoreEntitlementRepository(store).getEntitlement("sub-a")
        }
        Unit
    }

    @Test
    fun invalidFirestoreDocumentIdsFailBeforeStoreAccess() = runBlocking {
        val repository = FirestoreEntitlementRepository(RecordingFirestoreStore())

        assertFailsWith<IllegalArgumentException> { repository.getEntitlement("sub/escape") }
        assertFailsWith<IllegalArgumentException> {
            repository.bindSubscriptionTokenHash(binding(owner = "sub-a").copy(purchaseTokenHash = "hash/escape"))
        }
        Unit
    }

    private fun binding(owner: String): SubscriptionBinding {
        return SubscriptionBinding(
            purchaseTokenHash = "token-hash",
            hashVersion = "hmac-sha256-v1",
            pepperVersion = "1",
            ownerGoogleSub = owner,
            packageName = "com.brianyeh.justnotes",
            productId = "just_notes_premium",
            basePlanId = "monthly",
            tokenCiphertext = "ciphertext",
            keyVersion = "key-version",
            encryptedAt = NOW,
            encryptionAlgorithm = "GOOGLE_SYMMETRIC_ENCRYPTION",
        )
    }

    private class RecordingFirestoreStore : FirestoreEntitlementDocumentStore {
        val entitlementDocuments = mutableMapOf<String, Map<String, Any?>>()
        val subscriptionDocuments = mutableMapOf<String, Map<String, Any?>>()

        override suspend fun getEntitlementDocument(documentId: String): Map<String, Any?>? {
            return entitlementDocuments[documentId]
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

        override suspend fun bindSubscriptionDocument(
            documentId: String,
            ownerGoogleSub: String,
            fields: Map<String, Any?>,
        ): TokenBindingResult {
            val existing = subscriptionDocuments[documentId]
            val existingOwner = existing?.get("ownerGoogleSub") as? String
            return when {
                existing == null -> {
                    subscriptionDocuments[documentId] = fields
                    TokenBindingResult.Bound
                }
                existingOwner == ownerGoogleSub -> TokenBindingResult.AlreadyOwnedBySameUser
                else -> TokenBindingResult.AlreadyOwnedByAnotherUser(existingOwner.orEmpty())
            }
        }
    }

    private companion object {
        const val NOW = 1_762_000_000_000L
    }
}
