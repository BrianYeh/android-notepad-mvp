package com.example.notepad.billing

import com.example.notepad.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class BackendEntitlementClientConfig(
    val baseUrl: String,
    val googleWebClientId: String,
) {
    val normalizedBaseUrl: String
        get() = baseUrl.trim().trimEnd('/')

    val isDisabled: Boolean
        get() = normalizedBaseUrl.isBlank()

    fun validationError(): String? {
        if (isDisabled) return null
        val clientId = googleWebClientId.trim()
        if (clientId.isEmpty()) return "Google web client ID is not configured."
        if (!GOOGLE_WEB_CLIENT_ID_PATTERN.matches(clientId)) {
            return "Google web client ID format is invalid."
        }
        val endpoint = runCatching { URL(normalizedBaseUrl) }.getOrNull()
            ?: return "Backend entitlement endpoint is invalid."
        if (
            endpoint.protocol != "https" ||
            endpoint.host.isBlank() ||
            endpoint.userInfo != null ||
            endpoint.path.isNotEmpty() ||
            endpoint.query != null ||
            endpoint.ref != null
        ) {
            return "Backend entitlement endpoint must be an HTTPS origin without credentials, query, or fragment."
        }
        return null
    }

    companion object {
        fun fromBuildConfig(): BackendEntitlementClientConfig {
            return BackendEntitlementClientConfig(
                baseUrl = BuildConfig.BACKEND_BASE_URL,
                googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
            )
        }

        private val GOOGLE_WEB_CLIENT_ID_PATTERN =
            Regex("^[A-Za-z0-9-]+\\.apps\\.googleusercontent\\.com$")
    }
}

internal sealed class BackendEntitlementFetchResult {
    data class Success(val response: PremiumBackendEntitlementResponse) : BackendEntitlementFetchResult()
    data object Disabled : BackendEntitlementFetchResult()
    data object NotSignedIn : BackendEntitlementFetchResult()
    data class Failure(val message: String) : BackendEntitlementFetchResult()
}

internal data class BackendBillingContext(
    val obfuscatedExternalAccountId: String,
)

internal data class BackendPurchaseCandidate(
    val purchaseToken: String,
    val packageName: String,
    val productId: String,
    val basePlanId: String?,
    val offerId: String?,
    val appVersion: String,
    val versionCode: Long,
    val deviceLocale: String,
) {
    override fun toString(): String =
        "BackendPurchaseCandidate(purchaseToken=[REDACTED], packageName=$packageName, " +
            "productId=$productId, basePlanId=$basePlanId, offerId=$offerId, " +
            "appVersion=$appVersion, versionCode=$versionCode, deviceLocale=$deviceLocale)"
}

internal sealed class BackendBillingContextFetchResult {
    data class Success(val context: BackendBillingContext) : BackendBillingContextFetchResult()
    data object Disabled : BackendBillingContextFetchResult()
    data object NotSignedIn : BackendBillingContextFetchResult()
    data object Unauthorized : BackendBillingContextFetchResult()
    data object Unavailable : BackendBillingContextFetchResult()
    data object Stale : BackendBillingContextFetchResult()
    data class Failure(val message: String) : BackendBillingContextFetchResult()
}

internal sealed class BackendPurchaseVerificationResult {
    data class Verified(val response: PremiumBackendEntitlementResponse) : BackendPurchaseVerificationResult()
    data class Pending(val response: PremiumBackendEntitlementResponse) : BackendPurchaseVerificationResult()
    data class Rejected(
        val httpStatus: Int,
        val response: PremiumBackendEntitlementResponse,
    ) : BackendPurchaseVerificationResult()

    data class Unavailable(val response: PremiumBackendEntitlementResponse) : BackendPurchaseVerificationResult()
    data object Disabled : BackendPurchaseVerificationResult()
    data object NotSignedIn : BackendPurchaseVerificationResult()
    data object Unauthorized : BackendPurchaseVerificationResult()
    data object Stale : BackendPurchaseVerificationResult()
    data class Failure(val message: String) : BackendPurchaseVerificationResult()
}

