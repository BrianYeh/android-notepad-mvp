package com.example.notepad.billing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class BackendEntitlementRepositoryTest {
    @Test
    fun missingEndpointDisablesRefreshWithoutApplyingEntitlement() = runBlocking {
        var applied = false
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    return BackendEntitlementFetchResult.Disabled
                }
            },
            authProvider = { BackendEntitlementAuth(idToken = "id-token", accountKey = "account-a") },
            currentAccountKeyProvider = { "account-a" },
            applyBackendEntitlement = {
                applied = true
                true
            },
        )

        val result = repository.refresh()

        assertEquals(BackendEntitlementFetchResult.Disabled, result)
        assertFalse(applied)
    }

    @Test
    fun missingIdTokenDoesNotApplyEntitlement() = runBlocking {
        var applied = false
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    return BackendEntitlementFetchResult.NotSignedIn
                }
            },
            authProvider = { null },
            currentAccountKeyProvider = { null },
            applyBackendEntitlement = {
                applied = true
                true
            },
        )

        val result = repository.refresh()

        assertEquals(BackendEntitlementFetchResult.NotSignedIn, result)
        assertFalse(applied)
    }

    @Test
    fun successfulAuthenticatedBackendResponseIsApplied() = runBlocking {
        var appliedResponse: PremiumBackendEntitlementResponse? = null
        val response = activeBackendResponse()
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    assertEquals("id-token", idToken)
                    return BackendEntitlementFetchResult.Success(response)
                }
            },
            authProvider = { BackendEntitlementAuth(idToken = "id-token", accountKey = "account-a") },
            currentAccountKeyProvider = { "account-a" },
            applyBackendEntitlement = {
                appliedResponse = it
                true
            },
        )

        val result = repository.refresh()

        assertTrue(result is BackendEntitlementFetchResult.Success)
        assertEquals(response, appliedResponse)
    }

    @Test
    fun malformedEndpointFailsClosedWithoutApplyingEntitlement() = runBlocking {
        val client = HttpBackendEntitlementClient(
            config = BackendEntitlementClientConfig(
                baseUrl = "://not-a-url",
                googleWebClientId = "web-client-id",
            ),
        )

        val result = client.fetchEntitlement(idToken = "id-token")

        assertTrue(result is BackendEntitlementFetchResult.Failure)
    }

    @Test
    fun cleartextEndpointFailsClosedBeforeSendingToken() = runBlocking {
        var openedConnection = false
        val client = HttpBackendEntitlementClient(
            config = BackendEntitlementClientConfig(
                baseUrl = "http://backend.example",
                googleWebClientId = "web-client-id",
            ),
            openConnection = {
                openedConnection = true
                throw AssertionError("HTTP endpoints must fail before opening a connection.")
            },
        )

        val result = client.fetchEntitlement(idToken = "id-token")

        assertTrue(result is BackendEntitlementFetchResult.Failure)
        assertFalse(openedConnection)
    }

    @Test
    fun staleSuccessfulResponseAfterAccountChangeIsNotApplied() = runBlocking {
        var currentAccountKey = "account-a"
        var applied = false
        val response = activeBackendResponse()
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    assertEquals("old-token", idToken)
                    currentAccountKey = "account-b"
                    return BackendEntitlementFetchResult.Success(response)
                }
            },
            authProvider = { BackendEntitlementAuth(idToken = "old-token", accountKey = currentAccountKey) },
            currentAccountKeyProvider = { currentAccountKey },
            applyBackendEntitlement = {
                applied = true
                true
            },
        )

        val result = repository.refresh()

        assertTrue(result is BackendEntitlementFetchResult.Success)
        assertFalse(applied)
    }

    @Test
    fun refreshedTokenForSameAccountStillAppliesResponse() = runBlocking {
        var currentToken = "old-token"
        var appliedResponse: PremiumBackendEntitlementResponse? = null
        val response = activeBackendResponse()
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    assertEquals("old-token", idToken)
                    currentToken = "new-token"
                    return BackendEntitlementFetchResult.Success(response)
                }
            },
            authProvider = { BackendEntitlementAuth(idToken = currentToken, accountKey = "account-a") },
            currentAccountKeyProvider = { "account-a" },
            applyBackendEntitlement = {
                appliedResponse = it
                true
            },
        )

        val result = repository.refresh()

        assertTrue(result is BackendEntitlementFetchResult.Success)
        assertEquals(response, appliedResponse)
    }

    @Test
    fun successfulResponseAppliesWhenSecondTokenRefreshWouldFailForSameAccount() = runBlocking {
        var authRequestCount = 0
        var appliedResponse: PremiumBackendEntitlementResponse? = null
        val response = activeBackendResponse()
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    assertEquals("fresh-token", idToken)
                    return BackendEntitlementFetchResult.Success(response)
                }
            },
            authProvider = {
                authRequestCount += 1
                if (authRequestCount == 1) {
                    BackendEntitlementAuth(idToken = "fresh-token", accountKey = "account-a")
                } else {
                    null
                }
            },
            currentAccountKeyProvider = { "account-a" },
            applyBackendEntitlement = {
                appliedResponse = it
                true
            },
        )

        val result = repository.refresh()

        assertTrue(result is BackendEntitlementFetchResult.Success)
        assertEquals(response, appliedResponse)
        assertEquals(1, authRequestCount)
    }

    @Test
    fun outOfOrderSuccessfulRefreshesOnlyApplyNewestResponse() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val firstCanReturn = CompletableDeferred<Unit>()
        val secondCanReturn = CompletableDeferred<Unit>()
        val olderResponse = activeBackendResponse(purchaseTokenHash = "older-token-hash")
        val newerResponse = activeBackendResponse(purchaseTokenHash = "newer-token-hash")
        val appliedResponses = mutableListOf<PremiumBackendEntitlementResponse>()
        var fetchCount = 0
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    assertEquals("id-token", idToken)
                    fetchCount += 1
                    return when (fetchCount) {
                        1 -> {
                            firstStarted.complete(Unit)
                            firstCanReturn.await()
                            BackendEntitlementFetchResult.Success(olderResponse)
                        }
                        2 -> {
                            secondStarted.complete(Unit)
                            secondCanReturn.await()
                            BackendEntitlementFetchResult.Success(newerResponse)
                        }
                        else -> error("Unexpected refresh $fetchCount")
                    }
                }
            },
            authProvider = { BackendEntitlementAuth(idToken = "id-token", accountKey = "account-a") },
            currentAccountKeyProvider = { "account-a" },
            applyBackendEntitlement = {
                appliedResponses += it
                true
            },
        )

        val firstRefresh = async { repository.refresh() }
        firstStarted.await()
        val secondRefresh = async { repository.refresh() }
        secondStarted.await()

        secondCanReturn.complete(Unit)
        secondRefresh.await()
        assertEquals(listOf(newerResponse), appliedResponses)

        firstCanReturn.complete(Unit)
        firstRefresh.await()
        assertEquals(listOf(newerResponse), appliedResponses)
    }

    @Test
    fun laterFailedRefreshDoesNotSuppressEarlierSuccessfulResponse() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val firstCanReturn = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val response = activeBackendResponse()
        val appliedResponses = mutableListOf<PremiumBackendEntitlementResponse>()
        var fetchCount = 0
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    assertEquals("id-token", idToken)
                    fetchCount += 1
                    return when (fetchCount) {
                        1 -> {
                            firstStarted.complete(Unit)
                            firstCanReturn.await()
                            BackendEntitlementFetchResult.Success(response)
                        }
                        2 -> {
                            secondStarted.complete(Unit)
                            BackendEntitlementFetchResult.Failure("Temporary backend error.")
                        }
                        else -> error("Unexpected refresh $fetchCount")
                    }
                }
            },
            authProvider = { BackendEntitlementAuth(idToken = "id-token", accountKey = "account-a") },
            currentAccountKeyProvider = { "account-a" },
            applyBackendEntitlement = {
                appliedResponses += it
                true
            },
        )

        val firstRefresh = async { repository.refresh() }
        firstStarted.await()
        val secondRefresh = async { repository.refresh() }
        secondStarted.await()

        assertTrue(secondRefresh.await() is BackendEntitlementFetchResult.Failure)
        assertEquals(emptyList<PremiumBackendEntitlementResponse>(), appliedResponses)

        firstCanReturn.complete(Unit)
        assertTrue(firstRefresh.await() is BackendEntitlementFetchResult.Success)
        assertEquals(listOf(response), appliedResponses)
    }

    @Test
    fun malformedJsonFailsClosedWithoutApplyingEntitlement() = runBlocking {
        val client = HttpBackendEntitlementClient(
            config = BackendEntitlementClientConfig(
                baseUrl = "https://backend.example",
                googleWebClientId = "web-client-id",
            ),
            openConnection = { url ->
                FakeHttpURLConnection(
                    url = url,
                    responseCode = 200,
                    body = "not-json",
                )
            },
        )

        val result = client.fetchEntitlement(idToken = "id-token")

        assertTrue(result is BackendEntitlementFetchResult.Failure)
    }

    private class FakeHttpURLConnection(
        url: URL,
        private val responseCode: Int,
        private val body: String,
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = responseCode
        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
    }

    private fun activeBackendResponse(
        purchaseTokenHash: String = "token-hash",
    ): PremiumBackendEntitlementResponse {
        return PremiumBackendEntitlementResponse(
            hasPremium = true,
            status = PremiumSubscriptionStatus.Active,
            source = PremiumEntitlementSource.BackendVerified,
            packageName = "com.brianyeh.justnotes",
            productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
            basePlanId = "annual",
            expiryTime = 1_762_000_000_000L,
            lastVerifiedAt = 1_761_000_000_000L,
            purchaseTokenHash = purchaseTokenHash,
        )
    }
}
