package com.brianyeh.justnotes.backend.account

import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.google.cloud.firestore.Firestore
import java.security.MessageDigest
import java.util.Base64
import java.util.Date
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
    suspend fun deleteAccountDocumentsAtomically(
        entitlementDocumentId: String,
        guardDocumentId: String,
        guardExpiresAt: Long,
        ownerGoogleSub: String,
        subscriptionLimit: Int,
        deletionDecision: (
            entitlement: Map<String, Any?>?,
            subscriptions: List<OwnedSubscriptionDocument>,
        ) -> AccountDeletionResult,
    ): AccountDeletionResult
}

class FirestoreAccountDeletionRepository(
    private val store: AccountDeletionDocumentStore,
) : AccountDeletionRepository {
    override suspend fun deleteAccountData(googleSub: String, now: Long): AccountDeletionResult {
        return try {
            val documentId = safeDocumentId(googleSub)
            val guardDocumentId = accountDeletionGuardDocumentId(googleSub)
            val guardExpiresAt = Math.addExact(now, ACCOUNT_DELETION_GUARD_DURATION_MS)
            store.deleteAccountDocumentsAtomically(
                entitlementDocumentId = documentId,
                guardDocumentId = guardDocumentId,
                guardExpiresAt = guardExpiresAt,
                ownerGoogleSub = googleSub,
                subscriptionLimit = QUERY_LIMIT,
            ) { entitlement, subscriptions ->
                deletionDecision(googleSub, entitlement, subscriptions)
            }
        } catch (_: Exception) {
            AccountDeletionResult.FailedClosed
        }
    }

    private fun deletionDecision(
        googleSub: String,
        entitlement: Map<String, Any?>?,
        subscriptions: List<OwnedSubscriptionDocument>,
    ): AccountDeletionResult {
        when (entitlementReadiness(entitlement, googleSub)) {
            DeletionReadiness.BLOCKED -> return AccountDeletionResult.BlockedByNonterminalSubscription
            DeletionReadiness.MALFORMED -> return AccountDeletionResult.FailedClosed
            DeletionReadiness.READY -> Unit
        }
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
        return AccountDeletionResult.Deleted
    }

    private fun entitlementReadiness(
        fields: Map<String, Any?>?,
        googleSub: String,
    ): DeletionReadiness {
        if (fields == null) return DeletionReadiness.READY
        if (fields[GOOGLE_SUB_FIELD] != googleSub) return DeletionReadiness.MALFORMED
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
        const val MAX_SUBSCRIPTIONS = 498
        const val QUERY_LIMIT = MAX_SUBSCRIPTIONS + 1
        const val MAX_DOCUMENT_ID_LENGTH = 1_500
        const val HAS_PREMIUM_FIELD = "hasPremium"
        const val STATUS_FIELD = "status"
        const val GOOGLE_SUB_FIELD = "googleSub"
        const val OWNER_GOOGLE_SUB_FIELD = "ownerGoogleSub"
    }
}

class GoogleCloudFirestoreAccountDeletionDocumentStore(
    private val firestore: Firestore,
) : AccountDeletionDocumentStore {
    override suspend fun deleteAccountDocumentsAtomically(
        entitlementDocumentId: String,
        guardDocumentId: String,
        guardExpiresAt: Long,
        ownerGoogleSub: String,
        subscriptionLimit: Int,
        deletionDecision: (
            entitlement: Map<String, Any?>?,
            subscriptions: List<OwnedSubscriptionDocument>,
        ) -> AccountDeletionResult,
    ): AccountDeletionResult {
        return withContext(Dispatchers.IO) {
            val entitlementReference = firestore.collection(ENTITLEMENTS_COLLECTION)
                .document(entitlementDocumentId)
            val guardReference = firestore.collection(ACCOUNT_DELETION_GUARDS_COLLECTION)
                .document(guardDocumentId)
            val subscriptionsQuery = firestore.collection(SUBSCRIPTIONS_COLLECTION)
                .whereEqualTo(OWNER_GOOGLE_SUB_FIELD, ownerGoogleSub)
                .limit(subscriptionLimit)
            firestore.runTransaction { transaction ->
                val entitlement = transaction.get(entitlementReference).get().data
                val subscriptions = transaction.get(subscriptionsQuery).get().documents.map { snapshot ->
                    OwnedSubscriptionDocument(
                        documentId = snapshot.id,
                        fields = snapshot.data.orEmpty(),
                    )
                }
                val decision = deletionDecision(entitlement, subscriptions)
                if (decision == AccountDeletionResult.Deleted) {
                    transaction.set(
                        guardReference,
                        mapOf(
                            ACCOUNT_DELETION_GUARD_EXPIRES_AT_FIELD to guardExpiresAt,
                            ACCOUNT_DELETION_GUARD_TTL_FIELD to Date(guardExpiresAt),
                        ),
                    )
                    transaction.delete(entitlementReference)
                    subscriptions.forEach { subscription ->
                        transaction.delete(
                            firestore.collection(SUBSCRIPTIONS_COLLECTION).document(subscription.documentId),
                        )
                    }
                }
                decision
            }.get()
        }
    }

    private companion object {
        const val ENTITLEMENTS_COLLECTION = "entitlements"
        const val SUBSCRIPTIONS_COLLECTION = "subscriptions"
        const val OWNER_GOOGLE_SUB_FIELD = "ownerGoogleSub"
    }
}

internal const val ACCOUNT_DELETION_GUARDS_COLLECTION = "accountDeletionGuards"
internal const val ACCOUNT_DELETION_GUARD_EXPIRES_AT_FIELD = "expiresAt"
internal const val ACCOUNT_DELETION_GUARD_TTL_FIELD = "expireAt"
internal const val ACCOUNT_DELETION_GUARD_DURATION_MS = 15L * 60L * 1_000L

internal fun accountDeletionGuardDocumentId(googleSub: String): String {
    require(googleSub.isNotBlank()) { "Google subject is blank." }
    val digest = MessageDigest.getInstance("SHA-256").digest(googleSub.toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

internal fun isAccountDeletionGuardActive(fields: Map<String, Any?>?, now: Long): Boolean {
    if (fields == null) return false
    val expiresAt = (fields[ACCOUNT_DELETION_GUARD_EXPIRES_AT_FIELD] as? Number)?.toLong()
        ?: return true
    return expiresAt > now
}
