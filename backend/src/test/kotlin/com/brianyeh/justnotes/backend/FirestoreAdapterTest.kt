package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.entitlement.AcknowledgementClaimResult
import com.brianyeh.justnotes.backend.entitlement.AcknowledgementCompletionResult
import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.entitlement.EntitlementReconciliationResult
import com.brianyeh.justnotes.backend.entitlement.FirestoreEntitlementDocumentStore
import com.brianyeh.justnotes.backend.entitlement.FirestoreEntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.SubscriptionRecord
import com.brianyeh.justnotes.backend.entitlement.SubscriptionWriteResult
import com.brianyeh.justnotes.backend.entitlement.reconciledEntitlement
import com.brianyeh.justnotes.backend.entitlement.selectEffectiveEntitlement
import com.brianyeh.justnotes.backend.entitlement.selectReconciledEntitlementRecord
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals(BackendSubscriptionStatus.Active.name, stored["status"])
        assertEquals(record.expiryTime, stored["expiryTime"])
        assertEquals(0L, stored["acknowledgementClaimGeneration"])
        assertNull(stored["acknowledgementLeaseUntil"])
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
            acknowledgementAttemptCount = 0,
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
            acknowledgementAttemptCount = 0,
            nextAcknowledgementAttemptAt = null,
            lastAcknowledgementErrorCode = null,
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
    fun acknowledgementClaimUsesLeaseAndAcknowledgedStateCannotBeDowngraded() = runBlocking {
        val store = RecordingFirestoreStore()
        val repository = FirestoreEntitlementRepository(store)
        val pending = subscription(owner = "sub-a").copy(
            nextAcknowledgementAttemptAt = null,
            lastAcknowledgementErrorCode = null,
        )
        repository.upsertSubscriptionForOwner(pending)

        val claim = repository.claimSubscriptionAcknowledgement(
            "token-hash",
            "sub-a",
            NOW,
            NOW + 60_000L,
        ) as AcknowledgementClaimResult.Claimed
        assertTrue(
            repository.claimSubscriptionAcknowledgement("token-hash", "sub-a", NOW, NOW + 60_000L) is
                AcknowledgementClaimResult.NotDue,
        )

        val completion = repository.completeSubscriptionAcknowledgement(
            purchaseTokenHash = "token-hash",
            ownerGoogleSub = "sub-a",
            generation = claim.generation,
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
            acknowledgementAttemptCount = 0,
            nextAcknowledgementAttemptAt = null,
            lastAcknowledgementErrorCode = null,
        )
        assertTrue(completion is AcknowledgementCompletionResult.Applied)
        repository.upsertSubscriptionForOwner(
            pending.copy(
                acknowledgementAttemptCount = 3,
                nextAcknowledgementAttemptAt = NOW + 3_600_000L,
                lastVerifiedAt = NOW + 2L,
            ),
        )

        val stored = requireNotNull(repository.getSubscription("token-hash"))
        assertEquals(BackendAcknowledgementState.Acknowledged, stored.acknowledgementState)
        assertEquals(0, stored.acknowledgementAttemptCount)
        assertNull(stored.nextAcknowledgementAttemptAt)
        assertNull(stored.acknowledgementLeaseUntil)

        val reconciled = repository.reconcileEntitlementFromSubscription("token-hash", "sub-a", NOW)
            as EntitlementReconciliationResult.Success
        assertTrue(reconciled.entitlement.hasPremium)
        assertEquals(BackendSubscriptionStatus.Active, reconciled.entitlement.status)
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
            status = BackendSubscriptionStatus.Active,
            expiryTime = NOW + 30L * 24L * 60L * 60L * 1_000L,
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
            "status" to status.name,
            "expiryTime" to expiryTime,
            "acknowledgementClaimGeneration" to acknowledgementClaimGeneration,
            "acknowledgementLeaseUntil" to acknowledgementLeaseUntil,
        )
    }

    private inner class RecordingFirestoreStore : FirestoreEntitlementDocumentStore {
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
        ): Map<String, Any?> {
            val effective = selectEffectiveEntitlement(
                entitlementDocuments[documentId]?.toEntitlementRecord(),
                fields.toEntitlementRecord(),
            )
            return effective.toDocumentFields().also { entitlementDocuments[documentId] = it }
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
                    val merged = mergeSubscription(existing.toSubscriptionRecord(), fields.toSubscriptionRecord())
                    subscriptionDocuments[documentId] = merged.toExpectedDocumentFields()
                    SubscriptionWriteResult.UpdatedForSameOwner
                }
            }
        }

        override suspend fun claimSubscriptionAcknowledgement(
            documentId: String,
            ownerGoogleSub: String,
            now: Long,
            leaseUntil: Long,
        ): AcknowledgementClaimResult {
            val existing = subscriptionDocuments[documentId]
                ?: return AcknowledgementClaimResult.Missing
            if (existing["ownerGoogleSub"] != ownerGoogleSub) {
                return AcknowledgementClaimResult.OwnedByAnotherUser
            }
            val record = existing.toSubscriptionRecord()
            return when {
                record.acknowledgementState == BackendAcknowledgementState.Acknowledged ->
                    AcknowledgementClaimResult.AlreadyAcknowledged(record)
                record.acknowledgementState == BackendAcknowledgementState.Failed ->
                    AcknowledgementClaimResult.TerminalFailure(record)
                !record.status.isGrantable() || record.expiryTime?.let { it <= now } != false ->
                    AcknowledgementClaimResult.NotEligible(record)
                record.nextAcknowledgementAttemptAt?.let { it > now } == true ||
                    record.acknowledgementLeaseUntil?.let { it > now } == true ->
                    AcknowledgementClaimResult.NotDue(record)
                else -> {
                    val generation = record.acknowledgementClaimGeneration + 1L
                    val claimed = record.copy(
                        acknowledgementClaimGeneration = generation,
                        acknowledgementLeaseUntil = leaseUntil,
                    )
                    subscriptionDocuments[documentId] = claimed.toExpectedDocumentFields()
                    AcknowledgementClaimResult.Claimed(claimed, generation)
                }
            }
        }

        override suspend fun completeSubscriptionAcknowledgement(
            documentId: String,
            ownerGoogleSub: String,
            generation: Long,
            acknowledgementState: BackendAcknowledgementState,
            acknowledgementAttemptCount: Int,
            nextAcknowledgementAttemptAt: Long?,
            lastAcknowledgementErrorCode: String?,
        ): AcknowledgementCompletionResult {
            val existing = subscriptionDocuments[documentId]?.toSubscriptionRecord()
                ?: return AcknowledgementCompletionResult.Missing
            if (existing.ownerGoogleSub != ownerGoogleSub) {
                return AcknowledgementCompletionResult.OwnedByAnotherUser
            }
            if (
                existing.acknowledgementClaimGeneration != generation ||
                existing.acknowledgementLeaseUntil == null
            ) {
                return AcknowledgementCompletionResult.Stale(existing)
            }
            val updated = existing.copy(
                acknowledgementState = acknowledgementState,
                acknowledgementAttemptCount = acknowledgementAttemptCount,
                nextAcknowledgementAttemptAt = nextAcknowledgementAttemptAt,
                lastAcknowledgementErrorCode = lastAcknowledgementErrorCode,
                acknowledgementLeaseUntil = null,
            )
            subscriptionDocuments[documentId] = updated.toExpectedDocumentFields()
            return AcknowledgementCompletionResult.Applied(updated)
        }

        override suspend fun reconcileEntitlementFromSubscription(
            entitlementDocumentId: String,
            subscriptionDocumentId: String,
            ownerGoogleSub: String,
            now: Long,
        ): EntitlementReconciliationResult {
            val subscription = subscriptionDocuments[subscriptionDocumentId]?.toSubscriptionRecord()
                ?: return EntitlementReconciliationResult.Missing
            if (subscription.ownerGoogleSub != ownerGoogleSub) {
                return EntitlementReconciliationResult.OwnedByAnotherUser
            }
            val effective = selectReconciledEntitlementRecord(
                entitlementDocuments[entitlementDocumentId]?.toEntitlementRecord(),
                subscription.reconciledEntitlement(now),
                now,
            )
            entitlementDocuments[entitlementDocumentId] = effective.toDocumentFields()
            return EntitlementReconciliationResult.Success(effective, subscription)
        }

        private fun mergeSubscription(
            existing: SubscriptionRecord,
            incoming: SubscriptionRecord,
        ): SubscriptionRecord {
            val lifecycle = when {
                incoming.lastVerifiedAt > existing.lastVerifiedAt -> incoming
                incoming.lastVerifiedAt < existing.lastVerifiedAt -> existing
                existing.status.isGrantable() && !incoming.status.isGrantable() -> incoming
                !existing.status.isGrantable() && incoming.status.isGrantable() -> existing
                else -> incoming
            }
            val generation = maxOf(
                existing.acknowledgementClaimGeneration,
                incoming.acknowledgementClaimGeneration,
            )
            if (
                existing.acknowledgementState == BackendAcknowledgementState.Acknowledged ||
                incoming.acknowledgementState == BackendAcknowledgementState.Acknowledged
            ) {
                return lifecycle.copy(
                    acknowledgementState = BackendAcknowledgementState.Acknowledged,
                    acknowledgementAttemptCount = 0,
                    nextAcknowledgementAttemptAt = null,
                    lastAcknowledgementErrorCode = null,
                    acknowledgementClaimGeneration = generation,
                    acknowledgementLeaseUntil = null,
                )
            }
            if (existing.acknowledgementState == BackendAcknowledgementState.Failed) {
                return lifecycle.copy(
                    acknowledgementState = BackendAcknowledgementState.Failed,
                    acknowledgementAttemptCount = existing.acknowledgementAttemptCount,
                    nextAcknowledgementAttemptAt = null,
                    lastAcknowledgementErrorCode = existing.lastAcknowledgementErrorCode,
                    acknowledgementClaimGeneration = generation,
                    acknowledgementLeaseUntil = null,
                )
            }
            val acknowledgement = if (
                existing.acknowledgementClaimGeneration > incoming.acknowledgementClaimGeneration ||
                (
                    existing.acknowledgementClaimGeneration == incoming.acknowledgementClaimGeneration &&
                        existing.acknowledgementAttemptCount > incoming.acknowledgementAttemptCount
                    )
            ) {
                existing
            } else {
                incoming
            }
            return lifecycle.copy(
                acknowledgementState = acknowledgement.acknowledgementState,
                acknowledgementAttemptCount = acknowledgement.acknowledgementAttemptCount,
                nextAcknowledgementAttemptAt = acknowledgement.nextAcknowledgementAttemptAt,
                lastAcknowledgementErrorCode = acknowledgement.lastAcknowledgementErrorCode,
                acknowledgementClaimGeneration = generation,
                acknowledgementLeaseUntil = acknowledgement.acknowledgementLeaseUntil,
            )
        }

        private fun Map<String, Any?>.toSubscriptionRecord(): SubscriptionRecord {
            return SubscriptionRecord(
                purchaseTokenHash = getValue("purchaseTokenHash") as String,
                hashVersion = getValue("hashVersion") as String,
                pepperVersion = getValue("pepperVersion") as String,
                ownerGoogleSub = getValue("ownerGoogleSub") as String,
                packageName = getValue("packageName") as String,
                productId = getValue("productId") as String,
                basePlanId = this["basePlanId"] as? String,
                offerId = this["offerId"] as? String,
                linkedPurchaseTokenHash = this["linkedPurchaseTokenHash"] as? String,
                tokenCiphertext = getValue("tokenCiphertext") as String,
                keyVersion = getValue("keyVersion") as String,
                encryptedAt = (getValue("encryptedAt") as Number).toLong(),
                encryptionAlgorithm = getValue("encryptionAlgorithm") as String,
                acknowledgementState = enumValueOf<BackendAcknowledgementState>(
                    getValue("acknowledgementState") as String,
                ),
                acknowledgementAttemptCount = (this["acknowledgementAttemptCount"] as? Number)?.toInt() ?: 0,
                nextAcknowledgementAttemptAt = (this["nextAcknowledgementAttemptAt"] as? Number)?.toLong(),
                lastAcknowledgementErrorCode = this["lastAcknowledgementErrorCode"] as? String,
                lastVerifiedAt = (getValue("lastVerifiedAt") as Number).toLong(),
                status = (this["status"] as? String)?.let {
                    enumValueOf<BackendSubscriptionStatus>(it)
                } ?: BackendSubscriptionStatus.Unknown,
                expiryTime = (this["expiryTime"] as? Number)?.toLong(),
                acknowledgementClaimGeneration =
                    (this["acknowledgementClaimGeneration"] as? Number)?.toLong() ?: 0L,
                acknowledgementLeaseUntil = (this["acknowledgementLeaseUntil"] as? Number)?.toLong(),
            )
        }

        private fun Map<String, Any?>.toEntitlementRecord(): EntitlementRecord {
            return EntitlementRecord(
                googleSub = getValue("googleSub") as String,
                hasPremium = this["hasPremium"] as? Boolean ?: false,
                status = (this["status"] as? String)?.let {
                    enumValueOf<BackendSubscriptionStatus>(it)
                } ?: BackendSubscriptionStatus.Unknown,
                packageName = this["packageName"] as? String,
                productId = this["productId"] as? String,
                basePlanId = this["basePlanId"] as? String,
                offerId = this["offerId"] as? String,
                expiryTime = (this["expiryTime"] as? Number)?.toLong(),
                lastVerifiedAt = (this["lastVerifiedAt"] as? Number)?.toLong(),
                purchaseTokenHash = this["purchaseTokenHash"] as? String,
                acknowledgementState = (this["acknowledgementState"] as? String)?.let {
                    enumValueOf<BackendAcknowledgementState>(it)
                },
            )
        }

        private fun EntitlementRecord.toDocumentFields(): Map<String, Any?> {
            return mapOf(
                "googleSub" to googleSub,
                "hasPremium" to hasPremium,
                "status" to status.name,
                "source" to source.name,
                "packageName" to packageName,
                "productId" to productId,
                "basePlanId" to basePlanId,
                "offerId" to offerId,
                "expiryTime" to expiryTime,
                "lastVerifiedAt" to lastVerifiedAt,
                "stale" to stale,
                "purchaseTokenHash" to purchaseTokenHash,
                "acknowledgementState" to acknowledgementState?.name,
            )
        }

        private fun BackendSubscriptionStatus.isGrantable(): Boolean {
            return this == BackendSubscriptionStatus.Active ||
                this == BackendSubscriptionStatus.GracePeriod ||
                this == BackendSubscriptionStatus.CanceledActiveUntilExpiry
        }
    }

    private companion object {
        const val NOW = 1_762_000_000_000L
    }
}
