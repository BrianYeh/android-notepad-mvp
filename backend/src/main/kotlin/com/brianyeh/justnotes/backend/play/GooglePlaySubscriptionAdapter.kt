package com.brianyeh.justnotes.backend.play

import com.brianyeh.justnotes.backend.security.PurchaseTokenHasher
import com.brianyeh.justnotes.backend.security.TokenHash
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AndroidPublisherGateway {
    fun getSubscription(packageName: String, purchaseToken: String): SubscriptionPurchaseV2
    fun acknowledgeSubscription(packageName: String, productId: String, purchaseToken: String)
}

class GoogleAndroidPublisherGateway(
    private val publisher: AndroidPublisher,
) : AndroidPublisherGateway {
    override fun getSubscription(packageName: String, purchaseToken: String): SubscriptionPurchaseV2 {
        return publisher.purchases().subscriptionsv2().get(packageName, purchaseToken).execute()
    }

    override fun acknowledgeSubscription(packageName: String, productId: String, purchaseToken: String) {
        publisher.purchases().subscriptions().acknowledge(
            packageName,
            productId,
            purchaseToken,
            SubscriptionPurchasesAcknowledgeRequest(),
        ).execute()
    }
}

class GooglePlaySubscriptionVerifier(
    private val gateway: AndroidPublisherGateway,
    private val purchaseTokenHasher: PurchaseTokenHasher,
) : PlaySubscriptionVerifier {
    override suspend fun verify(
        packageName: String,
        purchaseToken: String,
    ): PlaySubscriptionVerificationResult {
        if (packageName.isBlank() || purchaseToken.isBlank()) {
            return PlaySubscriptionVerificationResult.Failure(
                reason = "Package name and purchase token are required.",
                retryable = false,
                code = PlayVerificationFailureCode.INVALID_INPUT,
            )
        }
        return withContext(Dispatchers.IO) {
            try {
                val tokenHash = purchaseTokenHasher.hashPurchaseToken(purchaseToken)
                val response = gateway.getSubscription(packageName, purchaseToken)
                val linkedTokenHash = response.linkedPurchaseToken
                    ?.takeIf { it.isNotBlank() }
                    ?.let(purchaseTokenHasher::hashPurchaseToken)
                PlaySubscriptionVerificationResult.Success(
                    AndroidPublisherSubscriptionMapper.fromApi(
                        packageName = packageName,
                        response = response,
                        tokenHash = tokenHash,
                        linkedTokenHash = linkedTokenHash,
                    ),
                )
            } catch (exception: GoogleJsonResponseException) {
                PlaySubscriptionVerificationResult.Failure(
                    reason = "Google Play subscription verification failed.",
                    retryable = exception.statusCode == 429 || exception.statusCode >= 500,
                    code = when {
                        exception.statusCode == 429 -> PlayVerificationFailureCode.PLAY_API_RATE_LIMITED
                        exception.statusCode >= 500 -> PlayVerificationFailureCode.PLAY_API_UNAVAILABLE
                        else -> PlayVerificationFailureCode.PLAY_API_REJECTED
                    },
                )
            } catch (_: IOException) {
                PlaySubscriptionVerificationResult.Failure(
                    reason = "Google Play subscription verification failed.",
                    retryable = true,
                    code = PlayVerificationFailureCode.PLAY_API_UNAVAILABLE,
                )
            } catch (_: RuntimeException) {
                PlaySubscriptionVerificationResult.Failure(
                    reason = "Google Play subscription verification failed.",
                    retryable = false,
                    code = PlayVerificationFailureCode.PLAY_RESPONSE_INVALID,
                )
            }
        }
    }
}

class GooglePlaySubscriptionAcknowledger(
    private val gateway: AndroidPublisherGateway,
) : PlaySubscriptionAcknowledger {
    override suspend fun acknowledge(
        packageName: String,
        productId: String,
        purchaseToken: String,
    ): PlaySubscriptionAcknowledgementResult {
        if (packageName.isBlank() || productId.isBlank() || purchaseToken.isBlank()) {
            return PlaySubscriptionAcknowledgementResult.Failure(
                reason = "Subscription acknowledgement inputs are incomplete.",
                retryable = false,
                code = PlayAcknowledgementFailureCode.INVALID_INPUT,
            )
        }
        return withContext(Dispatchers.IO) {
            try {
                gateway.acknowledgeSubscription(packageName, productId, purchaseToken)
                PlaySubscriptionAcknowledgementResult.Acknowledged
            } catch (exception: GoogleJsonResponseException) {
                PlaySubscriptionAcknowledgementResult.Failure(
                    reason = "Google Play subscription acknowledgement failed.",
                    retryable = exception.statusCode == 409 ||
                        exception.statusCode == 429 ||
                        exception.statusCode >= 500,
                    code = when {
                        exception.statusCode == 409 -> PlayAcknowledgementFailureCode.PLAY_ACK_CONFLICT
                        exception.statusCode == 429 -> PlayAcknowledgementFailureCode.PLAY_ACK_RATE_LIMITED
                        exception.statusCode >= 500 -> PlayAcknowledgementFailureCode.PLAY_ACK_UNAVAILABLE
                        else -> PlayAcknowledgementFailureCode.PLAY_ACK_REJECTED
                    },
                )
            } catch (_: IOException) {
                PlaySubscriptionAcknowledgementResult.Failure(
                    reason = "Google Play subscription acknowledgement failed.",
                    retryable = true,
                    code = PlayAcknowledgementFailureCode.PLAY_ACK_UNAVAILABLE,
                )
            } catch (_: RuntimeException) {
                PlaySubscriptionAcknowledgementResult.Failure(
                    reason = "Google Play subscription acknowledgement failed.",
                    retryable = false,
                    code = PlayAcknowledgementFailureCode.PLAY_ACK_REJECTED,
                )
            }
        }
    }
}

