package com.brianyeh.justnotes.backend.entitlement

import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface FirestoreEntitlementDocumentStore {
    suspend fun getEntitlementDocument(documentId: String): Map<String, Any?>?
    suspend fun upsertEntitlementDocumentIfNotOlder(
        documentId: String,
        lastVerifiedAt: Long?,
        fields: Map<String, Any?>,
    )
    suspend fun bindSubscriptionDocument(
        documentId: String,
        ownerGoogleSub: String,
        fields: Map<String, Any?>,
    ): TokenBindingResult
}

class GoogleCloudFirestoreEntitlementDocumentStore(
    private val firestore: Firestore,
) : FirestoreEntitlementDocumentStore {
    override suspend fun getEntitlementDocument(documentId: String): Map<String, Any?>? {
        return withContext(Dispatchers.IO) {
            val snapshot = firestore.collection(ENTITLEMENTS_COLLECTION).document(documentId).get().get()
            snapshot.data
        }
    }

    override suspend fun upsertEntitlementDocumentIfNotOlder(
        documentId: String,
        lastVerifiedAt: Long?,
        fields: Map<String, Any?>,
    ) {
        withContext(Dispatchers.IO) {
            val reference = firestore.collection(ENTITLEMENTS_COLLECTION).document(documentId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(reference).get()
                val existingLastVerifiedAt = snapshot.getLong(LAST_VERIFIED_AT_FIELD)
                val shouldWrite = !snapshot.exists() ||
                    existingLastVerifiedAt == null ||
                    (lastVerifiedAt != null && lastVerifiedAt >= existingLastVerifiedAt)
                if (shouldWrite) transaction.set(reference, fields)
            }.get()
        }
    }

    override suspend fun bindSubscriptionDocument(
        documentId: String,
        ownerGoogleSub: String,
        fields: Map<String, Any?>,
    ): TokenBindingResult {
        return withContext(Dispatchers.IO) {
            val reference = firestore.collection(SUBSCRIPTIONS_COLLECTION).document(documentId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(reference).get()
                val existingOwner = snapshot.getString(OWNER_GOOGLE_SUB_FIELD)
                when {
                    !snapshot.exists() -> {
                        transaction.create(reference, fields)
                        TokenBindingResult.Bound
                    }
                    existingOwner == ownerGoogleSub -> TokenBindingResult.AlreadyOwnedBySameUser
                    else -> TokenBindingResult.AlreadyOwnedByAnotherUser(existingOwner.orEmpty())
                }
            }.get()
        }
    }

    private companion object {
        const val ENTITLEMENTS_COLLECTION = "entitlements"
        const val SUBSCRIPTIONS_COLLECTION = "subscriptions"
        const val OWNER_GOOGLE_SUB_FIELD = "ownerGoogleSub"
        const val LAST_VERIFIED_AT_FIELD = "lastVerifiedAt"
    }
}

class FirestoreEntitlementRepository(
    private val store: FirestoreEntitlementDocumentStore,
) : EntitlementRepository {
    override suspend fun getEntitlement(googleSub: String): EntitlementRecord? {
        val documentId = safeDocumentId(googleSub, "Google subject")
        return store.getEntitlementDocument(documentId)?.toEntitlementRecord()?.also { record ->
            check(record.googleSub == googleSub) {
                "Firestore entitlement owner does not match its document ID."
            }
        }
    }

    override suspend fun upsertEntitlement(record: EntitlementRecord) {
        val documentId = safeDocumentId(record.googleSub, "Google subject")
        store.upsertEntitlementDocumentIfNotOlder(
            documentId = documentId,
            lastVerifiedAt = record.lastVerifiedAt,
            fields = record.toFirestoreFields(),
        )
    }

    override suspend fun bindSubscriptionTokenHash(binding: SubscriptionBinding): TokenBindingResult {
        val documentId = safeDocumentId(binding.purchaseTokenHash, "Purchase token hash")
        return store.bindSubscriptionDocument(
            documentId = documentId,
            ownerGoogleSub = binding.ownerGoogleSub,
            fields = binding.toFirestoreFields(),
        )
    }

    private fun EntitlementRecord.toFirestoreFields(): Map<String, Any?> {
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

    private fun SubscriptionBinding.toFirestoreFields(): Map<String, Any?> {
        return mapOf(
            "purchaseTokenHash" to purchaseTokenHash,
            "hashVersion" to hashVersion,
            "pepperVersion" to pepperVersion,
            "ownerGoogleSub" to ownerGoogleSub,
            "packageName" to packageName,
            "productId" to productId,
            "basePlanId" to basePlanId,
            "tokenCiphertext" to tokenCiphertext,
            "keyVersion" to keyVersion,
            "encryptedAt" to encryptedAt,
            "encryptionAlgorithm" to encryptionAlgorithm,
        )
    }

    private fun Map<String, Any?>.toEntitlementRecord(): EntitlementRecord {
        return EntitlementRecord(
            googleSub = string("googleSub") ?: error("Firestore entitlement is missing googleSub."),
            hasPremium = this["hasPremium"] as? Boolean ?: false,
            status = enumValueOrDefault(string("status"), BackendSubscriptionStatus.Unknown),
            source = enumValueOrDefault(string("source"), BackendEntitlementSource.None),
            packageName = string("packageName"),
            productId = string("productId"),
            basePlanId = string("basePlanId"),
            offerId = string("offerId"),
            expiryTime = long("expiryTime"),
            lastVerifiedAt = long("lastVerifiedAt"),
            stale = this["stale"] as? Boolean ?: false,
            purchaseTokenHash = string("purchaseTokenHash"),
            acknowledgementState = enumValueOrNull<BackendAcknowledgementState>(string("acknowledgementState")),
        )
    }

    private fun Map<String, Any?>.string(name: String): String? {
        return (this[name] as? String)?.takeIf { it.isNotBlank() }
    }

    private fun Map<String, Any?>.long(name: String): Long? {
        return (this[name] as? Number)?.toLong()
    }

    private companion object {
        val DOCUMENT_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,512}$")

        fun safeDocumentId(value: String, label: String): String {
            require(DOCUMENT_ID_PATTERN.matches(value)) { "$label cannot be used as a Firestore document ID." }
            return value
        }

        inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
            return value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
        }

        inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? {
            return value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
        }
    }
}
