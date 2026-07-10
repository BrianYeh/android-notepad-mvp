package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.play.AndroidPublisherGateway
import com.brianyeh.justnotes.backend.play.GooglePlaySubscriptionAcknowledger
import com.brianyeh.justnotes.backend.play.GooglePlaySubscriptionVerifier
import com.brianyeh.justnotes.backend.play.PlayAcknowledgementState
import com.brianyeh.justnotes.backend.play.PlayAcknowledgementFailureCode
import com.brianyeh.justnotes.backend.play.PlaySubscriptionAcknowledgementResult
import com.brianyeh.justnotes.backend.play.PlaySubscriptionState
import com.brianyeh.justnotes.backend.play.PlayVerificationFailureCode
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerificationResult
import com.brianyeh.justnotes.backend.security.HmacSha256PurchaseTokenHasher
import com.brianyeh.justnotes.backend.security.StaticSecretValueProvider
import com.brianyeh.justnotes.backend.security.VersionedSecret
import com.google.api.services.androidpublisher.model.AutoRenewingPlan
import com.google.api.services.androidpublisher.model.ExternalAccountIdentifiers
import com.google.api.services.androidpublisher.model.OfferDetails
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseLineItem
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GooglePlayAdapterTest {
    @Test
    fun subscriptionsV2ResponseMapsToHashedVerificationWithoutRawTokens() = runBlocking {
        val gateway = RecordingPublisherGateway(subscription())
        val verifier = GooglePlaySubscriptionVerifier(gateway, hasher())

        val result = verifier.verify(PACKAGE_NAME, PURCHASE_TOKEN)

        assertTrue(result is PlaySubscriptionVerificationResult.Success)
        val verification = result.verification
        assertEquals(PlaySubscriptionState.SUBSCRIPTION_STATE_ACTIVE, verification.playSubscriptionState)
        assertEquals(PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING, verification.playAcknowledgementState)
        assertEquals("just_notes_premium", verification.lineItems.single().productId)
        assertEquals("monthly", verification.lineItems.single().basePlanId)
        assertEquals("trial10d", verification.lineItems.single().offerId)
        assertEquals(EXPIRY_TIME, verification.lineItems.single().expiryTime)
        assertEquals("expected-obfuscated-id", verification.externalAccountIdentifiers?.obfuscatedExternalAccountId)
        assertNotEquals(PURCHASE_TOKEN, verification.purchaseTokenHash)
        assertNotEquals(LINKED_TOKEN, verification.linkedPurchaseTokenHash)
        assertEquals("hmac-sha256-v1", verification.purchaseTokenHashVersion)
        assertEquals("5", verification.purchaseTokenPepperVersion)
        assertEquals(PURCHASE_TOKEN, gateway.lastGetToken)
        assertFalse(verification.toString().contains(PURCHASE_TOKEN))
        assertFalse(verification.toString().contains(LINKED_TOKEN))
    }

    @Test
    fun unknownPlayStatesFailClosedInMapping() = runBlocking {
        val response = subscription()
            .setSubscriptionState("FUTURE_STATE")
            .setAcknowledgementState("FUTURE_ACK_STATE")
        val result = GooglePlaySubscriptionVerifier(
            RecordingPublisherGateway(response),
            hasher(),
        ).verify(PACKAGE_NAME, PURCHASE_TOKEN)

        assertTrue(result is PlaySubscriptionVerificationResult.Success)
        assertEquals(PlaySubscriptionState.UNKNOWN, result.verification.playSubscriptionState)
        assertEquals(PlayAcknowledgementState.UNKNOWN, result.verification.playAcknowledgementState)
    }

    @Test
    fun verifierFailureIsGenericAndDoesNotLeakPurchaseToken() = runBlocking {
        val gateway = object : AndroidPublisherGateway {
            override fun getSubscription(packageName: String, purchaseToken: String): SubscriptionPurchaseV2 {
                throw IOException("failed for $purchaseToken")
            }

            override fun acknowledgeSubscription(packageName: String, productId: String, purchaseToken: String) = Unit
        }

        val result = GooglePlaySubscriptionVerifier(gateway, hasher()).verify(PACKAGE_NAME, PURCHASE_TOKEN)

        assertTrue(result is PlaySubscriptionVerificationResult.Failure)
        assertEquals(PlayVerificationFailureCode.PLAY_API_UNAVAILABLE, result.code)
        assertTrue(result.retryable)
        assertFalse(result.reason.contains(PURCHASE_TOKEN))
    }

    @Test
    fun verifierInvalidInputHasStableNonRetryableCode() = runBlocking {
        val result = GooglePlaySubscriptionVerifier(
            RecordingPublisherGateway(subscription()),
            hasher(),
        ).verify(PACKAGE_NAME, "")

        assertTrue(result is PlaySubscriptionVerificationResult.Failure)
        assertEquals(PlayVerificationFailureCode.INVALID_INPUT, result.code)
        assertFalse(result.retryable)
    }

    @Test
    fun verifierRejectedRequestHasStableNonRetryableCode() = runBlocking {
        val gateway = object : AndroidPublisherGateway {
            override fun getSubscription(packageName: String, purchaseToken: String): SubscriptionPurchaseV2 {
                throw GoogleJsonResponseException(
                    HttpResponseException.Builder(404, "Not Found", HttpHeaders()),
                    GoogleJsonError(),
                )
            }

            override fun acknowledgeSubscription(packageName: String, productId: String, purchaseToken: String) = Unit
        }

        val result = GooglePlaySubscriptionVerifier(gateway, hasher()).verify(PACKAGE_NAME, PURCHASE_TOKEN)

        assertTrue(result is PlaySubscriptionVerificationResult.Failure)
        assertEquals(PlayVerificationFailureCode.PLAY_API_REJECTED, result.code)
        assertFalse(result.retryable)
        assertFalse(result.reason.contains(PURCHASE_TOKEN))
    }

    @Test
    fun acknowledgementCallsOfficialGatewayAndReturnsSuccess() = runBlocking {
        val gateway = RecordingPublisherGateway(subscription())

        val result = GooglePlaySubscriptionAcknowledger(gateway).acknowledge(
            PACKAGE_NAME,
            "just_notes_premium",
            PURCHASE_TOKEN,
        )

        assertEquals(PlaySubscriptionAcknowledgementResult.Acknowledged, result)
        assertEquals(Triple(PACKAGE_NAME, "just_notes_premium", PURCHASE_TOKEN), gateway.lastAcknowledgement)
    }

    @Test
    fun acknowledgementIoFailureIsRetryableAndRedacted() = runBlocking {
        val gateway = object : AndroidPublisherGateway {
            override fun getSubscription(packageName: String, purchaseToken: String): SubscriptionPurchaseV2 {
                return subscription()
            }

            override fun acknowledgeSubscription(packageName: String, productId: String, purchaseToken: String) {
                throw IOException("failed for $purchaseToken")
            }
        }

        val result = GooglePlaySubscriptionAcknowledger(gateway).acknowledge(
            PACKAGE_NAME,
            "just_notes_premium",
            PURCHASE_TOKEN,
        )

        assertTrue(result is PlaySubscriptionAcknowledgementResult.Failure)
        assertEquals(PlayAcknowledgementFailureCode.PLAY_ACK_UNAVAILABLE, result.code)
        assertTrue(result.retryable)
        assertFalse(result.reason.contains(PURCHASE_TOKEN))
    }

    @Test
    fun acknowledgementRateLimitIsRetryable() = runBlocking {
        val gateway = object : AndroidPublisherGateway {
            override fun getSubscription(packageName: String, purchaseToken: String): SubscriptionPurchaseV2 {
                return subscription()
            }

            override fun acknowledgeSubscription(packageName: String, productId: String, purchaseToken: String) {
                throw GoogleJsonResponseException(
                    HttpResponseException.Builder(429, "Too Many Requests", HttpHeaders()),
                    GoogleJsonError(),
                )
            }
        }

        val result = GooglePlaySubscriptionAcknowledger(gateway).acknowledge(
            PACKAGE_NAME,
            "just_notes_premium",
            PURCHASE_TOKEN,
        )

        assertTrue(result is PlaySubscriptionAcknowledgementResult.Failure)
        assertEquals(PlayAcknowledgementFailureCode.PLAY_ACK_RATE_LIMITED, result.code)
        assertTrue(result.retryable)
        assertFalse(result.reason.contains(PURCHASE_TOKEN))
    }

    @Test
    fun acknowledgementInvalidInputHasStableNonRetryableCode() = runBlocking {
        val result = GooglePlaySubscriptionAcknowledger(
            RecordingPublisherGateway(subscription()),
        ).acknowledge(PACKAGE_NAME, "just_notes_premium", "")

        assertTrue(result is PlaySubscriptionAcknowledgementResult.Failure)
        assertEquals(PlayAcknowledgementFailureCode.INVALID_INPUT, result.code)
        assertFalse(result.retryable)
    }

    private fun subscription(): SubscriptionPurchaseV2 {
        return SubscriptionPurchaseV2()
            .setSubscriptionState("SUBSCRIPTION_STATE_ACTIVE")
            .setAcknowledgementState("ACKNOWLEDGEMENT_STATE_PENDING")
            .setLinkedPurchaseToken(LINKED_TOKEN)
            .setExternalAccountIdentifiers(
                ExternalAccountIdentifiers()
                    .setObfuscatedExternalAccountId("expected-obfuscated-id")
                    .setObfuscatedExternalProfileId("profile-id"),
            )
            .setLineItems(
                listOf(
                    SubscriptionPurchaseLineItem()
                        .setProductId("just_notes_premium")
                        .setExpiryTime("2025-11-06T10:40:00Z")
                        .setOfferDetails(
                            OfferDetails()
                                .setBasePlanId("monthly")
                                .setOfferId("trial10d"),
                        )
                        .setAutoRenewingPlan(AutoRenewingPlan().setAutoRenewEnabled(true)),
                ),
            )
    }

    private fun hasher(): HmacSha256PurchaseTokenHasher {
        return HmacSha256PurchaseTokenHasher(
            StaticSecretValueProvider(VersionedSecret("test-pepper", "5")),
        )
    }

    private class RecordingPublisherGateway(
        private val response: SubscriptionPurchaseV2,
    ) : AndroidPublisherGateway {
        var lastGetToken: String? = null
        var lastAcknowledgement: Triple<String, String, String>? = null

        override fun getSubscription(packageName: String, purchaseToken: String): SubscriptionPurchaseV2 {
            lastGetToken = purchaseToken
            return response
        }

        override fun acknowledgeSubscription(packageName: String, productId: String, purchaseToken: String) {
            lastAcknowledgement = Triple(packageName, productId, purchaseToken)
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.brianyeh.justnotes"
        const val PURCHASE_TOKEN = "raw-purchase-token"
        const val LINKED_TOKEN = "raw-linked-token"
        const val EXPIRY_TIME = 1_762_425_600_000L
    }
}
