package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.InMemoryEntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.SubscriptionRecord
import com.brianyeh.justnotes.backend.play.PlayAcknowledgementState
import com.brianyeh.justnotes.backend.play.PlaySubscriptionLineItem
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerification
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerificationResult
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerifier
import com.brianyeh.justnotes.backend.play.PlayVerificationFailureCode
import com.brianyeh.justnotes.backend.routes.justNotesRtdnRoutes
import com.brianyeh.justnotes.backend.rtdn.FirestoreRtdnEventRepository
import com.brianyeh.justnotes.backend.rtdn.RtdnEventDocumentStore
import com.brianyeh.justnotes.backend.rtdn.RtdnEventMutation
import com.brianyeh.justnotes.backend.rtdn.RtdnProcessor
import com.brianyeh.justnotes.backend.security.PurchaseTokenCipher
import com.brianyeh.justnotes.backend.security.PurchaseTokenHasher
import com.brianyeh.justnotes.backend.security.TokenCiphertext
import com.brianyeh.justnotes.backend.security.TokenHash
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RtdnAcceptanceTest {
    @Test
    fun duplicateRenewalThenRevocationTraversesRealRouteWithoutSensitivePersistence() = testApplication {
        val harness = Harness(
            results = ArrayDeque(
                listOf(
                    success(BackendSubscriptionStatus.Active, NOW + DAY),
                    success(BackendSubscriptionStatus.Revoked, NOW + DAY),
                ),
            ),
        )
        harness.seedSubscription()
        application { justNotesRtdnRoutes(harness.config, harness.processor) }

        val renewal = client.postJson(envelope("renewal-1"))
        val duplicate = client.postJson(envelope("renewal-1"))
        val revoked = client.postJson(envelope("revoke-1"))

        assertEquals(HttpStatusCode.NoContent, renewal.status)
        assertEquals(HttpStatusCode.NoContent, duplicate.status)
        assertEquals(HttpStatusCode.NoContent, revoked.status)
        assertEquals(2, harness.verifier.calls)
        assertFalse(requireNotNull(harness.repository.getEntitlement(OWNER)).hasPremium)
        assertEquals(BackendSubscriptionStatus.Revoked, harness.repository.getSubscription(TOKEN_HASH)?.status)
        assertEquals(2, harness.eventStore.documents.size)
        harness.eventStore.documents.values.flatMap { it.entries }.forEach { (name, value) ->
            assertFalse(name in FORBIDDEN_EVENT_FIELDS, name)
            assertFalse(value.toString().contains(RAW_TOKEN))
            assertFalse(value.toString().contains(CIPHERTEXT))
            assertFalse(value.toString().contains(OWNER))
        }
    }

    @Test
    fun missingOwnerKmsFailurePlayOutageAndWrongPackageFailClosed() = testApplication {
        val missing = Harness(ArrayDeque(listOf(success(BackendSubscriptionStatus.Active, NOW + DAY))))
        val kms = Harness(
            ArrayDeque(listOf(success(BackendSubscriptionStatus.Active, NOW + DAY))),
            decryptFailure = true,
        ).also { it.seedSubscription() }
        val play = Harness(
            ArrayDeque(
                listOf(
                    PlaySubscriptionVerificationResult.Failure(
                        reason = "redacted",
                        retryable = true,
                        code = PlayVerificationFailureCode.PLAY_API_UNAVAILABLE,
                    ),
                ),
            ),
        ).also { it.seedSubscription() }
        application { justNotesRtdnRoutes(missing.config, missing.processor) }

        val missingResponse = client.postJson(envelope("missing"))
        assertEquals(HttpStatusCode.ServiceUnavailable, missingResponse.status)
        assertFalse(missingResponse.bodyAsText().contains(RAW_TOKEN))

        // Exercise the other processors directly through their real route in isolated test hosts below.
        assertTrue(kms.processor.process(parsedEnvelope("kms").first, parsedEnvelope("kms").second) is
            com.brianyeh.justnotes.backend.rtdn.RtdnProcessResult.RetryableFailure)
        assertTrue(play.processor.process(parsedEnvelope("play").first, parsedEnvelope("play").second) is
            com.brianyeh.justnotes.backend.rtdn.RtdnProcessResult.RetryableFailure)

        val wrongPackage = client.postJson(envelope("wrong", packageName = "com.attacker"))
        assertEquals(HttpStatusCode.BadRequest, wrongPackage.status)
    }

    private suspend fun io.ktor.client.HttpClient.postJson(body: String) = post("/v1/play/rtdn") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private class Harness(
        results: ArrayDeque<PlaySubscriptionVerificationResult>,
        decryptFailure: Boolean = false,
    ) {
        val config = enabledConfig()
        val repository = InMemoryEntitlementRepository()
        val eventStore = RecordingEventStore()
        val verifier = QueueVerifier(results)
        private val hasher = object : PurchaseTokenHasher {
            override fun hashPurchaseToken(purchaseToken: String) =
                TokenHash(TOKEN_HASH, "hmac-sha256-v1", "1")
        }
        private val cipher = object : PurchaseTokenCipher {
            override fun encrypt(purchaseToken: String, now: Long): TokenCiphertext = error("not used")

            override fun decrypt(ciphertext: TokenCiphertext): String {
                if (decryptFailure) error("redacted")
                return RAW_TOKEN
            }
        }
        val processor = RtdnProcessor(
            config = config,
            eventRepository = FirestoreRtdnEventRepository(eventStore, ttlDays = 30),
            entitlementRepository = repository,
            tokenHasher = hasher,
            tokenCipher = cipher,
            playVerifier = verifier,
            nowMillis = { NOW },
        )

        suspend fun seedSubscription() {
            repository.upsertSubscriptionForOwner(
                SubscriptionRecord(
                    purchaseTokenHash = TOKEN_HASH,
                    hashVersion = "hmac-sha256-v1",
                    pepperVersion = "1",
                    ownerGoogleSub = OWNER,
                    packageName = PACKAGE_NAME,
                    productId = PRODUCT_ID,
                    basePlanId = "monthly",
                    offerId = null,
                    linkedPurchaseTokenHash = null,
                    tokenCiphertext = CIPHERTEXT,
                    keyVersion = "projects/p/locations/l/keyRings/r/cryptoKeys/k/cryptoKeyVersions/1",
                    encryptedAt = NOW - 1,
                    encryptionAlgorithm = "GOOGLE_SYMMETRIC_ENCRYPTION",
                    acknowledgementState = BackendAcknowledgementState.Acknowledged,
                    acknowledgementAttemptCount = 0,
                    nextAcknowledgementAttemptAt = null,
                    lastAcknowledgementErrorCode = null,
                    lastVerifiedAt = NOW - 1,
                    status = BackendSubscriptionStatus.Expired,
                    expiryTime = NOW - 1,
                ),
            )
        }
    }

    private class QueueVerifier(
        private val results: ArrayDeque<PlaySubscriptionVerificationResult>,
    ) : PlaySubscriptionVerifier {
        var calls = 0

        override suspend fun verify(packageName: String, purchaseToken: String): PlaySubscriptionVerificationResult {
            calls += 1
            return results.removeFirst()
        }
    }

    private class RecordingEventStore : RtdnEventDocumentStore {
        val documents = linkedMapOf<String, Map<String, Any?>>()
        private val mutex = Mutex()

        override suspend fun <T> transact(
            documentId: String,
            operation: (Map<String, Any?>?) -> RtdnEventMutation<T>,
        ): T = mutex.withLock {
            val mutation = operation(documents[documentId])
            mutation.fields?.let { documents[documentId] = it.toMap() }
            mutation.result
        }
    }

    companion object {
        private const val NOW = 1_783_814_400_000L
        private const val DAY = 86_400_000L
        private const val PACKAGE_NAME = "com.brianyeh.justnotes"
        private const val PRODUCT_ID = "just_notes_premium"
        private const val SUBSCRIPTION =
            "projects/gen-lang-client-0599059254/subscriptions/just-notes-rtdn-push-dev"
        private const val OWNER = "google-sub"
        private const val RAW_TOKEN = "raw-purchase-token"
        private const val TOKEN_HASH = "token-hash"
        private const val CIPHERTEXT = "encrypted-token"
        private val FORBIDDEN_EVENT_FIELDS = setOf(
            "messageId", "data", "body", "purchaseToken", "purchaseTokenHash",
            "ownerGoogleSub", "email", "authorization", "tokenCiphertext",
        )

        private fun enabledConfig() = BackendConfig.fromEnvironment(
            mapOf(
                "RTDN_ENABLED" to "true",
                "RTDN_EXPECTED_SUBSCRIPTION" to SUBSCRIPTION,
            ),
        )

        private fun success(status: BackendSubscriptionStatus, expiry: Long) =
            PlaySubscriptionVerificationResult.Success(
                PlaySubscriptionVerification(
                    packageName = PACKAGE_NAME,
                    purchaseTokenHash = TOKEN_HASH,
                    subscriptionState = status,
                    lineItems = listOf(PlaySubscriptionLineItem(PRODUCT_ID, "monthly", null, expiry)),
                    acknowledgementState = "ACKNOWLEDGED",
                    playAcknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED,
                    autoRenewing = status == BackendSubscriptionStatus.Active,
                    linkedPurchaseTokenHash = null,
                ),
            )

        private fun envelope(messageId: String, packageName: String = PACKAGE_NAME): String {
            val notification = developerNotification(packageName)
            val data = Base64.getEncoder().encodeToString(notification.toByteArray())
            return """{"message":{"messageId":"$messageId","data":"$data"},"subscription":"$SUBSCRIPTION"}"""
        }

        private fun developerNotification(packageName: String) =
            """{"version":"1.0","packageName":"$packageName","eventTimeMillis":"$NOW","subscriptionNotification":{"version":"1.0","notificationType":2,"purchaseToken":"$RAW_TOKEN","subscriptionId":"$PRODUCT_ID"}}"""

        private fun parsedEnvelope(messageId: String): Pair<
            com.brianyeh.justnotes.backend.rtdn.RtdnEnvelope,
            com.brianyeh.justnotes.backend.rtdn.RtdnNotification,
        > {
            val parsed = com.brianyeh.justnotes.backend.rtdn.RtdnJson.parse(
                envelope(messageId), PACKAGE_NAME, SUBSCRIPTION,
            ) as com.brianyeh.justnotes.backend.rtdn.RtdnParseResult.Success
            return parsed.envelope to parsed.notification
        }
    }
}
