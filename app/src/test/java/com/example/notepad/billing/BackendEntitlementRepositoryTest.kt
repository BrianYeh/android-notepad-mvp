package com.example.notepad.billing

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
            idTokenProvider = { "id-token" },
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
            idTokenProvider = { null },
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
        val response = PremiumBackendEntitlementResponse(
            hasPremium = true,
            status = PremiumSubscriptionStatus.Active,
            source = PremiumEntitlementSource.BackendVerified,
            packageName = "com.brianyeh.justnotes",
            productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
            basePlanId = "annual",
            expiryTime = 1_762_000_000_000L,
            lastVerifiedAt = 1_761_000_000_000L,
            purchaseTokenHash = "token-hash",
        )
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    assertEquals("id-token", idToken)
                    return BackendEntitlementFetchResult.Success(response)
                }
            },
            idTokenProvider = { "id-token" },
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
        var currentToken = "old-token"
        var applied = false
        val response = PremiumBackendEntitlementResponse(
            hasPremium = true,
            status = PremiumSubscriptionStatus.Active,
            source = PremiumEntitlementSource.BackendVerified,
            packageName = "com.brianyeh.justnotes",
            productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
            basePlanId = "annual",
            expiryTime = 1_762_000_000_000L,
            lastVerifiedAt = 1_761_000_000_000L,
            purchaseTokenHash = "token-hash",
        )
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    assertEquals("old-token", idToken)
                    currentToken = "new-token"
                    return BackendEntitlementFetchResult.Success(response)
                }
            },
            idTokenProvider = { currentToken },
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
}