internal interface BackendEntitlementClient {
    fun preflight(): BackendEntitlementFetchResult? = null

    suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult

    suspend fun fetchBillingContext(idToken: String?): BackendBillingContextFetchResult {
        return BackendBillingContextFetchResult.Failure("Backend billing context is not supported.")
    }

    suspend fun verifyPurchase(
        idToken: String?,
        candidate: BackendPurchaseCandidate,
    ): BackendPurchaseVerificationResult {
        return BackendPurchaseVerificationResult.Failure("Backend purchase verification is not supported.")
    }
}

internal data class BackendEntitlementAuth(
    val idToken: String,
    val accountKey: String,
)

internal class HttpBackendEntitlementClient(
    private val config: BackendEntitlementClientConfig = BackendEntitlementClientConfig.fromBuildConfig(),
    private val openConnection: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpURLConnection },
    private val totalTimeoutMillis: Long = TOTAL_TIMEOUT_MS,
) : BackendEntitlementClient {
    override fun preflight(): BackendEntitlementFetchResult? {
        if (config.isDisabled) return BackendEntitlementFetchResult.Disabled
        return config.validationError()?.let { BackendEntitlementFetchResult.Failure(it) }
    }

    override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
        preflight()?.let { return it }
        val bearerToken = idToken?.takeIf { it.isNotBlank() } ?: return BackendEntitlementFetchResult.NotSignedIn
        return runHttpRequestWithDeadline(
            timeoutMillis = totalTimeoutMillis,
            timeoutResult = BackendEntitlementFetchResult.Failure(ENTITLEMENT_REQUEST_FAILURE),
        ) {
            runCancellableHttpRequest { trackConnection ->
                try {
                val endpoint = URL("${config.normalizedBaseUrl}/v1/entitlement")
                val connection = openConnection(endpoint).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Authorization", "Bearer $bearerToken")
                    setRequestProperty("Accept", JSON_CONTENT_TYPE)
                }
                trackConnection(connection)
                val responseCode = connection.responseCode
                if (responseCode != HTTP_OK) {
                    connection.closeErrorBody()
                    BackendEntitlementFetchResult.Failure("Backend entitlement request failed ($responseCode).")
                } else {
                    BackendEntitlementFetchResult.Success(
                        parseEntitlementResponse(connection.readSuccessBody()),
                    )
                }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: IOException) {
                    BackendEntitlementFetchResult.Failure(ENTITLEMENT_REQUEST_FAILURE)
                } catch (_: IllegalArgumentException) {
                    BackendEntitlementFetchResult.Failure(ENTITLEMENT_RESPONSE_FAILURE)
                } catch (_: JSONException) {
                    BackendEntitlementFetchResult.Failure(ENTITLEMENT_RESPONSE_FAILURE)
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    BackendEntitlementFetchResult.Failure(ENTITLEMENT_REQUEST_FAILURE)
                }
            }
        }
    }

    override suspend fun fetchBillingContext(idToken: String?): BackendBillingContextFetchResult {
        preflight()?.let { return it.toBillingContextResult() }
        val bearerToken = idToken?.takeIf { it.isNotBlank() }
            ?: return BackendBillingContextFetchResult.NotSignedIn
        return runHttpRequestWithDeadline(
            timeoutMillis = totalTimeoutMillis,
            timeoutResult = BackendBillingContextFetchResult.Failure(BILLING_CONTEXT_REQUEST_FAILURE),
        ) {
            runCancellableHttpRequest { trackConnection ->
                try {
                val connection = configuredConnection(
                    endpointPath = BILLING_CONTEXT_PATH,
                    bearerToken = bearerToken,
                    requestMethod = "GET",
                )
                trackConnection(connection)
                when (connection.responseCode) {
                    HTTP_OK -> BackendBillingContextFetchResult.Success(
                        parseBillingContext(connection.readSuccessBody()),
                    )
                    HTTP_UNAUTHORIZED -> {
                        connection.closeErrorBody()
                        BackendBillingContextFetchResult.Unauthorized
                    }
                    HTTP_SERVICE_UNAVAILABLE -> {
                        connection.closeErrorBody()
                        BackendBillingContextFetchResult.Unavailable
                    }
                    else -> {
                        connection.closeErrorBody()
                        BackendBillingContextFetchResult.Failure(BILLING_CONTEXT_REQUEST_FAILURE)
                    }
                }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: IOException) {
                    BackendBillingContextFetchResult.Failure(BILLING_CONTEXT_REQUEST_FAILURE)
                } catch (_: IllegalArgumentException) {
                    BackendBillingContextFetchResult.Failure(BILLING_CONTEXT_RESPONSE_FAILURE)
                } catch (_: JSONException) {
                    BackendBillingContextFetchResult.Failure(BILLING_CONTEXT_RESPONSE_FAILURE)
                } catch (_: Exception) {
                    BackendBillingContextFetchResult.Failure(BILLING_CONTEXT_REQUEST_FAILURE)
                }
            }
        }
    }

    override suspend fun verifyPurchase(
        idToken: String?,
        candidate: BackendPurchaseCandidate,
    ): BackendPurchaseVerificationResult {
        preflight()?.let { return it.toPurchaseVerificationResult() }
        val bearerToken = idToken?.takeIf { it.isNotBlank() }
            ?: return BackendPurchaseVerificationResult.NotSignedIn
        return runHttpRequestWithDeadline(
            timeoutMillis = totalTimeoutMillis,
            timeoutResult = BackendPurchaseVerificationResult.Failure(PURCHASE_VERIFY_REQUEST_FAILURE),
        ) {
            runCancellableHttpRequest { trackConnection ->
                try {
                val connection = configuredConnection(
                    endpointPath = BILLING_VERIFY_PATH,
                    bearerToken = bearerToken,
                    requestMethod = "POST",
                ).apply {
                    doOutput = true
                    setRequestProperty("Content-Type", JSON_CONTENT_TYPE)
                }
                trackConnection(connection)
                val requestBytes = candidate.toVerifyRequestJson().toByteArray(StandardCharsets.UTF_8)
                connection.setFixedLengthStreamingMode(requestBytes.size)
                connection.outputStream.use { output -> output.write(requestBytes) }
                val responseCode = connection.responseCode
                when {
                    responseCode == HTTP_UNAUTHORIZED -> {
                        connection.closeErrorBody()
                        BackendPurchaseVerificationResult.Unauthorized
                    }
                    responseCode !in VERIFY_ENVELOPE_HTTP_STATUSES -> {
                        connection.closeErrorBody()
                        BackendPurchaseVerificationResult.Failure(PURCHASE_VERIFY_REQUEST_FAILURE)
                    }
                    else -> {
                        val body = if (responseCode in HTTP_OK..HTTP_ACCEPTED) {
                            connection.readSuccessBody()
                        } else {
                            connection.readErrorBody()
                                ?: return@runCancellableHttpRequest BackendPurchaseVerificationResult.Failure(
                                    PURCHASE_VERIFY_RESPONSE_FAILURE,
                                )
                        }
                        val response = parseVerifyResponse(body)
                        require(response.matchesHttpStatus(responseCode)) {
                            "Verify response does not match HTTP status."
                        }
                        when (responseCode) {
                            HTTP_OK -> BackendPurchaseVerificationResult.Verified(response)
                            HTTP_ACCEPTED -> BackendPurchaseVerificationResult.Pending(response)
                            HTTP_BAD_REQUEST,
                            HTTP_CONFLICT,
                            HTTP_UNPROCESSABLE_ENTITY,
                            -> BackendPurchaseVerificationResult.Rejected(responseCode, response)
                            HTTP_SERVICE_UNAVAILABLE -> BackendPurchaseVerificationResult.Unavailable(response)
                            else -> error("Unsupported verified HTTP status.")
                        }
                    }
                }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: IOException) {
                    BackendPurchaseVerificationResult.Failure(PURCHASE_VERIFY_REQUEST_FAILURE)
                } catch (_: IllegalArgumentException) {
                    BackendPurchaseVerificationResult.Failure(PURCHASE_VERIFY_RESPONSE_FAILURE)
                } catch (_: JSONException) {
                    BackendPurchaseVerificationResult.Failure(PURCHASE_VERIFY_RESPONSE_FAILURE)
                } catch (_: Exception) {
                    BackendPurchaseVerificationResult.Failure(PURCHASE_VERIFY_REQUEST_FAILURE)
                }
            }
        }
    }

    private fun configuredConnection(
        endpointPath: String,
        bearerToken: String,
        requestMethod: String,
    ): HttpURLConnection {
        val endpoint = URL("${config.normalizedBaseUrl}$endpointPath")
        return openConnection(endpoint).apply {
            this.requestMethod = requestMethod
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $bearerToken")
            setRequestProperty("Accept", JSON_CONTENT_TYPE)
        }
    }

    private fun parseEntitlementResponse(json: String): PremiumBackendEntitlementResponse {
        val root = JSONObject(json)
        root.requireExactFields(ENTITLEMENT_RESPONSE_FIELDS)
        root.requireSchemaVersion()
        root.requiredBoolean("stale")
        return PremiumBackendEntitlementResponse(
            hasPremium = root.requiredBoolean("hasPremium"),
            status = root.requiredBackendStatus(),
            source = root.requiredBackendSource(),
            packageName = root.requiredNullableString("packageName"),
            productId = root.requiredNullableString("productId"),
            basePlanId = root.requiredNullableString("basePlanId"),
            offerId = root.requiredNullableString("offerId"),
            expiryTime = root.requiredNullableLong("expiryTime"),
            lastVerifiedAt = root.requiredNullableLong("lastVerifiedAt"),
            purchaseTokenHash = root.requiredNullableString("purchaseTokenHash"),
            acknowledgementState =
                root.requiredNullableEnum<PremiumBackendAcknowledgementState>("acknowledgementState"),
        )
    }

    private fun parseBillingContext(json: String): BackendBillingContext {
        val root = JSONObject(json)
        root.requireExactFields(BILLING_CONTEXT_RESPONSE_FIELDS)
        root.requireSchemaVersion()
        val accountId = root.requiredString("obfuscatedExternalAccountId")
        require(OBFUSCATED_ACCOUNT_ID_PATTERN.matches(accountId)) {
            "Billing context account ID is invalid."
        }
        return BackendBillingContext(obfuscatedExternalAccountId = accountId)
    }

    private fun parseVerifyResponse(json: String): PremiumBackendEntitlementResponse {
        val root = JSONObject(json)
        root.requireExactFields(VERIFY_RESPONSE_FIELDS)
        root.requireSchemaVersion()
        val source = root.requiredBackendSource()
        require(source != PremiumEntitlementSource.ReviewerGrant) {
            "Purchase verification cannot return a reviewer grant."
        }
        val errorCode = root.requiredNullableString("errorCode")
        require(errorCode == null || errorCode in VERIFY_ERROR_CODES) {
            "Verify response error code is invalid."
        }
        return PremiumBackendEntitlementResponse(
            hasPremium = root.requiredBoolean("hasPremium"),
            status = root.requiredBackendStatus(),
            source = source,
            packageName = root.requiredNullableString("packageName"),
            productId = root.requiredNullableString("productId"),
            basePlanId = root.requiredNullableString("basePlanId"),
            offerId = root.requiredNullableString("offerId"),
            expiryTime = root.requiredNullableLong("expiryTime"),
            lastVerifiedAt = root.requiredNullableLong("lastVerifiedAt"),
            purchaseTokenHash = root.requiredNullableString("purchaseTokenHash"),
            acknowledgementState =
                root.requiredNullableEnum<PremiumBackendAcknowledgementState>("acknowledgementState"),
            retryable = root.requiredBoolean("retryable"),
            retryAfterSeconds = root.requiredNullableLong("retryAfterSeconds"),
            errorCode = errorCode,
            reason = root.requiredNullableString("reason"),
        )
    }

    private companion object {
        const val TIMEOUT_MS = 5_000
        const val TOTAL_TIMEOUT_MS = 10_000L
        const val HTTP_OK = 200
        const val HTTP_ACCEPTED = 202
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_CONFLICT = 409
        const val HTTP_UNPROCESSABLE_ENTITY = 422
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val JSON_CONTENT_TYPE = "application/json"
        const val BILLING_CONTEXT_PATH = "/v1/billing/context"
        const val BILLING_VERIFY_PATH = "/v1/billing/verify"
        const val ENTITLEMENT_REQUEST_FAILURE = "Backend entitlement request failed."
        const val ENTITLEMENT_RESPONSE_FAILURE = "Backend entitlement response was invalid."
        const val BILLING_CONTEXT_REQUEST_FAILURE = "Backend billing context request failed."
        const val BILLING_CONTEXT_RESPONSE_FAILURE = "Backend billing context response was invalid."
        const val PURCHASE_VERIFY_REQUEST_FAILURE = "Backend purchase verification request failed."
        const val PURCHASE_VERIFY_RESPONSE_FAILURE = "Backend purchase verification response was invalid."

        val VERIFY_ENVELOPE_HTTP_STATUSES = setOf(
            HTTP_OK,
            HTTP_ACCEPTED,
            HTTP_BAD_REQUEST,
            HTTP_CONFLICT,
            HTTP_UNPROCESSABLE_ENTITY,
            HTTP_SERVICE_UNAVAILABLE,
        )
        val BILLING_CONTEXT_RESPONSE_FIELDS = setOf("schemaVersion", "obfuscatedExternalAccountId")
        val ENTITLEMENT_RESPONSE_FIELDS = setOf(
            "schemaVersion",
            "hasPremium",
            "status",
            "source",
            "packageName",
            "productId",
            "basePlanId",
            "offerId",
            "expiryTime",
            "lastVerifiedAt",
            "stale",
            "purchaseTokenHash",
            "acknowledgementState",
        )
        val VERIFY_RESPONSE_FIELDS = setOf(
            "schemaVersion",
            "hasPremium",
            "status",
            "source",
            "packageName",
            "productId",
            "basePlanId",
            "offerId",
            "expiryTime",
            "lastVerifiedAt",
            "purchaseTokenHash",
            "acknowledgementState",
            "retryable",
            "retryAfterSeconds",
            "errorCode",
            "reason",
        )
        val VERIFY_ERROR_CODES = setOf(
            "INVALID_REQUEST",
            "PURCHASE_PENDING",
            "PACKAGE_NOT_ALLOWED",
            "CATALOG_MISMATCH",
            "OWNER_MISMATCH",
            "MISSING_OBFUSCATED_ACCOUNT_ID",
            "TOKEN_ALREADY_BOUND",
            "PLAY_VERIFICATION_UNAVAILABLE",
            "ACKNOWLEDGEMENT_RETRY",
            "ACKNOWLEDGEMENT_FAILED",
            "DEPENDENCY_UNAVAILABLE",
        )
        val OBFUSCATED_ACCOUNT_ID_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")
    }
}

