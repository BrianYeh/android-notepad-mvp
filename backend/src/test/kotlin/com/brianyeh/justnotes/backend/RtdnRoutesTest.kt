package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.routes.justNotesRtdnRoutes
import com.brianyeh.justnotes.backend.rtdn.RtdnEnvelope
import com.brianyeh.justnotes.backend.rtdn.RtdnErrorCode
import com.brianyeh.justnotes.backend.rtdn.RtdnJson
import com.brianyeh.justnotes.backend.rtdn.RtdnNotification
import com.brianyeh.justnotes.backend.rtdn.RtdnNotificationProcessor
import com.brianyeh.justnotes.backend.rtdn.RtdnProcessResult
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RtdnRoutesTest {
    @Test
    fun disabledRouteRemainsNotImplemented() = testApplication {
        application { justNotesRtdnRoutes(BackendConfig.fromEnvironment(emptyMap()), null) }

        val response = client.post("/v1/play/rtdn")

        assertEquals(HttpStatusCode.NotImplemented, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun validDeliveryMapsCompletedAndIgnoredToNoContent() = testApplication {
        val processor = RecordingProcessor(RtdnProcessResult.Completed("PLAY_REQUERIED"))
        application { justNotesRtdnRoutes(enabledConfig(), processor) }

        val response = client.post("/v1/play/rtdn") {
            contentType(ContentType.Application.Json)
            setBody(validEnvelope())
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals(1, processor.calls)
    }

    @Test
    fun malformedAndOversizedDeliveriesReturnBadRequestWithoutCallingProcessor() = testApplication {
        val processor = RecordingProcessor(RtdnProcessResult.Completed("PLAY_REQUERIED"))
        application { justNotesRtdnRoutes(enabledConfig(), processor) }

        val malformed = client.post("/v1/play/rtdn") {
            contentType(ContentType.Application.Json)
            setBody("{purchase-token-secret")
        }
        val oversized = client.post("/v1/play/rtdn") {
            contentType(ContentType.Application.Json)
            setBody("x".repeat(RtdnJson.MAX_HTTP_BODY_BYTES + 1))
        }

        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals(HttpStatusCode.BadRequest, oversized.status)
        assertEquals(0, processor.calls)
        assertFalse(malformed.bodyAsText().contains("purchase-token-secret"))
    }

    @Test
    fun retryableProcessorFailureReturnsServiceUnavailable() = testApplication {
        val processor = RecordingProcessor(
            RtdnProcessResult.RetryableFailure(RtdnErrorCode.DEPENDENCY_UNAVAILABLE, 10),
        )
        application { justNotesRtdnRoutes(enabledConfig(), processor) }

        val response = client.post("/v1/play/rtdn") {
            contentType(ContentType.Application.Json)
            setBody(validEnvelope())
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("10", response.headers[HttpHeaders.RetryAfter])
        assertFalse(response.bodyAsText().contains("purchase-token-secret"))
    }

    private class RecordingProcessor(private val result: RtdnProcessResult) : RtdnNotificationProcessor {
        var calls = 0

        override suspend fun process(
            envelope: RtdnEnvelope,
            notification: RtdnNotification,
        ): RtdnProcessResult {
            calls += 1
            return result
        }
    }

    companion object {
        private const val PACKAGE_NAME = "com.brianyeh.justnotes"
        private const val SUBSCRIPTION =
            "projects/gen-lang-client-0599059254/subscriptions/just-notes-rtdn-push-dev"

        private fun enabledConfig() = BackendConfig.fromEnvironment(
            mapOf(
                "RTDN_ENABLED" to "true",
                "RTDN_EXPECTED_SUBSCRIPTION" to SUBSCRIPTION,
            ),
        )

        private fun validEnvelope(): String {
            val decoded =
                """{"version":"1.0","packageName":"$PACKAGE_NAME","eventTimeMillis":"1783814400000","subscriptionNotification":{"version":"1.0","notificationType":2,"purchaseToken":"purchase-token-secret","subscriptionId":"just_notes_premium"}}"""
            val data = Base64.getEncoder().encodeToString(decoded.toByteArray())
            return """{"message":{"messageId":"message-1","data":"$data"},"subscription":"$SUBSCRIPTION"}"""
        }
    }
}
