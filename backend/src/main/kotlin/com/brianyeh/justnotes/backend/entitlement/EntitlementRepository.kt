package com.brianyeh.justnotes.backend.entitlement

sealed class SubscriptionWriteResult {
    data object Created : SubscriptionWriteResult()
    data object UpdatedForSameOwner : SubscriptionWriteResult()
    data object OwnedByAnotherUser : SubscriptionWriteResult()
}

interface EntitlementRepository {
    suspend fun getEntitlement(googleSub: String): EntitlementRecord?
    suspend fun upsertEntitlement(record: EntitlementRecord)
    suspend fun getSubscription(purchaseTokenHash: String): SubscriptionRecord?
    suspend fun upsertSubscriptionForOwner(record: SubscriptionRecord): SubscriptionWriteResult
}
