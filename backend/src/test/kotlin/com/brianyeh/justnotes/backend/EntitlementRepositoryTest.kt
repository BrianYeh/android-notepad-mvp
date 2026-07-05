package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.entitlement.EntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.SubscriptionBinding
import com.brianyeh.justnotes.backend.entitlement.TokenBindingResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class EntitlementRepositoryTest {
    @Test
    fun tokenHashOwnershipIsUnique() = runBlocking {
        val repository = InMemoryTestEntitlementRepository()
        val binding = SubscriptionBinding(
            purchaseTokenHash = "hash",
            ownerGoogleSub = "sub-a",
            packageName = "com.brianyeh.justnotes",
            productId = "just_notes_premium",
            basePlanId = "monthly",
        )

        assertEquals(TokenBindingResult.Bound, repository.bindSubscriptionTokenHash(binding))
        assertEquals(TokenBindingResult.AlreadyOwnedBySameUser, repository.bindSubscriptionTokenHash(binding))
        assertEquals(
            TokenBindingResult.AlreadyOwnedByAnotherUser("sub-a"),
            repository.bindSubscriptionTokenHash(binding.copy(ownerGoogleSub = "sub-b")),
        )
    }
}

private class InMemoryTestEntitlementRepository : EntitlementRepository {
    private val entitlements = mutableMapOf<String, EntitlementRecord>()
    private val tokenOwners = mutableMapOf<String, String>()

    override suspend fun getEntitlement(googleSub: String): EntitlementRecord? = entitlements[googleSub]

    override suspend fun upsertEntitlement(record: EntitlementRecord) {
        entitlements[record.googleSub] = record
    }

    override suspend fun bindSubscriptionTokenHash(binding: SubscriptionBinding): TokenBindingResult {
        val existingOwner = tokenOwners[binding.purchaseTokenHash]
        return when {
            existingOwner == null -> {
                tokenOwners[binding.purchaseTokenHash] = binding.ownerGoogleSub
                TokenBindingResult.Bound
            }
            existingOwner == binding.ownerGoogleSub -> TokenBindingResult.AlreadyOwnedBySameUser
            else -> TokenBindingResult.AlreadyOwnedByAnotherUser(existingOwner)
        }
    }
}
