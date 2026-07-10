package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.entitlement.InMemoryEntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.SubscriptionBinding
import com.brianyeh.justnotes.backend.entitlement.TokenBindingResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class EntitlementRepositoryTest {
    @Test
    fun tokenHashOwnershipIsUnique() = runBlocking {
        val repository = InMemoryEntitlementRepository()
        val binding = SubscriptionBinding(
            purchaseTokenHash = "hash",
            hashVersion = "hmac-sha256-v1",
            pepperVersion = "1",
            ownerGoogleSub = "sub-a",
            packageName = "com.brianyeh.justnotes",
            productId = "just_notes_premium",
            basePlanId = "monthly",
            tokenCiphertext = "ciphertext",
            keyVersion = "key-version-1",
            encryptedAt = 1_762_000_000_000L,
            encryptionAlgorithm = "GOOGLE_SYMMETRIC_ENCRYPTION",
        )

        assertEquals(TokenBindingResult.Bound, repository.bindSubscriptionTokenHash(binding))
        assertEquals(TokenBindingResult.AlreadyOwnedBySameUser, repository.bindSubscriptionTokenHash(binding))
        assertEquals(
            TokenBindingResult.AlreadyOwnedByAnotherUser("sub-a"),
            repository.bindSubscriptionTokenHash(binding.copy(ownerGoogleSub = "sub-b")),
        )
    }

    @Test
    fun entitlementRecordsCanBeStoredAndReadBack() = runBlocking {
        val repository = InMemoryEntitlementRepository()
        val record = EntitlementRecord(
            googleSub = "sub-a",
            hasPremium = true,
            status = BackendSubscriptionStatus.Active,
            packageName = "com.brianyeh.justnotes",
            productId = "just_notes_premium",
            basePlanId = "monthly",
        )

        repository.upsertEntitlement(record)

        assertEquals(record, repository.getEntitlement("sub-a"))
        assertEquals(null, repository.getEntitlement("sub-b"))
    }
}
