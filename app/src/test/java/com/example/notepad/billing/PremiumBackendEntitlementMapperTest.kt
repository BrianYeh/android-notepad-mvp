package com.example.notepad.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumBackendEntitlementMapperTest {
    @Test
    fun acknowledgedGrantableBackendResponsesMapWithoutFlatteningStatus() {
        val grantableStatuses = listOf(
            PremiumSubscriptionStatus.Active,
            PremiumSubscriptionStatus.GracePeriod,
            PremiumSubscriptionStatus.CanceledActiveUntilExpiry,
        )

        grantableStatuses.forEach { status ->
            val result = mapResponse(
                response = backendResponse(status = status),
            )

            assertTrue("$status should be accepted", result.accepted)
            assertEquals(status, result.snapshot.status)
            assertEquals(PremiumEntitlementSource.BackendVerified, result.snapshot.source)
            assertEquals(
                PremiumAcknowledgementStatus.Acknowledged,
                result.snapshot.acknowledgementStatus,
            )
            assertTrue(
                "$status should grant while acknowledged and unexpired",
                result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW),
            )
        }
    }

    @Test
    fun pendingFailedAndUnknownBackendAcknowledgementsFailClosedAndMapFaithfully() {
        val grantableStatuses = listOf(
            PremiumSubscriptionStatus.Active,
            PremiumSubscriptionStatus.GracePeriod,
        )
        val acknowledgementMappings = mapOf(
            PremiumBackendAcknowledgementState.Pending to PremiumAcknowledgementStatus.Pending,
            PremiumBackendAcknowledgementState.Failed to PremiumAcknowledgementStatus.Failed,
            PremiumBackendAcknowledgementState.Unknown to PremiumAcknowledgementStatus.Unknown,
        )

        grantableStatuses.forEach { status ->
            acknowledgementMappings.forEach { (backendState, snapshotState) ->
                val result = mapResponse(
                    response = backendResponse(
                        hasPremium = false,
                        status = status,
                        acknowledgementState = backendState,
                    ),
                )

                assertTrue("$status with $backendState should be accepted", result.accepted)
                assertEquals(status, result.snapshot.status)
                assertEquals(snapshotState, result.snapshot.acknowledgementStatus)
                assertFalse(
                    "$status with $backendState must fail closed",
                    result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW),
                )
            }
        }
    }

    @Test
    fun missingBackendAcknowledgementFailsClosedAsUnknown() {
        val result = mapResponse(
            response = backendResponse(
                hasPremium = false,
                acknowledgementState = null,
            ),
        )

        assertTrue(result.accepted)
        assertEquals(PremiumAcknowledgementStatus.Unknown, result.snapshot.acknowledgementStatus)
        assertFalse(result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
    }

    @Test
    fun retryMetadataMapsToPendingSnapshotWithoutGrantingPremium() {
        val result = mapResponse(
            response = backendResponse(
                hasPremium = false,
                acknowledgementState = PremiumBackendAcknowledgementState.Pending,
                retryable = true,
                retryAfterSeconds = 900L,
                errorCode = "ACKNOWLEDGEMENT_RETRY",
                reason = "Purchase verification is pending.",
            ),
        )

        assertTrue(result.accepted)
        assertEquals(PremiumAcknowledgementStatus.Pending, result.snapshot.acknowledgementStatus)
        assertEquals(NOW + 900_000L, result.snapshot.nextAcknowledgementAttemptAt)
        assertEquals("Purchase verification is pending.", result.snapshot.lastAcknowledgementError)
        assertFalse(result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
    }

    @Test
    fun canceledActiveUntilExpiryRequiresFutureExpiryAndAcknowledgement() {
        val expired = mapResponse(
            response = backendResponse(
                hasPremium = false,
                status = PremiumSubscriptionStatus.CanceledActiveUntilExpiry,
                expiryTime = NOW - 1L,
            ),
        )
        val pending = mapResponse(
            response = backendResponse(
                hasPremium = false,
                status = PremiumSubscriptionStatus.CanceledActiveUntilExpiry,
                acknowledgementState = PremiumBackendAcknowledgementState.Pending,
            ),
        )

        listOf(expired, pending).forEach { result ->
            assertTrue(result.accepted)
            assertEquals(PremiumSubscriptionStatus.CanceledActiveUntilExpiry, result.snapshot.status)
            assertFalse(result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
        }
    }

    @Test
    fun pausedBackendResponseNeverGrantsPremium() {
        val result = mapResponse(
            response = backendResponse(
                hasPremium = false,
                status = PremiumSubscriptionStatus.Paused,
            ),
        )

        assertTrue(result.accepted)
        assertEquals(PremiumSubscriptionStatus.Paused, result.snapshot.status)
        assertFalse(result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
    }

    @Test
    fun backendResponseRejectsWrongPackageProductAndBasePlan() {
        val wrongPackage = mapResponse(
            response = backendResponse(packageName = "com.attacker.notes"),
        )
        val wrongProduct = mapResponse(
            response = backendResponse(productId = "other_subscription"),
        )
        val wrongBasePlan = mapResponse(
            response = backendResponse(basePlanId = "weekly"),
        )
        val legacyProduct = mapResponse(
            response = backendResponse(productId = "just_notes_premium_monthly"),
        )

        listOf(wrongPackage, wrongProduct, wrongBasePlan, legacyProduct).forEach { result ->
            assertFalse(result.accepted)
            assertEquals(PremiumSubscriptionStatus.Error, result.snapshot.status)
            assertFalse(result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
        }
    }

    @Test
    fun backendResponseValidatesTheCompletePlanAndOfferTuple() {
        val monthlyTrial = mapResponse(
            response = backendResponse(basePlanId = "monthly", offerId = "trial10d"),
        )
        val annualBasePlan = mapResponse(
            response = backendResponse(basePlanId = "annual", offerId = null),
        )
        val unknownMonthlyOffer = mapResponse(
            response = backendResponse(basePlanId = "monthly", offerId = "unknown-offer"),
        )
        val annualTrial = mapResponse(
            response = backendResponse(basePlanId = "annual", offerId = "trial10d"),
        )

        assertTrue(monthlyTrial.accepted)
        assertTrue(annualBasePlan.accepted)
        listOf(unknownMonthlyOffer, annualTrial).forEach { result ->
            assertFalse(result.accepted)
            assertFalse(result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
        }
    }

    @Test
    fun backendEntitlementResponseMapsBackendHashAndVerificationTime() {
        val result = mapResponse(
            response = backendResponse(
                offerId = "trial10d",
                purchaseTokenHash = "backend-hmac",
                lastVerifiedAt = NOW - 100L,
            ),
        )

        assertTrue(result.accepted)
        assertEquals("backend-hmac", result.snapshot.purchaseTokenHash)
        assertEquals(NOW - 100L, result.snapshot.lastBackendVerifiedAt)
        assertTrue(result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
    }

    @Test
    fun noBackendRecordResponseIsAcceptedWithoutApplyingSnapshot() {
        val result = noBackendRecordResult()

        assertTrue(result.accepted)
        assertFalse(result.shouldApplySnapshot)
        assertEquals(PremiumSubscriptionStatus.Unknown, result.snapshot.status)
        assertEquals(PremiumEntitlementSource.None, result.snapshot.source)
        assertFalse(result.snapshot.hasPremiumAccess(allowClientObservedAccess = true, now = NOW))
    }

    @Test
    fun responseWithErrorMetadataIsNotMistakenForNoBackendRecord() {
        val result = mapResponse(
            response = PremiumBackendEntitlementResponse(
                hasPremium = false,
                status = PremiumSubscriptionStatus.Unknown,
                source = PremiumEntitlementSource.None,
                retryable = true,
                retryAfterSeconds = 30L,
                errorCode = "DEPENDENCY_UNAVAILABLE",
                reason = "Purchase verification is temporarily unavailable.",
            ),
        )

        assertFalse(result.accepted)
        assertFalse(result.shouldApplySnapshot)
        assertEquals(PremiumSubscriptionStatus.Error, result.snapshot.status)
    }

    @Test
    fun operationErrorEnvelopesAreRejectedWithoutReplacingCurrentEntitlement() {
        val current = PremiumSubscriptionSnapshot(
            status = PremiumSubscriptionStatus.Active,
            source = PremiumEntitlementSource.BackendVerified,
            productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
            basePlanId = "annual",
            expiryTime = NOW + 1_000L,
            lastBackendVerifiedAt = NOW,
            acknowledgementStatus = PremiumAcknowledgementStatus.Acknowledged,
        )
        val errorCodes = listOf(
            "TOKEN_ALREADY_BOUND",
            "OWNER_MISMATCH",
            "DEPENDENCY_UNAVAILABLE",
        )

        errorCodes.forEach { errorCode ->
            val result = mapResponse(
                response = backendResponse(
                    hasPremium = false,
                    status = PremiumSubscriptionStatus.VerificationPending,
                    acknowledgementState = PremiumBackendAcknowledgementState.Pending,
                    errorCode = errorCode,
                    reason = "Purchase verification did not complete.",
                ),
            )

            assertFalse(errorCode, result.accepted)
            assertFalse(errorCode, result.shouldApplySnapshot)
            assertFalse(errorCode, shouldPersistBackendEntitlementResult(current, result))
        }
    }

    @Test
    fun noBackendRecordClearsStoredBackendVerifiedEntitlement() {
        val result = noBackendRecordResult()
        val current = PremiumSubscriptionSnapshot(
            status = PremiumSubscriptionStatus.Active,
            source = PremiumEntitlementSource.BackendVerified,
            productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
            basePlanId = "monthly",
            expiryTime = NOW + 1_000L,
            lastBackendVerifiedAt = NOW - 1_000L,
            acknowledgementStatus = PremiumAcknowledgementStatus.Acknowledged,
        )

        assertTrue(shouldPersistBackendEntitlementResult(current, result))
    }

    @Test
    fun noBackendRecordDoesNotOverwriteClientObservedEntitlement() {
        val result = noBackendRecordResult()
        val current = PremiumSubscriptionSnapshot(
            status = PremiumSubscriptionStatus.Active,
            source = PremiumEntitlementSource.ClientObserved,
            productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
            basePlanId = "monthly",
            expiryTime = NOW + 1_000L,
            acknowledgementStatus = PremiumAcknowledgementStatus.Acknowledged,
        )

        assertFalse(shouldPersistBackendEntitlementResult(current, result))
    }

    @Test
    fun backendEntitlementResponseRejectsSelfAuthoredOrInconsistentAccess() {
        val clientObserved = mapResponse(
            response = backendResponse(source = PremiumEntitlementSource.ClientObserved),
        )
        val expiredPremium = mapResponse(
            response = backendResponse(
                status = PremiumSubscriptionStatus.Expired,
                expiryTime = NOW - 1L,
            ),
        )
        val unacknowledgedPremium = mapResponse(
            response = backendResponse(
                acknowledgementState = PremiumBackendAcknowledgementState.Pending,
            ),
        )

        listOf(clientObserved, expiredPremium, unacknowledgedPremium).forEach { result ->
            assertFalse(result.accepted)
            assertEquals(PremiumSubscriptionStatus.Error, result.snapshot.status)
            assertFalse(result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
        }
    }

    private fun mapResponse(
        response: PremiumBackendEntitlementResponse,
    ): PremiumBackendVerificationResult {
        return PremiumBackendEntitlementMapper.fromEntitlementResponse(
            expectedPackageName = PACKAGE_NAME,
            response = response,
            now = NOW,
        )
    }

    private fun backendResponse(
        hasPremium: Boolean = true,
        status: PremiumSubscriptionStatus = PremiumSubscriptionStatus.Active,
        source: PremiumEntitlementSource = PremiumEntitlementSource.BackendVerified,
        packageName: String? = PACKAGE_NAME,
        productId: String? = PremiumCatalog.PREFERRED_PRODUCT_ID,
        basePlanId: String? = "monthly",
        offerId: String? = null,
        expiryTime: Long? = NOW + 30L * 24L * 60L * 60L * 1_000L,
        lastVerifiedAt: Long? = NOW,
        purchaseTokenHash: String? = "backend-hmac",
        acknowledgementState: PremiumBackendAcknowledgementState? =
            PremiumBackendAcknowledgementState.Acknowledged,
        retryable: Boolean = false,
        retryAfterSeconds: Long? = null,
        errorCode: String? = null,
        reason: String? = null,
    ): PremiumBackendEntitlementResponse {
        return PremiumBackendEntitlementResponse(
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

    private fun noBackendRecordResult(): PremiumBackendVerificationResult {
        return mapResponse(
            response = PremiumBackendEntitlementResponse(
                hasPremium = false,
                status = PremiumSubscriptionStatus.Unknown,
                source = PremiumEntitlementSource.None,
            ),
        )
    }

    private companion object {
        const val PACKAGE_NAME = "com.brianyeh.justnotes"
        const val NOW = 1_762_000_000_000L
    }
}