internal class BackendEntitlementRepository(
    private val client: BackendEntitlementClient = HttpBackendEntitlementClient(),
    private val authProvider: suspend () -> BackendEntitlementAuth?,
    private val currentAccountKeyProvider: () -> String?,
    private val applyBackendEntitlement: (PremiumBackendEntitlementResponse) -> Boolean,
) {
    private val refreshSequence = AtomicLong(0L)
    private val latestSuccessfulRefreshId = AtomicLong(0L)
    private val contextSequence = AtomicLong(0L)
    private val latestSuccessfulContextId = AtomicLong(0L)
    private val verificationSequence = AtomicLong(0L)
    private val latestSuccessfulVerificationId = AtomicLong(0L)

    suspend fun refresh(): BackendEntitlementFetchResult {
        val refreshId = refreshSequence.incrementAndGet()
        client.preflight()?.let { return it }
        val requestAuth = authProvider()
        return when (val result = client.fetchEntitlement(requestAuth?.idToken)) {
            is BackendEntitlementFetchResult.Success -> {
                if (
                    requestAuth != null &&
                    currentAccountKeyProvider() == requestAuth.accountKey &&
                    markLatestSuccessfulRequest(refreshId, latestSuccessfulRefreshId)
                ) {
                    applyBackendEntitlement(result.response)
                }
                result
            }
            BackendEntitlementFetchResult.Disabled,
            BackendEntitlementFetchResult.NotSignedIn,
            is BackendEntitlementFetchResult.Failure,
            -> result
        }
    }

    suspend fun fetchBillingContext(): BackendBillingContextFetchResult {
        val requestId = contextSequence.incrementAndGet()
        client.preflight()?.let { return it.toBillingContextResult() }
        val requestAuth = authProvider()
        val result = client.fetchBillingContext(requestAuth?.idToken)
        if (result.requiresContextAccountGuard()) {
            if (requestAuth == null || currentAccountKeyProvider() != requestAuth.accountKey) {
                return BackendBillingContextFetchResult.Stale
            }
        }
        if (requestId < latestSuccessfulContextId.get()) {
            return BackendBillingContextFetchResult.Stale
        }
        return if (result is BackendBillingContextFetchResult.Success) {
            if (markLatestSuccessfulRequest(requestId, latestSuccessfulContextId)) {
                result
            } else {
                BackendBillingContextFetchResult.Stale
            }
        } else {
            result
        }
    }

    suspend fun verifyPurchase(candidate: BackendPurchaseCandidate): BackendPurchaseVerificationResult {
        val requestId = verificationSequence.incrementAndGet()
        client.preflight()?.let { return it.toPurchaseVerificationResult() }
        val requestAuth = authProvider()
        val result = client.verifyPurchase(requestAuth?.idToken, candidate)
        if (result.requiresVerificationAccountGuard()) {
            if (requestAuth == null || currentAccountKeyProvider() != requestAuth.accountKey) {
                return BackendPurchaseVerificationResult.Stale
            }
        }
        if (requestId < latestSuccessfulVerificationId.get()) {
            return BackendPurchaseVerificationResult.Stale
        }
        return if (result.isSuccessfulVerificationResponse()) {
            if (markLatestSuccessfulRequest(requestId, latestSuccessfulVerificationId)) {
                result
            } else {
                BackendPurchaseVerificationResult.Stale
            }
        } else {
            result
        }
    }

    private fun markLatestSuccessfulRequest(requestId: Long, latestSuccessfulId: AtomicLong): Boolean {
        var latestSuccessId = latestSuccessfulId.get()
        while (requestId > latestSuccessId) {
            if (latestSuccessfulId.compareAndSet(latestSuccessId, requestId)) return true
            latestSuccessId = latestSuccessfulId.get()
        }
        return false
    }
}

