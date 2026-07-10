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
