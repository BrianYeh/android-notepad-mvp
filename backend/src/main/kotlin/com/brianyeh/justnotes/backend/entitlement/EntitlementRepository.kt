package com.brianyeh.justnotes.backend.entitlement

sealed class TokenBindingResult {
    data object Bound : TokenBindingResult()
    data object AlreadyOwnedBySameUser : TokenBindingResult()
    data class AlreadyOwnedByAnotherUser(val ownerGoogleSub: String) : TokenBindingResult()
}

interface EntitlementRepository {
    suspend fun getEntitlement(googleSub: String): EntitlementRecord?
    suspend fun upsertEntitlement(record: EntitlementRecord)
    suspend fun bindSubscriptionTokenHash(binding: SubscriptionBinding): TokenBindingResult
}

object NoopEntitlementRepository : EntitlementRepository {
    override suspend fun getEntitlement(googleSub: String): EntitlementRecord? = null

    override suspend fun upsertEntitlement(record: EntitlementRecord) = Unit

    override suspend fun bindSubscriptionTokenHash(binding: SubscriptionBinding): TokenBindingResult {
        return TokenBindingResult.Bound
    }
}