private fun BackendBillingContextFetchResult.requiresContextAccountGuard(): Boolean {
    return this is BackendBillingContextFetchResult.Success ||
        this is BackendBillingContextFetchResult.Failure ||
        this == BackendBillingContextFetchResult.Unauthorized ||
        this == BackendBillingContextFetchResult.Unavailable
}

private fun BackendPurchaseVerificationResult.requiresVerificationAccountGuard(): Boolean {
    return this is BackendPurchaseVerificationResult.Verified ||
        this is BackendPurchaseVerificationResult.Pending ||
        this is BackendPurchaseVerificationResult.Rejected ||
        this is BackendPurchaseVerificationResult.Unavailable ||
        this is BackendPurchaseVerificationResult.Failure ||
        this == BackendPurchaseVerificationResult.Unauthorized
}

private fun BackendPurchaseVerificationResult.isSuccessfulVerificationResponse(): Boolean {
    return this is BackendPurchaseVerificationResult.Verified ||
        this is BackendPurchaseVerificationResult.Pending
}

private fun BackendEntitlementFetchResult.toBillingContextResult(): BackendBillingContextFetchResult {
    return when (this) {
        BackendEntitlementFetchResult.Disabled -> BackendBillingContextFetchResult.Disabled
        BackendEntitlementFetchResult.NotSignedIn -> BackendBillingContextFetchResult.NotSignedIn
        is BackendEntitlementFetchResult.Failure -> BackendBillingContextFetchResult.Failure(message)
        is BackendEntitlementFetchResult.Success ->
            BackendBillingContextFetchResult.Failure("Backend billing context preflight was invalid.")
    }
}

