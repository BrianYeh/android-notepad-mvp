package com.brianyeh.justnotes.backend.entitlement

import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface FirestoreEntitlementDocumentStore {
    suspend fun getEntitlementDocument(documentId: String): Map<String, Any?>?
    suspend fun getSubscriptionDocument(documentId: String): Map<String, Any?>?
    suspend fun upsertEntitlementDocumentIfNotOlder(
        documentId: String,
        lastVerifiedAt: Long?,
        fields: Map<String, Any?>,
    ): Map<String, Any?>
    suspend fun upsertSubscriptionDocumentForOwner(
        documentId: String,
        ownerGoogleSub: String,
        lastVerifiedAt: Long,
        fields: Map<String, Any?>,
    ): SubscriptionWriteResult
    suspend fun claimSubscriptionAcknowledgement(
        documentId: String,
        ownerGoogleSub: String,
        now: Long,
        leaseUntil: Long,
    ): AcknowledgementClaimResult
    suspend fun completeSubscriptionAcknowledgement(
        documentId: String,
        ownerGoogleSub: String,
        generation: Long,
        acknowledgementState: BackendAcknowledgementState,
        acknowledgementAttemptCount: Int,
        nextAcknowledgementAttemptAt: Long?,
        lastAcknowledgementErrorCode: String?,
    ): AcknowledgementCompletionResult
    suspend fun reconcileEntitlementFromSubscription(
        entitlementDocumentId: String,
        subscriptionDocumentId: String,
        ownerGoogleSub: String,
        now: Long,
    ): EntitlementReconciliationResult
}

