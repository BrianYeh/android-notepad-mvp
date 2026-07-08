package com.example.notepad.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

class BackendIdTokenProviderTest {
    @Test
    fun refreshesCachedAccountBeforeReturningBackendEntitlementAuth() = runBlocking {
        var cachedAccount = TestAccount(idToken = "cached-token", accountKey = "account-a")
        var refreshCount = 0

        val auth = refreshedBackendEntitlementAuth(
            googleWebClientId = "web-client-id",
            cachedAccount = cachedAccount,
            refreshAccount = {
                refreshCount += 1
                TestAccount(idToken = "fresh-token", accountKey = "account-a")
            },
            readIdToken = TestAccount::idToken,
            readAccountKey = TestAccount::accountKey,
            cacheRefreshedAccount = { cachedAccount = it },
        )

        assertEquals("fresh-token", auth?.idToken)
        assertEquals("account-a", auth?.accountKey)
        assertEquals(TestAccount(idToken = "fresh-token", accountKey = "account-a"), cachedAccount)
        assertEquals(1, refreshCount)
    }

    @Test
    fun blankWebClientIdSkipsRefreshAndReturnsNoBackendAuth() = runBlocking {
        var refreshCount = 0

        val auth = refreshedBackendEntitlementAuth(
            googleWebClientId = "",
            cachedAccount = TestAccount(idToken = "cached-token", accountKey = "account-a"),
            refreshAccount = {
                refreshCount += 1
                TestAccount(idToken = "fresh-token", accountKey = "account-a")
            },
            readIdToken = TestAccount::idToken,
            readAccountKey = TestAccount::accountKey,
            cacheRefreshedAccount = {},
        )

        assertNull(auth)
        assertEquals(0, refreshCount)
    }

    @Test
    fun backendAuthCanUseLastSignedInAccountWithoutDriveAccount() {
        val account = backendEntitlementAccountForAuth(
            driveAccount = null,
            lastSignedInAccount = TestAccount(idToken = "cached-token", accountKey = "account-a"),
            allowLastSignedInAccount = true,
        )

        assertEquals(TestAccount(idToken = "cached-token", accountKey = "account-a"), account)
    }

    @Test
    fun explicitSignOutBlocksLastSignedInAccountFallback() {
        val account = backendEntitlementAccountForAuth(
            driveAccount = null,
            lastSignedInAccount = TestAccount(idToken = "cached-token", accountKey = "account-a"),
            allowLastSignedInAccount = false,
        )

        assertNull(account)
    }

    @Test
    fun backendOnlyRefreshWithoutDrivePermissionKeepsDriveAccount() {
        val driveAccount = TestAccount(idToken = "drive-token", accountKey = "account-a")
        val refreshedAccount = TestAccount(idToken = "fresh-token", accountKey = "account-a")

        val account = driveAccountAfterBackendAuthRefresh(
            currentDriveAccount = driveAccount,
            refreshedAccount = refreshedAccount,
            refreshedHasDrivePermission = false,
        )

        assertEquals(driveAccount, account)
    }

    @Test
    fun backendRefreshWithDrivePermissionCachesRefreshedDriveAccount() {
        val driveAccount = TestAccount(idToken = "drive-token", accountKey = "account-a")
        val refreshedAccount = TestAccount(idToken = "fresh-token", accountKey = "account-a")

        val account = driveAccountAfterBackendAuthRefresh(
            currentDriveAccount = driveAccount,
            refreshedAccount = refreshedAccount,
            refreshedHasDrivePermission = true,
        )

        assertEquals(refreshedAccount, account)
    }

    @Test
    fun cancelledRefreshIsPropagated() = runBlocking {
        val cancellation = CancellationException("cancelled")

        try {
            refreshedBackendEntitlementAuth(
                googleWebClientId = "web-client-id",
                cachedAccount = TestAccount(idToken = "cached-token", accountKey = "account-a"),
                refreshAccount = { throw cancellation },
                readIdToken = TestAccount::idToken,
                readAccountKey = TestAccount::accountKey,
                cacheRefreshedAccount = {},
            )
        } catch (exception: CancellationException) {
            assertSame(cancellation, exception)
            return@runBlocking
        }
        throw AssertionError("CancellationException should be propagated.")
    }

    private data class TestAccount(
        val idToken: String?,
        val accountKey: String?,
    )
}