private fun BackendEntitlementFetchResult.toPurchaseVerificationResult(): BackendPurchaseVerificationResult {
    return when (this) {
        BackendEntitlementFetchResult.Disabled -> BackendPurchaseVerificationResult.Disabled
        BackendEntitlementFetchResult.NotSignedIn -> BackendPurchaseVerificationResult.NotSignedIn
        is BackendEntitlementFetchResult.Failure -> BackendPurchaseVerificationResult.Failure(message)
        is BackendEntitlementFetchResult.Success ->
            BackendPurchaseVerificationResult.Failure("Backend purchase verification preflight was invalid.")
    }
}

private fun BackendPurchaseCandidate.toVerifyRequestJson(): String {
    return JSONObject().apply {
        put("purchaseToken", purchaseToken)
        put("packageName", packageName)
        put("productId", productId)
        put("basePlanId", basePlanId ?: JSONObject.NULL)
        put("offerId", offerId ?: JSONObject.NULL)
        put("appVersion", appVersion)
        put("versionCode", versionCode)
        put("deviceLocale", deviceLocale)
    }.toString()
}

private suspend fun <T : Any> runHttpRequestWithDeadline(
    timeoutMillis: Long,
    timeoutResult: T,
    block: suspend () -> T,
): T {
    require(timeoutMillis > 0L) { "HTTP request timeout must be positive." }
    return withTimeoutOrNull(timeoutMillis) { block() } ?: timeoutResult
}

