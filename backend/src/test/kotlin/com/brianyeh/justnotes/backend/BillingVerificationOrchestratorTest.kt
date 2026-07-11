package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.billing.BillingErrorCode
import com.brianyeh.justnotes.backend.billing.BillingVerificationOrchestrator
import com.brianyeh.justnotes.backend.billing.BillingVerifyRequest
import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.AcknowledgementCompletionResult
import com.brianyeh.justnotes.backend.entitlement.AcknowledgementClaimResult
import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.entitlement.EntitlementReconciliationResult
import com.brianyeh.justnotes.backend.entitlement.EntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.PurchaseOwnershipValidator
import com.brianyeh.justnotes.backend.entitlement.SubscriptionRecord
import com.brianyeh.justnotes.backend.entitlement.SubscriptionWriteResult
import com.brianyeh.justnotes.backend.entitlement.reconciledEntitlement
import com.brianyeh.justnotes.backend.entitlement.selectEffectiveEntitlement
import com.brianyeh.justnotes.backend.entitlement.selectReconciledEntitlementRecord
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
import com.brianyeh.justnotes.backend.play.PlayVerificationFailureCode
import com.brianyeh.justnotes.backend.security.ObfuscatedAccountIdDeriver
import com.brianyeh.justnotes.backend.security.PurchaseTokenCipher
import com.brianyeh.justnotes.backend.security.TokenCiphertext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BillingVerificationOrchestratorTest {
    @Test
    fun activeAlreadyAcknowledgedGrantsAfterBindingAndWritesEntitlementLast() = runBlocking {
        val scenario = Scenario()

        val outcome = scenario.verify()

        assertEquals(200, outcome.httpStatus)
        assertTrue(outcome.response.hasPremium)
        assertEquals(BackendSubscriptionStatus.Active, outcome.response.status)
        assertEquals(BackendAcknowledgementState.Acknowledged, outcome.response.acknowledgementState)
        assertEquals(0, scenario.acknowledger.callCount)
        assertEquals(1, scenario.cipher.encryptCount)
        assertEquals(
            listOf(
                "play.verify",
                "ownership.derive",
                "repository.getSubscription",
                "kms.encrypt",
                "repository.upsertSubscription",
                "repository.getSubscription",
                "repository.upsertEntitlement",
            ),
            scenario.calls,
        )
        assertTrue(scenario.repository.entitlement?.hasPremium == true)
        assertFalse(outcome.response.toString().contains(RAW_TOKEN))
        assertFalse(requireNotNull(scenario.repository.subscription).toString().contains(RAW_TOKEN))
    }

    @Test
    fun pendingAcknowledgementGrantsOnlyAfterBackendAcknowledgementSucceeds() = runBlocking {
        val scenario = Scenario(
            verification = verification(acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING),
            acknowledgementResult = PlaySubscriptionAcknowledgementResult.Acknowledged,
        )

        val outcome = scenario.verify()

        assertEquals(200, outcome.httpStatus)
        assertTrue(outcome.response.hasPremium)
        assertEquals(BackendAcknowledgementState.Acknowledged, outcome.response.acknowledgementState)
        assertEquals(1, scenario.acknowledger.callCount)
        assertEquals(BackendAcknowledgementState.Acknowledged, scenario.repository.subscription?.acknowledgementState)
        assertEquals("repository.upsertEntitlement", scenario.calls.last())
    }

    @Test
    fun retryableAcknowledgementFailurePersistsBackoffAndRepeatedRequestDoesNotAckEarly() = runBlocking {
        val scenario = Scenario(
            verification = verification(acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING),
            acknowledgementResult = PlaySubscriptionAcknowledgementResult.Failure(
                reason = "redacted",
                retryable = true,
                code = PlayAcknowledgementFailureCode.PLAY_ACK_UNAVAILABLE,
            ),
        )

        val first = scenario.verify()
        val second = scenario.verify()

        assertEquals(202, first.httpStatus)
        assertFalse(first.response.hasPremium)
        assertEquals(BillingErrorCode.ACKNOWLEDGEMENT_RETRY, first.response.errorCode)
        assertEquals(900, first.response.retryAfterSeconds)
        assertEquals(202, second.httpStatus)
        assertEquals(900, second.response.retryAfterSeconds)
        assertEquals(1, scenario.acknowledger.callCount)
        assertEquals(1, scenario.repository.subscription?.acknowledgementAttemptCount)
        assertEquals(NOW + 900_000L, scenario.repository.subscription?.nextAcknowledgementAttemptAt)
        assertEquals("PLAY_ACK_UNAVAILABLE", scenario.repository.subscription?.lastAcknowledgementErrorCode)
        assertFalse(scenario.repository.entitlement?.hasPremium ?: true)
    }

    @Test
    fun differentTokenPremiumDoesNotHideSubmittedTokenAcknowledgementRetry() = runBlocking {
        val scenario = Scenario(
            verification = verification(acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING),
            acknowledgementResult = PlaySubscriptionAcknowledgementResult.Failure(
                reason = "redacted",
                retryable = true,
                code = PlayAcknowledgementFailureCode.PLAY_ACK_UNAVAILABLE,
            ),
        )
        scenario.repository.entitlement = EntitlementRecord(
            googleSub = GOOGLE_SUB,
            hasPremium = true,
            status = BackendSubscriptionStatus.Active,
            packageName = PACKAGE_NAME,
            productId = PRODUCT_ID,
            basePlanId = "annual",
            expiryTime = NOW + 60L * 24L * 60L * 60L * 1_000L,
            lastVerifiedAt = NOW + 1L,
            purchaseTokenHash = "newer-existing-token",
            acknowledgementState = BackendAcknowledgementState.Acknowledged,
        )

        val outcome = scenario.verify()

        assertEquals(202, outcome.httpStatus)
        assertTrue(outcome.response.hasPremium)
        assertEquals(BillingErrorCode.ACKNOWLEDGEMENT_RETRY, outcome.response.errorCode)
        assertEquals(900, outcome.response.retryAfterSeconds)
        assertEquals("newer-existing-token", outcome.response.purchaseTokenHash)
    }

    @Test
    fun acknowledgementRetryRunsWhenDueAndClearsRetryMetadata() = runBlocking {
        var now = NOW
        val scenario = Scenario(
            nowMillis = { now },
            verification = verification(acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING),
            acknowledgementResult = PlaySubscriptionAcknowledgementResult.Failure(
                reason = "redacted",
                retryable = true,
                code = PlayAcknowledgementFailureCode.PLAY_ACK_UNAVAILABLE,
            ),
        )
        scenario.verify()
        now += 900_000L
        scenario.acknowledger.result = PlaySubscriptionAcknowledgementResult.Acknowledged

        val outcome = scenario.verify()

        assertEquals(200, outcome.httpStatus)
        assertTrue(outcome.response.hasPremium)
        assertEquals(2, scenario.acknowledger.callCount)
        assertEquals(BackendAcknowledgementState.Acknowledged, scenario.repository.subscription?.acknowledgementState)
        assertEquals(0, scenario.repository.subscription?.acknowledgementAttemptCount)
        assertNull(scenario.repository.subscription?.nextAcknowledgementAttemptAt)
        assertNull(scenario.repository.subscription?.lastAcknowledgementErrorCode)
    }

    @Test
    fun nonRetryableAcknowledgementFailureNeverGrants() = runBlocking {
        val scenario = Scenario(
            verification = verification(acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING),
            acknowledgementResult = PlaySubscriptionAcknowledgementResult.Failure(
                reason = "redacted",
                retryable = false,
                code = PlayAcknowledgementFailureCode.PLAY_ACK_REJECTED,
            ),
        )

        val outcome = scenario.verify()

        assertEquals(503, outcome.httpStatus)
        assertFalse(outcome.response.hasPremium)
        assertEquals(BillingErrorCode.ACKNOWLEDGEMENT_FAILED, outcome.response.errorCode)
        assertEquals(BackendAcknowledgementState.Failed, outcome.response.acknowledgementState)
        assertEquals(BackendAcknowledgementState.Failed, scenario.repository.entitlement?.acknowledgementState)
        assertFalse(scenario.repository.entitlement?.hasPremium ?: true)
    }

    @Test
    fun nonRetryableAcknowledgementFailureIsStickyAcrossLaterPlayPendingResponses() = runBlocking {
        val scenario = Scenario(
            verification = verification(acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING),
            acknowledgementResult = PlaySubscriptionAcknowledgementResult.Failure(
                reason = "redacted",
                retryable = false,
                code = PlayAcknowledgementFailureCode.PLAY_ACK_REJECTED,
            ),
        )

        val first = scenario.verify()
        val second = scenario.verify()

        assertEquals(503, first.httpStatus)
        assertEquals(503, second.httpStatus)
        assertEquals(BackendAcknowledgementState.Failed, second.response.acknowledgementState)
        assertEquals(1, scenario.acknowledger.callCount)
    }

    @Test
    fun thrownAcknowledgementErrorUsesPersistedBackoff() = runBlocking {
        val scenario = Scenario(
            verification = verification(acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING),
        )
        scenario.acknowledger.failure = IllegalStateException("unexpected upstream failure")

        val first = scenario.verify()
        val second = scenario.verify()

        assertEquals(202, first.httpStatus)
        assertEquals(BillingErrorCode.ACKNOWLEDGEMENT_RETRY, first.response.errorCode)
        assertEquals(900, first.response.retryAfterSeconds)
        assertEquals(202, second.httpStatus)
        assertEquals(1, scenario.acknowledger.callCount)
        assertEquals("PLAY_ACK_UNAVAILABLE", scenario.repository.subscription?.lastAcknowledgementErrorCode)
    }

    @Test
    fun expiryIsRecheckedAfterAcknowledgementNetworkCall() = runBlocking {
        var now = NOW
        val expiryTime = NOW + 1_000L
        val scenario = Scenario(
            nowMillis = { now },
            verification = verification(
                acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING,
                expiryTime = expiryTime,
            ),
            acknowledgementResult = PlaySubscriptionAcknowledgementResult.Acknowledged,
        )
        scenario.acknowledger.beforeReturn = { now = expiryTime }

        val outcome = scenario.verify()

        assertEquals(200, outcome.httpStatus)
        assertEquals(BackendSubscriptionStatus.Expired, outcome.response.status)
        assertFalse(outcome.response.hasPremium)
    }

    @Test
    fun concurrentSameOwnerRequestsClaimOnlyOneAcknowledgementAttempt() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val scenario = Scenario(
            verification = verification(acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING),
        )
        scenario.acknowledger.beforeResult = { callNumber ->
            if (callNumber == 1) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        scenario.acknowledger.resultForCall = { callNumber ->
            if (callNumber == 1) {
                PlaySubscriptionAcknowledgementResult.Acknowledged
            } else {
                PlaySubscriptionAcknowledgementResult.Failure(
                    reason = "concurrent conflict",
                    retryable = true,
                    code = PlayAcknowledgementFailureCode.PLAY_ACK_CONFLICT,
                )
            }
        }

        val first = async { scenario.verify() }
        firstStarted.await()
        val second = async { scenario.verify() }
        withTimeout(2_000L) {
            while (!second.isCompleted && scenario.acknowledger.callCount < 2) yield()
        }
        releaseFirst.complete(Unit)
        val outcomes = listOf(first.await(), second.await())

        assertEquals(1, scenario.acknowledger.callCount)
        assertEquals(BackendAcknowledgementState.Acknowledged, scenario.repository.subscription?.acknowledgementState)
        assertTrue(scenario.repository.entitlement?.hasPremium == true)
        assertTrue(outcomes.any { it.response.hasPremium })
    }

    @Test
    fun staleNotDueRequestCannotDowngradeAcknowledgedPremium() = runBlocking {
        val acknowledgementStarted = CompletableDeferred<Unit>()
        val releaseAcknowledgement = CompletableDeferred<Unit>()
        val staleEntitlementWriteStarted = CompletableDeferred<Unit>()
        val releaseStaleEntitlementWrite = CompletableDeferred<Unit>()
        val scenario = Scenario(
            verification = verification(acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING),
        )
        scenario.acknowledger.beforeResult = { callNumber ->
            if (callNumber == 1) {
                acknowledgementStarted.complete(Unit)
                releaseAcknowledgement.await()
            }
        }
        scenario.repository.beforeEntitlementWrite = { record ->
            if (record.status == BackendSubscriptionStatus.VerificationPending) {
                staleEntitlementWriteStarted.complete(Unit)
                releaseStaleEntitlementWrite.await()
            }
        }

        val acknowledging = async { scenario.verify() }
        acknowledgementStarted.await()
        val staleNotDue = async { scenario.verify() }
        staleEntitlementWriteStarted.await()

        releaseAcknowledgement.complete(Unit)
        assertTrue(acknowledging.await().response.hasPremium)
        releaseStaleEntitlementWrite.complete(Unit)
        val staleOutcome = staleNotDue.await()

        assertEquals(1, scenario.acknowledger.callCount)
        assertEquals(200, staleOutcome.httpStatus)
        assertTrue(staleOutcome.response.hasPremium)
        assertTrue(scenario.repository.entitlement?.hasPremium == true)
        assertEquals(
            BackendAcknowledgementState.Acknowledged,
            scenario.repository.entitlement?.acknowledgementState,
        )
    }

    @Test
    fun olderActivePlaySnapshotCannotRegrantAfterNewerRevocation() = runBlocking {
        val clock = AtomicLong(NOW)
        val olderPlayCallReady = CompletableDeferred<Unit>()
        val releaseOlderPlayCall = CompletableDeferred<Unit>()
        val scenario = Scenario(nowMillis = clock::get)
        scenario.verifier.resultForCall = { callNumber ->
            if (callNumber == 1) {
                verification()
            } else {
                verification(
                    state = BackendSubscriptionStatus.Revoked,
                    playState = PlaySubscriptionState.REVOKED,
                )
            }
        }
        scenario.verifier.beforeResult = { callNumber ->
            if (callNumber == 1) {
                olderPlayCallReady.complete(Unit)
                releaseOlderPlayCall.await()
            }
        }

        val olderActive = async { scenario.verify() }
        olderPlayCallReady.await()
        clock.set(NOW + 1_000L)
        val newerRevoked = scenario.verify()
        assertEquals(BackendSubscriptionStatus.Revoked, newerRevoked.response.status)
        clock.set(NOW + 2_000L)
        releaseOlderPlayCall.complete(Unit)
        val staleOutcome = olderActive.await()

        assertFalse(staleOutcome.response.hasPremium)
        assertEquals(BackendSubscriptionStatus.Revoked, staleOutcome.response.status)
        assertFalse(scenario.repository.entitlement?.hasPremium ?: true)
        assertEquals(BackendSubscriptionStatus.Revoked, scenario.repository.entitlement?.status)
    }

    @Test
    fun encryptionTimestampIsCapturedAfterPlayVerification() = runBlocking {
        val clock = AtomicLong(NOW)
        val scenario = Scenario(nowMillis = clock::get)
        scenario.verifier.beforeResult = {
            clock.set(NOW + 5_000L)
        }

        val outcome = scenario.verify()

        assertEquals(200, outcome.httpStatus)
        val stored = requireNotNull(scenario.repository.subscription)
        assertEquals(NOW, stored.lastVerifiedAt)
        assertEquals(NOW + 5_000L, stored.encryptedAt)
    }

    @Test
    fun staleAcknowledgementCompletionCannotOverwriteTerminalState() = runBlocking {
        val scenario = Scenario(
            verification = verification(acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING),
        )
        scenario.repository.afterAcknowledgementClaim = { result ->
            if (result is AcknowledgementClaimResult.Claimed) {
                scenario.repository.completeSubscriptionAcknowledgement(
                    purchaseTokenHash = TOKEN_HASH,
                    ownerGoogleSub = GOOGLE_SUB,
                    generation = result.generation,
                    acknowledgementState = BackendAcknowledgementState.Failed,
                    acknowledgementAttemptCount = 1,
                    nextAcknowledgementAttemptAt = null,
                    lastAcknowledgementErrorCode = "PLAY_ACK_REJECTED",
                )
            }
        }

        val outcome = scenario.verify()

        assertEquals(503, outcome.httpStatus)
        assertEquals(BillingErrorCode.ACKNOWLEDGEMENT_FAILED, outcome.response.errorCode)
        assertEquals(1, scenario.acknowledger.callCount)
        assertEquals(BackendAcknowledgementState.Failed, scenario.repository.subscription?.acknowledgementState)
    }

    @Test
    fun pendingPurchaseReturnsAcceptedWithoutAcknowledgementOrPremium() = runBlocking {
        val scenario = Scenario(
            verification = verification(
                state = BackendSubscriptionStatus.PendingPurchase,
                playState = PlaySubscriptionState.SUBSCRIPTION_STATE_PENDING,
                acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING,
            ),
        )

        val outcome = scenario.verify()

        assertEquals(202, outcome.httpStatus)
        assertEquals(BillingErrorCode.PURCHASE_PENDING, outcome.response.errorCode)
        assertEquals(BackendSubscriptionStatus.PendingPurchase, outcome.response.status)
        assertFalse(outcome.response.hasPremium)
        assertEquals(0, scenario.acknowledger.callCount)
    }

    @Test
    fun canceledPendingPurchaseIsTerminalAndAllowsAForwardPurchase() = runBlocking {
        val scenario = Scenario(
            verification = verification(
                state = BackendSubscriptionStatus.Free,
                playState = PlaySubscriptionState.PENDING_PURCHASE_CANCELED,
                acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING,
                expiryTime = NOW,
            ),
        )

        val outcome = scenario.verify()

        assertEquals(200, outcome.httpStatus)
        assertEquals(BackendSubscriptionStatus.Free, outcome.response.status)
        assertFalse(outcome.response.retryable)
        assertEquals(null, outcome.response.retryAfterSeconds)
        assertFalse(outcome.response.hasPremium)
        assertEquals(0, scenario.acknowledger.callCount)
    }

    @Test
    fun grantablePlayStateWithElapsedExpiryIsExpiredWithoutAcknowledgement() = runBlocking {
        val scenario = Scenario(
            verification = verification(
                acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING,
                expiryTime = NOW,
            ),
        )

        val outcome = scenario.verify()

        assertEquals(200, outcome.httpStatus)
        assertEquals(BackendSubscriptionStatus.Expired, outcome.response.status)
        assertFalse(outcome.response.hasPremium)
        assertEquals(0, scenario.acknowledger.callCount)
    }

    @Test
    fun terminalNonGrantingStatesArePersistedWithoutAcknowledgement() = runBlocking {
        val states = listOf(
            BackendSubscriptionStatus.OnHold,
            BackendSubscriptionStatus.Paused,
            BackendSubscriptionStatus.Expired,
            BackendSubscriptionStatus.Revoked,
        )

        states.forEach { status ->
            val scenario = Scenario(verification = verification(state = status))
            val outcome = scenario.verify()

            assertEquals(200, outcome.httpStatus, status.name)
            assertEquals(status, outcome.response.status)
            assertFalse(outcome.response.hasPremium, status.name)
            assertEquals(0, scenario.acknowledger.callCount, status.name)
            assertEquals(status, scenario.repository.entitlement?.status)
        }
    }

    @Test
    fun canceledUnexpiredAcknowledgedSubscriptionKeepsTruthfulGrantingState() = runBlocking {
        val scenario = Scenario(
            verification = verification(
                state = BackendSubscriptionStatus.CanceledActiveUntilExpiry,
                playState = PlaySubscriptionState.SUBSCRIPTION_STATE_CANCELED,
                canceledButActiveUntilExpiry = true,
            ),
        )

        val outcome = scenario.verify()

        assertEquals(200, outcome.httpStatus)
        assertTrue(outcome.response.hasPremium)
        assertEquals(BackendSubscriptionStatus.CanceledActiveUntilExpiry, outcome.response.status)
    }

    @Test
    fun playFailureStopsBeforeOwnershipKmsAndFirestore() = runBlocking {
        val scenario = Scenario(
            verificationResult = PlaySubscriptionVerificationResult.Failure(
                reason = "redacted",
                retryable = true,
                code = PlayVerificationFailureCode.PLAY_API_UNAVAILABLE,
            ),
        )

        val outcome = scenario.verify()

        assertEquals(503, outcome.httpStatus)
        assertEquals(BillingErrorCode.PLAY_VERIFICATION_UNAVAILABLE, outcome.response.errorCode)
        assertEquals(listOf("play.verify"), scenario.calls)
        assertNull(scenario.repository.subscription)
        assertNull(scenario.repository.entitlement)
    }

    @Test
    fun missingOrMismatchedOwnershipStopsBeforeKmsAndBinding() = runBlocking {
        listOf(null, "wrong-account-id").forEach { playAccountId ->
            val scenario = Scenario(
                verification = verification(obfuscatedAccountId = playAccountId),
            )

            val outcome = scenario.verify()

            assertEquals(422, outcome.httpStatus)
            assertEquals(
                if (playAccountId == null) {
                    BillingErrorCode.MISSING_OBFUSCATED_ACCOUNT_ID
                } else {
                    BillingErrorCode.OWNER_MISMATCH
                },
                outcome.response.errorCode,
            )
            assertEquals(0, scenario.cipher.encryptCount)
            assertNull(scenario.repository.subscription)
            assertEquals(listOf("play.verify") + if (playAccountId == null) emptyList() else listOf("ownership.derive"), scenario.calls)
        }
    }

    @Test
    fun invalidPackageAndCatalogHintsFailBeforeSensitiveWrites() = runBlocking {
        val wrongPackage = Scenario(request = request().copy(packageName = "com.attacker"))
        val wrongProduct = Scenario(request = request().copy(productId = "other"))
        val wrongAuthoritativePlan = Scenario(request = request().copy(basePlanId = "annual", offerId = null))

        val packageOutcome = wrongPackage.verify()
        val productOutcome = wrongProduct.verify()
        val planOutcome = wrongAuthoritativePlan.verify()

        assertEquals(BillingErrorCode.PACKAGE_NOT_ALLOWED, packageOutcome.response.errorCode)
        assertEquals(emptyList(), wrongPackage.calls)
        assertEquals(BillingErrorCode.CATALOG_MISMATCH, productOutcome.response.errorCode)
        assertEquals(emptyList(), wrongProduct.calls)
        assertEquals(BillingErrorCode.CATALOG_MISMATCH, planOutcome.response.errorCode)
        assertEquals(listOf("play.verify"), wrongAuthoritativePlan.calls)
        assertEquals(0, wrongAuthoritativePlan.cipher.encryptCount)
    }

    @Test
    fun sameOwnerDuplicateReusesCiphertextAndSkipsAcknowledgement() = runBlocking {
        val scenario = Scenario()
        scenario.repository.subscription = subscription(owner = GOOGLE_SUB)

        val outcome = scenario.verify()

        assertEquals(200, outcome.httpStatus)
        assertTrue(outcome.response.hasPremium)
        assertEquals(0, scenario.cipher.encryptCount)
        assertEquals(0, scenario.acknowledger.callCount)
        assertEquals("existing-ciphertext", scenario.repository.subscription?.tokenCiphertext)
        assertEquals("existing-key", scenario.repository.subscription?.keyVersion)
    }

    @Test
    fun differentOwnerBindingReturnsConflictWithoutLeakingOwner() = runBlocking {
        val scenario = Scenario()
        scenario.repository.subscription = subscription(owner = "other-google-sub")

        val outcome = scenario.verify()

        assertEquals(409, outcome.httpStatus)
        assertEquals(BillingErrorCode.TOKEN_ALREADY_BOUND, outcome.response.errorCode)
        assertFalse(outcome.response.toString().contains("other-google-sub"))
        assertEquals(0, scenario.cipher.encryptCount)
        assertEquals(0, scenario.acknowledger.callCount)
        assertNull(scenario.repository.entitlement)
    }

    @Test
    fun concurrentDifferentOwnerWriteConflictStopsBeforeAcknowledgement() = runBlocking {
        val scenario = Scenario()
        scenario.repository.nextWriteResult = SubscriptionWriteResult.OwnedByAnotherUser

        val outcome = scenario.verify()

        assertEquals(409, outcome.httpStatus)
        assertEquals(BillingErrorCode.TOKEN_ALREADY_BOUND, outcome.response.errorCode)
        assertEquals(1, scenario.cipher.encryptCount)
        assertEquals(0, scenario.acknowledger.callCount)
        assertNull(scenario.repository.entitlement)
    }

    @Test
    fun kmsFailureReturnsDependencyErrorWithoutBindingAcknowledgementOrEntitlement() = runBlocking {
        val scenario = Scenario()
        scenario.cipher.failure = IllegalStateException("kms unavailable")

        val outcome = scenario.verify()

        assertEquals(503, outcome.httpStatus)
        assertEquals(BillingErrorCode.DEPENDENCY_UNAVAILABLE, outcome.response.errorCode)
        assertEquals(0, scenario.repository.subscriptionWriteCount)
        assertEquals(0, scenario.acknowledger.callCount)
        assertNull(scenario.repository.entitlement)
    }

    @Test
    fun retryAfterEntitlementWriteFailureUsesStoredAcknowledgementWithoutSecondAck() = runBlocking {
        val scenario = Scenario(
            verification = verification(acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING),
            acknowledgementResult = PlaySubscriptionAcknowledgementResult.Acknowledged,
        )
        scenario.repository.failNextEntitlementWrite = true

        val first = scenario.verify()
        val second = scenario.verify()

        assertEquals(503, first.httpStatus)
        assertFalse(first.response.hasPremium)
        assertEquals(BillingErrorCode.DEPENDENCY_UNAVAILABLE, first.response.errorCode)
        assertEquals(BackendAcknowledgementState.Acknowledged, scenario.repository.subscription?.acknowledgementState)
        assertEquals(200, second.httpStatus)
        assertTrue(second.response.hasPremium)
        assertEquals(1, scenario.acknowledger.callCount)
    }

    @Test
    fun linkedTokenIsStoredOnlyAsHashAndRawTokenNeverAppearsInRecords() = runBlocking {
        val scenario = Scenario()

        scenario.verify()

        val stored = requireNotNull(scenario.repository.subscription)
        assertEquals("linked-token-hash", stored.linkedPurchaseTokenHash)
        assertFalse(stored.toString().contains(RAW_TOKEN))
        assertFalse(stored.toString().contains("raw-linked-token"))
        assertFalse(requireNotNull(scenario.repository.entitlement).toString().contains(RAW_TOKEN))
    }

    private class Scenario(
        request: BillingVerifyRequest = request(),
        verification: PlaySubscriptionVerification = verification(),
        verificationResult: PlaySubscriptionVerificationResult? = null,
        acknowledgementResult: PlaySubscriptionAcknowledgementResult = PlaySubscriptionAcknowledgementResult.Acknowledged,
        val nowMillis: () -> Long = { NOW },
    ) {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val repository = RecordingRepository(calls)
        val verifier = RecordingVerifier(
            calls = calls,
            result = verificationResult ?: PlaySubscriptionVerificationResult.Success(verification),
        )
        val acknowledger = RecordingAcknowledger(calls, acknowledgementResult)
        val cipher = RecordingCipher(calls)
        private val requestValue = request
        private val orchestrator = BillingVerificationOrchestrator(
            config = config(),
            entitlementRepository = repository,
            playSubscriptionVerifier = verifier,
            playSubscriptionAcknowledger = acknowledger,
            ownershipValidator = PurchaseOwnershipValidator(RecordingAccountIdDeriver(calls)),
            purchaseTokenCipher = cipher,
            nowMillis = nowMillis,
        )

        suspend fun verify() = orchestrator.verify(GOOGLE_SUB, requestValue)
    }

    private class RecordingVerifier(
        private val calls: MutableList<String>,
        var result: PlaySubscriptionVerificationResult,
    ) : PlaySubscriptionVerifier {
        private val verificationCallCount = AtomicInteger()
        var beforeResult: suspend (Int) -> Unit = {}
        var resultForCall: (Int) -> PlaySubscriptionVerification = {
            (result as PlaySubscriptionVerificationResult.Success).verification
        }

        override suspend fun verify(packageName: String, purchaseToken: String): PlaySubscriptionVerificationResult {
            calls += "play.verify"
            assertEquals(PACKAGE_NAME, packageName)
            assertEquals(RAW_TOKEN, purchaseToken)
            val callNumber = verificationCallCount.incrementAndGet()
            beforeResult(callNumber)
            return when (val configured = result) {
                is PlaySubscriptionVerificationResult.Failure -> configured
                is PlaySubscriptionVerificationResult.Success ->
                    PlaySubscriptionVerificationResult.Success(resultForCall(callNumber))
            }
        }
    }

    private class RecordingAcknowledger(
        private val calls: MutableList<String>,
        var result: PlaySubscriptionAcknowledgementResult,
    ) : PlaySubscriptionAcknowledger {
        private val acknowledgementCallCount = AtomicInteger()
        val callCount: Int
            get() = acknowledgementCallCount.get()
        var failure: RuntimeException? = null
        var beforeReturn: () -> Unit = {}
        var beforeResult: suspend (Int) -> Unit = {}
        var resultForCall: (Int) -> PlaySubscriptionAcknowledgementResult = { result }

        override suspend fun acknowledge(
            packageName: String,
            productId: String,
            purchaseToken: String,
        ): PlaySubscriptionAcknowledgementResult {
            calls += "play.acknowledge"
            val callNumber = acknowledgementCallCount.incrementAndGet()
            beforeResult(callNumber)
            beforeReturn()
            failure?.let { throw it }
            assertEquals(PACKAGE_NAME, packageName)
            assertEquals(PRODUCT_ID, productId)
            assertEquals(RAW_TOKEN, purchaseToken)
            return resultForCall(callNumber)
        }
    }

    private class RecordingAccountIdDeriver(
        private val calls: MutableList<String>,
    ) : ObfuscatedAccountIdDeriver {
        override fun derive(googleSub: String): String {
            calls += "ownership.derive"
            assertEquals(GOOGLE_SUB, googleSub)
            return OBFUSCATED_ACCOUNT_ID
        }
    }

    private class RecordingCipher(
        private val calls: MutableList<String>,
    ) : PurchaseTokenCipher {
        var encryptCount = 0
        var failure: RuntimeException? = null

        override fun encrypt(purchaseToken: String, now: Long): TokenCiphertext {
            calls += "kms.encrypt"
            encryptCount += 1
            failure?.let { throw it }
            assertEquals(RAW_TOKEN, purchaseToken)
            return TokenCiphertext(
                tokenCiphertext = "new-ciphertext",
                keyVersion = "new-key",
                encryptedAt = now,
                encryptionAlgorithm = "GOOGLE_SYMMETRIC_ENCRYPTION",
            )
        }

        override fun decrypt(ciphertext: TokenCiphertext): String {
            throw UnsupportedOperationException("Decrypt is not used by purchase verification.")
        }
    }

    private class RecordingRepository(
        private val calls: MutableList<String>,
    ) : EntitlementRepository {
        private val mutex = Mutex()
        var subscription: SubscriptionRecord? = null
        var entitlement: EntitlementRecord? = null
        var subscriptionWriteCount = 0
        var nextWriteResult: SubscriptionWriteResult? = null
        var failNextEntitlementWrite = false
        var beforeEntitlementWrite: suspend (EntitlementRecord) -> Unit = {}
        var afterAcknowledgementClaim: suspend (AcknowledgementClaimResult) -> Unit = {}

        override suspend fun getEntitlement(googleSub: String): EntitlementRecord? = mutex.withLock { entitlement }

        override suspend fun upsertEntitlement(record: EntitlementRecord): EntitlementRecord {
            beforeEntitlementWrite(record)
            return mutex.withLock {
                calls += "repository.upsertEntitlement"
                if (failNextEntitlementWrite) {
                    failNextEntitlementWrite = false
                    throw IllegalStateException("entitlement write failed")
                }
                val effective = selectEffectiveEntitlement(entitlement, record)
                entitlement = effective
                effective
            }
        }

        override suspend fun getSubscription(purchaseTokenHash: String): SubscriptionRecord? = mutex.withLock {
            calls += "repository.getSubscription"
            assertEquals(TOKEN_HASH, purchaseTokenHash)
            subscription
        }

        override suspend fun upsertSubscriptionForOwner(record: SubscriptionRecord): SubscriptionWriteResult =
            mutex.withLock {
                calls += "repository.upsertSubscription"
                subscriptionWriteCount += 1
                nextWriteResult?.let {
                    nextWriteResult = null
                    return@withLock it
                }
                val current = subscription
                when {
                    current == null -> {
                        subscription = record
                        SubscriptionWriteResult.Created
                    }
                    current.ownerGoogleSub != record.ownerGoogleSub -> SubscriptionWriteResult.OwnedByAnotherUser
                    else -> {
                        subscription = mergeSubscription(current, record)
                        SubscriptionWriteResult.UpdatedForSameOwner
                    }
                }
            }

        override suspend fun claimSubscriptionAcknowledgement(
            purchaseTokenHash: String,
            ownerGoogleSub: String,
            now: Long,
            leaseUntil: Long,
        ): AcknowledgementClaimResult {
            val result = mutex.withLock {
                calls += "repository.claimAcknowledgement"
                assertEquals(TOKEN_HASH, purchaseTokenHash)
                val current = subscription ?: return@withLock AcknowledgementClaimResult.Missing
                when {
                    current.ownerGoogleSub != ownerGoogleSub -> AcknowledgementClaimResult.OwnedByAnotherUser
                    current.acknowledgementState == BackendAcknowledgementState.Acknowledged ->
                        AcknowledgementClaimResult.AlreadyAcknowledged(current)
                    current.acknowledgementState == BackendAcknowledgementState.Failed ->
                        AcknowledgementClaimResult.TerminalFailure(current)
                    !current.status.isGrantable() || current.expiryTime?.let { it <= now } != false ->
                        AcknowledgementClaimResult.NotEligible(current)
                    current.nextAcknowledgementAttemptAt?.let { it > now } == true ||
                        current.acknowledgementLeaseUntil?.let { it > now } == true ->
                        AcknowledgementClaimResult.NotDue(current)
                    else -> {
                        val generation = current.acknowledgementClaimGeneration + 1L
                        val claimed = current.copy(
                            acknowledgementClaimGeneration = generation,
                            acknowledgementLeaseUntil = leaseUntil,
                        )
                        subscription = claimed
                        AcknowledgementClaimResult.Claimed(claimed, generation)
                    }
                }
            }
            afterAcknowledgementClaim(result)
            return result
        }

        override suspend fun completeSubscriptionAcknowledgement(
            purchaseTokenHash: String,
            ownerGoogleSub: String,
            generation: Long,
            acknowledgementState: BackendAcknowledgementState,
            acknowledgementAttemptCount: Int,
            nextAcknowledgementAttemptAt: Long?,
            lastAcknowledgementErrorCode: String?,
        ): AcknowledgementCompletionResult = mutex.withLock {
            val current = subscription ?: return@withLock AcknowledgementCompletionResult.Missing
            when {
                current.ownerGoogleSub != ownerGoogleSub -> AcknowledgementCompletionResult.OwnedByAnotherUser
                current.acknowledgementClaimGeneration != generation ||
                    current.acknowledgementLeaseUntil == null -> AcknowledgementCompletionResult.Stale(current)
                else -> {
                    val updated = current.copy(
                        acknowledgementState = acknowledgementState,
                        acknowledgementAttemptCount = acknowledgementAttemptCount,
                        nextAcknowledgementAttemptAt = nextAcknowledgementAttemptAt,
                        lastAcknowledgementErrorCode = lastAcknowledgementErrorCode,
                        acknowledgementLeaseUntil = null,
                    )
                    subscription = updated
                    AcknowledgementCompletionResult.Applied(updated)
                }
            }
        }

        override suspend fun reconcileEntitlementFromSubscription(
            purchaseTokenHash: String,
            ownerGoogleSub: String,
            now: Long,
            maxStaleMillis: Long,
        ): EntitlementReconciliationResult {
            val tentative = mutex.withLock {
                val current = subscription ?: return@withLock null
                current.reconciledEntitlement(now)
            } ?: return EntitlementReconciliationResult.Missing
            beforeEntitlementWrite(tentative)
            return mutex.withLock {
                calls += "repository.upsertEntitlement"
                if (failNextEntitlementWrite) {
                    failNextEntitlementWrite = false
                    throw IllegalStateException("entitlement write failed")
                }
                val current = subscription ?: return@withLock EntitlementReconciliationResult.Missing
                if (current.ownerGoogleSub != ownerGoogleSub) {
                    return@withLock EntitlementReconciliationResult.OwnedByAnotherUser
                }
                val effective = selectReconciledEntitlementRecord(
                    entitlement,
                    current.reconciledEntitlement(now),
                    now,
                    maxStaleMillis,
                )
                entitlement = effective
                EntitlementReconciliationResult.Success(effective, current)
            }
        }

        private fun mergeSubscription(
            current: SubscriptionRecord,
            incoming: SubscriptionRecord,
        ): SubscriptionRecord {
            val base = when {
                incoming.lastVerifiedAt > current.lastVerifiedAt -> incoming
                incoming.lastVerifiedAt < current.lastVerifiedAt -> current
                current.status.isGrantable() && !incoming.status.isGrantable() -> incoming
                !current.status.isGrantable() && incoming.status.isGrantable() -> current
                else -> incoming
            }
            val generation = maxOf(
                current.acknowledgementClaimGeneration,
                incoming.acknowledgementClaimGeneration,
            )
            if (
                current.acknowledgementState == BackendAcknowledgementState.Acknowledged ||
                incoming.acknowledgementState == BackendAcknowledgementState.Acknowledged
            ) {
                return base.copy(
                    acknowledgementState = BackendAcknowledgementState.Acknowledged,
                    acknowledgementAttemptCount = 0,
                    nextAcknowledgementAttemptAt = null,
                    lastAcknowledgementErrorCode = null,
                    acknowledgementClaimGeneration = generation,
                    acknowledgementLeaseUntil = null,
                )
            }
            val acknowledgementSource = when {
                current.acknowledgementState == BackendAcknowledgementState.Failed -> current
                incoming.acknowledgementState == BackendAcknowledgementState.Failed -> incoming
                current.acknowledgementClaimGeneration > incoming.acknowledgementClaimGeneration -> current
                incoming.acknowledgementClaimGeneration > current.acknowledgementClaimGeneration -> incoming
                incoming.acknowledgementAttemptCount >= current.acknowledgementAttemptCount -> incoming
                else -> current
            }
            return base.copy(
                acknowledgementState = acknowledgementSource.acknowledgementState,
                acknowledgementAttemptCount = maxOf(
                    current.acknowledgementAttemptCount,
                    incoming.acknowledgementAttemptCount,
                ),
                nextAcknowledgementAttemptAt = listOfNotNull(
                    current.nextAcknowledgementAttemptAt,
                    incoming.nextAcknowledgementAttemptAt,
                ).maxOrNull(),
                lastAcknowledgementErrorCode = acknowledgementSource.lastAcknowledgementErrorCode,
                acknowledgementClaimGeneration = generation,
                acknowledgementLeaseUntil = acknowledgementSource.acknowledgementLeaseUntil,
            )
        }

        private fun BackendSubscriptionStatus.isGrantable(): Boolean {
            return this == BackendSubscriptionStatus.Active ||
                this == BackendSubscriptionStatus.GracePeriod ||
                this == BackendSubscriptionStatus.CanceledActiveUntilExpiry
        }
    }

    private companion object {
        const val NOW = 1_762_000_000_000L
        const val GOOGLE_SUB = "google-sub"
        const val PACKAGE_NAME = "com.brianyeh.justnotes"
        const val PRODUCT_ID = "just_notes_premium"
        const val RAW_TOKEN = "raw-purchase-token"
        const val TOKEN_HASH = "purchase-token-hash"
        const val OBFUSCATED_ACCOUNT_ID = "expected-obfuscated-account-id"

        fun config(): BackendConfig {
            return BackendConfig.fromEnvironment(
                mapOf("GOOGLE_WEB_CLIENT_ID" to "test-web-client.apps.googleusercontent.com"),
            )
        }

        fun request(): BillingVerifyRequest {
            return BillingVerifyRequest(
                purchaseToken = RAW_TOKEN,
                packageName = PACKAGE_NAME,
                productId = PRODUCT_ID,
                basePlanId = "monthly",
                offerId = "trial10d",
                appVersion = "1.0.7",
                versionCode = 5,
                deviceLocale = "zh-TW",
            )
        }

        fun verification(
            state: BackendSubscriptionStatus = BackendSubscriptionStatus.Active,
            playState: PlaySubscriptionState = PlaySubscriptionState.SUBSCRIPTION_STATE_ACTIVE,
            acknowledgementState: PlayAcknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED,
            obfuscatedAccountId: String? = OBFUSCATED_ACCOUNT_ID,
            canceledButActiveUntilExpiry: Boolean = false,
            expiryTime: Long = NOW + 30L * 24L * 60L * 60L * 1_000L,
        ): PlaySubscriptionVerification {
            return PlaySubscriptionVerification(
                packageName = PACKAGE_NAME,
                purchaseTokenHash = TOKEN_HASH,
                subscriptionState = state,
                playSubscriptionState = playState,
                lineItems = listOf(
                    PlaySubscriptionLineItem(
                        productId = PRODUCT_ID,
                        basePlanId = "monthly",
                        offerId = "trial10d",
                        expiryTime = expiryTime,
                    ),
                ),
                acknowledgementState = acknowledgementState.name,
                playAcknowledgementState = acknowledgementState,
                autoRenewing = true,
                linkedPurchaseTokenHash = "linked-token-hash",
                externalAccountIdentifiers = PlayExternalAccountIdentifiers(obfuscatedAccountId),
                canceledButActiveUntilExpiry = canceledButActiveUntilExpiry,
                purchaseTokenHashVersion = "hmac-sha256-v1",
                purchaseTokenPepperVersion = "1",
            )
        }

        fun subscription(owner: String): SubscriptionRecord {
            return SubscriptionRecord(
                purchaseTokenHash = TOKEN_HASH,
                hashVersion = "hmac-sha256-v1",
                pepperVersion = "1",
                ownerGoogleSub = owner,
                packageName = PACKAGE_NAME,
                productId = PRODUCT_ID,
                basePlanId = "monthly",
                offerId = "trial10d",
                linkedPurchaseTokenHash = "linked-token-hash",
                tokenCiphertext = "existing-ciphertext",
                keyVersion = "existing-key",
                encryptedAt = NOW - 1,
                encryptionAlgorithm = "GOOGLE_SYMMETRIC_ENCRYPTION",
                acknowledgementState = BackendAcknowledgementState.Acknowledged,
                acknowledgementAttemptCount = 0,
                nextAcknowledgementAttemptAt = null,
                lastAcknowledgementErrorCode = null,
                lastVerifiedAt = NOW - 1,
            )
        }
    }
}
