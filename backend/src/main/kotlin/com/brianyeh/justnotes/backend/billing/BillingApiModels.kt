package com.brianyeh.justnotes.backend.billing

import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendEntitlementSource
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class BillingContextResponse(
    val schemaVersion: Int = 1,
    val obfuscatedExternalAccountId: String,
)

data class BillingVerifyRequest(
    val purchaseToken: String,
    val packageName: String,
    val productId: String,
    val basePlanId: String?,
    val offerId: String?,
    val appVersion: String,
    val versionCode: Long,
    val deviceLocale: String,
) {
    override fun toString(): String {
        return "BillingVerifyRequest(" +
            "purchaseToken=[REDACTED], " +
            "packageName=$packageName, " +
            "productId=$productId, " +
            "basePlanId=$basePlanId, " +
            "offerId=$offerId, " +
            "appVersion=$appVersion, " +
            "versionCode=$versionCode, " +
            "deviceLocale=$deviceLocale)"
    }
}

enum class BillingErrorCode {
    INVALID_REQUEST,
    PURCHASE_PENDING,
    PACKAGE_NOT_ALLOWED,
    CATALOG_MISMATCH,
    OWNER_MISMATCH,
    MISSING_OBFUSCATED_ACCOUNT_ID,
    TOKEN_ALREADY_BOUND,
    PLAY_VERIFICATION_UNAVAILABLE,
    ACKNOWLEDGEMENT_RETRY,
    ACKNOWLEDGEMENT_FAILED,
    DEPENDENCY_UNAVAILABLE,
}

data class BillingVerifyResponse(
    val schemaVersion: Int = 1,
    val hasPremium: Boolean,
    val status: BackendSubscriptionStatus,
    val source: BackendEntitlementSource,
    val packageName: String?,
    val productId: String?,
    val basePlanId: String?,
    val offerId: String?,
    val expiryTime: Long?,
    val lastVerifiedAt: Long?,
    val purchaseTokenHash: String?,
    val acknowledgementState: BackendAcknowledgementState?,
    val retryable: Boolean,
    val retryAfterSeconds: Long?,
    val errorCode: BillingErrorCode?,
    val reason: String?,
)

sealed class BillingVerifyRequestParseResult {
    data class Success(val request: BillingVerifyRequest) : BillingVerifyRequestParseResult()
    data class Failure(val reason: String) : BillingVerifyRequestParseResult()
}

object BillingApiJson {
    private val expectedVerifyRequestFields = setOf(
        "purchaseToken",
        "packageName",
        "productId",
        "basePlanId",
        "offerId",
        "appVersion",
        "versionCode",
        "deviceLocale",
    )

    private val packageNamePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val catalogValuePattern = Regex("^[A-Za-z0-9._-]+$")
    private val appVersionPattern = Regex("^[A-Za-z0-9._+-]+$")
    private val localePattern = Regex("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$")

    fun parseVerifyRequest(body: String): BillingVerifyRequestParseResult {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return BillingVerifyRequestParseResult.Failure(INVALID_JSON_REASON)
        if (root.keys != expectedVerifyRequestFields) {
            return BillingVerifyRequestParseResult.Failure(INVALID_JSON_REASON)
        }
        val request = runCatching {
            BillingVerifyRequest(
                purchaseToken = root.requiredString("purchaseToken"),
                packageName = root.requiredString("packageName"),
                productId = root.requiredString("productId"),
                basePlanId = root.nullableString("basePlanId"),
                offerId = root.nullableString("offerId"),
                appVersion = root.requiredString("appVersion"),
                versionCode = root.requiredLong("versionCode"),
                deviceLocale = root.requiredString("deviceLocale"),
            )
        }.getOrNull() ?: return BillingVerifyRequestParseResult.Failure(INVALID_JSON_REASON)
        validationError(request)?.let { reason ->
            return BillingVerifyRequestParseResult.Failure(reason)
        }
        return BillingVerifyRequestParseResult.Success(request)
    }