private suspend fun <T> runCancellableHttpRequest(
    block: ((HttpURLConnection) -> Unit) -> T,
): T = suspendCancellableCoroutine { continuation ->
    val connection = AtomicReference<HttpURLConnection?>(null)
    continuation.invokeOnCancellation {
        connection.get()?.disconnect()
    }
    try {
        val result = block { openedConnection ->
            connection.set(openedConnection)
            if (!continuation.isActive) openedConnection.disconnect()
        }
        if (continuation.isActive) continuation.resume(result)
    } catch (throwable: Throwable) {
        if (continuation.isActive) continuation.resumeWithException(throwable)
    } finally {
        connection.get()?.disconnect()
    }
}

private fun HttpURLConnection.readSuccessBody(): String {
    return inputStream.readUtf8BodyWithinLimit()
}

private fun HttpURLConnection.readErrorBody(): String? {
    return errorStream?.readUtf8BodyWithinLimit()
}

private fun HttpURLConnection.closeErrorBody() {
    errorStream?.close()
}

private fun InputStream.readUtf8BodyWithinLimit(): String {
    return use { input ->
        val output = ByteArrayOutputStream(minOf(MAX_RESPONSE_BODY_BYTES, READ_BUFFER_BYTES))
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var totalBytes = 0
        while (true) {
            val bytesRead = input.read(
                buffer,
                0,
                minOf(buffer.size, MAX_RESPONSE_BODY_BYTES - totalBytes + 1),
            )
            if (bytesRead == -1) break
            if (bytesRead == 0) continue
            totalBytes += bytesRead
            require(totalBytes <= MAX_RESPONSE_BODY_BYTES) { "Backend response body is too large." }
            output.write(buffer, 0, bytesRead)
        }
        String(output.toByteArray(), StandardCharsets.UTF_8)
    }
}

