package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.rtdn.RtdnErrorCode
import com.brianyeh.justnotes.backend.rtdn.RtdnJson
import com.brianyeh.justnotes.backend.rtdn.RtdnParseResult
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class RtdnModelsTest {
    @Test
    fun parsesValidSubscriptionNotificationAndRedactsTokenFromToString() {
        val result = RtdnJson.parse(
            body = envelope(developerNotification(extra = ",\"futureField\":true")),
            expectedPackageName = PACKAGE_NAME,
            expectedSubscription = PUSH_SUBSCRIPTION,
        )

        val success = assertIs<RtdnParseResult.Success>(result)
        assertEquals("message-1", success.envelope.messageId)
        assertEquals("2026-07-12T00:00:00Z", success.envelope.publishTime)
        assertEquals(2, success.notification.subscription.notificationType)
        assertEquals("purchase-token-secret", success.notification.subscription.purchaseToken)
        assertEquals("just_notes_premium", success.notification.subscription.subscriptionId)
        assertFalse(success.notification.toString().contains("purchase-token-secret"))
    }

    @Test
    fun rejectsMalformedJsonAndBase64WithoutEchoingSensitiveInput() {
        val malformedJson = RtdnJson.parse("{purchase-token-secret", PACKAGE_NAME, PUSH_SUBSCRIPTION)
        val malformedBase64 = RtdnJson.parse(
            """{"message":{"messageId":"message-1","data":"%%%purchase-token-secret%%%"},"subscription":"$PUSH_SUBSCRIPTION"}""",
            PACKAGE_NAME,
            PUSH_SUBSCRIPTION,
        )

        listOf(malformedJson, malformedBase64).forEach { result ->
            val failure = assertIs<RtdnParseResult.Failure>(result)
            assertEquals(RtdnErrorCode.INVALID_ENVELOPE, failure.errorCode)
            assertFalse(failure.toString().contains("purchase-token-secret"))
        }
    }

    @Test
    fun rejectsMissingMessageIdAndOversizedBodies() {
        val missingMessageId = envelope(developerNotification()).replace("\"messageId\":\"message-1\",", "")
        val oversizedBody = "x".repeat(RtdnJson.MAX_HTTP_BODY_BYTES + 1)
        val oversizedData = envelope("x".repeat(RtdnJson.MAX_DECODED_DATA_BYTES + 1))

        assertFailure(missingMessageId, RtdnErrorCode.INVALID_ENVELOPE)
        assertFailure(oversizedBody, RtdnErrorCode.INVALID_ENVELOPE)
        assertFailure(oversizedData, RtdnErrorCode.INVALID_ENVELOPE)
    }

    @Test
    fun rejectsWrongPushSubscriptionAndPackage() {
        val wrongSubscription = RtdnJson.parse(
            envelope(developerNotification()).replace(PUSH_SUBSCRIPTION, "projects/other/subscriptions/wrong"),
            PACKAGE_NAME,
            PUSH_SUBSCRIPTION,
        )
        val wrongPackage = RtdnJson.parse(
            envelope(developerNotification().replace(PACKAGE_NAME, "com.attacker.app")),
            PACKAGE_NAME,
            PUSH_SUBSCRIPTION,
        )

        assertEquals(RtdnErrorCode.INVALID_ENVELOPE, assertIs<RtdnParseResult.Failure>(wrongSubscription).errorCode)
        assertEquals(RtdnErrorCode.PACKAGE_MISMATCH, assertIs<RtdnParseResult.Failure>(wrongPackage).errorCode)
    }

    @Test
    fun ignoresTestAndOneTimeProductNotificationsWithoutReadingThemAsSubscriptions() {
        val test = developerNotification(
            notificationBody = "\"testNotification\":{\"version\":\"1.0\"}",
        )
        val oneTime = developerNotification(
            notificationBody = "\"oneTimeProductNotification\":{\"version\":\"1.0\",\"notificationType\":1,\"purchaseToken\":\"purchase-token-secret\",\"sku\":\"sku\"}",
        )

        assertEquals(
            RtdnErrorCode.UNSUPPORTED_NOTIFICATION,
            assertIs<RtdnParseResult.Ignored>(RtdnJson.parse(envelope(test), PACKAGE_NAME, PUSH_SUBSCRIPTION)).errorCode,
        )
        assertEquals(
            RtdnErrorCode.UNSUPPORTED_NOTIFICATION,
            assertIs<RtdnParseResult.Ignored>(RtdnJson.parse(envelope(oneTime), PACKAGE_NAME, PUSH_SUBSCRIPTION)).errorCode,
        )
    }

    private fun assertFailure(body: String, code: RtdnErrorCode) {
        val result = RtdnJson.parse(body, PACKAGE_NAME, PUSH_SUBSCRIPTION)
        assertEquals(code, assertIs<RtdnParseResult.Failure>(result).errorCode)
    }

    private fun envelope(decodedData: String): String {
        val data = Base64.getEncoder().encodeToString(decodedData.toByteArray())
        return """{"message":{"messageId":"message-1","publishTime":"2026-07-12T00:00:00Z","data":"$data","attributes":{"future":"value"}},"subscription":"$PUSH_SUBSCRIPTION","futureEnvelopeField":true}"""
    }

    private fun developerNotification(
        notificationBody: String = "\"subscriptionNotification\":{\"version\":\"1.0\",\"notificationType\":2,\"purchaseToken\":\"purchase-token-secret\",\"subscriptionId\":\"just_notes_premium\",\"futureNestedField\":true}",
        extra: String = "",
    ): String {
        return """{"version":"1.0","packageName":"$PACKAGE_NAME","eventTimeMillis":"1783814400000",$notificationBody$extra}"""
    }

    companion object {
        private const val PACKAGE_NAME = "com.brianyeh.justnotes"
        private const val PUSH_SUBSCRIPTION =
            "projects/gen-lang-client-0599059254/subscriptions/just-notes-rtdn-push-dev"
    }
}