class GoogleCloudFirestoreEntitlementDocumentStore(
    private val firestore: Firestore,
) : FirestoreEntitlementDocumentStore {
    override suspend fun getEntitlementDocument(documentId: String): Map<String, Any?>? {
        return withContext(Dispatchers.IO) {
            firestore.collection(ENTITLEMENTS_COLLECTION).document(documentId).get().get().data
        }
    }

    override suspend fun getSubscriptionDocument(documentId: String): Map<String, Any?>? {
        return withContext(Dispatchers.IO) {
            firestore.collection(SUBSCRIPTIONS_COLLECTION).document(documentId).get().get().data
        }
    }

    override suspend fun upsertEntitlementDocumentIfNotOlder(
        documentId: String,
        lastVerifiedAt: Long?,
        fields: Map<String, Any?>,
    ): Map<String, Any?> {
        return withContext(Dispatchers.IO) {
            val reference = firestore.collection(ENTITLEMENTS_COLLECTION).document(documentId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(reference).get()
                val existing = snapshot.data.orEmpty()
                val effective = selectEffectiveEntitlementFields(existing, fields)
                if (!snapshot.exists() || effective != existing) {
                    transaction.set(reference, effective)
                }
                effective
            }.get()
        }
    }

    override suspend fun upsertSubscriptionDocumentForOwner(
        documentId: String,
        ownerGoogleSub: String,
        lastVerifiedAt: Long,
        fields: Map<String, Any?>,
    ): SubscriptionWriteResult {
        return withContext(Dispatchers.IO) {
            val reference = firestore.collection(SUBSCRIPTIONS_COLLECTION).document(documentId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(reference).get()
                val existingOwner = snapshot.getString(OWNER_GOOGLE_SUB_FIELD)
                when {
                    !snapshot.exists() -> {
                        transaction.create(reference, fields)
                        SubscriptionWriteResult.Created
                    }
                    existingOwner != ownerGoogleSub -> SubscriptionWriteResult.OwnedByAnotherUser
                    else -> {
                        val existingLastVerifiedAt = snapshot.getLong(LAST_VERIFIED_AT_FIELD)
                        val existing = snapshot.data.orEmpty()
                        val lifecycleBase = selectLifecycleFields(
                            existing = existing,
                            incoming = fields,
                            existingLastVerifiedAt = existingLastVerifiedAt,
                            incomingLastVerifiedAt = lastVerifiedAt,
                        )
                        val merged = mergeAcknowledgementFields(existing, fields, lifecycleBase)
                        if (merged != existing) {
                            transaction.set(reference, merged)
                        }
                        SubscriptionWriteResult.UpdatedForSameOwner
                    }
                }
            }.get()
        }
    }

    override suspend fun claimSubscriptionAcknowledgement(
        documentId: String,
        ownerGoogleSub: String,
        now: Long,
        leaseUntil: Long,
    ): AcknowledgementClaimResult {
        require(leaseUntil > now) { "Acknowledgement lease must end after it starts." }
        return withContext(Dispatchers.IO) {
            val reference = firestore.collection(SUBSCRIPTIONS_COLLECTION).document(documentId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(reference).get()
                if (!snapshot.exists()) return@runTransaction AcknowledgementClaimResult.Missing
                val record = subscriptionRecordFromFirestore(snapshot.data.orEmpty())
                when {
                    record.ownerGoogleSub != ownerGoogleSub -> AcknowledgementClaimResult.OwnedByAnotherUser
                    record.acknowledgementState == BackendAcknowledgementState.Acknowledged ->
                        AcknowledgementClaimResult.AlreadyAcknowledged(record)
                    record.acknowledgementState == BackendAcknowledgementState.Failed ->
                        AcknowledgementClaimResult.TerminalFailure(record)
                    !record.isAcknowledgementEligible(now) -> AcknowledgementClaimResult.NotEligible(record)
                    record.acknowledgementState != BackendAcknowledgementState.Pending ->
                        AcknowledgementClaimResult.TerminalFailure(record)
                    record.nextAcknowledgementAttemptAt?.let { it > now } == true ||
                        record.acknowledgementLeaseUntil?.let { it > now } == true ->
                        AcknowledgementClaimResult.NotDue(record)
                    else -> {
                        val generation = record.acknowledgementClaimGeneration + 1L
                        val claimed = record.copy(
                            acknowledgementClaimGeneration = generation,
                            acknowledgementLeaseUntil = leaseUntil,
                        )
                        transaction.update(
                            reference,
                            mapOf(
                                ACKNOWLEDGEMENT_CLAIM_GENERATION_FIELD to generation,
                                ACKNOWLEDGEMENT_LEASE_UNTIL_FIELD to leaseUntil,
                            ),
                        )
                        AcknowledgementClaimResult.Claimed(claimed, generation)
                    }
                }
            }.get()
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
        require(
            acknowledgementState == BackendAcknowledgementState.Acknowledged ||
                acknowledgementState == BackendAcknowledgementState.Pending ||
                acknowledgementState == BackendAcknowledgementState.Failed,
        ) { "Unsupported acknowledgement completion state." }
        return withContext(Dispatchers.IO) {
            val reference = firestore.collection(SUBSCRIPTIONS_COLLECTION).document(documentId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(reference).get()
                if (!snapshot.exists()) return@runTransaction AcknowledgementCompletionResult.Missing
                val record = subscriptionRecordFromFirestore(snapshot.data.orEmpty())
                when {
                    record.ownerGoogleSub != ownerGoogleSub ->
                        AcknowledgementCompletionResult.OwnedByAnotherUser
                    record.acknowledgementClaimGeneration != generation ||
                        record.acknowledgementLeaseUntil == null ->
                        AcknowledgementCompletionResult.Stale(record)
                    else -> {
                        val updated = record.copy(
                            acknowledgementState = acknowledgementState,
                            acknowledgementAttemptCount = acknowledgementAttemptCount,
                            nextAcknowledgementAttemptAt = nextAcknowledgementAttemptAt,
                            lastAcknowledgementErrorCode = lastAcknowledgementErrorCode,
                            acknowledgementLeaseUntil = null,
                        )
                        transaction.update(reference, updated.acknowledgementFirestoreFields())
                        AcknowledgementCompletionResult.Applied(updated)
                    }
                }
            }.get()
        }
    }

    override suspend fun reconcileEntitlementFromSubscription(
        entitlementDocumentId: String,
        subscriptionDocumentId: String,
        ownerGoogleSub: String,
        now: Long,
    ): EntitlementReconciliationResult {
        return withContext(Dispatchers.IO) {
            val subscriptionReference = firestore.collection(SUBSCRIPTIONS_COLLECTION).document(subscriptionDocumentId)
            val entitlementReference = firestore.collection(ENTITLEMENTS_COLLECTION).document(entitlementDocumentId)
            firestore.runTransaction { transaction ->
                val subscriptionSnapshot = transaction.get(subscriptionReference).get()
                if (!subscriptionSnapshot.exists()) return@runTransaction EntitlementReconciliationResult.Missing
                val subscription = subscriptionRecordFromFirestore(subscriptionSnapshot.data.orEmpty())
                if (subscription.ownerGoogleSub != ownerGoogleSub) {
                    return@runTransaction EntitlementReconciliationResult.OwnedByAnotherUser
                }
                val entitlementSnapshot = transaction.get(entitlementReference).get()
                val existing = entitlementSnapshot.data?.let(::entitlementRecordFromFirestore)
                val candidate = subscription.toEntitlementRecord(now)
                val effective = selectReconciledEntitlement(existing, candidate, now)
                if (!entitlementSnapshot.exists() || effective != existing) {
                    transaction.set(entitlementReference, effective.toFirestoreFields())
                }
                EntitlementReconciliationResult.Success(effective, subscription)
            }.get()
        }
    }

    private fun mergeAcknowledgementFields(
        existing: Map<String, Any?>,
        incoming: Map<String, Any?>,
        lifecycleBase: Map<String, Any?>,
    ): Map<String, Any?> {
        val existingState = existing[ACKNOWLEDGEMENT_STATE_FIELD] as? String
        val incomingState = incoming[ACKNOWLEDGEMENT_STATE_FIELD] as? String
        val generation = maxOf(
            (existing[ACKNOWLEDGEMENT_CLAIM_GENERATION_FIELD] as? Number)?.toLong() ?: 0L,
            (incoming[ACKNOWLEDGEMENT_CLAIM_GENERATION_FIELD] as? Number)?.toLong() ?: 0L,
        )
        if (
            incomingState == BackendAcknowledgementState.Acknowledged.name ||
            existingState == BackendAcknowledgementState.Acknowledged.name
        ) {
            return lifecycleBase + acknowledgedFields(generation)
        }
        if (
            existingState == BackendAcknowledgementState.Failed.name &&
            incomingState != BackendAcknowledgementState.Acknowledged.name
        ) {
            return lifecycleBase + mapOf(
                ACKNOWLEDGEMENT_STATE_FIELD to BackendAcknowledgementState.Failed.name,
                ACKNOWLEDGEMENT_ATTEMPT_COUNT_FIELD to (existing[ACKNOWLEDGEMENT_ATTEMPT_COUNT_FIELD] ?: 0),
                NEXT_ACKNOWLEDGEMENT_ATTEMPT_AT_FIELD to null,
                LAST_ACKNOWLEDGEMENT_ERROR_CODE_FIELD to existing[LAST_ACKNOWLEDGEMENT_ERROR_CODE_FIELD],
                ACKNOWLEDGEMENT_CLAIM_GENERATION_FIELD to generation,
                ACKNOWLEDGEMENT_LEASE_UNTIL_FIELD to null,
            )
        }
        val existingGeneration = (existing[ACKNOWLEDGEMENT_CLAIM_GENERATION_FIELD] as? Number)?.toLong() ?: 0L
        val incomingGeneration = (incoming[ACKNOWLEDGEMENT_CLAIM_GENERATION_FIELD] as? Number)?.toLong() ?: 0L
        val existingAttempt = (existing[ACKNOWLEDGEMENT_ATTEMPT_COUNT_FIELD] as? Number)?.toInt() ?: 0
        val incomingAttempt = (incoming[ACKNOWLEDGEMENT_ATTEMPT_COUNT_FIELD] as? Number)?.toInt() ?: 0
        val existingNext = (existing[NEXT_ACKNOWLEDGEMENT_ATTEMPT_AT_FIELD] as? Number)?.toLong()
            ?: Long.MIN_VALUE
        val incomingNext = (incoming[NEXT_ACKNOWLEDGEMENT_ATTEMPT_AT_FIELD] as? Number)?.toLong()
            ?: Long.MIN_VALUE
        val existingLease = (existing[ACKNOWLEDGEMENT_LEASE_UNTIL_FIELD] as? Number)?.toLong() ?: Long.MIN_VALUE
        val incomingLease = (incoming[ACKNOWLEDGEMENT_LEASE_UNTIL_FIELD] as? Number)?.toLong() ?: Long.MIN_VALUE
        val acknowledgementSource = if (
            existingGeneration > incomingGeneration ||
            (existingGeneration == incomingGeneration && existingAttempt > incomingAttempt) ||
            (existingGeneration == incomingGeneration && existingAttempt == incomingAttempt && existingNext > incomingNext) ||
            (existingGeneration == incomingGeneration && existingAttempt == incomingAttempt && existingNext == incomingNext && existingLease > incomingLease)
        ) {
            existing
        } else {
            incoming
        }
        return lifecycleBase + mapOf(
            ACKNOWLEDGEMENT_STATE_FIELD to acknowledgementSource[ACKNOWLEDGEMENT_STATE_FIELD],
            ACKNOWLEDGEMENT_ATTEMPT_COUNT_FIELD to
                (acknowledgementSource[ACKNOWLEDGEMENT_ATTEMPT_COUNT_FIELD] ?: 0),
            NEXT_ACKNOWLEDGEMENT_ATTEMPT_AT_FIELD to
                acknowledgementSource[NEXT_ACKNOWLEDGEMENT_ATTEMPT_AT_FIELD],
            LAST_ACKNOWLEDGEMENT_ERROR_CODE_FIELD to
                acknowledgementSource[LAST_ACKNOWLEDGEMENT_ERROR_CODE_FIELD],
            ACKNOWLEDGEMENT_CLAIM_GENERATION_FIELD to generation,
            ACKNOWLEDGEMENT_LEASE_UNTIL_FIELD to acknowledgementSource[ACKNOWLEDGEMENT_LEASE_UNTIL_FIELD],
        )
    }

    private fun selectLifecycleFields(
        existing: Map<String, Any?>,
        incoming: Map<String, Any?>,
        existingLastVerifiedAt: Long?,
        incomingLastVerifiedAt: Long,
    ): Map<String, Any?> {
        if (existingLastVerifiedAt == null || incomingLastVerifiedAt > existingLastVerifiedAt) return incoming
        if (incomingLastVerifiedAt < existingLastVerifiedAt) return existing
        val existingGrantable = (existing[STATUS_FIELD] as? String).isGrantableStatusName()
        val incomingGrantable = (incoming[STATUS_FIELD] as? String).isGrantableStatusName()
        return when {
            existingGrantable && !incomingGrantable -> incoming
            !existingGrantable && incomingGrantable -> existing
            else -> incoming
        }
    }

    private fun String?.isGrantableStatusName(): Boolean {
        return this == BackendSubscriptionStatus.Active.name ||
            this == BackendSubscriptionStatus.GracePeriod.name ||
            this == BackendSubscriptionStatus.CanceledActiveUntilExpiry.name
    }

    private fun acknowledgedFields(generation: Long): Map<String, Any?> {
        return mapOf(
            ACKNOWLEDGEMENT_STATE_FIELD to BackendAcknowledgementState.Acknowledged.name,
            ACKNOWLEDGEMENT_ATTEMPT_COUNT_FIELD to 0,
            NEXT_ACKNOWLEDGEMENT_ATTEMPT_AT_FIELD to null,
            LAST_ACKNOWLEDGEMENT_ERROR_CODE_FIELD to null,
            ACKNOWLEDGEMENT_CLAIM_GENERATION_FIELD to generation,
            ACKNOWLEDGEMENT_LEASE_UNTIL_FIELD to null,
        )
    }

    private fun selectEffectiveEntitlementFields(
        existing: Map<String, Any?>,
        incoming: Map<String, Any?>,
    ): Map<String, Any?> {
        if (existing.isEmpty()) return incoming
        val sameToken = existing[PURCHASE_TOKEN_HASH_FIELD] != null &&
            existing[PURCHASE_TOKEN_HASH_FIELD] == incoming[PURCHASE_TOKEN_HASH_FIELD]
        val existingAcknowledgedGrant = existing[HAS_PREMIUM_FIELD] == true &&
            existing[ACKNOWLEDGEMENT_STATE_FIELD] == BackendAcknowledgementState.Acknowledged.name
        val incomingAcknowledgedGrant = incoming[HAS_PREMIUM_FIELD] == true &&
            incoming[ACKNOWLEDGEMENT_STATE_FIELD] == BackendAcknowledgementState.Acknowledged.name
        val existingAwaitingAcknowledgement = existing[HAS_PREMIUM_FIELD] != true &&
            existing[STATUS_FIELD] == BackendSubscriptionStatus.VerificationPending.name
        val incomingAwaitingAcknowledgement = incoming[HAS_PREMIUM_FIELD] != true &&
            incoming[STATUS_FIELD] == BackendSubscriptionStatus.VerificationPending.name

        if (sameToken && existingAcknowledgedGrant && incomingAwaitingAcknowledgement) {
            return promoteAcknowledgedGrantFields(existing, incoming)
        }
        if (sameToken && incomingAcknowledgedGrant && existingAwaitingAcknowledgement) {
            return promoteAcknowledgedGrantFields(incoming, existing)
        }

        val existingVerifiedAt = (existing[LAST_VERIFIED_AT_FIELD] as? Number)?.toLong()
        val incomingVerifiedAt = (incoming[LAST_VERIFIED_AT_FIELD] as? Number)?.toLong()
        return when {
            existingVerifiedAt == null -> incoming
            incomingVerifiedAt == null -> existing
            incomingVerifiedAt > existingVerifiedAt -> incoming
            incomingVerifiedAt < existingVerifiedAt -> existing
            existing[HAS_PREMIUM_FIELD] == true && incoming[HAS_PREMIUM_FIELD] != true -> incoming
            existing[HAS_PREMIUM_FIELD] != true && incoming[HAS_PREMIUM_FIELD] == true -> existing
            else -> incoming
        }
    }

    private fun promoteAcknowledgedGrantFields(
        acknowledgedGrant: Map<String, Any?>,
        verificationPending: Map<String, Any?>,
    ): Map<String, Any?> {
        val acknowledgedAt = (acknowledgedGrant[LAST_VERIFIED_AT_FIELD] as? Number)?.toLong() ?: Long.MIN_VALUE
        val pendingAt = (verificationPending[LAST_VERIFIED_AT_FIELD] as? Number)?.toLong() ?: Long.MIN_VALUE
        val latestLifecycle = if (pendingAt > acknowledgedAt) verificationPending else acknowledgedGrant
        return latestLifecycle + mapOf(
            HAS_PREMIUM_FIELD to true,
            STATUS_FIELD to acknowledgedGrant[STATUS_FIELD],
            ACKNOWLEDGEMENT_STATE_FIELD to BackendAcknowledgementState.Acknowledged.name,
        )
    }

    private companion object {
        const val ENTITLEMENTS_COLLECTION = "entitlements"
        const val SUBSCRIPTIONS_COLLECTION = "subscriptions"
        const val OWNER_GOOGLE_SUB_FIELD = "ownerGoogleSub"
        const val LAST_VERIFIED_AT_FIELD = "lastVerifiedAt"
        const val HAS_PREMIUM_FIELD = "hasPremium"
        const val STATUS_FIELD = "status"
        const val PURCHASE_TOKEN_HASH_FIELD = "purchaseTokenHash"
        const val ACKNOWLEDGEMENT_STATE_FIELD = "acknowledgementState"
        const val ACKNOWLEDGEMENT_ATTEMPT_COUNT_FIELD = "acknowledgementAttemptCount"
        const val NEXT_ACKNOWLEDGEMENT_ATTEMPT_AT_FIELD = "nextAcknowledgementAttemptAt"
        const val LAST_ACKNOWLEDGEMENT_ERROR_CODE_FIELD = "lastAcknowledgementErrorCode"
        const val ACKNOWLEDGEMENT_CLAIM_GENERATION_FIELD = "acknowledgementClaimGeneration"
        const val ACKNOWLEDGEMENT_LEASE_UNTIL_FIELD = "acknowledgementLeaseUntil"
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

    override suspend fun upsertEntitlement(record: EntitlementRecord): EntitlementRecord {
        val documentId = safeDocumentId(record.googleSub, "Google subject")
        return store.upsertEntitlementDocumentIfNotOlder(
            documentId = documentId,
            lastVerifiedAt = record.lastVerifiedAt,
            fields = record.toFirestoreFields(),
        ).toEntitlementRecord().also { effective ->
            check(effective.googleSub == record.googleSub) {
                "Firestore entitlement owner does not match its document ID."
            }
        }
    }

    override suspend fun getSubscription(purchaseTokenHash: String): SubscriptionRecord? {
        val documentId = safeDocumentId(purchaseTokenHash, "Purchase token hash")
        return store.getSubscriptionDocument(documentId)?.toSubscriptionRecord()?.also { record ->
            check(record.purchaseTokenHash == purchaseTokenHash) {
                "Firestore subscription hash does not match its document ID."
            }
        }
    }

    override suspend fun upsertSubscriptionForOwner(record: SubscriptionRecord): SubscriptionWriteResult {
        val documentId = safeDocumentId(record.purchaseTokenHash, "Purchase token hash")
        return store.upsertSubscriptionDocumentForOwner(
            documentId = documentId,
            ownerGoogleSub = record.ownerGoogleSub,
            lastVerifiedAt = record.lastVerifiedAt,
            fields = record.toFirestoreFields(),
        )
    }

    override suspend fun claimSubscriptionAcknowledgement(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        now: Long,
        leaseUntil: Long,
    ): AcknowledgementClaimResult {
        val documentId = safeDocumentId(purchaseTokenHash, "Purchase token hash")
        return store.claimSubscriptionAcknowledgement(
            documentId = documentId,
            ownerGoogleSub = ownerGoogleSub,
            now = now,
            leaseUntil = leaseUntil,
        )
    }

    override suspend fun completeSubscriptionAcknowledgement(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        generation: Long,
        acknowledgementState: BackendAcknowledgementState,
        acknowledgementAttemptCount: Int,
        nextAcknowledgementAttemptAt: Long?,
        lastAcknowledgementErrorCode: String?,
    ): AcknowledgementCompletionResult {
        val documentId = safeDocumentId(purchaseTokenHash, "Purchase token hash")
        return store.completeSubscriptionAcknowledgement(
            documentId = documentId,
            ownerGoogleSub = ownerGoogleSub,
            generation = generation,
            acknowledgementState = acknowledgementState,
            acknowledgementAttemptCount = acknowledgementAttemptCount,
            nextAcknowledgementAttemptAt = nextAcknowledgementAttemptAt,
            lastAcknowledgementErrorCode = lastAcknowledgementErrorCode,
        )
    }

    override suspend fun reconcileEntitlementFromSubscription(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        now: Long,
    ): EntitlementReconciliationResult {
        return store.reconcileEntitlementFromSubscription(
            entitlementDocumentId = safeDocumentId(ownerGoogleSub, "Google subject"),
            subscriptionDocumentId = safeDocumentId(purchaseTokenHash, "Purchase token hash"),
            ownerGoogleSub = ownerGoogleSub,
            now = now,
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

    private fun SubscriptionRecord.toFirestoreFields(): Map<String, Any?> {
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

    private fun Map<String, Any?>.toEntitlementRecord(): EntitlementRecord {
        return EntitlementRecord(
            googleSub = requiredString("googleSub", "Firestore entitlement"),
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

    private fun Map<String, Any?>.toSubscriptionRecord(): SubscriptionRecord {
        return SubscriptionRecord(
            purchaseTokenHash = requiredString("purchaseTokenHash", "Firestore subscription"),
            hashVersion = requiredString("hashVersion", "Firestore subscription"),
            pepperVersion = requiredString("pepperVersion", "Firestore subscription"),
            ownerGoogleSub = requiredString("ownerGoogleSub", "Firestore subscription"),
            packageName = requiredString("packageName", "Firestore subscription"),
            productId = requiredString("productId", "Firestore subscription"),
            basePlanId = string("basePlanId"),
            offerId = string("offerId"),
            linkedPurchaseTokenHash = string("linkedPurchaseTokenHash"),
            tokenCiphertext = requiredString("tokenCiphertext", "Firestore subscription"),
            keyVersion = requiredString("keyVersion", "Firestore subscription"),
            encryptedAt = requiredLong("encryptedAt", "Firestore subscription"),
            encryptionAlgorithm = requiredString("encryptionAlgorithm", "Firestore subscription"),
            acknowledgementState = enumValueOrDefault(
                string("acknowledgementState"),
                BackendAcknowledgementState.Unknown,
            ),
            acknowledgementAttemptCount = int("acknowledgementAttemptCount") ?: 0,
            nextAcknowledgementAttemptAt = long("nextAcknowledgementAttemptAt"),
            lastAcknowledgementErrorCode = string("lastAcknowledgementErrorCode"),
            lastVerifiedAt = requiredLong("lastVerifiedAt", "Firestore subscription"),
            status = enumValueOrDefault(string("status"), BackendSubscriptionStatus.Unknown),
            expiryTime = long("expiryTime"),
            acknowledgementClaimGeneration = long("acknowledgementClaimGeneration") ?: 0L,
            acknowledgementLeaseUntil = long("acknowledgementLeaseUntil"),
        )
    }

    private fun Map<String, Any?>.string(name: String): String? {
        return (this[name] as? String)?.takeIf { it.isNotBlank() }
    }

    private fun Map<String, Any?>.long(name: String): Long? {
        return (this[name] as? Number)?.toLong()
    }

    private fun Map<String, Any?>.int(name: String): Int? {
        return (this[name] as? Number)?.toInt()
    }

    private fun Map<String, Any?>.requiredString(name: String, label: String): String {
        return string(name) ?: error("$label is missing $name.")
    }

    private fun Map<String, Any?>.requiredLong(name: String, label: String): Long {
        return long(name) ?: error("$label is missing $name.")
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

private fun subscriptionRecordFromFirestore(fields: Map<String, Any?>): SubscriptionRecord {
    return SubscriptionRecord(
        purchaseTokenHash = fields.requiredFirestoreString("purchaseTokenHash", "Firestore subscription"),
        hashVersion = fields.requiredFirestoreString("hashVersion", "Firestore subscription"),
        pepperVersion = fields.requiredFirestoreString("pepperVersion", "Firestore subscription"),
        ownerGoogleSub = fields.requiredFirestoreString("ownerGoogleSub", "Firestore subscription"),
        packageName = fields.requiredFirestoreString("packageName", "Firestore subscription"),
        productId = fields.requiredFirestoreString("productId", "Firestore subscription"),
        basePlanId = fields.firestoreString("basePlanId"),
        offerId = fields.firestoreString("offerId"),
        linkedPurchaseTokenHash = fields.firestoreString("linkedPurchaseTokenHash"),
        tokenCiphertext = fields.requiredFirestoreString("tokenCiphertext", "Firestore subscription"),
        keyVersion = fields.requiredFirestoreString("keyVersion", "Firestore subscription"),
        encryptedAt = fields.requiredFirestoreLong("encryptedAt", "Firestore subscription"),
        encryptionAlgorithm = fields.requiredFirestoreString("encryptionAlgorithm", "Firestore subscription"),
        acknowledgementState = firestoreEnumOrDefault(
            fields.firestoreString("acknowledgementState"),
            BackendAcknowledgementState.Unknown,
        ),
        acknowledgementAttemptCount = (fields["acknowledgementAttemptCount"] as? Number)?.toInt() ?: 0,
        nextAcknowledgementAttemptAt = fields.firestoreLong("nextAcknowledgementAttemptAt"),
        lastAcknowledgementErrorCode = fields.firestoreString("lastAcknowledgementErrorCode"),
        lastVerifiedAt = fields.requiredFirestoreLong("lastVerifiedAt", "Firestore subscription"),
        status = firestoreEnumOrDefault(
            fields.firestoreString("status"),
            BackendSubscriptionStatus.Unknown,
        ),
        expiryTime = fields.firestoreLong("expiryTime"),
        acknowledgementClaimGeneration = fields.firestoreLong("acknowledgementClaimGeneration") ?: 0L,
        acknowledgementLeaseUntil = fields.firestoreLong("acknowledgementLeaseUntil"),
    )
}

private fun entitlementRecordFromFirestore(fields: Map<String, Any?>): EntitlementRecord {
    return EntitlementRecord(
        googleSub = fields.requiredFirestoreString("googleSub", "Firestore entitlement"),
        hasPremium = fields["hasPremium"] as? Boolean ?: false,
        status = firestoreEnumOrDefault(
            fields.firestoreString("status"),
            BackendSubscriptionStatus.Unknown,
        ),
        source = firestoreEnumOrDefault(
            fields.firestoreString("source"),
            BackendEntitlementSource.None,
        ),
        packageName = fields.firestoreString("packageName"),
        productId = fields.firestoreString("productId"),
        basePlanId = fields.firestoreString("basePlanId"),
        offerId = fields.firestoreString("offerId"),
        expiryTime = fields.firestoreLong("expiryTime"),
        lastVerifiedAt = fields.firestoreLong("lastVerifiedAt"),
        stale = fields["stale"] as? Boolean ?: false,
        purchaseTokenHash = fields.firestoreString("purchaseTokenHash"),
        acknowledgementState = firestoreEnumOrNull<BackendAcknowledgementState>(
            fields.firestoreString("acknowledgementState"),
        ),
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

private fun SubscriptionRecord.acknowledgementFirestoreFields(): Map<String, Any?> {
    return mapOf(
        "acknowledgementState" to acknowledgementState.name,
        "acknowledgementAttemptCount" to acknowledgementAttemptCount,
        "nextAcknowledgementAttemptAt" to nextAcknowledgementAttemptAt,
        "lastAcknowledgementErrorCode" to lastAcknowledgementErrorCode,
        "acknowledgementClaimGeneration" to acknowledgementClaimGeneration,
        "acknowledgementLeaseUntil" to acknowledgementLeaseUntil,
    )
}

private fun SubscriptionRecord.isAcknowledgementEligible(now: Long): Boolean {
    return status.isGrantable() && expiryTime?.let { it > now } == true
}

private fun SubscriptionRecord.toEntitlementRecord(now: Long): EntitlementRecord {
    val unexpired = expiryTime?.let { it > now } == true
    val hasPremium = status.isGrantable() &&
        unexpired &&
        acknowledgementState == BackendAcknowledgementState.Acknowledged
    val effectiveStatus = when {
        hasPremium -> status
        status == BackendSubscriptionStatus.PendingPurchase -> BackendSubscriptionStatus.PendingPurchase
        status.isGrantable() && !unexpired -> BackendSubscriptionStatus.Expired
        status.isGrantable() -> BackendSubscriptionStatus.VerificationPending
        else -> status
    }
    return EntitlementRecord(
        googleSub = ownerGoogleSub,
        hasPremium = hasPremium,
        status = effectiveStatus,
        source = BackendEntitlementSource.BackendVerified,
        packageName = packageName,
        productId = productId,
        basePlanId = basePlanId,
        offerId = offerId,
        expiryTime = expiryTime,
        lastVerifiedAt = lastVerifiedAt,
        stale = false,
        purchaseTokenHash = purchaseTokenHash,
        acknowledgementState = acknowledgementState,
    )
}

private fun selectReconciledEntitlement(
    existing: EntitlementRecord?,
    candidate: EntitlementRecord,
    now: Long,
): EntitlementRecord {
    if (existing == null) return candidate
    val safeExisting = existing.failClosedAt(now)
    check(safeExisting.googleSub == candidate.googleSub) {
        "Firestore entitlement owner does not match its document ID."
    }
    if (safeExisting.purchaseTokenHash == candidate.purchaseTokenHash) return candidate
    val existingVerifiedAt = safeExisting.lastVerifiedAt ?: Long.MIN_VALUE
    val candidateVerifiedAt = candidate.lastVerifiedAt ?: Long.MIN_VALUE
    return when {
        candidateVerifiedAt > existingVerifiedAt -> candidate
        candidateVerifiedAt < existingVerifiedAt -> safeExisting
        candidate.hasPremium && !safeExisting.hasPremium -> safeExisting
        else -> candidate
    }
}

private fun BackendSubscriptionStatus.isGrantable(): Boolean {
    return this == BackendSubscriptionStatus.Active ||
        this == BackendSubscriptionStatus.GracePeriod ||
        this == BackendSubscriptionStatus.CanceledActiveUntilExpiry
}

private fun Map<String, Any?>.firestoreString(name: String): String? {
    return (this[name] as? String)?.takeIf { it.isNotBlank() }
}

private fun Map<String, Any?>.firestoreLong(name: String): Long? {
    return (this[name] as? Number)?.toLong()
}

private fun Map<String, Any?>.requiredFirestoreString(name: String, label: String): String {
    return firestoreString(name) ?: error("$label is missing $name.")
}

private fun Map<String, Any?>.requiredFirestoreLong(name: String, label: String): Long {
    return firestoreLong(name) ?: error("$label is missing $name.")
}

private inline fun <reified T : Enum<T>> firestoreEnumOrDefault(value: String?, default: T): T {
    return value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}

private inline fun <reified T : Enum<T>> firestoreEnumOrNull(value: String?): T? {
    return value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
}
