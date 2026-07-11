package com.brianyeh.justnotes.backend.rtdn

import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementReconciliationResult
import com.brianyeh.justnotes.backend.entitlement.EntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.SubscriptionRecord
import com.brianyeh.justnotes.backend.entitlement.SubscriptionWriteResult
import com.brianyeh.justnotes.backend.play.PlaySubscriptionMapper
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerification
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerificationResult
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerifier
import com.brianyeh.justnotes.backend.play.PlayVerificationFailureCode
import com.brianyeh.justnotes.backend.security.PurchaseTokenCipher
import com.brianyeh.justnotes.backend.security.PurchaseTokenHasher
import com.brianyeh.justnotes.backend.security.TokenCiphertext
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.CancellationException

sealed class RtdnProcessResult {
    data class Completed(val outcome: String) : RtdnProcessResult()
    data class Ignored(val errorCode: RtdnErrorCode) : RtdnProcessResult()
    data class RetryableFailure(
        val errorCode: RtdnErrorCode,
        val retryAfterSeconds: Long,
    ) : RtdnProcessResult()
}

fun interface RtdnNotificationProcessor {
    suspend fun process(envelope: RtdnEnvelope, notification: RtdnNotification): RtdnProcessResult
}

class RtdnProcessor(
    private val config: BackendConfig,
    private val eventRepository: RtdnEventRepository,
    private val entitlementRepository: EntitlementRepository,
    private val tokenHasher: PurchaseTokenHasher,
    private val tokenCipher: PurchaseTokenCipher,
    private val playVerifier: PlaySubscriptionVerifier,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val processingLeaseMillis: Long = DEFAULT_PROCESSING_LEASE_MILLIS,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS,
) : RtdnNotificationProcessor {
    init {
        require(processingLeaseMillis > 0L) { "RTDN processing lease must be positive." }
        require(retryDelayMillis > 0L) { "RTDN retry delay must be positive." }
    }

    override suspend fun process(envelope: RtdnEnvelope, notification: RtdnNotification): RtdnProcessResult {
        val now = nowMillis()
        val messageIdHash = sha256UrlSafe(envelope.messageId)
        val claim = try {
            eventRepository.claim(messageIdHash, now, now + processingLeaseMillis)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            return retryableWithoutClaim(RtdnErrorCode.DEPENDENCY_UNAVAILABLE)
        }
        return when (claim) {
            RtdnClaimResult.AlreadyCompleted -> RtdnProcessResult.Completed(OUTCOME_DUPLICATE)
            RtdnClaimResult.InFlight -> retryableWithoutClaim(RtdnErrorCode.DEPENDENCY_UNAVAILABLE)
            is RtdnClaimResult.Claimed -> processClaimed(
                messageIdHash = messageIdHash,
                generation = claim.generation,
                notification = notification,
            )
        }
    }

    private suspend fun processClaimed(
        messageIdHash: String,
        generation: Long,
        notification: RtdnNotification,
    ): RtdnProcessResult {
        return try {
            val hintedHash = tokenHasher.hashPurchaseToken(notification.subscription.purchaseToken)
            val existing = entitlementRepository.getSubscription(hintedHash.value)
                ?: return release(
                    messageIdHash,
                    generation,
                    RtdnErrorCode.OWNER_BINDING_MISSING,
                )
            val decrypted = tokenCipher.decrypt(existing.toTokenCiphertext())
            val decryptedHash = tokenHasher.hashPurchaseToken(decrypted)
            if (
                decryptedHash.value != existing.purchaseTokenHash ||
                hintedHash.value != existing.purchaseTokenHash
            ) {
                return release(messageIdHash, generation, RtdnErrorCode.INTERNAL_ERROR)
            }

            when (val playResult = playVerifier.verify(config.allowedPackageName, decrypted)) {
                is PlaySubscriptionVerificationResult.Failure -> {
                    if (playResult.code == PlayVerificationFailureCode.INVALID_INPUT) {
                        completeIgnored(messageIdHash, generation, RtdnErrorCode.UNSUPPORTED_NOTIFICATION)
                    } else {
                        release(messageIdHash, generation, RtdnErrorCode.DEPENDENCY_UNAVAILABLE)
                    }
                }
                is PlaySubscriptionVerificationResult.Success -> reconcile(
                    messageIdHash = messageIdHash,
                    generation = generation,
                    existing = existing,
                    verification = playResult.verification,
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            release(messageIdHash, generation, RtdnErrorCode.DEPENDENCY_UNAVAILABLE)
        }
    }

    private suspend fun reconcile(
        messageIdHash: String,
        generation: Long,
        existing: SubscriptionRecord,
        verification: PlaySubscriptionVerification,
    ): RtdnProcessResult {
        val lineItem = verification.lineItems.singleOrNull()
        if (
            verification.purchaseTokenHash != existing.purchaseTokenHash ||
            lineItem == null ||
            config.validateCatalog(
                packageName = verification.packageName,
                productId = lineItem.productId,
                basePlanId = lineItem.basePlanId,
                offerId = lineItem.offerId,
            ) != null
        ) {
            return completeIgnored(messageIdHash, generation, RtdnErrorCode.UNSUPPORTED_NOTIFICATION)
        }
        val playAcknowledgement = PlaySubscriptionMapper.run {
            verification.toBackendAcknowledgementState()
        }
        val effectiveAcknowledgement = when {
            existing.acknowledgementState == BackendAcknowledgementState.Acknowledged ->
                BackendAcknowledgementState.Acknowledged
            playAcknowledgement == BackendAcknowledgementState.Acknowledged ->
                BackendAcknowledgementState.Acknowledged
            else -> playAcknowledgement
        }
        val effectiveStatus = if (verification.canceledButActiveUntilExpiry) {
            BackendSubscriptionStatus.CanceledActiveUntilExpiry
        } else {
            verification.subscriptionState
        }
        val updated = existing.copy(
            packageName = verification.packageName,
            productId = lineItem.productId,
            basePlanId = lineItem.basePlanId,
            offerId = lineItem.offerId,
            linkedPurchaseTokenHash = verification.linkedPurchaseTokenHash,
            acknowledgementState = effectiveAcknowledgement,
            acknowledgementAttemptCount = if (effectiveAcknowledgement == BackendAcknowledgementState.Acknowledged) {
                0
            } else {
                existing.acknowledgementAttemptCount
            },
            nextAcknowledgementAttemptAt = if (effectiveAcknowledgement == BackendAcknowledgementState.Acknowledged) {
                null
            } else {
                existing.nextAcknowledgementAttemptAt
            },
            lastAcknowledgementErrorCode = if (effectiveAcknowledgement == BackendAcknowledgementState.Acknowledged) {
                null
            } else {
                existing.lastAcknowledgementErrorCode
            },
            lastVerifiedAt = nowMillis(),
            status = effectiveStatus,
            expiryTime = lineItem.expiryTime,
        )
        when (entitlementRepository.upsertSubscriptionForOwner(updated)) {
            SubscriptionWriteResult.OwnedByAnotherUser ->
                return release(messageIdHash, generation, RtdnErrorCode.INTERNAL_ERROR)
            SubscriptionWriteResult.Created,
            SubscriptionWriteResult.UpdatedForSameOwner,
            -> Unit
        }
        when (
            entitlementRepository.reconcileEntitlementFromSubscription(
                purchaseTokenHash = existing.purchaseTokenHash,
                ownerGoogleSub = existing.ownerGoogleSub,
                now = nowMillis(),
                maxStaleMillis = config.entitlementMaxStaleMillis,
            )
        ) {
            EntitlementReconciliationResult.Missing,
            EntitlementReconciliationResult.OwnedByAnotherUser,
            -> return release(messageIdHash, generation, RtdnErrorCode.INTERNAL_ERROR)
            is EntitlementReconciliationResult.Success -> Unit
        }
        return complete(messageIdHash, generation, OUTCOME_PLAY_REQUERIED)
    }

    private suspend fun complete(
        messageIdHash: String,
        generation: Long,
        outcome: String,
    ): RtdnProcessResult {
        return if (eventRepository.complete(messageIdHash, generation, nowMillis(), outcome)) {
            RtdnProcessResult.Completed(outcome)
        } else {
            retryableWithoutClaim(RtdnErrorCode.DEPENDENCY_UNAVAILABLE)
        }
    }

    private suspend fun completeIgnored(
        messageIdHash: String,
        generation: Long,
        errorCode: RtdnErrorCode,
    ): RtdnProcessResult {
        return if (eventRepository.complete(messageIdHash, generation, nowMillis(), OUTCOME_IGNORED)) {
            RtdnProcessResult.Ignored(errorCode)
        } else {
            retryableWithoutClaim(RtdnErrorCode.DEPENDENCY_UNAVAILABLE)
        }
    }

    private suspend fun release(
        messageIdHash: String,
        generation: Long,
        errorCode: RtdnErrorCode,
    ): RtdnProcessResult {
        eventRepository.release(
            messageIdHash = messageIdHash,
            generation = generation,
            retryAt = nowMillis() + retryDelayMillis,
            errorCode = errorCode.name,
        )
        return retryableWithoutClaim(errorCode)
    }

    private fun retryableWithoutClaim(errorCode: RtdnErrorCode) =
        RtdnProcessResult.RetryableFailure(
            errorCode = errorCode,
            retryAfterSeconds = (retryDelayMillis + 999L) / 1_000L,
        )

    private fun SubscriptionRecord.toTokenCiphertext() = TokenCiphertext(
        tokenCiphertext = tokenCiphertext,
        keyVersion = keyVersion,
        encryptedAt = encryptedAt,
        encryptionAlgorithm = encryptionAlgorithm,
    )

    private fun sha256UrlSafe(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

    private companion object {
        const val DEFAULT_PROCESSING_LEASE_MILLIS = 60_000L
        const val DEFAULT_RETRY_DELAY_MILLIS = 10_000L
        const val OUTCOME_DUPLICATE = "DUPLICATE"
        const val OUTCOME_IGNORED = "IGNORED"
        const val OUTCOME_PLAY_REQUERIED = "PLAY_REQUERIED"
    }
}
