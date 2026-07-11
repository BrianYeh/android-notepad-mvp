package com.brianyeh.justnotes.backend.rtdn

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class RtdnEnvelope(
    val messageId: String,
    val publishTime: String?,
    val data: String,
) {
    override fun toString(): String =
        "RtdnEnvelope(messageId=$messageId, publishTime=$publishTime, data=[REDACTED])"
}

data class SubscriptionNotificationHint(
    val notificationType: Int,
    val purchaseToken: String,
    val subscriptionId: String?,
) {
    override fun toString(): String =
        "SubscriptionNotificationHint(notificationType=$notificationType, " +
            "purchaseToken=[REDACTED], subscriptionId=$subscriptionId)"
}

data class RtdnNotification(
    val version: String,
    val packageName: String,
    val eventTimeMillis: Long,
    val subscription: SubscriptionNotificationHint,
)

enum class RtdnErrorCode {
    INVALID_ENVELOPE,
    UNSUPPORTED_NOTIFICATION,
    PACKAGE_MISMATCH,
    OWNER_BINDING_MISSING,
    DEPENDENCY_UNAVAILABLE,
    INTERNAL_ERROR,
}

sealed class RtdnParseResult {
    data class Success(
        val envelope: RtdnEnvelope,
        val notification: RtdnNotification,
    ) : RtdnParseResult()

    data class Ignored(val errorCode: RtdnErrorCode) : RtdnParseResult()
    data class Failure(val errorCode: RtdnErrorCode) : RtdnParseResult()
}

object RtdnJson {
    const val MAX_HTTP_BODY_BYTES = 64 * 1024
    const val MAX_DECODED_DATA_BYTES = 32 * 1024

    fun parse(
        body: String,
        expectedPackageName: String,
        expectedSubscription: String,
    ): RtdnParseResult {
        if (body.toByteArray(Charsets.UTF_8).size > MAX_HTTP_BODY_BYTES) return invalid()
        if (expectedPackageName.isBlank() || expectedSubscription.isBlank()) return invalid()

        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return invalid()
        if (root.string("subscription") != expectedSubscription) return invalid()
        val message = root.objectValue("message") ?: return invalid()
        val messageId = message.string("messageId")?.takeIf { it.isNotBlank() } ?: return invalid()
        val publishTime = message.optionalString("publishTime") ?: if (message.containsKey("publishTime")) return invalid() else null
        val encodedData = message.string("data")?.takeIf { it.isNotBlank() } ?: return invalid()
        val decodedBytes = runCatching { Base64.getDecoder().decode(encodedData) }.getOrNull() ?: return invalid()
        if (decodedBytes.size > MAX_DECODED_DATA_BYTES) return invalid()
        val decoded = decodedBytes.toString(Charsets.UTF_8)
        val notificationRoot = runCatching { Json.parseToJsonElement(decoded).jsonObject }.getOrNull() ?: return invalid()
        val packageName = notificationRoot.string("packageName")?.takeIf { it.isNotBlank() } ?: return invalid()
        if (packageName != expectedPackageName) return RtdnParseResult.Failure(RtdnErrorCode.PACKAGE_MISMATCH)

        val subscriptionRoot = notificationRoot.objectValue("subscriptionNotification")
        if (subscriptionRoot == null) {
            return if (
                notificationRoot.containsKey("testNotification") ||
                notificationRoot.containsKey("oneTimeProductNotification") ||
                notificationRoot.containsKey("voidedPurchaseNotification")
            ) {
                RtdnParseResult.Ignored(RtdnErrorCode.UNSUPPORTED_NOTIFICATION)
            } else {
                invalid()
            }
        }

        val version = notificationRoot.string("version")?.takeIf { it.isNotBlank() } ?: return invalid()
        val eventTimeMillis = notificationRoot.longStringOrNumber("eventTimeMillis")
            ?.takeIf { it > 0L }
            ?: return invalid()
        val notificationType = subscriptionRoot.intNumber("notificationType")
            ?.takeIf { it > 0 }
            ?: return invalid()
        val purchaseToken = subscriptionRoot.string("purchaseToken")
            ?.takeIf { it.isNotBlank() && it.length <= 4096 }
            ?: return invalid()
        val subscriptionId = subscriptionRoot.optionalString("subscriptionId")
            ?: if (subscriptionRoot.containsKey("subscriptionId")) return invalid() else null

        return RtdnParseResult.Success(
            envelope = RtdnEnvelope(
                messageId = messageId,
                publishTime = publishTime,
                data = encodedData,
            ),
            notification = RtdnNotification(
                version = version,
                packageName = packageName,
                eventTimeMillis = eventTimeMillis,
                subscription = SubscriptionNotificationHint(
                    notificationType = notificationType,
                    purchaseToken = purchaseToken,
                    subscriptionId = subscriptionId,
                ),
            ),
        )
    }

    private fun invalid(): RtdnParseResult.Failure =
        RtdnParseResult.Failure(RtdnErrorCode.INVALID_ENVELOPE)

    private fun JsonObject.objectValue(name: String): JsonObject? =
        runCatching { this[name]?.jsonObject }.getOrNull()

    private fun JsonObject.string(name: String): String? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        if (!primitive.isString) return null
        return primitive.contentOrNull
    }

    private fun JsonObject.optionalString(name: String): String? = string(name)

    private fun JsonObject.longStringOrNumber(name: String): Long? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        return if (primitive.isString) primitive.contentOrNull?.toLongOrNull() else primitive.jsonPrimitive.longOrNull
    }

    private fun JsonObject.intNumber(name: String): Int? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.jsonPrimitive.intOrNull
    }
}
