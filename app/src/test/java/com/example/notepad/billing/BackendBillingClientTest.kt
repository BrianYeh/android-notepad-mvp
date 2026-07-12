package com.example.notepad.billing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BackendBillingClientTest {
    @Test
    fun billingContextUsesAuthenticatedJsonGetWithFiveSecondTimeoutsAndDisconnects() = runBlocking {
        lateinit var connection: RecordingHttpURLConnection
        val client = client { url ->
            RecordingHttpURLConnection(
                url = url,
                configuredResponseCode = 200,
                responseBody = """{"schemaVersion":1,"obfuscatedExternalAccountId":"${"a".repeat(43)}"}""",
            ).also { connection = it }
        }

        val result = client.fetchBillingContext("id-token")

        assertEquals(
            BackendBillingContextFetchResult.Success(
                BackendBillingContext(obfuscatedExternalAccountId = "a".repeat(43)),
            ),
            result,
        )
        assertEquals("https://backend.example/v1/billing/context", connection.url.toString())
        assertEquals("GET", connection.requestMethod)
        assertEquals(5_000, connection.connectTimeout)
        assertEquals(5_000, connection.readTimeout)
        assertEquals("Bearer id-token", connection.getRequestProperty("Authorization"))
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertNull(connection.getRequestProperty("Content-Type"))
        assertTrue(connection.disconnected)
    }

    @Test
    fun verifyPostsCompleteUtf8JsonWithExplicitNullableHintsAndRedactedCandidate() = runBlocking {
        lateinit var connection: RecordingHttpURLConnection
        val candidate = candidate(basePlanId = null, offerId = null)
        val client = client { url ->
            RecordingHttpURLConnection(
                url = url,
                configuredResponseCode = 200,
                responseBody = verifyEnvelope(),
            ).also { connection = it }
        }

        val result = client.verifyPurchase("id-token", candidate)

        assertTrue(result is BackendPurchaseVerificationResult.Verified)
        assertEquals("https://backend.example/v1/billing/verify", connection.url.toString())
        assertEquals("POST", connection.requestMethod)
        assertEquals(5_000, connection.connectTimeout)
        assertEquals(5_000, connection.readTimeout)
        assertEquals("Bearer id-token", connection.getRequestProperty("Authorization"))
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertEquals("application/json", connection.getRequestProperty("Content-Type"))
        val body = JSONObject(connection.requestBody.toString(Charsets.UTF_8.name()))
        assertEquals(
            setOf(
                "purchaseToken",
                "packageName",
                "productId",
                "basePlanId",
                "offerId",
                "appVersion",
                "versionCode",
                "deviceLocale",
            ),
            body.keys().asSequence().toSet(),
        )
        assertEquals(PURCHASE_TOKEN, body.getString("purchaseToken"))
        assertTrue(body.isNull("basePlanId"))
        assertTrue(body.isNull("offerId"))
        assertEquals("1.0.7", body.getString("appVersion"))
        assertEquals(5L, body.getLong("versionCode"))
        assertTrue(connection.disconnected)
        assertTrue(candidate.toString().contains("purchaseToken=[REDACTED]"))
        assertFalse(candidate.toString().contains(PURCHASE_TOKEN))
    }

    @Test
    fun verifyParsesEveryFrozenHttpStatusFromTheCorrectStream() = runBlocking {
        data class Case(
            val httpStatus: Int,
            val errorCode: String?,
            val resultCheck: (BackendPurchaseVerificationResult) -> Boolean,
        )

        val cases = listOf(
            Case(200, null) { it is BackendPurchaseVerificationResult.Verified },
            Case(202, "PURCHASE_PENDING") { it is BackendPurchaseVerificationResult.Pending },
            Case(400, "INVALID_REQUEST") {
                it is BackendPurchaseVerificationResult.Rejected && it.httpStatus == 400
            },
            Case(409, "TOKEN_ALREADY_BOUND") {
                it is BackendPurchaseVerificationResult.Rejected && it.httpStatus == 409
            },
            Case(422, "OWNER_MISMATCH") {
                it is BackendPurchaseVerificationResult.Rejected && it.httpStatus == 422
            },
            Case(503, "PLAY_VERIFICATION_UNAVAILABLE") {
                it is BackendPurchaseVerificationResult.Unavailable
            },
        )

        cases.forEach { case ->
            lateinit var connection: RecordingHttpURLConnection
            val client = client { url ->
                RecordingHttpURLConnection(
                    url = url,
                    configuredResponseCode = case.httpStatus,
                    responseBody = when (case.httpStatus) {
                        200 -> verifyEnvelope(errorCode = case.errorCode)
                        202 -> verifyEnvelope(
                            hasPremium = false,
                            status = "PendingPurchase",
                            acknowledgementState = "Pending",
                            retryable = true,
                            retryAfterSeconds = 900,
                            errorCode = case.errorCode,
                            reason = "Safe backend reason.",
                        )
                        else -> ""
                    },
                    errorBody = if (case.httpStatus == 200 || case.httpStatus == 202) null else verifyEnvelope(
                        hasPremium = false,
                        status = if (case.httpStatus == 202) "PendingPurchase" else "VerificationPending",
                        acknowledgementState = if (case.httpStatus == 202) "Pending" else null,
                        retryable = case.httpStatus == 202 || case.httpStatus == 503,
                        retryAfterSeconds = if (case.httpStatus == 202) 900 else null,
                        errorCode = case.errorCode,
                        reason = "Safe backend reason.",
                    ),
                ).also { connection = it }
            }

            val result = client.verifyPurchase("id-token", candidate())

            assertTrue("HTTP ${case.httpStatus}: $result", case.resultCheck(result))
            assertEquals(case.httpStatus !in 200..299, connection.errorStreamRead)
            assertTrue(connection.disconnected)
            val response = result.responseOrNull()
            assertNotNull(response)
            assertEquals(case.errorCode, response?.errorCode)
        }
    }

    @Test
    fun verifyTreatsUnauthorizedAsTypedResultAndReadsErrorStream() = runBlocking {
        lateinit var connection: RecordingHttpURLConnection
        val client = client { url ->
            RecordingHttpURLConnection(
                url = url,
                configuredResponseCode = 401,
                errorBody = """{"error":"unauthorized"}""",
            ).also { connection = it }
        }

        val result = client.verifyPurchase("id-token", candidate())

        assertEquals(BackendPurchaseVerificationResult.Unauthorized, result)
        assertTrue(connection.errorStreamRead)
        assertTrue(connection.disconnected)
    }

    @Test
    fun contextMapsUnauthorizedAndUnavailableWithoutReflectingResponseBodies() = runBlocking {
        val unauthorized = client { url ->
            RecordingHttpURLConnection(
                url = url,
                configuredResponseCode = 401,
                errorBody = """{"error":"unauthorized-$PURCHASE_TOKEN"}""",
            )
        }.fetchBillingContext("id-token")
        val unavailable = client { url ->
            RecordingHttpURLConnection(
                url = url,
                configuredResponseCode = 503,
                errorBody = """{"error":"dependency-$PURCHASE_TOKEN"}""",
            )
        }.fetchBillingContext("id-token")

        assertEquals(BackendBillingContextFetchResult.Unauthorized, unauthorized)
        assertEquals(BackendBillingContextFetchResult.Unavailable, unavailable)
        assertFalse(unauthorized.toString().contains(PURCHASE_TOKEN))
        assertFalse(unavailable.toString().contains(PURCHASE_TOKEN))
    }

    @Test
    fun strictParsersRejectMalformedSchemasTypesEnumsAndStatusCombinations() = runBlocking {
        val malformedContextBodies = listOf(
            "not-json",
            """{"schemaVersion":"1","obfuscatedExternalAccountId":"${"a".repeat(43)}"}""",
            """{"schemaVersion":1,"obfuscatedExternalAccountId":"short"}""",
            """{"schemaVersion":1,"obfuscatedExternalAccountId":"${"a".repeat(43)}","extra":true}""",
        )
        malformedContextBodies.forEach { body ->
            val result = client { url ->
                RecordingHttpURLConnection(url, 200, responseBody = body)
            }.fetchBillingContext("id-token")
            assertTrue(body, result is BackendBillingContextFetchResult.Failure)
        }

        val malformedVerifyBodies = listOf(
            "not-json",
            verifyEnvelope(schemaVersion = 2),
            verifyEnvelope(status = "FutureState"),
            verifyEnvelope(status = "Error"),
            verifyEnvelope(acknowledgementState = "FutureAck"),
            verifyEnvelope(source = "ReviewerGrant"),
            verifyEnvelope(errorCode = "INVALID_REQUEST"),
            JSONObject(verifyEnvelope()).put("extra", true).toString(),
            JSONObject(verifyEnvelope()).put("retryable", "false").toString(),
        )
        malformedVerifyBodies.forEach { body ->
            val result = client { url ->
                RecordingHttpURLConnection(url, 200, responseBody = body)
            }.verifyPurchase("id-token", candidate())
            assertTrue(body, result is BackendPurchaseVerificationResult.Failure)
            assertFalse(result.toString().contains(PURCHASE_TOKEN))
        }
    }

    @Test
    fun networkAndTimeoutFailuresUseFixedSafeMessagesAndAlwaysDisconnect() = runBlocking {
        listOf<IOException>(
            IOException("upstream included $PURCHASE_TOKEN"),
            SocketTimeoutException("timeout included $PURCHASE_TOKEN"),
        ).forEach { failure ->
            lateinit var connection: RecordingHttpURLConnection
            val client = client { url ->
                RecordingHttpURLConnection(
                    url = url,
                    responseCodeFailure = failure,
                ).also { connection = it }
            }

            val result = client.verifyPurchase("id-token", candidate())

            assertTrue(result is BackendPurchaseVerificationResult.Failure)
            assertEquals(
                "Backend purchase verification request failed.",
                (result as BackendPurchaseVerificationResult.Failure).message,
            )
            assertFalse(result.message.contains(PURCHASE_TOKEN))
            assertTrue(connection.disconnected)
        }
    }

    @Test
    fun cancellationPropagatesAndStillDisconnects() {
        lateinit var connection: RecordingHttpURLConnection
        val client = client { url ->
            RecordingHttpURLConnection(
                url = url,
                responseCodeFailure = CancellationException("cancel-$PURCHASE_TOKEN"),
            ).also { connection = it }
        }

        assertThrows(CancellationException::class.java) {
            runBlocking { client.verifyPurchase("id-token", candidate()) }
        }
        assertTrue(connection.disconnected)
    }

    @Test
    fun cancellingBlockedRequestDisconnectsTheRealConnectionBridge() = runBlocking {
        val connection = BlockingHttpURLConnection(URL("https://backend.example/v1/billing/context"))
        val request = async(Dispatchers.IO) {
            client { connection }.fetchBillingContext("id-token")
        }
        assertTrue(connection.responseCodeEntered.await(1, TimeUnit.SECONDS))

        request.cancel()
        val completedAfterCancellation = withTimeoutOrNull(1_000L) {
            request.join()
            true
        } ?: false
        connection.disconnect()
        request.join()

        assertTrue("Cancellation must actively disconnect blocked HTTP I/O", completedAfterCancellation)
        assertTrue(connection.disconnected)
    }

    @Test
    fun responseReadsAreBoundedAndUnusedErrorBodiesAreClosedWithoutDraining() = runBlocking {
        val oversizedSuccessBody = """{"schemaVersion":1,"obfuscatedExternalAccountId":"${"a".repeat(70_000)}"}"""
        val successStream = CountingInputStream(
            ByteArrayInputStream(oversizedSuccessBody.toByteArray()),
        )
        val oversizedConnection = object : HttpURLConnection(
            URL("https://backend.example/v1/billing/context"),
        ) {
            var disconnected = false
            override fun connect() = Unit
            override fun disconnect() {
                disconnected = true
            }
            override fun usingProxy(): Boolean = false
            override fun getResponseCode(): Int = 200
            override fun getInputStream(): InputStream = successStream
        }

        val oversized = client { oversizedConnection }.fetchBillingContext("id-token")

        assertTrue(oversized is BackendBillingContextFetchResult.Failure)
        assertTrue(successStream.bytesRead <= MAX_EXPECTED_RESPONSE_READ_BYTES)
        assertTrue(successStream.closed)
        assertTrue(oversizedConnection.disconnected)

        val errorStream = CountingInputStream(
            ByteArrayInputStream("x".repeat(70_000).toByteArray()),
        )
        val unauthorizedConnection = object : HttpURLConnection(
            URL("https://backend.example/v1/billing/context"),
        ) {
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy(): Boolean = false
            override fun getResponseCode(): Int = 401
            override fun getErrorStream(): InputStream = errorStream
        }

        val unauthorized = client { unauthorizedConnection }.fetchBillingContext("id-token")

        assertEquals(BackendBillingContextFetchResult.Unauthorized, unauthorized)
        assertEquals(0, errorStream.bytesRead)
        assertTrue(errorStream.closed)
    }

    @Test
    fun totalRequestDeadlineDisconnectsSlowIoAndReturnsSafeFailure() = runBlocking {
        val connection = BlockingHttpURLConnection(URL("https://backend.example/v1/billing/context"))
        val client = HttpBackendEntitlementClient(
            config = BackendEntitlementClientConfig(
                baseUrl = "https://backend.example",
                googleWebClientId = TEST_WEB_CLIENT_ID,
            ),
            openConnection = { connection },
            totalTimeoutMillis = 50L,
        )

        val result = withTimeoutOrNull(1_000L) {
            client.fetchBillingContext("id-token")
        }

        assertEquals(
            BackendBillingContextFetchResult.Failure("Backend billing context request failed."),
            result,
        )
        assertTrue(connection.disconnected)
    }

    @Test
    fun missingIdentityNeverOpensAConnection() = runBlocking {
        var opened = false
        val client = client {
            opened = true
            error("Signed-out requests must not open a connection.")
        }

        assertEquals(BackendBillingContextFetchResult.NotSignedIn, client.fetchBillingContext(null))
        assertEquals(
            BackendPurchaseVerificationResult.NotSignedIn,
            client.verifyPurchase(" ", candidate()),
        )
        assertFalse(opened)
    }

    private fun client(
        openConnection: (URL) -> HttpURLConnection,
    ): HttpBackendEntitlementClient {
        return HttpBackendEntitlementClient(
            config = BackendEntitlementClientConfig(
                baseUrl = "https://backend.example",
                googleWebClientId = TEST_WEB_CLIENT_ID,
            ),
            openConnection = openConnection,
        )
    }

    private fun candidate(
        basePlanId: String? = "monthly",
        offerId: String? = "trial10d",
    ): BackendPurchaseCandidate {
        return BackendPurchaseCandidate(
            purchaseToken = PURCHASE_TOKEN,
            packageName = "com.brianyeh.justnotes",
            productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
            basePlanId = basePlanId,
            offerId = offerId,
            appVersion = "1.0.7",
            versionCode = 5,
            deviceLocale = "zh-TW",
        )
    }

    private fun verifyEnvelope(
        schemaVersion: Int = 1,
        hasPremium: Boolean = true,
        status: String = "Active",
        source: String = "BackendVerified",
        acknowledgementState: String? = "Acknowledged",
        retryable: Boolean = false,
        retryAfterSeconds: Long? = null,
        errorCode: String? = null,
        reason: String? = null,
    ): String {
        return JSONObject().apply {
            put("schemaVersion", schemaVersion)
            put("hasPremium", hasPremium)
            put("status", status)
            put("source", source)
            put("packageName", "com.brianyeh.justnotes")
            put("productId", PremiumCatalog.PREFERRED_PRODUCT_ID)
            put("basePlanId", "monthly")
            put("offerId", "trial10d")
            put("expiryTime", 1_900_000_000_000L)
            put("lastVerifiedAt", 1_800_000_000_000L)
            put("purchaseTokenHash", "backend-hmac-hash")
            put("acknowledgementState", acknowledgementState ?: JSONObject.NULL)
            put("retryable", retryable)
            put("retryAfterSeconds", retryAfterSeconds ?: JSONObject.NULL)
            put("errorCode", errorCode ?: JSONObject.NULL)
            put("reason", reason ?: JSONObject.NULL)
        }.toString()
    }

    private fun BackendPurchaseVerificationResult.responseOrNull(): PremiumBackendEntitlementResponse? {
        return when (this) {
            is BackendPurchaseVerificationResult.Verified -> response
            is BackendPurchaseVerificationResult.Pending -> response
            is BackendPurchaseVerificationResult.Rejected -> response
            is BackendPurchaseVerificationResult.Unavailable -> response
            BackendPurchaseVerificationResult.Disabled,
            BackendPurchaseVerificationResult.NotSignedIn,
            BackendPurchaseVerificationResult.Stale,
            BackendPurchaseVerificationResult.Unauthorized,
            is BackendPurchaseVerificationResult.Failure,
            -> null
        }
    }

    private class RecordingHttpURLConnection(
        url: URL,
        private val configuredResponseCode: Int = 200,
        private val responseBody: String = "",
        private val errorBody: String? = null,
        private val responseCodeFailure: Throwable? = null,
    ) : HttpURLConnection(url) {
        val requestBody = ByteArrayOutputStream()
        var disconnected = false
        var errorStreamRead = false

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int {
            responseCodeFailure?.let { throw it }
            return configuredResponseCode
        }

        override fun getInputStream(): InputStream = ByteArrayInputStream(responseBody.toByteArray())

        override fun getErrorStream(): InputStream? {
            errorStreamRead = true
            return errorBody?.let { ByteArrayInputStream(it.toByteArray()) }
        }

        override fun getOutputStream(): ByteArrayOutputStream = requestBody
    }

    private class CountingInputStream(
        private val delegate: InputStream,
    ) : InputStream() {
        var bytesRead: Int = 0
            private set
        var closed: Boolean = false
            private set

        override fun read(): Int {
            val value = delegate.read()
            if (value != -1) bytesRead += 1
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = delegate.read(buffer, offset, length)
            if (count > 0) bytesRead += count
            return count
        }

        override fun close() {
            closed = true
            delegate.close()
        }
    }

    private class BlockingHttpURLConnection(url: URL) : HttpURLConnection(url) {
        val responseCodeEntered = CountDownLatch(1)
        private val releaseResponseCode = CountDownLatch(1)

        @Volatile
        var disconnected: Boolean = false
            private set

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
            releaseResponseCode.countDown()
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int {
            responseCodeEntered.countDown()
            releaseResponseCode.await()
            throw IOException("connection released")
        }
    }

    private companion object {
        const val TEST_WEB_CLIENT_ID = "test-web-client.apps.googleusercontent.com"
        const val PURCHASE_TOKEN = "sensitive-purchase-token"
        const val MAX_EXPECTED_RESPONSE_READ_BYTES = 64 * 1_024 + 1
    }
}