object AndroidPublisherSubscriptionMapper {
    fun fromApi(
        packageName: String,
        response: SubscriptionPurchaseV2,
        tokenHash: TokenHash,
        linkedTokenHash: TokenHash?,
    ): PlaySubscriptionVerification {
        val snapshot = PlaySubscriptionsV2Snapshot(
            packageName = packageName,
            purchaseTokenHash = tokenHash.value,
            subscriptionState = response.subscriptionState.toPlaySubscriptionState(),
            acknowledgementState = response.acknowledgementState.toPlayAcknowledgementState(),
            lineItems = response.lineItems.orEmpty().map { lineItem ->
                PlaySubscriptionLineItem(
                    productId = lineItem.productId.orEmpty(),
                    basePlanId = lineItem.offerDetails?.basePlanId,
                    offerId = lineItem.offerDetails?.offerId,
                    expiryTime = lineItem.expiryTime?.let(::parseEpochMillis),
                )
            },
            autoRenewing = response.lineItems.orEmpty()
                .mapNotNull { it.autoRenewingPlan?.autoRenewEnabled }
                .takeIf { it.isNotEmpty() }
                ?.any { it },
            linkedPurchaseTokenHash = linkedTokenHash?.value,
            externalAccountIdentifiers = response.externalAccountIdentifiers?.let { identifiers ->
                PlayExternalAccountIdentifiers(
                    obfuscatedExternalAccountId = identifiers.obfuscatedExternalAccountId,
                    obfuscatedExternalProfileId = identifiers.obfuscatedExternalProfileId,
                )
            },
            purchaseTokenHashVersion = tokenHash.hashVersion,
            purchaseTokenPepperVersion = tokenHash.pepperVersion,
        )
        return PlaySubscriptionMapper.fromSubscriptionsV2(snapshot)
    }

    private fun String?.toPlaySubscriptionState(): PlaySubscriptionState {
        return when (this) {
            "SUBSCRIPTION_STATE_PENDING" -> PlaySubscriptionState.SUBSCRIPTION_STATE_PENDING
            "SUBSCRIPTION_STATE_ACTIVE" -> PlaySubscriptionState.SUBSCRIPTION_STATE_ACTIVE
            "SUBSCRIPTION_STATE_PAUSED" -> PlaySubscriptionState.SUBSCRIPTION_STATE_PAUSED
            "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" -> PlaySubscriptionState.SUBSCRIPTION_STATE_IN_GRACE_PERIOD
            "SUBSCRIPTION_STATE_ON_HOLD" -> PlaySubscriptionState.SUBSCRIPTION_STATE_ON_HOLD
            "SUBSCRIPTION_STATE_CANCELED" -> PlaySubscriptionState.SUBSCRIPTION_STATE_CANCELED
            "SUBSCRIPTION_STATE_EXPIRED" -> PlaySubscriptionState.SUBSCRIPTION_STATE_EXPIRED
            "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED" -> PlaySubscriptionState.PENDING_PURCHASE_CANCELED
            "SUBSCRIPTION_STATE_UNSPECIFIED" -> PlaySubscriptionState.SUBSCRIPTION_STATE_UNSPECIFIED
            else -> PlaySubscriptionState.UNKNOWN
        }
    }

    private fun String?.toPlayAcknowledgementState(): PlayAcknowledgementState {
        return when (this) {
            "ACKNOWLEDGEMENT_STATE_PENDING" -> PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING
            "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED" -> PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED
            "ACKNOWLEDGEMENT_STATE_UNSPECIFIED" -> PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_UNSPECIFIED
            else -> PlayAcknowledgementState.UNKNOWN
        }
    }

    private fun parseEpochMillis(value: String): Long {
        return runCatching { Instant.parse(value).toEpochMilli() }
            .getOrElse { throw IllegalArgumentException("Google Play returned an invalid expiry time.") }
    }
}
