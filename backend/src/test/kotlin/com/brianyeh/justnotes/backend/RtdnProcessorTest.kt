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
import com.brianyeh.justnotes.backend.rtdn.RtdnClaimResult
import com.brianyeh.justnotes.backend.rtdn.RtdnEnvelope
import com.brianyeh.justnotes.backend.rtdn.RtdnErrorCode
import com.brianyeh.justnotes.backend.rtdn.RtdnEventRepository
import com.brianyeh.justnotes.backend.rtdn.RtdnNotification
import com.brianyeh.justnotes.backend.rtdn.RtdnProcessResult
import com.brianyeh.justnotes.backend.rtdn.RtdnProcessor
import com.brianyeh.justnotes.backend.rtdn.SubscriptionNotificationHint
import com.brianyeh.justnotes.backend.security.PurchaseTokenCipher
import com.brianyeh.justnotes.backend.security.PurchaseTokenHasher
import com.brianyeh.justnotes.backend.security.TokenCiphertext
import com.brianyeh.justnotes.backend.security.TokenHash
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RtdnProcessorTest {
    @Test
    fun rechecksPlayAndReconcilesFromAuthoritativeStateWithoutTrustingNotificationType() = runBlocking {
        val scenario = Scenario()
        scenario.seedSubscription(status = BackendSubscriptionStatus.Expired)

        val result = scenario.processor.process(envelope(), notification(notificationType = 13))

        assertEquals("PLAY_REQUERIED", assertIs<RtdnProcessResult.Completed>(result).outcome)
        assertEquals(listOf(RAW_TOKEN), scenario.verifier.tokens)
        assertEquals("PLAY_REQUERIED", scenario.events.completedOutcome)
        assertTrue(requireNotNull(scenario.repository.getEntitlement(OWNER)).hasPremium)
        val stored = requireNotNull(scenario.repository.getSubscription(TOKEN_HASH))
        assertEquals(BackendSubscriptionStatus.Active, stored.status)
        assertEquals(CIPHERTEXT, stored.tokenCiphertext)
        assertFalse(stored.toString().contains(RAW_TOKEN))
    }

    @Test
    fun missingOwnerBindingIsRetryableAndNeverCallsPlay() = runBlocking {
        val scenario = Scenario()

        val result = scenario.processor.process(envelope(), notification())

        val retry = assertIs<RtdnProcessResult.RetryableFailure>(result)
        assertEquals(RtdnErrorCode.OWNER_BINDING_MISSING, retry.errorCode)
        assertEquals(10L, retry.retryAfterSeconds)
        assertTrue(scenario.verifier.tokens.isEmpty())
        assertEquals("OWNER_BINDING_MISSING", scenario.events.releasedErrorCode)
    }

    @Test
    fun retryablePlayFailureReleasesDeliveryForPubSubRetry() = runBlocking {
        val scenario = Scenario(
            playResult = PlaySubscriptionVerificationResult.Failure(
                reason = "redacted",
                retryable = true,
                code = PlayVerificationFailureCode.PLAY_API_UNAVAILABLE,
            ),
        )
        scenario.seedSubscription()

        val result = scenario.processor.process(envelope(), notification())

        val retry = assertIs<RtdnProcessResult.RetryableFailure>(result)
        assertEquals(RtdnErrorCode.DEPENDENCY_UNAVAILABLE, retry.errorCode)
        assertEquals("DEPENDENCY_UNAVAILABLE", scenario.events.releasedErrorCode)
        assertEquals(null, scenario.events.completedOutcome)
    }

    @Test
    fun completedDuplicateDoesNoSensitiveWork() = runBlocking {
        val scenario = Scenario(claimResult = RtdnClaimResult.AlreadyCompleted)

        val result = scenario.processor.process(envelope(), notification())

        assertEquals("DUPLICATE", assertIs<RtdnProcessResult.Completed>(result).outcome)
        assertTrue(scenario.verifier.tokens.isEmpty())
        assertEquals(0, scenario.cipher.decryptCount)
    }

    @Test
    fun inFlightDeliveryRemainsRetryableUntilOriginalClaimCompletes() = runBlocking {
        val scenario = Scenario(claimResult = RtdnClaimResult.InFlight)

        val result = scenario.processor.process(envelope(), notification())

        assertEquals(
            RtdnErrorCode.DEPENDENCY_UNAVAILABLE,
            assertIs<RtdnProcessResult.RetryableFailure>(result).errorCode,
        )
        assertTrue(scenario.verifier.tokens.isEmpty())
        assertEquals(0, scenario.cipher.decryptCount)
    }

    @Test
    fun mismatchedDecryptedTokenHashFailsClosedWithoutCallingPlay() = runBlocking {
        val scenario = Scenario(decryptedToken = "different-token", decryptedHash = "different-hash")
        scenario.seedSubscription()

        val result = scenario.processor.process(envelope(), notification())

        assertEquals(
            RtdnErrorCode.INTERNAL_ERROR,
            assertIs<RtdnProcessResult.RetryableFailure>(result).errorCode,
        )
        assertTrue(scenario.verifier.tokens.isEmpty())
    }

    @Test
    fun lifecycleMatrixAlwaysUsesFreshPlayTruth() = runBlocking {
        val cases = listOf(
            LifecycleCase(BackendSubscriptionStatus.Active, false, true),
            LifecycleCase(BackendSubscriptionStatus.GracePeriod, false, true),
            LifecycleCase(BackendSubscriptionStatus.Active, true, true),
            LifecycleCase(BackendSubscriptionStatus.OnHold, false, false),
            LifecycleCase(BackendSubscriptionStatus.Paused, false, false),
            LifecycleCase(BackendSubscriptionStatus.Expired, false, false),
            LifecycleCase(BackendSubscriptionStatus.Revoked, false, false),
        )

        cases.forEach { case ->
            val scenario = Scenario(
                playResult = successVerification(
                    status = case.status,
                    canceledButActive = case.canceledButActive,
                ),
            )
            scenario.seedSubscription(status = BackendSubscriptionStatus.Unknown)

            scenario.processor.process(envelope(), notification(notificationType = 999))

            val entitlement = requireNotNull(scenario.repository.getEntitlement(OWNER))
            assertEquals(case.hasPremium, entitlement.hasPremium, case.toString())
            assertEquals(
                if (case.canceledButActive) BackendSubscriptionStatus.CanceledActiveUntilExpiry else case.status,
                requireNotNull(scenario.repository.getSubscription(TOKEN_HASH)).status,
                case.toString(),
            )
        }
    }

    @Test
    fun impossibleInvalidPlayInputIsCompletedWithoutChangingEntitlement() = runBlocking {
        val scenario = Scenario(
            playResult = PlaySubscriptionVerificationResult.Failure(
                reason = "redacted",
                retryable = false,
                code = PlayVerificationFailureCode.INVALID_INPUT,
            ),
        )
        scenario.seedSubscription(status = BackendSubscriptionStatus.Active)

        val result = scenario.processor.process(envelope(), notification())

        assertIs<RtdnProcessResult.Ignored>(result)
        assertEquals("IGNORED", scenario.events.completedOutcome)
        assertEquals(BackendSubscriptionStatus.Active, scenario.repository.getSubscription(TOKEN_HASH)?.status)
    }

    @Test
    fun nonRetryablePlayRejectionStillRedeliversForIamRecoveryOrDeadLetterInspection() = runBlocking {
        val scenario = Scenario(
            playResult = PlaySubscriptionVerificationResult.Failure(
                reason = "redacted",
                retryable = false,
                code = PlayVerificationFailureCode.PLAY_API_REJECTED,
            ),
        )
        scenario.seedSubscription()

        val result = scenario.processor.process(envelope(), notification())

        assertEquals(
            RtdnErrorCode.DEPENDENCY_UNAVAILABLE,
            assertIs<RtdnProcessResult.RetryableFailure>(result).errorCode,
        )
        assertEquals("DEPENDENCY_UNAVAILABLE", scenario.events.releasedErrorCode)
        assertEquals(null, scenario.events.completedOutcome)
    }

    @Test
    fun kmsFailureIsRetryableAndNeverCallsPlay() = runBlocking {
        val scenario = Scenario(decryptFailure = true)
        scenario.seedSubscription()

        val result = scenario.processor.process(envelope(), notification())

        assertEquals(
            RtdnErrorCode.DEPENDENCY_UNAVAILABLE,
            assertIs<RtdnProcessResult.RetryableFailure>(result).errorCode,
        )
        assertTrue(scenario.verifier.tokens.isEmpty())
        assertEquals("DEPENDENCY_UNAVAILABLE", scenario.events.releasedErrorCode)
    }

    @Test
    fun releaseStoreFailureStillReturnsRetryableInsteadOfEscapingAsHttp500() = runBlocking {
        val scenario = Scenario(decryptFailure = true, releaseFailure = true)
        scenario.seedSubscription()

        val result = scenario.processor.process(envelope(), notification())

        assertEquals(
            RtdnErrorCode.DEPENDENCY_UNAVAILABLE,
            assertIs<RtdnProcessResult.RetryableFailure>(result).errorCode,
        )
    }

    @Test
    fun playObservationTimestampIsCapturedBeforeSlowVerificationCompletes() = runBlocking {
        var now = NOW
        val scenario = Scenario(
            nowMillis = { now },
            onVerify = { now += 5_000L },
        )
        scenario.seedSubscription()

        scenario.processor.process(envelope(), notification())

        assertEquals(NOW, scenario.repository.getSubscription(TOKEN_HASH)?.lastVerifiedAt)
    }

    private class Scenario(
        playResult: PlaySubscriptionVerificationResult = successVerification(),
        claimResult: RtdnClaimResult = RtdnClaimResult.Claimed(1),
        decryptedToken: String = RAW_TOKEN,
        decryptedHash: String = TOKEN_HASH,
        decryptFailure: Boolean = false,
        releaseFailure: Boolean = false,
        nowMillis: () -> Long = { NOW },
        onVerify: () -> Unit = {},
    ) {
        val repository = InMemoryEntitlementRepository()
        val events = RecordingEventRepository(claimResult, releaseFailure)
        val verifier = RecordingVerifier(playResult, onVerify)
        val cipher = RecordingCipher(decryptedToken, decryptFailure)
        private val hasher = object : PurchaseTokenHasher {
            override fun hashPurchaseToken(purchaseToken: String) =
                TokenHash(
                    if (purchaseToken == RAW_TOKEN) TOKEN_HASH else decryptedHash,
                    HASH_VERSION,
                    PEPPER_VERSION,
                )
        }
        val processor = RtdnProcessor(
            config = config(),
            eventRepository = events,
            entitlementRepository = repository,
            tokenHasher = hasher,
            tokenCipher = cipher,
            playVerifier = verifier,
            nowMillis = nowMillis,
        )

        suspend fun seedSubscription(status: BackendSubscriptionStatus = BackendSubscriptionStatus.Active) {
            repository.upsertSubscriptionForOwner(
                SubscriptionRecord(
                    purchaseTokenHash = TOKEN_HASH,
                    hashVersion = HASH_VERSION,
                    pepperVersion = PEPPER_VERSION,
                    ownerGoogleSub = OWNER,
                    packageName = PACKAGE_NAME,
                    productId = PRODUCT_ID,
                    basePlanId = "monthly",
                    offerId = null,
                    linkedPurchaseTokenHash = null,
                    tokenCiphertext = CIPHERTEXT,
                    keyVersion = "1",
                    encryptedAt = NOW - 10_000,
                    encryptionAlgorithm = "GOOGLE_TINK",
                    acknowledgementState = BackendAcknowledgementState.Acknowledged,
                    acknowledgementAttemptCount = 0,
                    nextAcknowledgementAttemptAt = null,
                    lastAcknowledgementErrorCode = null,
                    lastVerifiedAt = NOW - 10_000,
                    status = status,
                    expiryTime = NOW - 1,
                ),
            )
        }
    }

    private class RecordingEventRepository(
        private val claimResult: RtdnClaimResult,
        private val releaseFailure: Boolean,
    ) : RtdnEventRepository {
        var completedOutcome: String? = null
        var releasedErrorCode: String? = null

        override suspend fun claim(messageIdHash: String, now: Long, leaseUntil: Long) = claimResult

        override suspend fun complete(
            messageIdHash: String,
            generation: Long,
            completedAt: Long,
            outcome: String,
        ): Boolean {
            completedOutcome = outcome
            return true
        }

        override suspend fun release(
            messageIdHash: String,
            generation: Long,
            retryAt: Long,
            errorCode: String,
        ): Boolean {
            if (releaseFailure) error("redacted")
            releasedErrorCode = errorCode
            return true
        }
    }

    private class RecordingVerifier(
        private val result: PlaySubscriptionVerificationResult,
        private val onVerify: () -> Unit,
    ) : PlaySubscriptionVerifier {
        val tokens = mutableListOf<String>()

        override suspend fun verify(packageName: String, purchaseToken: String): PlaySubscriptionVerificationResult {
            tokens += purchaseToken
            onVerify()
            return result
        }
    }

    private class RecordingCipher(
        private val decryptedToken: String,
        private val decryptFailure: Boolean,
    ) : PurchaseTokenCipher {
        var decryptCount = 0

        override fun encrypt(purchaseToken: String, now: Long): TokenCiphertext = error("not used")

        override fun decrypt(ciphertext: TokenCiphertext): String {
            decryptCount += 1
            if (decryptFailure) error("redacted")
            return decryptedToken
        }
    }

    private data class LifecycleCase(
        val status: BackendSubscriptionStatus,
        val canceledButActive: Boolean,
        val hasPremium: Boolean,
    )

    companion object {
        private const val NOW = 1_783_814_400_000L
        private const val RAW_TOKEN = "raw-purchase-token"
        private const val TOKEN_HASH = "token-hash"
        private const val CIPHERTEXT = "encrypted-token"
        private const val HASH_VERSION = "hmac-sha256-v1"
        private const val PEPPER_VERSION = "1"
        private const val OWNER = "google-sub"
        private const val PACKAGE_NAME = "com.brianyeh.justnotes"
        private const val PRODUCT_ID = "just_notes_premium"

        private fun envelope() = RtdnEnvelope("message-1", null, "[REDACTED]")

        private fun notification(notificationType: Int = 4) = RtdnNotification(
            version = "1.0",
            packageName = PACKAGE_NAME,
            eventTimeMillis = NOW,
            subscription = SubscriptionNotificationHint(notificationType, RAW_TOKEN, PRODUCT_ID),
        )

        private fun successVerification(
            status: BackendSubscriptionStatus = BackendSubscriptionStatus.Active,
            canceledButActive: Boolean = false,
        ) = PlaySubscriptionVerificationResult.Success(
            PlaySubscriptionVerification(
                packageName = PACKAGE_NAME,
                purchaseTokenHash = TOKEN_HASH,
                subscriptionState = status,
                lineItems = listOf(
                    PlaySubscriptionLineItem(PRODUCT_ID, "monthly", null, NOW + 86_400_000L),
                ),
                acknowledgementState = "ACKNOWLEDGED",
                playAcknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED,
                autoRenewing = true,
                linkedPurchaseTokenHash = null,
                canceledButActiveUntilExpiry = canceledButActive,
            ),
        )

        private fun config() = BackendConfig(
            environment = "test",
            allowedPackageName = PACKAGE_NAME,
            allowedProductId = PRODUCT_ID,
            allowedBasePlanIds = setOf("monthly", "annual"),
            allowedOffersByBasePlanId = mapOf("monthly" to setOf(null, "trial10d"), "annual" to setOf(null)),
            googleWebClientId = null,
            issuerAllowlist = emptySet(),
            firestoreProjectId = null,
            firestoreDatabaseId = "(default)",
            tokenHashSecretResource = null,
            obfuscatedAccountSecretResource = null,
            emailHashSecretResource = null,
            kmsTokenEncryptionKeyResource = null,
            entitlementReverifyTtlMillis = 1,
            entitlementMaxStaleMillis = 86_400_000L,
        )
    }
}