    fun encodeContextResponse(response: BillingContextResponse): String {
        return buildJsonObject {
            put("schemaVersion", response.schemaVersion)
            put("obfuscatedExternalAccountId", response.obfuscatedExternalAccountId)
        }.toString()
    }

    fun encodeVerifyResponse(response: BillingVerifyResponse): String {
        return buildJsonObject {
            put("schemaVersion", response.schemaVersion)
            put("hasPremium", response.hasPremium)
            put("status", response.status.name)
            put("source", response.source.name)
            putNullable("packageName", response.packageName)
            putNullable("productId", response.productId)
            putNullable("basePlanId", response.basePlanId)
            putNullable("offerId", response.offerId)
            putNullable("expiryTime", response.expiryTime)
            putNullable("lastVerifiedAt", response.lastVerifiedAt)
            putNullable("purchaseTokenHash", response.purchaseTokenHash)
            putNullable("acknowledgementState", response.acknowledgementState?.name)
            put("retryable", response.retryable)
            putNullable("retryAfterSeconds", response.retryAfterSeconds)
            putNullable("errorCode", response.errorCode?.name)
            putNullable("reason", response.reason)
        }.toString()
    }

    private fun validationError(request: BillingVerifyRequest): String? {
        if (request.purchaseToken.isBlank()) return "purchaseToken is required."
        if (request.purchaseToken.length > 4096) return "purchaseToken is too long."
        if (request.packageName.length > 128 || !packageNamePattern.matches(request.packageName)) {
            return "packageName is invalid."
        }
        if (!request.productId.isBoundedCatalogValue()) return "productId is invalid."
        if (request.basePlanId != null && !request.basePlanId.isBoundedCatalogValue()) {
            return "basePlanId is invalid."
        }
        if (request.offerId != null && request.basePlanId == null) return "offerId requires basePlanId."
        if (request.offerId != null && !request.offerId.isBoundedCatalogValue()) {
            return "offerId is invalid."
        }
        if (
            request.appVersion.isBlank() ||
            request.appVersion.length > 32 ||
            !appVersionPattern.matches(request.appVersion)
        ) {
            return "appVersion is invalid."
        }
        if (request.versionCode <= 0) return "versionCode must be positive."
        if (
            request.deviceLocale.length > 35 ||
            !localePattern.matches(request.deviceLocale)
        ) {
            return "deviceLocale is invalid."
        }
        return null
    }

    private fun String.isBoundedCatalogValue(): Boolean {
        return length in 1..128 && catalogValuePattern.matches(this)
    }

    private fun JsonObject.requiredString(name: String): String {
        val primitive = this[name] as? JsonPrimitive
            ?: throw IllegalArgumentException("$name must be a string.")
        if (!primitive.isString) throw IllegalArgumentException("$name must be a string.")
        return primitive.contentOrNull
            ?: throw IllegalArgumentException("$name must be a string.")
    }

    private fun JsonObject.nullableString(name: String): String? {
        val value = this[name]
        if (value == JsonNull) return null
        val primitive = value as? JsonPrimitive
            ?: throw IllegalArgumentException("$name must be a string or null.")
        if (!primitive.isString) throw IllegalArgumentException("$name must be a string or null.")
        return primitive.jsonPrimitive.contentOrNull
            ?: throw IllegalArgumentException("$name must be a string or null.")
    }

    private fun JsonObject.requiredLong(name: String): Long {
        val primitive = this[name] as? JsonPrimitive
            ?: throw IllegalArgumentException("$name must be a number.")
        if (primitive.isString) throw IllegalArgumentException("$name must be a number.")
        return primitive.jsonPrimitive.longOrNull
            ?: throw IllegalArgumentException("$name must be a number.")
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(name: String, value: String?) {
        if (value == null) put(name, JsonNull) else put(name, value)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(name: String, value: Long?) {
        if (value == null) put(name, JsonNull) else put(name, value)
    }

    private const val INVALID_JSON_REASON = "Request JSON is invalid."
}
