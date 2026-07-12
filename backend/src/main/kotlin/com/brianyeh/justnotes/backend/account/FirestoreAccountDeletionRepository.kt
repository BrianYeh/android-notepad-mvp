package com.brianyeh.justnotes.backend.account

import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OwnedSubscriptionDocument(
    val documentId: String,
    val fields: Map<String, Any?>,
) {
    override fun toString(): String =
        "OwnedSubscriptionDocument(documentId=[REDACTED], fields=[REDACTED])"
}

interface AccountDeletionDocumentStore {
    suspend fun getEntitlementDocument(documentId: String): Map<String, Any?>?

    suspend fun getOwnedSubscriptionDocuments(
        ownerGoogleSub: String,
        limit: Int,
    ): List<OwnedSubscriptionDocument>

    suspend fun deleteAccountDocuments(
        entitlementDocumentId: String,
        subscriptionDocumentIds: List<String>,
    )
}

class FirestoreAccountDeletionRepository(
    private val store: AccountDeletionDocumentStore,
) : AccountDeletionRepository {
    override suspend fun deleteAccountData(googleSub: String, now: Long): AccountDeletionResult {
        return try {
            val documentId = safeDocumentId(googleSub)
            when (entitlementReadiness(store.getEntitlementDocument(documentId))) {
                DeletionReadiness.BLOCKED -> return AccountDeletionResult.BlockedByNonterminalSubscription
                DeletionReadiness.MALFORMED -> return AccountDeletionResult.FailedClosed
                DeletionReadiness.READY -> Unit
            }

            val subscriptions = store.getOwnedSubscriptionDocuments(
                ownerGoogleSub = googleSub,
                limit = QUERY_LIMIT,
            )
            if (subscriptions.size > MAX_SUBSCRIPTIONS) return AccountDeletionResult.FailedClosed
            if (subscriptions.map { it.documentId }.toSet().size != subscriptions.size) {
                return AccountDeletionResult.FailedClosed
            }
            for (subscription in subscriptions) {
                safeDocumentId(subscription.documentId)
                if (subscription.fields[OWNER_GOOGLE_SUB_FIELD] != googleSub) {
                    return AccountDeletionResult.FailedClosed
                }
                when (subscriptionReadiness(subscription.fields)) {
                    DeletionReadiness.BLOCKED ->
                        return AccountDeletionResult.BlockedByNonterminalSubscription
                    DeletionReadiness.MALFORMED -> return AccountDeletionResult.FailedClosed
                    DeletionReadiness.READY -> Unit
                }
            }

            store.deleteAccountDocuments(
                entitlementDocumentId = documentId,
                subscriptionDocumentIds = subscriptions.map { it.documentId },
            )
            AccountDeletionResult.Deleted
        } catch (_: Exception) {
            AccountDeletionResult.FailedClosed
        }
    }

    private fun entitlementReadiness(fields: Map<String, Any?>?): DeletionReadiness {
        if (fields == null) return DeletionReadiness.READY
        val hasPremium = fields[HAS_PREMIUM_FIELD] as? Boolean ?: return DeletionReadiness.MALFORMED
        val status = fields.subscriptionStatus() ?: return DeletionReadiness.MALFORMED
        if (hasPremium) return DeletionReadiness.BLOCKED
        return when (status) {
            BackendSubscriptionStatus.Free,
            BackendSubscriptionStatus.Unknown,
            BackendSubscriptionStatus.Expired,
            BackendSubscriptionStatus.Revoked,
            -> DeletionReadiness.READY
            else -> DeletionReadiness.BLOCKED
        }
    }

    private fun subscriptionReadiness(fields: Map<String, Any?>): DeletionReadiness {
        val status = fields.subscriptionStatus() ?: return DeletionReadiness.MALFORMED
        return if (
            status == BackendSubscriptionStatus.Expired ||
            status == BackendSubscriptionStatus.Revoked
        ) {
            DeletionReadiness.READY
        } else {
            DeletionReadiness.BLOCKED
        }
    }

    private fun Map<String, Any?>.subscriptionStatus(): BackendSubscriptionStatus? {
        val statusName = this[STATUS_FIELD] as? String ?: return null
        return BackendSubscriptionStatus.entries.firstOrNull { it.name == statusName }
    }

    private fun safeDocumentId(value: String): String {
        require(value.isNotBlank()) { "Firestore document ID is blank." }
        require(value.length <= MAX_DOCUMENT_ID_LENGTH) { "Firestore document ID is too long." }
        require('/' !in value && value != "." && value != "..") { "Firestore document ID is unsafe." }
        return value
    }

    private enum class DeletionReadiness {
        READY,
        BLOCKED,
        MALFORMED,
    }

    private companion object {
        const val MAX_SUBSCRIPTIONS = 499
        const val QUERY_LIMIT = MAX_SUBSCRIPTIONS + 1
        const val MAX_DOCUMENT_ID_LENGTH = 1_500
        const val HAS_PREMIUM_FIELD = "hasPremium"
        const val STATUS_FIELD = "status"
        const val OWNER_GOOGLE_SUB_FIELD = "ownerGoogleSub"
    }
}

class GoogleCloudFirestoreAccountDeletionDocumentStore(
    private val firestore: Firestore,
) : AccountDeletionDocumentStore {
    override suspend fun getEntitlementDocument(documentId: String): Map<String, Any?>? {
        return withContext(Dispatchers.IO) {
            firestore.collection(ENTITLEMENTS_COLLECTION).document(documentId).get().get().data
        }
    }

    override suspend fun getOwnedSubscriptionDocuments(
        ownerGoogleSub: String,
        limit: Int,
    ): List<OwnedSubscriptionDocument> {
        return withContext(Dispatchers.IO) {
            firestore.collection(SUBSCRIPTIONS_COLLECTION)
                .whereEqualTo(OWNER_GOOGLE_SUB_FIELD, ownerGoogleSub)
                .limit(limit)
                .get()
                .get()
                .documents
                .map { snapshot ->
                    OwnedSubscriptionDocument(
                        documentId = snapshot.id,
                        fields = snapshot.data.orEmpty(),
                    )
                }
        }
    }

    override suspend fun deleteAccountDocuments(
        entitlementDocumentId: String,
        subscriptionDocumentIds: List<String>,
    ) {
        withContext(Dispatchers.IO) {
            val batch = firestore.batch()
            batch.delete(firestore.collection(ENTITLEMENTS_COLLECTION).document(entitlementDocumentId))
            subscriptionDocumentIds.forEach { documentId ->
                batch.delete(firestore.collection(SUBSCRIPTIONS_COLLECTION).document(documentId))
            }
            batch.commit().get()
        }
    }

    private companion object {
        const val ENTITLEMENTS_COLLECTION = "entitlements"
        const val SUBSCRIPTIONS_COLLECTION = "subscriptions"
        const val OWNER_GOOGLE_SUB_FIELD = "ownerGoogleSub"
    }
}
