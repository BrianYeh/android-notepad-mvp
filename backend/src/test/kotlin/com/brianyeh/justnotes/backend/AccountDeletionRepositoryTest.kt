package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.account.AccountDeletionDocumentStore
import com.brianyeh.justnotes.backend.account.AccountDeletionResult
import com.brianyeh.justnotes.backend.account.FirestoreAccountDeletionRepository
import com.brianyeh.justnotes.backend.account.OwnedSubscriptionDocument
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AccountDeletionRepositoryTest {
    @Test
    fun noRecordsIsIdempotentSuccess() = runSuspend {
        val store = FakeDeletionStore()

        val result = FirestoreAccountDeletionRepository(store).deleteAccountData(GOOGLE_SUB, NOW)

        assertEquals(AccountDeletionResult.Deleted, result)
        assertEquals(listOf(GOOGLE_SUB to emptyList()), store.deletions)
    }

    @Test
    fun activePendingPausedOnHoldAndCanceledBeforeExpiryAreBlocked() = runSuspend {
        val blockedStatuses = listOf(
            BackendSubscriptionStatus.Active,
            BackendSubscriptionStatus.PendingPurchase,
            BackendSubscriptionStatus.VerificationPending,
            BackendSubscriptionStatus.Paused,
            BackendSubscriptionStatus.OnHold,
            BackendSubscriptionStatus.CanceledActiveUntilExpiry,
        )

        blockedStatuses.forEach { status ->
            val store = FakeDeletionStore(
                subscriptions = mutableListOf(subscription("token-hash", status)),
            )

            val result = FirestoreAccountDeletionRepository(store).deleteAccountData(GOOGLE_SUB, NOW)

            assertEquals(AccountDeletionResult.BlockedByNonterminalSubscription, result, status.name)
            assertEquals(emptyList(), store.deletions, status.name)
        }
    }

    @Test
    fun expiredAndRevokedSubscriptionsAreDeletedWithEntitlement() = runSuspend {
        val store = FakeDeletionStore(
            entitlement = mapOf(
                "hasPremium" to false,
                "status" to BackendSubscriptionStatus.Expired.name,
                "expiryTime" to NOW - 1L,
            ),
            subscriptions = mutableListOf(
                subscription("expired-token-hash", BackendSubscriptionStatus.Expired),
                subscription("revoked-token-hash", BackendSubscriptionStatus.Revoked),
            ),
        )

        val result = FirestoreAccountDeletionRepository(store).deleteAccountData(GOOGLE_SUB, NOW)

        assertEquals(AccountDeletionResult.Deleted, result)
        assertEquals(
            listOf(GOOGLE_SUB to listOf("expired-token-hash", "revoked-token-hash")),
            store.deletions,
        )
    }

    @Test
    fun unknownOrMalformedFirestoreStateFailsClosed() = runSuspend {
        val malformedEntitlement = FakeDeletionStore(entitlement = mapOf("status" to 7))
        val malformedSubscription = FakeDeletionStore(
            subscriptions = mutableListOf(
                OwnedSubscriptionDocument("token-hash", mapOf("status" to "NOT_A_STATUS")),
            ),
        )

        assertEquals(
            AccountDeletionResult.FailedClosed,
            FirestoreAccountDeletionRepository(malformedEntitlement).deleteAccountData(GOOGLE_SUB, NOW),
        )
        assertEquals(
            AccountDeletionResult.FailedClosed,
            FirestoreAccountDeletionRepository(malformedSubscription).deleteAccountData(GOOGLE_SUB, NOW),
        )
    }

    @Test
    fun moreThan499OwnedSubscriptionsFailsClosed() = runSuspend {
        val store = FakeDeletionStore(
            subscriptions = MutableList(500) { index ->
                subscription("token-hash-$index", BackendSubscriptionStatus.Expired)
            },
        )

        val result = FirestoreAccountDeletionRepository(store).deleteAccountData(GOOGLE_SUB, NOW)

        assertEquals(AccountDeletionResult.FailedClosed, result)
        assertEquals(emptyList(), store.deletions)
    }

    @Test
    fun deletionResultNeverContainsDocumentContentsOrGoogleSubject() = runSuspend {
        val store = FakeDeletionStore(
            subscriptions = mutableListOf(subscription("sensitive-token-hash", BackendSubscriptionStatus.Expired)),
        )

        val result = FirestoreAccountDeletionRepository(store).deleteAccountData(GOOGLE_SUB, NOW)

        assertEquals(AccountDeletionResult.Deleted, result)
        assertFalse(result.toString().contains(GOOGLE_SUB))
        assertFalse(result.toString().contains("sensitive-token-hash"))
    }

    private fun subscription(
        documentId: String,
        status: BackendSubscriptionStatus,
    ): OwnedSubscriptionDocument {
        return OwnedSubscriptionDocument(
            documentId = documentId,
            fields = mapOf(
                "ownerGoogleSub" to GOOGLE_SUB,
                "status" to status.name,
                "expiryTime" to NOW - 1L,
            ),
        )
    }

    private class FakeDeletionStore(
        var entitlement: Map<String, Any?>? = null,
        val subscriptions: MutableList<OwnedSubscriptionDocument> = mutableListOf(),
    ) : AccountDeletionDocumentStore {
        val deletions = mutableListOf<Pair<String, List<String>>>()

        override suspend fun getEntitlementDocument(documentId: String): Map<String, Any?>? = entitlement

        override suspend fun getOwnedSubscriptionDocuments(
            ownerGoogleSub: String,
            limit: Int,
        ): List<OwnedSubscriptionDocument> = subscriptions.take(limit)

        override suspend fun deleteAccountDocuments(
            entitlementDocumentId: String,
            subscriptionDocumentIds: List<String>,
        ) {
            deletions += entitlementDocumentId to subscriptionDocumentIds
        }
    }

    private fun runSuspend(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }

    private companion object {
        const val GOOGLE_SUB = "google-sub"
        const val NOW = 1_762_000_000_000L
    }
}
