package com.example.notepad.billing

import com.example.notepad.BuildConfig
import kotlinx.coroutines.CancellationException
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

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

internal interface BackendEntitlementClient {
    fun preflight(): BackendEntitlementFetchResult? = null

    suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult
}

internal data class BackendEntitlementAuth(
    val idToken: String,
    val accountKey: String,
)

internal class HttpBackendEntitlementClient(
    private val config: BackendEntitlementClientConfig = BackendEntitlementClientConfig.fromBuildConfig(),
    private val openConnection: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpURLConnection },
) : BackendEntitlementClient {
    override fun preflight(): BackendEntitlementFetchResult? {
        if (config.isDisabled) return BackendEntitlementFetchResult.Disabled
        return config.validationError()?.let { BackendEntitlementFetchResult.Failure(it) }
    }

    override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
        preflight()?.let { return it }
        val bearerToken = idToken?.takeIf { it.isNotBlank() } ?: return BackendEntitlementFetchResult.NotSignedIn
        var connection: HttpURLConnection? = null
        return try {
            val endpoint = URL("${config.normalizedBaseUrl}/v1/entitlement")
            connection = openConnection(endpoint)
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $bearerToken")
            connection.setRequestProperty("Accept", "application/json")
            val responseCode = connection.responseCode
            if (responseCode != HTTP_OK) {
                return BackendEntitlementFetchResult.Failure("Backend entitlement request failed ($responseCode).")
            }
            BackendEntitlementFetchResult.Success(parseResponse(connection.inputStream.bufferedReader().readText()))
        } catch (exception: IOException) {
            BackendEntitlementFetchResult.Failure(exception.message ?: "Backend entitlement request failed.")
        } catch (exception: IllegalArgumentException) {
            BackendEntitlementFetchResult.Failure(exception.message ?: "Backend entitlement response was invalid.")
        } catch (exception: JSONException) {
            BackendEntitlementFetchResult.Failure(exception.message ?: "Backend entitlement response was invalid.")
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            BackendEntitlementFetchResult.Failure(exception.message ?: "Backend entitlement request failed.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseResponse(json: String): PremiumBackendEntitlementResponse {
        val root = JSONObject(json)
        return PremiumBackendEntitlementResponse(
            hasPremium = root.optBoolean("hasPremium", false),
            status = enumValueOrDefault(root.optString("status"), PremiumSubscriptionStatus.Unknown),
            source = enumValueOrDefault(root.optString("source"), PremiumEntitlementSource.None),
            packageName = root.optionalString("packageName"),
            productId = root.optionalString("productId"),
            basePlanId = root.optionalString("basePlanId"),
            offerId = root.optionalString("offerId"),
            expiryTime = root.optionalLong("expiryTime"),
            lastVerifiedAt = root.optionalLong("lastVerifiedAt"),
            purchaseTokenHash = root.optionalString("purchaseTokenHash"),
        )
    }

    private companion object {
        const val TIMEOUT_MS = 5_000
        const val HTTP_OK = 200
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

    suspend fun refresh(): BackendEntitlementFetchResult {
        val refreshId = refreshSequence.incrementAndGet()
        client.preflight()?.let { return it }
        val requestAuth = authProvider()
        return when (val result = client.fetchEntitlement(requestAuth?.idToken)) {
            is BackendEntitlementFetchResult.Success -> {
                if (
                    requestAuth != null &&
                    currentAccountKeyProvider() == requestAuth.accountKey &&
                    markLatestSuccessfulRefresh(refreshId)
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

    private fun markLatestSuccessfulRefresh(refreshId: Long): Boolean {
        var latestSuccessId = latestSuccessfulRefreshId.get()
        while (refreshId > latestSuccessId) {
            if (latestSuccessfulRefreshId.compareAndSet(latestSuccessId, refreshId)) return true
            latestSuccessId = latestSuccessfulRefreshId.get()
        }
        return false
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, defaultValue: T): T {
    return value?.takeIf { it.isNotBlank() }?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: defaultValue
}

private fun JSONObject.optionalString(name: String): String? {
    return if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null
}

private fun JSONObject.optionalLong(name: String): Long? {
    return if (has(name) && !isNull(name)) optLong(name) else null
}
