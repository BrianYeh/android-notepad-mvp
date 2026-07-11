package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerificationResult
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.auth.VerifiedGoogleIdentity
import com.brianyeh.justnotes.backend.billing.BillingVerificationOrchestrator
import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.InMemoryEntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.PurchaseOwnershipValidator
import com.brianyeh.justnotes.backend.play.PlayAcknowledgementFailureCode
import com.brianyeh.justnotes.backend.play.PlayAcknowledgementState
import com.brianyeh.justnotes.backend.play.PlayExternalAccountIdentifiers
import com.brianyeh.justnotes.backend.play.PlaySubscriptionAcknowledgementResult
import com.brianyeh.justnotes.backend.play.PlaySubscriptionAcknowledger
import com.brianyeh.justnotes.backend.play.PlaySubscriptionLineItem
import com.brianyeh.justnotes.backend.play.PlaySubscriptionState
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerification
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerificationResult
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerifier
import com.brianyeh.justnotes.backend.routes.justNotesRoutes
import com.brianyeh.justnotes.backend.security.ObfuscatedAccountIdDeriver
import com.brianyeh.justnotes.backend.security.PurchaseTokenCipher
import com.brianyeh.justnotes.backend.security.TokenCiphertext
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PurchaseFlowAcceptanceTest {
    @Test
    fun successPersistsEncryptedTokenAcknowledgesAndGrantsOnlyAtTheEnd() = testApplication {
        val fixture = Fixture()
        application { fixture.install(this) }

        val response = client.postVerify("success-token")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"hasPremium\":true")
        assertFalse(response.bodyAsText().contains("success-token"))
        assertEquals(listOf("identity", "play", "encrypt", "ack"), fixture.externalCalls)
        val subscription = assertNotNull(fixture.repository.getSubscription(safeHash("success-token")))
        assertEquals("ciphertext", subscription.tokenCiphertext)
        assertEquals(BackendAcknowledgementState.Acknowledged, subscription.acknowledgementState)
        assertEquals(true, fixture.repository.getEntitlement(GOOGLE_SUB)?.hasPremium)
    }

    @Test
    fun pendingNeverAcknowledgesOrGrantsPremium() = testApplication {
        val fixture = Fixture(
            verificationFor = { token -> verification(token, BackendSubscriptionStatus.PendingPurchase) },
        )
        application { fixture.install(this) }

        val response = client.postVerify("pending-token")

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertContains(response.bodyAsText(), "\"status\":\"PendingPurchase\"")
        assertContains(response.bodyAsText(), "\"hasPremium\":false")
        assertEquals(listOf("identity", "play", "encrypt"), fixture.externalCalls)
        assertEquals(false, fixture.repository.getEntitlement(GOOGLE_SUB)?.hasPremium)
    }

    @Test
    fun sameOwnerRepeatIsIdempotentAndOtherOwnerConflictHasNoSensitiveWrites() = testApplication {
        val fixture = Fixture()
        application { fixture.install(this) }

        assertEquals(HttpStatusCode.OK, client.postVerify("repeat-token").status)
        fixture.externalCalls.clear()
        assertEquals(HttpStatusCode.OK, client.postVerify("repeat-token").status)
        assertEquals(listOf("identity", "play"), fixture.externalCalls)

        val existing = assertNotNull(fixture.repository.getSubscription(safeHash("repeat-token")))
        fixture.repository.upsertSubscriptionForOwner(
            existing.copy(purchaseTokenHash = safeHash("conflict-token"), ownerGoogleSub = "other-owner"),
        )
        fixture.externalCalls.clear()
        val conflict = client.postVerify("conflict-token")

        assertEquals(HttpStatusCode.Conflict, conflict.status)
        assertContains(conflict.bodyAsText(), "\"errorCode\":\"TOKEN_ALREADY_BOUND\"")
        assertFalse(conflict.bodyAsText().contains("other-owner"))
        assertFalse(conflict.bodyAsText().contains("conflict-token"))
        assertEquals(listOf("identity", "play"), fixture.externalCalls)
    }

    @Test
    fun retryableAcknowledgementFailureRetriesLaterAndOnlyThenGrants() = testApplication {
        var now = NOW
        val acknowledgementResults = ArrayDeque<PlaySubscriptionAcknowledgementResult>().apply {
            add(
                PlaySubscriptionAcknowledgementResult.Failure(
                    reason = "redacted",
                    retryable = true,
                    code = PlayAcknowledgementFailureCode.PLAY_ACK_UNAVAILABLE,
                ),
            )
            add(PlaySubscriptionAcknowledgementResult.Acknowledged)
        }
        val fixture = Fixture(
            clock = { now },
            acknowledge = { acknowledgementResults.removeFirst() },
        )
        application { fixture.install(this) }

        val retry = client.postVerify("ack-token")
        assertEquals(HttpStatusCode.Accepted, retry.status)
        assertContains(retry.bodyAsText(), "\"errorCode\":\"ACKNOWLEDGEMENT_RETRY\"")
        assertEquals(false, fixture.repository.getEntitlement(GOOGLE_SUB)?.hasPremium)

        now += 901_000L
        val success = client.postVerify("ack-token")
        assertEquals(HttpStatusCode.OK, success.status)
        assertContains(success.bodyAsText(), "\"hasPremium\":true")
        assertEquals(true, fixture.repository.getEntitlement(GOOGLE_SUB)?.hasPremium)
    }

    @Test
    fun expiredRevokedAndMalformedRequestsRemainNonPremium() = testApplication {
        val fixture = Fixture(
            verificationFor = { token ->
                when (token) {
                    "expired-token" -> verification(token, BackendSubscriptionStatus.Expired)
                    "revoked-token" -> verification(token, BackendSubscriptionStatus.Revoked)
                    else -> error("unexpected token")
                }
            },
        )
        application { fixture.install(this) }

        listOf("expired-token" to "Expired", "revoked-token" to "Revoked").forEach { (token, status) ->
            val response = client.postVerify(token)
            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "\"status\":\"$status\"")
            assertContains(response.bodyAsText(), "\"hasPremium\":false")
        }

        fixture.externalCalls.clear()
        val malformed = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer tester-token")
            contentType(ContentType.Application.Json)
            setBody("{not-json")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertNull(fixture.repository.getSubscription("hash-not-json"))
        assertEquals(listOf("identity"), fixture.externalCalls)
    }

    private class Fixture(
        val repository: InMemoryEntitlementRepository = InMemoryEntitlementRepository(),
        val externalCalls: MutableList<String> = mutableListOf(),
        private val clock: () -> Long = { NOW },
        private val verificationFor: (String) -> PlaySubscriptionVerification = { token -> verification(token) },
        private val acknowledge: () -> PlaySubscriptionAcknowledgementResult = {
            PlaySubscriptionAcknowledgementResult.Acknowledged
        },
    ) {
        fun install(application: io.ktor.server.application.Application) {
            val deriver = object : ObfuscatedAccountIdDeriver {
                override fun derive(googleSub: String): String = ACCOUNT_ID
            }
            val orchestrator = BillingVerificationOrchestrator(
                config = config(),
                entitlementRepository = repository,
                playSubscriptionVerifier = object : PlaySubscriptionVerifier {
                    override suspend fun verify(
                        packageName: String,
                        purchaseToken: String,
                    ): PlaySubscriptionVerificationResult {
                        externalCalls += "play"
                        return PlaySubscriptionVerificationResult.Success(verificationFor(purchaseToken))
                    }
                },
                playSubscriptionAcknowledger = object : PlaySubscriptionAcknowledger {
                    override suspend fun acknowledge(
                        packageName: String,
                        productId: String,
                        purchaseToken: String,
                    ): PlaySubscriptionAcknowledgementResult {
                        externalCalls += "ack"
                        return acknowledge()
                    }
                },
                ownershipValidator = PurchaseOwnershipValidator(deriver),
                purchaseTokenCipher = object : PurchaseTokenCipher {
                    override fun encrypt(purchaseToken: String, now: Long): TokenCiphertext {
                        externalCalls += "encrypt"
                        return TokenCiphertext("ciphertext", "key-version", now, "GOOGLE_SYMMETRIC_ENCRYPTION")
                    }

                    override fun decrypt(ciphertext: TokenCiphertext): String = error("not used")
                },
                nowMillis = clock,
            )
            application.justNotesRoutes(
                config = config(),
                idTokenVerifier = object : GoogleIdTokenVerifier {
                    override suspend fun verify(idToken: String): GoogleIdTokenVerificationResult {
                        externalCalls += "identity"
                        return GoogleIdTokenVerificationResult.Success(VerifiedGoogleIdentity(GOOGLE_SUB))
                    }
                },
                entitlementRepository = repository,
                billingVerificationOrchestrator = orchestrator,
                obfuscatedAccountIdDeriver = deriver,
                clock = clock,
            )
        }
    }

    private companion object {
        const val NOW = 1_762_000_000_000L
        const val GOOGLE_SUB = "google-sub"
        const val ACCOUNT_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

        fun config(): BackendConfig = BackendConfig.fromEnvironment(
            mapOf("GOOGLE_WEB_CLIENT_ID" to "test-web-client.apps.googleusercontent.com"),
        )

        fun verification(
            token: String,
            status: BackendSubscriptionStatus = BackendSubscriptionStatus.Active,
        ): PlaySubscriptionVerification {
            val grantable = status == BackendSubscriptionStatus.Active
            return PlaySubscriptionVerification(
                packageName = "com.brianyeh.justnotes",
                purchaseTokenHash = safeHash(token),
                subscriptionState = status,
                playSubscriptionState = when (status) {
                    BackendSubscriptionStatus.PendingPurchase -> PlaySubscriptionState.SUBSCRIPTION_STATE_PENDING
                    BackendSubscriptionStatus.Expired -> PlaySubscriptionState.SUBSCRIPTION_STATE_EXPIRED
                    BackendSubscriptionStatus.Revoked -> PlaySubscriptionState.REVOKED
                    else -> PlaySubscriptionState.SUBSCRIPTION_STATE_ACTIVE
                },
                lineItems = listOf(
                    PlaySubscriptionLineItem(
                        productId = "just_notes_premium",
                        basePlanId = "monthly",
                        offerId = "trial10d",
                        expiryTime = if (grantable) NOW + 30L * 24L * 60L * 60L * 1_000L else NOW - 1L,
                    ),
                ),
                acknowledgementState = if (grantable) {
                    PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING.name
                } else {
                    PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED.name
                },
                playAcknowledgementState = if (grantable) {
                    PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING
                } else {
                    PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED
                },
                autoRenewing = grantable,
                linkedPurchaseTokenHash = null,
                externalAccountIdentifiers = PlayExternalAccountIdentifiers(ACCOUNT_ID),
                purchaseTokenHashVersion = "hmac-sha256-v1",
                purchaseTokenPepperVersion = "1",
            )
        }
    }
}

private fun safeHash(token: String): String = "sha256-${token.hashCode().toUInt().toString(16)}"

private suspend fun io.ktor.client.HttpClient.postVerify(token: String) = post("/v1/billing/verify") {
    header(HttpHeaders.Authorization, "Bearer tester-token")
    contentType(ContentType.Application.Json)
    setBody(
        """
        {
          "purchaseToken":"$token",
          "packageName":"com.brianyeh.justnotes",
          "productId":"just_notes_premium",
          "basePlanId":"monthly",
          "offerId":"trial10d",
          "appVersion":"1.0.7",
          "versionCode":5,
          "deviceLocale":"zh-TW"
        }
        """.trimIndent(),
    )
}
