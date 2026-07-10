package com.brianyeh.justnotes.backend.billing

import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.AcknowledgementCompletionResult
import com.brianyeh.justnotes.backend.entitlement.AcknowledgementClaimResult
import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendEntitlementSource
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.entitlement.EntitlementReconciliationResult
import com.brianyeh.justnotes.backend.entitlement.EntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.PurchaseOwnershipErrorCode
import com.brianyeh.justnotes.backend.entitlement.PurchaseOwnershipResult
import com.brianyeh.justnotes.backend.entitlement.PurchaseOwnershipValidator
import com.brianyeh.justnotes.backend.entitlement.SubscriptionRecord
import com.brianyeh.justnotes.backend.entitlement.SubscriptionWriteResult
import com.brianyeh.justnotes.backend.entitlement.reconciledEntitlement
import com.brianyeh.justnotes.backend.play.PlaySubscriptionAcknowledgementResult
import com.brianyeh.justnotes.backend.play.PlayAcknowledgementFailureCode
import com.brianyeh.justnotes.backend.play.PlaySubscriptionAcknowledger
import com.brianyeh.justnotes.backend.play.PlaySubscriptionLineItem
import com.brianyeh.justnotes.backend.play.PlaySubscriptionMapper
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerification
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerificationResult
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerifier
import com.brianyeh.justnotes.backend.security.PurchaseTokenCipher
import kotlinx.coroutines.CancellationException
import kotlin.math.min

data class BillingVerificationOutcome(
    val httpStatus: Int,
    val response: BillingVerifyResponse,
)

