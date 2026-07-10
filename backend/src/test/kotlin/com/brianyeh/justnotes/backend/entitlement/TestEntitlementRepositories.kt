package com.brianyeh.justnotes.backend.entitlement

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryEntitlementRepository : EntitlementRepository {
    private val mutex = Mutex()
    private val entitlements = mutableMapOf<String, EntitlementRecord>()
    private val subscriptions = mutableMapOf<String, SubscriptionRecord>()

    override suspend fun getEntitlement(googleSub: String): EntitlementRecord? {
        return mutex.withLock { entitlements[googleSub] }
    }

    override suspend fun upsertEntitlement(record: EntitlementRecord) {
        mutex.withLock {
            val existing = entitlements[record.googleSub]
            val existingVerifiedAt = existing?.lastVerifiedAt
            if (
                existing == null ||
                existingVerifiedAt == null ||
                (record.lastVerifiedAt != null && record.lastVerifiedAt >= existingVerifiedAt)
            ) {
                entitlements[record.googleSub] = record
            }
        }
    }

    override suspend fun getSubscription(purchaseTokenHash: String): SubscriptionRecord? {
        return mutex.withLock { subscriptions[purchaseTokenHash] }
    }

    override suspend fun upsertSubscriptionForOwner(record: SubscriptionRecord): SubscriptionWriteResult {
        return mutex.withLock {
            val existing = subscriptions[record.purchaseTokenHash]
            when {
                existing == null -> {
                    subscriptions[record.purchaseTokenHash] = record
                    SubscriptionWriteResult.Created
                }
                existing.ownerGoogleSub != record.ownerGoogleSub -> SubscriptionWriteResult.OwnedByAnotherUser
                else -> {
                    if (record.lastVerifiedAt >= existing.lastVerifiedAt) {
                        subscriptions[record.purchaseTokenHash] = record
                    }
                    SubscriptionWriteResult.UpdatedForSameOwner
                }
            }
        }
    }
}
