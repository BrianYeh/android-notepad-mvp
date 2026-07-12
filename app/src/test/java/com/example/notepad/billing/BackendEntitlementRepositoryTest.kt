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
    fun blankEndpointKeepsClientDisabledWhenWebClientIdIsConfigured() = runBlocking {
        var openedConnection = false
        val client = HttpBackendEntitlementClient(
            config = BackendEntitlementClientConfig(
                baseUrl = "  ",
                googleWebClientId = TEST_WEB_CLIENT_ID,
            ),
            openConnection = {
                openedConnection = true
                throw AssertionError("A blank endpoint must not open a connection.")
            },
        )

        val result = client.fetchEntitlement(idToken = "id-token")

        assertEquals(BackendEntitlementFetchResult.Disabled, result)
        assertFalse(openedConnection)
    }

    @Test
    fun disabledBackendSkipsIdTokenRefresh() = runBlocking {
        var authRequested = false
        val repository = BackendEntitlementRepository(
            client = HttpBackendEntitlementClient(
                config = BackendEntitlementClientConfig(
                    baseUrl = "",
                    googleWebClientId = TEST_WEB_CLIENT_ID,
                ),
            ),
            authProvider = {
                authRequested = true
                BackendEntitlementAuth(idToken = "id-token", accountKey = "account-a")
            },
            currentAccountKeyProvider = { "account-a" },
            applyBackendEntitlement = { true },
        )

        val result = repository.refresh()

        assertEquals(BackendEntitlementFetchResult.Disabled, result)
        assertFalse(authRequested)
    }

    @Test
    fun configuredEndpointWithoutWebClientIdFailsClosedBeforeSendingToken() = runBlocking {
        var openedConnection = false
        val client = HttpBackendEntitlementClient(
            config = BackendEntitlementClientConfig(
                baseUrl = "https://backend.example",
                googleWebClientId = "",
            ),
            openConnection = {
                openedConnection = true
                throw AssertionError("Invalid identity config must not open a connection.")
            },
        )

        val result = client.fetchEntitlement(idToken = "id-token")

        assertEquals(
            "Google web client ID is not configured.",
            (result as BackendEntitlementFetchResult.Failure).message,
        )
        assertFalse(openedConnection)
    }

    @Test
    fun configuredEndpointWithMalformedWebClientIdFailsClosedBeforeSendingToken() = runBlocking {
        var openedConnection = false
        val client = HttpBackendEntitlementClient(
            config = BackendEntitlementClientConfig(
                baseUrl = "https://backend.example",
                googleWebClientId = "android-client-id",
            ),
            openConnection = {
                openedConnection = true
                throw AssertionError("Invalid identity config must not open a connection.")
            },
        )

        val result = client.fetchEntitlement(idToken = "id-token")

        assertEquals(
            "Google web client ID format is invalid.",
            (result as BackendEntitlementFetchResult.Failure).message,
        )
        assertFalse(openedConnection)
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
                googleWebClientId = TEST_WEB_CLIENT_ID,
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
                googleWebClientId = TEST_WEB_CLIENT_ID,
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
    fun unsafeHttpsOriginsFailClosedBeforeSendingToken() = runBlocking {
        val unsafeOrigins = listOf(
            "https://user:password@backend.example",
            "https://backend.example/prefix",
            "https://backend.example?environment=dev",
            "https://backend.example#entitlement",
        )

        unsafeOrigins.forEach { baseUrl ->
            var openedConnection = false
            val client = HttpBackendEntitlementClient(
                config = BackendEntitlementClientConfig(
                    baseUrl = baseUrl,
                    googleWebClientId = TEST_WEB_CLIENT_ID,
                ),
                openConnection = {
                    openedConnection = true
                    throw AssertionError("Unsafe HTTPS origins must fail before opening a connection.")
                },
            )

            val result = client.fetchEntitlement(idToken = "id-token")

            assertTrue(baseUrl, result is BackendEntitlementFetchResult.Failure)
            assertFalse(baseUrl, openedConnection)
        }
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
    fun billingContextResponseIsStaleAfterAccountSwitch() = runBlocking {
        var currentAccountKey = "account-a"
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    error("Entitlement GET is not expected.")
                }

                override suspend fun fetchBillingContext(idToken: String?): BackendBillingContextFetchResult {
                    assertEquals("old-token", idToken)
                    currentAccountKey = "account-b"
                    return BackendBillingContextFetchResult.Success(
                        BackendBillingContext("a".repeat(43)),
                    )
                }
            },
            authProvider = { BackendEntitlementAuth("old-token", currentAccountKey) },
            currentAccountKeyProvider = { currentAccountKey },
            applyBackendEntitlement = { true },
        )

        val result = repository.fetchBillingContext()

        assertEquals(BackendBillingContextFetchResult.Stale, result)
    }

    @Test
    fun outOfOrderBillingContextsOnlyExposeTheNewestSuccessfulResponse() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val firstCanReturn = CompletableDeferred<Unit>()
        val secondCanReturn = CompletableDeferred<Unit>()
        var requestCount = 0
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    error("Entitlement GET is not expected.")
                }

                override suspend fun fetchBillingContext(idToken: String?): BackendBillingContextFetchResult {
                    requestCount += 1
                    return when (requestCount) {
                        1 -> {
                            firstStarted.complete(Unit)
                            firstCanReturn.await()
                            BackendBillingContextFetchResult.Success(BackendBillingContext("a".repeat(43)))
                        }
                        2 -> {
                            secondStarted.complete(Unit)
                            secondCanReturn.await()
                            BackendBillingContextFetchResult.Success(BackendBillingContext("b".repeat(43)))
                        }
                        else -> error("Unexpected context request $requestCount")
                    }
                }
            },
            authProvider = { BackendEntitlementAuth("id-token", "account-a") },
            currentAccountKeyProvider = { "account-a" },
            applyBackendEntitlement = { true },
        )

        val first = async { repository.fetchBillingContext() }
        firstStarted.await()
        val second = async { repository.fetchBillingContext() }
        secondStarted.await()

        secondCanReturn.complete(Unit)
        assertEquals(
            BackendBillingContextFetchResult.Success(BackendBillingContext("b".repeat(43))),
            second.await(),
        )
        firstCanReturn.complete(Unit)
        assertEquals(BackendBillingContextFetchResult.Stale, first.await())
    }

    @Test
    fun olderBillingContextFailureIsStaleAfterNewerSuccess() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val firstCanReturn = CompletableDeferred<Unit>()
        var requestCount = 0
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    error("Entitlement GET is not expected.")
                }

                override suspend fun fetchBillingContext(idToken: String?): BackendBillingContextFetchResult {
                    requestCount += 1
                    return when (requestCount) {
                        1 -> {
                            firstStarted.complete(Unit)
                            firstCanReturn.await()
                            BackendBillingContextFetchResult.Unavailable
                        }
                        2 -> BackendBillingContextFetchResult.Success(
                            BackendBillingContext("b".repeat(43)),
                        )
                        else -> error("Unexpected context request $requestCount")
                    }
                }
            },
            authProvider = { BackendEntitlementAuth("id-token", "account-a") },
            currentAccountKeyProvider = { "account-a" },
            applyBackendEntitlement = { true },
        )

        val olderFailure = async { repository.fetchBillingContext() }
        firstStarted.await()
        val newerSuccess = repository.fetchBillingContext()

        assertEquals(
            BackendBillingContextFetchResult.Success(BackendBillingContext("b".repeat(43))),
            newerSuccess,
        )
        firstCanReturn.complete(Unit)
        assertEquals(BackendBillingContextFetchResult.Stale, olderFailure.await())
    }

    @Test
    fun purchaseVerificationResponseIsStaleAfterAccountSwitch() = runBlocking {
        var currentAccountKey = "account-a"
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    error("Entitlement GET is not expected.")
                }

                override suspend fun verifyPurchase(
                    idToken: String?,
                    candidate: BackendPurchaseCandidate,
                ): BackendPurchaseVerificationResult {
                    assertEquals("old-token", idToken)
                    currentAccountKey = "account-b"
                    return BackendPurchaseVerificationResult.Verified(activeBackendResponse())
                }
            },
            authProvider = { BackendEntitlementAuth("old-token", currentAccountKey) },
            currentAccountKeyProvider = { currentAccountKey },
            applyBackendEntitlement = { true },
        )

        val result = repository.verifyPurchase(purchaseCandidate())

        assertEquals(BackendPurchaseVerificationResult.Stale, result)
    }

    @Test
    fun outOfOrderPurchaseVerificationsOnlyExposeTheNewestSuccessfulResponse() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val firstCanReturn = CompletableDeferred<Unit>()
        val secondCanReturn = CompletableDeferred<Unit>()
        val olderResponse = activeBackendResponse(purchaseTokenHash = "older-token-hash")
        val newerResponse = activeBackendResponse(purchaseTokenHash = "newer-token-hash")
        var requestCount = 0
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    error("Entitlement GET is not expected.")
                }

                override suspend fun verifyPurchase(
                    idToken: String?,
                    candidate: BackendPurchaseCandidate,
                ): BackendPurchaseVerificationResult {
                    requestCount += 1
                    return when (requestCount) {
                        1 -> {
                            firstStarted.complete(Unit)
                            firstCanReturn.await()
                            BackendPurchaseVerificationResult.Verified(olderResponse)
                        }
                        2 -> {
                            secondStarted.complete(Unit)
                            secondCanReturn.await()
                            BackendPurchaseVerificationResult.Verified(newerResponse)
                        }
                        else -> error("Unexpected verify request $requestCount")
                    }
                }
            },
            authProvider = { BackendEntitlementAuth("id-token", "account-a") },
            currentAccountKeyProvider = { "account-a" },
            applyBackendEntitlement = { true },
        )

        val first = async { repository.verifyPurchase(purchaseCandidate()) }
        firstStarted.await()
        val second = async { repository.verifyPurchase(purchaseCandidate()) }
        secondStarted.await()

        secondCanReturn.complete(Unit)
        assertEquals(BackendPurchaseVerificationResult.Verified(newerResponse), second.await())
        firstCanReturn.complete(Unit)
        assertEquals(BackendPurchaseVerificationResult.Stale, first.await())
    }

    @Test
    fun olderVerificationFailureIsStaleAfterNewerSuccess() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val firstCanReturn = CompletableDeferred<Unit>()
        val newerResponse = activeBackendResponse(purchaseTokenHash = "newer-token-hash")
        var requestCount = 0
        val repository = BackendEntitlementRepository(
            client = object : BackendEntitlementClient {
                override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
                    error("Entitlement GET is not expected.")
                }

                override suspend fun verifyPurchase(
                    idToken: String?,
                    candidate: BackendPurchaseCandidate,
                ): BackendPurchaseVerificationResult {
                    requestCount += 1
                    return when (requestCount) {
                        1 -> {
                            firstStarted.complete(Unit)
                            firstCanReturn.await()
                            BackendPurchaseVerificationResult.Unavailable(
                                PremiumBackendEntitlementResponse(
                                    hasPremium = false,
                                    status = PremiumSubscriptionStatus.VerificationPending,
                                    source = PremiumEntitlementSource.BackendVerified,
                                    acknowledgementState = PremiumBackendAcknowledgementState.Pending,
                                    retryable = true,
                                    errorCode = "DEPENDENCY_UNAVAILABLE",
                                ),
                            )
                        }
                        2 -> BackendPurchaseVerificationResult.Verified(newerResponse)
                        else -> error("Unexpected verify request $requestCount")
                    }
                }
            },
            authProvider = { BackendEntitlementAuth("id-token", "account-a") },
            currentAccountKeyProvider = { "account-a" },
            applyBackendEntitlement = { true },
        )

        val olderFailure = async { repository.verifyPurchase(purchaseCandidate()) }
        firstStarted.await()
        val newerSuccess = repository.verifyPurchase(purchaseCandidate())

        assertEquals(BackendPurchaseVerificationResult.Verified(newerResponse), newerSuccess)
        firstCanReturn.complete(Unit)
        assertEquals(BackendPurchaseVerificationResult.Stale, olderFailure.await())
    }

    @Test
    fun malformedJsonFailsClosedWithoutApplyingEntitlement() = runBlocking {
        val client = HttpBackendEntitlementClient(
            config = BackendEntitlementClientConfig(
                baseUrl = "https://backend.example",
                googleWebClientId = TEST_WEB_CLIENT_ID,
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

    @Test
    fun backendParserAcceptsReviewerGrantButStillRejectsClientObserved() = runBlocking {
        fun clientFor(source: String) = HttpBackendEntitlementClient(
            config = BackendEntitlementClientConfig(
                baseUrl = "https://backend.example",
                googleWebClientId = TEST_WEB_CLIENT_ID,
            ),
            openConnection = { url ->
                FakeHttpURLConnection(
                    url = url,
                    responseCode = 200,
                    body = reviewerEntitlementEnvelope(source),
                )
            },
        )

        val reviewer = clientFor("ReviewerGrant").fetchEntitlement("id-token")
        val clientObserved = clientFor("ClientObserved").fetchEntitlement("id-token")

        assertEquals(
            PremiumEntitlementSource.ReviewerGrant,
            (reviewer as BackendEntitlementFetchResult.Success).response.source,
        )
        assertTrue(clientObserved is BackendEntitlementFetchResult.Failure)
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

    private fun purchaseCandidate(): BackendPurchaseCandidate {
        return BackendPurchaseCandidate(
            purchaseToken = "transient-token",
            packageName = "com.brianyeh.justnotes",
            productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
            basePlanId = "monthly",
            offerId = "trial10d",
            appVersion = "1.0.7",
            versionCode = 5,
            deviceLocale = "zh-TW",
        )
    }

    private fun reviewerEntitlementEnvelope(source: String): String {
        return """{
            "schemaVersion":1,
            "hasPremium":true,
            "status":"Active",
            "source":"$source",
            "packageName":null,
            "productId":null,
            "basePlanId":null,
            "offerId":null,
            "expiryTime":1900000000000,
            "lastVerifiedAt":1800000000000,
            "stale":false,
            "purchaseTokenHash":null,
            "acknowledgementState":"NotRequired"
        }""".trimIndent()
    }

    private companion object {
        const val TEST_WEB_CLIENT_ID = "test-web-client.apps.googleusercontent.com"
    }
}