class BillingVerificationOrchestrator(
    private val config: BackendConfig,
    private val entitlementRepository: EntitlementRepository,
    private val playSubscriptionVerifier: PlaySubscriptionVerifier,
    private val playSubscriptionAcknowledger: PlaySubscriptionAcknowledger,
    private val ownershipValidator: PurchaseOwnershipValidator,
    private val purchaseTokenCipher: PurchaseTokenCipher,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun verify(
        googleSub: String,
        request: BillingVerifyRequest,
    ): BillingVerificationOutcome {
        val requestNow = nowMillis()
        validateRequestHints(request, requestNow)?.let { return it }

        val playObservedAt = nowMillis()
        val verification = when (
            val result = callPlayVerifier(request.purchaseToken)
                ?: return dependencyFailure(now = nowMillis())
        ) {
            is PlaySubscriptionVerificationResult.Failure -> {
                return errorOutcome(
                    httpStatus = HTTP_SERVICE_UNAVAILABLE,
                    now = nowMillis(),
                    retryable = result.retryable,
                    errorCode = BillingErrorCode.PLAY_VERIFICATION_UNAVAILABLE,
                    reason = PLAY_UNAVAILABLE_REASON,
                )
            }
            is PlaySubscriptionVerificationResult.Success -> result.verification
        }

        val lineItem = authoritativeLineItem(verification)
            ?: return catalogFailure(nowMillis(), verification, null)
        if (!requestHintsMatchPlay(request, lineItem)) {
            return catalogFailure(nowMillis(), verification, lineItem)
        }

        val ownership = try {
            ownershipValidator.validate(googleSub, verification.externalAccountIdentifiers)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            return dependencyFailure(nowMillis(), verification, lineItem)
        }
        if (ownership is PurchaseOwnershipResult.Failure) {
            return errorOutcome(
                httpStatus = HTTP_UNPROCESSABLE_ENTITY,
                now = nowMillis(),
                status = BackendSubscriptionStatus.VerificationPending,
                source = BackendEntitlementSource.BackendVerified,
                packageName = verification.packageName,
                lineItem = lineItem,
                purchaseTokenHash = verification.purchaseTokenHash,
                acknowledgementState = verification.backendAcknowledgementState(),
                retryable = false,
                errorCode = when (ownership.code) {
                    PurchaseOwnershipErrorCode.OWNER_MISMATCH -> BillingErrorCode.OWNER_MISMATCH
                    PurchaseOwnershipErrorCode.MISSING_OBFUSCATED_ACCOUNT_ID ->
                        BillingErrorCode.MISSING_OBFUSCATED_ACCOUNT_ID
                },
                reason = OWNERSHIP_FAILURE_REASON,
            )
        }

        val existing = try {
            entitlementRepository.getSubscription(verification.purchaseTokenHash)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            return dependencyFailure(nowMillis(), verification, lineItem)
        }
        if (existing != null && existing.ownerGoogleSub != googleSub) {
            return tokenConflict(nowMillis(), verification, lineItem)
        }

        var subscription = buildSubscriptionRecord(
            googleSub = googleSub,
            request = request,
            verification = verification,
            lineItem = lineItem,
            existing = existing,
            playObservedAt = playObservedAt,
        ) ?: return dependencyFailure(nowMillis(), verification, lineItem)

        when (val persisted = writeAndReloadSubscription(subscription)) {
            SubscriptionPersistenceResult.Conflict -> return tokenConflict(nowMillis(), verification, lineItem)
            SubscriptionPersistenceResult.Failure -> return dependencyFailure(nowMillis(), verification, lineItem)
            is SubscriptionPersistenceResult.Success -> subscription = persisted.record
        }

        if (subscription.acknowledgementState == BackendAcknowledgementState.Pending) {
            val claimNow = nowMillis()
            when (
                val claim = claimAcknowledgement(
                    purchaseTokenHash = subscription.purchaseTokenHash,
                    ownerGoogleSub = googleSub,
                    now = claimNow,
                    leaseUntil = claimNow + ACKNOWLEDGEMENT_LEASE_MILLIS,
                ) ?: return dependencyFailure(nowMillis(), verification, lineItem)
            ) {
                AcknowledgementClaimResult.Missing ->
                    return dependencyFailure(nowMillis(), verification, lineItem)
                AcknowledgementClaimResult.OwnedByAnotherUser ->
                    return tokenConflict(nowMillis(), verification, lineItem)
                is AcknowledgementClaimResult.AlreadyAcknowledged -> subscription = claim.record
                is AcknowledgementClaimResult.TerminalFailure -> subscription = claim.record
                is AcknowledgementClaimResult.NotEligible -> subscription = claim.record
                is AcknowledgementClaimResult.NotDue -> subscription = claim.record
                is AcknowledgementClaimResult.Claimed -> {
                    subscription = claim.record
                    val acknowledgement = callAcknowledger(request.purchaseToken, subscription.productId)
                    val acknowledgementCompletedAt = nowMillis()
                    val attemptCount = if (acknowledgement is PlaySubscriptionAcknowledgementResult.Failure) {
                        subscription.acknowledgementAttemptCount + 1
                    } else {
                        0
                    }
                    val completion = completeAcknowledgement(
                        purchaseTokenHash = subscription.purchaseTokenHash,
                        ownerGoogleSub = googleSub,
                        generation = claim.generation,
                        acknowledgementState = when (acknowledgement) {
                            PlaySubscriptionAcknowledgementResult.Acknowledged ->
                                BackendAcknowledgementState.Acknowledged
                            is PlaySubscriptionAcknowledgementResult.Failure -> if (acknowledgement.retryable) {
                                BackendAcknowledgementState.Pending
                            } else {
                                BackendAcknowledgementState.Failed
                            }
                        },
                        acknowledgementAttemptCount = attemptCount,
                        nextAcknowledgementAttemptAt = if (
                            acknowledgement is PlaySubscriptionAcknowledgementResult.Failure &&
                            acknowledgement.retryable
                        ) {
                            acknowledgementCompletedAt + retryDelayMillis(attemptCount)
                        } else {
                            null
                        },
                        lastAcknowledgementErrorCode =
                            (acknowledgement as? PlaySubscriptionAcknowledgementResult.Failure)?.code?.name,
                    ) ?: return dependencyFailure(nowMillis(), verification, lineItem)
                    subscription = when (completion) {
                        AcknowledgementCompletionResult.Missing ->
                            return dependencyFailure(nowMillis(), verification, lineItem)
                        AcknowledgementCompletionResult.OwnedByAnotherUser ->
                            return tokenConflict(nowMillis(), verification, lineItem)
                        is AcknowledgementCompletionResult.Applied -> completion.record
                        is AcknowledgementCompletionResult.Stale -> completion.record
                    }
                }
            }
        }

        val reconciled = reconcileEntitlement(
            purchaseTokenHash = subscription.purchaseTokenHash,
            ownerGoogleSub = googleSub,
            now = nowMillis(),
        ) ?: return dependencyFailure(nowMillis(), verification, lineItem)
        return when (reconciled) {
            EntitlementReconciliationResult.Missing -> dependencyFailure(nowMillis(), verification, lineItem)
            EntitlementReconciliationResult.OwnedByAnotherUser -> tokenConflict(nowMillis(), verification, lineItem)
            is EntitlementReconciliationResult.Success -> reconciledOutcome(
                entitlement = reconciled.entitlement,
                subscription = reconciled.subscription,
                now = nowMillis(),
            )
        }
    }

    private fun validateRequestHints(
        request: BillingVerifyRequest,
        now: Long,
    ): BillingVerificationOutcome? {
        if (request.packageName != config.allowedPackageName) {
            return errorOutcome(
                httpStatus = HTTP_UNPROCESSABLE_ENTITY,
                now = now,
                retryable = false,
                errorCode = BillingErrorCode.PACKAGE_NOT_ALLOWED,
                reason = PACKAGE_FAILURE_REASON,
            )
        }
        if (request.productId != config.allowedProductId) {
            return errorOutcome(
                httpStatus = HTTP_UNPROCESSABLE_ENTITY,
                now = now,
                retryable = false,
                errorCode = BillingErrorCode.CATALOG_MISMATCH,
                reason = CATALOG_FAILURE_REASON,
            )
        }
        if (request.basePlanId == null && request.offerId != null) {
            return errorOutcome(
                httpStatus = HTTP_UNPROCESSABLE_ENTITY,
                now = now,
                retryable = false,
                errorCode = BillingErrorCode.CATALOG_MISMATCH,
                reason = CATALOG_FAILURE_REASON,
            )
        }
        if (
            request.basePlanId != null &&
            config.validateCatalog(
                packageName = request.packageName,
                productId = request.productId,
                basePlanId = request.basePlanId,
                offerId = request.offerId,
            ) != null
        ) {
            return errorOutcome(
                httpStatus = HTTP_UNPROCESSABLE_ENTITY,
                now = now,
                retryable = false,
                errorCode = BillingErrorCode.CATALOG_MISMATCH,
                reason = CATALOG_FAILURE_REASON,
            )
        }
        return null
    }

    private suspend fun callPlayVerifier(purchaseToken: String): PlaySubscriptionVerificationResult? {
        return try {
            playSubscriptionVerifier.verify(config.allowedPackageName, purchaseToken)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun callAcknowledger(
        purchaseToken: String,
        productId: String,
    ): PlaySubscriptionAcknowledgementResult {
        return try {
            playSubscriptionAcknowledger.acknowledge(
                packageName = config.allowedPackageName,
                productId = productId,
                purchaseToken = purchaseToken,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            PlaySubscriptionAcknowledgementResult.Failure(
                reason = ACK_FAILURE_REASON,
                retryable = true,
                code = PlayAcknowledgementFailureCode.PLAY_ACK_UNAVAILABLE,
            )
        }
    }

    private fun authoritativeLineItem(verification: PlaySubscriptionVerification): PlaySubscriptionLineItem? {
        val lineItem = verification.lineItems.singleOrNull() ?: return null
        return lineItem.takeIf {
            config.validateCatalog(
                packageName = verification.packageName,
                productId = it.productId,
                basePlanId = it.basePlanId,
                offerId = it.offerId,
            ) == null
        }
    }

    private fun requestHintsMatchPlay(
        request: BillingVerifyRequest,
        lineItem: PlaySubscriptionLineItem,
    ): Boolean {
        if (request.productId != lineItem.productId) return false
        val basePlanHint = request.basePlanId ?: return request.offerId == null
        return basePlanHint == lineItem.basePlanId && request.offerId == lineItem.offerId
    }

    private fun buildSubscriptionRecord(
        googleSub: String,
        request: BillingVerifyRequest,
        verification: PlaySubscriptionVerification,
        lineItem: PlaySubscriptionLineItem,
        existing: SubscriptionRecord?,
        playObservedAt: Long,
    ): SubscriptionRecord? {
        val playAcknowledgement = verification.backendAcknowledgementState()
        val effectiveAcknowledgement = when {
            existing?.acknowledgementState == BackendAcknowledgementState.Acknowledged ->
                BackendAcknowledgementState.Acknowledged
            playAcknowledgement == BackendAcknowledgementState.Acknowledged ->
                BackendAcknowledgementState.Acknowledged
            else -> playAcknowledgement
        }
        if (existing != null) {
            return existing.copy(
                packageName = verification.packageName,
                productId = lineItem.productId,
                basePlanId = lineItem.basePlanId,
                offerId = lineItem.offerId,
                linkedPurchaseTokenHash = verification.linkedPurchaseTokenHash,
                status = verification.effectiveStatus(),
                expiryTime = lineItem.expiryTime,
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
                lastVerifiedAt = playObservedAt,
            )
        }

        val hashVersion = verification.purchaseTokenHashVersion?.takeIf { it.isNotBlank() } ?: return null
        val pepperVersion = verification.purchaseTokenPepperVersion?.takeIf { it.isNotBlank() } ?: return null
        val encryptionStartedAt = nowMillis()
        val encrypted = try {
            purchaseTokenCipher.encrypt(request.purchaseToken, encryptionStartedAt)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            return null
        }
        return SubscriptionRecord(
            purchaseTokenHash = verification.purchaseTokenHash,
            hashVersion = hashVersion,
            pepperVersion = pepperVersion,
            ownerGoogleSub = googleSub,
            packageName = verification.packageName,
            productId = lineItem.productId,
            basePlanId = lineItem.basePlanId,
            offerId = lineItem.offerId,
            linkedPurchaseTokenHash = verification.linkedPurchaseTokenHash,
            tokenCiphertext = encrypted.tokenCiphertext,
            keyVersion = encrypted.keyVersion,
            encryptedAt = encrypted.encryptedAt,
            encryptionAlgorithm = encrypted.encryptionAlgorithm,
            acknowledgementState = effectiveAcknowledgement,
            acknowledgementAttemptCount = 0,
            nextAcknowledgementAttemptAt = null,
            lastAcknowledgementErrorCode = null,
            lastVerifiedAt = playObservedAt,
            status = verification.effectiveStatus(),
            expiryTime = lineItem.expiryTime,
            acknowledgementClaimGeneration = 0,
            acknowledgementLeaseUntil = null,
        )
    }

    private suspend fun writeAndReloadSubscription(record: SubscriptionRecord): SubscriptionPersistenceResult {
        return try {
            when (entitlementRepository.upsertSubscriptionForOwner(record)) {
                SubscriptionWriteResult.OwnedByAnotherUser -> SubscriptionPersistenceResult.Conflict
                SubscriptionWriteResult.Created,
                SubscriptionWriteResult.UpdatedForSameOwner,
                -> loadSubscription(record.purchaseTokenHash, record.ownerGoogleSub).toPersistenceResult()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            SubscriptionPersistenceResult.Failure
        }
    }

    private suspend fun claimAcknowledgement(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        now: Long,
        leaseUntil: Long,
    ): AcknowledgementClaimResult? {
        return try {
            entitlementRepository.claimSubscriptionAcknowledgement(
                purchaseTokenHash = purchaseTokenHash,
                ownerGoogleSub = ownerGoogleSub,
                now = now,
                leaseUntil = leaseUntil,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun completeAcknowledgement(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        generation: Long,
        acknowledgementState: BackendAcknowledgementState,
        acknowledgementAttemptCount: Int,
        nextAcknowledgementAttemptAt: Long?,
        lastAcknowledgementErrorCode: String?,
    ): AcknowledgementCompletionResult? {
        return try {
            entitlementRepository.completeSubscriptionAcknowledgement(
                purchaseTokenHash = purchaseTokenHash,
                ownerGoogleSub = ownerGoogleSub,
                generation = generation,
                acknowledgementState = acknowledgementState,
                acknowledgementAttemptCount = acknowledgementAttemptCount,
                nextAcknowledgementAttemptAt = nextAcknowledgementAttemptAt,
                lastAcknowledgementErrorCode = lastAcknowledgementErrorCode,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun reconcileEntitlement(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        now: Long,
    ): EntitlementReconciliationResult? {
        return try {
            entitlementRepository.reconcileEntitlementFromSubscription(
                purchaseTokenHash = purchaseTokenHash,
                ownerGoogleSub = ownerGoogleSub,
                now = now,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadSubscription(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
    ): SubscriptionLoadResult {
        return try {
            val record = entitlementRepository.getSubscription(purchaseTokenHash)
                ?: return SubscriptionLoadResult.Failure
            if (record.ownerGoogleSub != ownerGoogleSub) {
                SubscriptionLoadResult.Conflict
            } else {
                SubscriptionLoadResult.Success(record)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            SubscriptionLoadResult.Failure
        }
    }

    private fun SubscriptionLoadResult.toPersistenceResult(): SubscriptionPersistenceResult {
        return when (this) {
            SubscriptionLoadResult.Conflict -> SubscriptionPersistenceResult.Conflict
            SubscriptionLoadResult.Failure -> SubscriptionPersistenceResult.Failure
            is SubscriptionLoadResult.Success -> SubscriptionPersistenceResult.Success(record)
        }
    }

    private fun PlaySubscriptionVerification.backendAcknowledgementState(): BackendAcknowledgementState {
        return PlaySubscriptionMapper.run { toBackendAcknowledgementState() }
    }

    private fun PlaySubscriptionVerification.effectiveStatus(): BackendSubscriptionStatus {
        return if (canceledButActiveUntilExpiry) {
            BackendSubscriptionStatus.CanceledActiveUntilExpiry
        } else {
            subscriptionState
        }
    }

    private fun BackendSubscriptionStatus.isGrantable(): Boolean {
        return this == BackendSubscriptionStatus.Active ||
            this == BackendSubscriptionStatus.GracePeriod ||
            this == BackendSubscriptionStatus.CanceledActiveUntilExpiry
    }

    private fun catalogFailure(
        now: Long,
        verification: PlaySubscriptionVerification,
        lineItem: PlaySubscriptionLineItem?,
    ): BillingVerificationOutcome {
        return errorOutcome(
            httpStatus = HTTP_UNPROCESSABLE_ENTITY,
            now = now,
            source = BackendEntitlementSource.BackendVerified,
            packageName = verification.packageName,
            lineItem = lineItem,
            purchaseTokenHash = verification.purchaseTokenHash,
            acknowledgementState = verification.backendAcknowledgementState(),
            retryable = false,
            errorCode = BillingErrorCode.CATALOG_MISMATCH,
            reason = CATALOG_FAILURE_REASON,
        )
    }

    private fun tokenConflict(
        now: Long,
        verification: PlaySubscriptionVerification,
        lineItem: PlaySubscriptionLineItem,
    ): BillingVerificationOutcome {
        return errorOutcome(
            httpStatus = HTTP_CONFLICT,
            now = now,
            status = BackendSubscriptionStatus.VerificationPending,
            source = BackendEntitlementSource.BackendVerified,
            packageName = verification.packageName,
            lineItem = lineItem,
            purchaseTokenHash = verification.purchaseTokenHash,
            acknowledgementState = verification.backendAcknowledgementState(),
            retryable = false,
            errorCode = BillingErrorCode.TOKEN_ALREADY_BOUND,
            reason = TOKEN_CONFLICT_REASON,
        )
    }

    private fun dependencyFailure(
        now: Long,
        verification: PlaySubscriptionVerification? = null,
        lineItem: PlaySubscriptionLineItem? = null,
    ): BillingVerificationOutcome {
        return errorOutcome(
            httpStatus = HTTP_SERVICE_UNAVAILABLE,
            now = now,
            source = if (verification == null) BackendEntitlementSource.None else BackendEntitlementSource.BackendVerified,
            packageName = verification?.packageName,
            lineItem = lineItem,
            purchaseTokenHash = verification?.purchaseTokenHash,
            acknowledgementState = verification?.backendAcknowledgementState(),
            retryable = true,
            errorCode = BillingErrorCode.DEPENDENCY_UNAVAILABLE,
            reason = DEPENDENCY_FAILURE_REASON,
        )
    }

    private fun errorOutcome(
        httpStatus: Int,
        now: Long,
        status: BackendSubscriptionStatus = BackendSubscriptionStatus.Unknown,
        source: BackendEntitlementSource = BackendEntitlementSource.None,
        packageName: String? = null,
        lineItem: PlaySubscriptionLineItem? = null,
        purchaseTokenHash: String? = null,
        acknowledgementState: BackendAcknowledgementState? = null,
        retryable: Boolean,
        retryAfterSeconds: Long? = null,
        errorCode: BillingErrorCode,
        reason: String,
    ): BillingVerificationOutcome {
        return BillingVerificationOutcome(
            httpStatus = httpStatus,
            response = BillingVerifyResponse(
                hasPremium = false,
                status = status,
                source = source,
                packageName = packageName,
                productId = lineItem?.productId,
                basePlanId = lineItem?.basePlanId,
                offerId = lineItem?.offerId,
                expiryTime = lineItem?.expiryTime,
                lastVerifiedAt = now,
                purchaseTokenHash = purchaseTokenHash,
                acknowledgementState = acknowledgementState,
                retryable = retryable,
                retryAfterSeconds = retryAfterSeconds,
                errorCode = errorCode,
                reason = reason,
            ),
        )
    }

    private fun EntitlementRecord.toVerifyResponse(
        acknowledgementState: BackendAcknowledgementState,
        retryable: Boolean,
        retryAfterSeconds: Long?,
        errorCode: BillingErrorCode?,
        reason: String?,
    ): BillingVerifyResponse {
        return BillingVerifyResponse(
            hasPremium = hasPremium,
            status = status,
            source = source,
            packageName = packageName,
            productId = productId,
            basePlanId = basePlanId,
            offerId = offerId,
            expiryTime = expiryTime,
            lastVerifiedAt = lastVerifiedAt,
            purchaseTokenHash = purchaseTokenHash,
            acknowledgementState = acknowledgementState,
            retryable = retryable,
            retryAfterSeconds = retryAfterSeconds,
            errorCode = errorCode,
            reason = reason,
        )
    }

    private fun reconciledOutcome(
        entitlement: EntitlementRecord,
        subscription: SubscriptionRecord,
        now: Long,
    ): BillingVerificationOutcome {
        val acknowledgementState = entitlement.acknowledgementState ?: subscription.acknowledgementState
        val submittedEntitlement = subscription.reconciledEntitlement(now)
        val submittedAcknowledgementState = submittedEntitlement.acknowledgementState ?: subscription.acknowledgementState
        val retryAt = listOfNotNull(
            subscription.nextAcknowledgementAttemptAt,
            subscription.acknowledgementLeaseUntil,
        ).maxOrNull()
        val control = when (submittedEntitlement.status) {
            BackendSubscriptionStatus.PendingPurchase -> ResponseControl(
                httpStatus = HTTP_ACCEPTED,
                retryable = false,
                errorCode = BillingErrorCode.PURCHASE_PENDING,
                reason = PURCHASE_PENDING_REASON,
            )
            BackendSubscriptionStatus.VerificationPending -> when (submittedAcknowledgementState) {
                BackendAcknowledgementState.Pending -> ResponseControl(
                    httpStatus = HTTP_ACCEPTED,
                    retryable = true,
                    retryAfterSeconds = retryAt?.let {
                        millisToCeilingSeconds((it - now).coerceAtLeast(0L))
                    },
                    errorCode = BillingErrorCode.ACKNOWLEDGEMENT_RETRY,
                    reason = ACK_RETRY_REASON,
                )
                BackendAcknowledgementState.Failed,
                BackendAcknowledgementState.Unknown,
                BackendAcknowledgementState.NotRequired,
                -> ResponseControl(
                    httpStatus = HTTP_SERVICE_UNAVAILABLE,
                    retryable = false,
                    errorCode = BillingErrorCode.ACKNOWLEDGEMENT_FAILED,
                    reason = ACK_FAILURE_REASON,
                )
                BackendAcknowledgementState.Acknowledged -> ResponseControl(httpStatus = HTTP_OK)
            }
            else -> ResponseControl(httpStatus = HTTP_OK)
        }
        return BillingVerificationOutcome(
            httpStatus = control.httpStatus,
            response = entitlement.toVerifyResponse(
                acknowledgementState = acknowledgementState,
                retryable = control.retryable,
                retryAfterSeconds = control.retryAfterSeconds,
                errorCode = control.errorCode,
                reason = control.reason,
            ),
        )
    }

    private fun retryDelayMillis(attemptCount: Int): Long {
        val shift = (attemptCount - 1).coerceIn(0, 5)
        return min(15L * 60_000L * (1L shl shift), 6L * 60L * 60_000L)
    }

    private fun millisToCeilingSeconds(millis: Long): Long {
        return (millis + 999L) / 1_000L
    }

    private sealed class SubscriptionPersistenceResult {
        data class Success(val record: SubscriptionRecord) : SubscriptionPersistenceResult()
        data object Conflict : SubscriptionPersistenceResult()
        data object Failure : SubscriptionPersistenceResult()
    }

    private sealed class SubscriptionLoadResult {
        data class Success(val record: SubscriptionRecord) : SubscriptionLoadResult()
        data object Conflict : SubscriptionLoadResult()
        data object Failure : SubscriptionLoadResult()
    }

    private data class ResponseControl(
        val httpStatus: Int,
        val retryable: Boolean = false,
        val retryAfterSeconds: Long? = null,
        val errorCode: BillingErrorCode? = null,
        val reason: String? = null,
    )

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_ACCEPTED = 202
        const val HTTP_CONFLICT = 409
        const val HTTP_UNPROCESSABLE_ENTITY = 422
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val ACKNOWLEDGEMENT_LEASE_MILLIS = 60_000L

        const val PACKAGE_FAILURE_REASON = "Package is not allowed."
        const val CATALOG_FAILURE_REASON = "Purchase does not match the supported catalog."
        const val PLAY_UNAVAILABLE_REASON = "Google Play verification is unavailable."
        const val OWNERSHIP_FAILURE_REASON = "Purchase ownership could not be verified."
        const val TOKEN_CONFLICT_REASON = "Purchase token is already linked to another account."
        const val ACK_RETRY_REASON = "Purchase verification is pending."
        const val ACK_FAILURE_REASON = "Purchase acknowledgement failed."
        const val PURCHASE_PENDING_REASON = "Purchase is pending."
        const val DEPENDENCY_FAILURE_REASON = "Purchase verification dependency is unavailable."
    }
}