private fun JSONObject.requireExactFields(expected: Set<String>) {
    require(keys().asSequence().toSet() == expected) { "JSON fields do not match the contract." }
}

private fun JSONObject.requireSchemaVersion() {
    val value = get("schemaVersion")
    require(value is Int && value == 1) { "JSON schema version is invalid." }
}

private fun JSONObject.requiredString(name: String): String {
    val value = get(name)
    require(value is String && value.isNotBlank()) { "$name must be a non-blank string." }
    return value
}

private fun JSONObject.requiredNullableString(name: String): String? {
    val value = get(name)
    if (value == JSONObject.NULL) return null
    require(value is String && value.isNotBlank()) { "$name must be a non-blank string or null." }
    return value
}

private fun JSONObject.requiredBoolean(name: String): Boolean {
    return get(name) as? Boolean ?: throw IllegalArgumentException("$name must be a boolean.")
}

private fun JSONObject.requiredNullableLong(name: String): Long? {
    return when (val value = get(name)) {
        JSONObject.NULL -> null
        is Byte -> value.toLong()
        is Short -> value.toLong()
        is Int -> value.toLong()
        is Long -> value
        else -> throw IllegalArgumentException("$name must be an integer or null.")
    }
}

private inline fun <reified T : Enum<T>> JSONObject.requiredEnum(name: String): T {
    val value = requiredString(name)
    return enumValues<T>().firstOrNull { it.name == value }
        ?: throw IllegalArgumentException("$name is invalid.")
}

