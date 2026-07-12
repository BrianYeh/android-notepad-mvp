package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.account.accountDeletionGuardDocumentId
import com.brianyeh.justnotes.backend.account.isAccountDeletionGuardActive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountDeletionGuardTest {
    @Test
    fun guardDocumentIdIsStableAndDoesNotExposeGoogleSubject() {
        val first = accountDeletionGuardDocumentId(GOOGLE_SUB)

        assertEquals(first, accountDeletionGuardDocumentId(GOOGLE_SUB))
        assertEquals(43, first.length)
        assertFalse(first.contains(GOOGLE_SUB))
    }

    @Test
    fun guardBlocksUntilExpiryAndMalformedGuardFailsClosed() {
        assertFalse(isAccountDeletionGuardActive(null, NOW))
        assertTrue(isAccountDeletionGuardActive(mapOf("expiresAt" to NOW + 1L), NOW))
        assertFalse(isAccountDeletionGuardActive(mapOf("expiresAt" to NOW), NOW))
        assertTrue(isAccountDeletionGuardActive(emptyMap(), NOW))
        assertTrue(isAccountDeletionGuardActive(mapOf("expiresAt" to "invalid"), NOW))
    }

    private companion object {
        const val GOOGLE_SUB = "google-sub"
        const val NOW = 1_762_000_000_000L
    }
}
