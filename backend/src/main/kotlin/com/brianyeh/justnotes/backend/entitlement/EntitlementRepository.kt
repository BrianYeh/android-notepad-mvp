package com.brianyeh.justnotes.backend.entitlement

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

class InMemoryEntitlementRepository : EntitlementRepository {
    private val mutex = Mutex()
    private val entitlements = mutableMapOf<String, EntitlementRecord>()
    private val tokenOwners = mutableMapOf<String, String>()

    override suspend fun getEntitlement(googleSub: String): EntitlementRecord? {
        return mutex.withLock { entitlements[googleSub] }
    }

    override suspend fun upsertEntitlement(record: EntitlementRecord) {
        mutex.withLock {
            entitlements[record.googleSub] = record
        }
    }

    override suspend fun bindSubscriptionTokenHash(binding: SubscriptionBinding): TokenBindingResult {
        return mutex.withLock {
            val existingOwner = tokenOwners[binding.purchaseTokenHash]
            when {
                existingOwner == null -> {
                    tokenOwners[binding.purchaseTokenHash] = binding.ownerGoogleSub
                    TokenBindingResult.Bound
                }
                existingOwner == binding.ownerGoogleSub -> TokenBindingResult.AlreadyOwnedBySameUser
                else -> TokenBindingResult.AlreadyOwnedByAnotherUser(existingOwner)
            }
        }
    }
}

object NoopEntitlementRepository : EntitlementRepository {
    override suspend fun getEntitlement(googleSub: String): EntitlementRecord? = null

    override suspend fun upsertEntitlement(record: EntitlementRecord) = Unit

    override suspend fun bindSubscriptionTokenHash(binding: SubscriptionBinding): TokenBindingResult {
        return TokenBindingResult.Bound
    }
}