private inline fun <reified T : Enum<T>> JSONObject.requiredNullableEnum(name: String): T? {
    val value = requiredNullableString(name) ?: return null
    return enumValues<T>().firstOrNull { it.name == value }
        ?: throw IllegalArgumentException("$name is invalid.")
}

private fun JSONObject.requiredBackendSource(): PremiumEntitlementSource {
    val source = requiredEnum<PremiumEntitlementSource>("source")
    require(
        source == PremiumEntitlementSource.None ||
            source == PremiumEntitlementSource.BackendVerified ||
            source == PremiumEntitlementSource.ReviewerGrant,
    ) { "Backend response source is invalid." }
    return source
}

private fun JSONObject.requiredBackendStatus(): PremiumSubscriptionStatus {
    val status = requiredEnum<PremiumSubscriptionStatus>("status")
    require(status.name in BACKEND_SUBSCRIPTION_STATUS_NAMES) {
        "Backend response status is invalid."
    }
    return status
}

private fun PremiumBackendEntitlementResponse.matchesHttpStatus(httpStatus: Int): Boolean {
    return when (httpStatus) {
        200 -> errorCode == null
        202 -> errorCode == "PURCHASE_PENDING" || errorCode == "ACKNOWLEDGEMENT_RETRY"
        400 -> errorCode == "INVALID_REQUEST"
        409 -> errorCode == "TOKEN_ALREADY_BOUND"
        422 -> errorCode in setOf(
            "PACKAGE_NOT_ALLOWED",
            "CATALOG_MISMATCH",
            "OWNER_MISMATCH",
            "MISSING_OBFUSCATED_ACCOUNT_ID",
        )
        503 -> errorCode in setOf(
            "PLAY_VERIFICATION_UNAVAILABLE",
            "ACKNOWLEDGEMENT_FAILED",
            "DEPENDENCY_UNAVAILABLE",
        )
        else -> false
    }
}

private val BACKEND_SUBSCRIPTION_STATUS_NAMES = setOf(
    "Unknown",
    "Free",
    "VerificationPending",
    "PendingPurchase",
    "Active",
    "GracePeriod",
    "CanceledActiveUntilExpiry",
    "Paused",
    "OnHold",
    "Expired",
    "Revoked",
)

private const val MAX_RESPONSE_BODY_BYTES = 64 * 1_024
private const val READ_BUFFER_BYTES = 8 * 1_024
