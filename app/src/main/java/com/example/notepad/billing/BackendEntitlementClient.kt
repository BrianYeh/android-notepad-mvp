package com.example.notepad.billing

import com.example.notepad.BuildConfig
import kotlinx.coroutines.CancellationException
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal data class BackendEntitlementClientConfig(
    val baseUrl: String,
    val googleWebClientId: String,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && googleWebClientId.isNotBlank()

    companion object {
        fun fromBuildConfig(): BackendEntitlementClientConfig {
            return BackendEntitlementClientConfig(
                baseUrl = BuildConfig.BACKEND_BASE_URL,
                googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
            )
        }
    }
}

internal sealed class BackendEntitlementFetchResult {
    data class Success(val response: PremiumBackendEntitlementResponse) : BackendEntitlementFetchResult()
    data object Disabled : BackendEntitlementFetchResult()
    data object NotSignedIn : BackendEntitlementFetchResult()
    data class Failure(val message: String) : BackendEntitlementFetchResult()
}

internal interface BackendEntitlementClient {
    suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult
}

internal class HttpBackendEntitlementClient(
    private val config: BackendEntitlementClientConfig = BackendEntitlementClientConfig.fromBuildConfig(),
    private val openConnection: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpURLConnection },
) : BackendEntitlementClient {
    override suspend fun fetchEntitlement(idToken: String?): BackendEntitlementFetchResult {
        if (!config.isConfigured) return BackendEntitlementFetchResult.Disabled
        val bearerToken = idToken?.takeIf { it.isNotBlank() } ?: return BackendEntitlementFetchResult.NotSignedIn
        var connection: HttpURLConnection? = null
        return try {
            val endpoint = URL("${config.baseUrl.trimEnd('/')}/v1/entitlement")
            if (endpoint.protocol != "https") {
                return BackendEntitlementFetchResult.Failure("Backend entitlement endpoint must use HTTPS.")
            }
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
    private val idTokenProvider: () -> String?,
    private val applyBackendEntitlement: (PremiumBackendEntitlementResponse) -> Boolean,
) {
    suspend fun refresh(): BackendEntitlementFetchResult {
        val requestIdToken = idTokenProvider()
        return when (val result = client.fetchEntitlement(requestIdToken)) {
            is BackendEntitlementFetchResult.Success -> {
                if (idTokenProvider() == requestIdToken) {
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
