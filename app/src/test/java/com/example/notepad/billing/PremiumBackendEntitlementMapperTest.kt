package com.example.notepad.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumBackendEntitlementMapperTest {
    @Test
    fun backendVerifiedAcknowledgedActiveSubscriptionGrantsPremium() {
        val result = PremiumBackendEntitlementMapper.fromVerification(
            expectedPackageName = PACKAGE_NAME,
            verification = activeVerification(),
            now = NOW,
        )

        assertTrue(result.accepted)
        assertEquals(PremiumSubscriptionStatus.Active, result.snapshot.status)
        assertEquals(PremiumEntitlementSource.BackendVerified, result.snapshot.source)
        assertEquals(PremiumAcknowledgementStatus.Acknowledged, result.snapshot.acknowledgementStatus)
        assertTrue(result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
    }

    @Test
    fun activeSubscriptionWithoutBackendAcknowledgementStaysVerificationPending() {
        val result = PremiumBackendEntitlementMapper.fromVerification(
            expectedPackageName = PACKAGE_NAME,
            verification = activeVerification(
                acknowledgementState = PremiumBackendAcknowledgementState.Pending,
            ),
            now = NOW,
        )

        assertTrue(result.accepted)
        assertEquals(PremiumSubscriptionStatus.VerificationPending, result.snapshot.status)
        assertEquals(PremiumAcknowledgementStatus.BackendRequired, result.snapshot.acknowledgementStatus)
        assertFalse(result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
    }

    @Test
    fun acknowledgementFailureSchedulesRetryAndDoesNotGrantPremium() {
        val result = PremiumBackendEntitlementMapper.fromVerification(
            expectedPackageName = PACKAGE_NAME,
            verification = activeVerification(
                acknowledgementState = PremiumBackendAcknowledgementState.Failed,
                acknowledgementAttemptCount = 2,
                nextAcknowledgementAttemptAt = NOW + 30_000L,
                lastAcknowledgementError = "Google Play acknowledgement timed out.",
            ),
            now = NOW,
        )

        assertTrue(result.accepted)
        assertEquals(PremiumSubscriptionStatus.VerificationPending, result.snapshot.status)
        assertEquals(PremiumAcknowledgementStatus.RetryScheduled, result.snapshot.acknowledgementStatus)
        assertEquals(2, result.snapshot.acknowledgementAttemptCount)
        assertFalse(result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
    }

    @Test
    fun backendRejectsWrongPackageProductAndBasePlan() {
        val wrongPackage = PremiumBackendEntitlementMapper.fromVerification(
            expectedPackageName = PACKAGE_NAME,
            verification = activeVerification(packageName = "com.attacker.notes"),
            now = NOW,
        )
        val wrongProduct = PremiumBackendEntitlementMapper.fromVerification(
            expectedPackageName = PACKAGE_NAME,
            verification = activeVerification(productId = "other_subscription"),
            now = NOW,
        )
        val wrongBasePlan = PremiumBackendEntitlementMapper.fromVerification(
            expectedPackageName = PACKAGE_NAME,
            verification = activeVerification(basePlanId = "weekly"),
            now = NOW,
        )
        val legacyProduct = PremiumBackendEntitlementMapper.fromVerification(
            expectedPackageName = PACKAGE_NAME,
            verification = activeVerification(productId = "just_notes_premium_monthly"),
            now = NOW,
        )

        listOf(wrongPackage, wrongProduct, wrongBasePlan, legacyProduct).forEach { result ->
            assertFalse(result.accepted)
            assertEquals(PremiumSubscriptionStatus.Error, result.snapshot.status)
            assertFalse(result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
        }
    }

    @Test
    fun rtdnDuplicateMessageIsIgnoredAfterRequeryRevokesAccess() {
        val initial = PremiumRtdnSimulationState(
            snapshot = PremiumBackendEntitlementMapper.fromVerification(
                expectedPackageName = PACKAGE_NAME,
                verification = activeVerification(),
                now = NOW,
            ).snapshot,
        )
        val event = PremiumRtdnEvent(
            messageId = "message-1",
            purchaseToken = PURCHASE_TOKEN,
            eventTime = NOW + 1L,
            notificationType = PremiumRtdnNotificationType.Revoked,
        )

        val revoked = PremiumBackendEntitlementMapper.applyRtdnRequery(
            state = initial,
            event = event,
            requeryResult = activeVerification(
                subscriptionState = PremiumBackendSubscriptionState.Revoked,
            ),
            expectedPackageName = PACKAGE_NAME,
            now = NOW + 2L,
        )
        val duplicateWithDifferentPayload = PremiumBackendEntitlementMapper.applyRtdnRequery(
            state = revoked,
            event = event.copy(notificationType = PremiumRtdnNotificationType.Renewed),
            requeryResult = activeVerification(),
            expectedPackageName = PACKAGE_NAME,
            now = NOW + 3L,
        )

        assertEquals(PremiumSubscriptionStatus.Revoked, revoked.snapshot.status)
        assertFalse(revoked.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
        assertEquals(revoked, duplicateWithDifferentPayload)
    }

    @Test
    fun rtdnUsesRequeriedGooglePlayStateInsteadOfNotificationType() {
        val event = PremiumRtdnEvent(
            messageId = "message-2",
            purchaseToken = PURCHASE_TOKEN,
            eventTime = NOW + 1L,
            notificationType = PremiumRtdnNotificationType.Expired,
        )

        val state = PremiumBackendEntitlementMapper.applyRtdnRequery(
            state = PremiumRtdnSimulationState(),
            event = event,
            requeryResult = activeVerification(
                subscriptionState = PremiumBackendSubscriptionState.GracePeriod,
            ),
            expectedPackageName = PACKAGE_NAME,
            now = NOW + 2L,
        )

        assertEquals(PremiumSubscriptionStatus.GracePeriod, state.snapshot.status)
        assertTrue(state.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
    }

    @Test
    fun replacementPurchaseMovesEntitlementToNewToken() {
        val oldTokenHash = PremiumEntitlementStore.hashPurchaseToken(PURCHASE_TOKEN)
        val newToken = "new-token"

        val result = PremiumBackendEntitlementMapper.fromVerification(
            expectedPackageName = PACKAGE_NAME,
            verification = activeVerification(
                purchaseToken = newToken,
                linkedPurchaseToken = PURCHASE_TOKEN,
                basePlanId = "annual",
            ),
            now = NOW,
        )

        assertTrue(result.accepted)
        assertEquals(PremiumSubscriptionStatus.Active, result.snapshot.status)
        assertEquals(PremiumEntitlementStore.hashPurchaseToken(newToken), result.snapshot.purchaseTokenHash)
        assertFalse(result.snapshot.purchaseTokenHash == oldTokenHash)
        assertTrue(result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
    }

    @Test
    fun backendEntitlementResponseMapsToBackendVerifiedSnapshot() {
        val result = PremiumBackendEntitlementMapper.fromEntitlementResponse(
            expectedPackageName = PACKAGE_NAME,
            response = PremiumBackendEntitlementResponse(
                hasPremium = true,
                status = PremiumSubscriptionStatus.Active,
                source = PremiumEntitlementSource.BackendVerified,
                packageName = PACKAGE_NAME,
                productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
                basePlanId = "monthly",
                offerId = "trial10d",
                expiryTime = NOW + 1_000L,
                lastVerifiedAt = NOW,
                purchaseTokenHash = "token-hash",
            ),
            now = NOW,
        )

        assertTrue(result.accepted)
        assertEquals(PremiumEntitlementSource.BackendVerified, result.snapshot.source)
        assertEquals(PremiumSubscriptionStatus.Active, result.snapshot.status)
        assertEquals("token-hash", result.snapshot.purchaseTokenHash)
        assertTrue(result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
    }

    @Test
    fun noBackendRecordResponseIsAcceptedWithoutApplyingSnapshot() {
        val result = PremiumBackendEntitlementMapper.fromEntitlementResponse(
            expectedPackageName = PACKAGE_NAME,
            response = PremiumBackendEntitlementResponse(
                hasPremium = false,
                status = PremiumSubscriptionStatus.Unknown,
                source = PremiumEntitlementSource.None,
            ),
            now = NOW,
        )

        assertTrue(result.accepted)
        assertFalse(result.shouldApplySnapshot)
        assertEquals(PremiumSubscriptionStatus.Unknown, result.snapshot.status)
        assertEquals(PremiumEntitlementSource.None, result.snapshot.source)
        assertFalse(result.snapshot.hasPremiumAccess(allowClientObservedAccess = true, now = NOW))
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
        val clientObserved = PremiumBackendEntitlementMapper.fromEntitlementResponse(
            expectedPackageName = PACKAGE_NAME,
            response = PremiumBackendEntitlementResponse(
                hasPremium = true,
                status = PremiumSubscriptionStatus.Active,
                source = PremiumEntitlementSource.ClientObserved,
                packageName = PACKAGE_NAME,
                productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
                basePlanId = "monthly",
            ),
            now = NOW,
        )
        val inconsistentPremium = PremiumBackendEntitlementMapper.fromEntitlementResponse(
            expectedPackageName = PACKAGE_NAME,
            response = PremiumBackendEntitlementResponse(
                hasPremium = true,
                status = PremiumSubscriptionStatus.Expired,
                source = PremiumEntitlementSource.BackendVerified,
                packageName = PACKAGE_NAME,
                productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
                basePlanId = "monthly",
            ),
            now = NOW,
        )

        listOf(clientObserved, inconsistentPremium).forEach { result ->
            assertFalse(result.accepted)
            assertEquals(PremiumSubscriptionStatus.Error, result.snapshot.status)
            assertFalse(result.snapshot.hasPremiumAccess(allowClientObservedAccess = false, now = NOW))
        }
    }

    private fun activeVerification(
        packageName: String = PACKAGE_NAME,
        productId: String = PremiumCatalog.PREFERRED_PRODUCT_ID,
        basePlanId: String = "monthly",
        purchaseToken: String = PURCHASE_TOKEN,
        linkedPurchaseToken: String? = null,
        subscriptionState: PremiumBackendSubscriptionState = PremiumBackendSubscriptionState.Active,
        acknowledgementState: PremiumBackendAcknowledgementState = PremiumBackendAcknowledgementState.Acknowledged,
        acknowledgementAttemptCount: Int = 0,
        nextAcknowledgementAttemptAt: Long? = null,
        lastAcknowledgementError: String? = null,
    ): PremiumBackendPurchaseVerification {
        return PremiumBackendPurchaseVerification(
            packageName = packageName,
            productId = productId,
            basePlanId = basePlanId,
            purchaseToken = purchaseToken,
            linkedPurchaseToken = linkedPurchaseToken,
            purchaseTime = NOW - 1_000L,
            expiryTime = NOW + 30L * 24L * 60L * 60L * 1_000L,
            subscriptionState = subscriptionState,
            acknowledgementState = acknowledgementState,
            acknowledgementAttemptCount = acknowledgementAttemptCount,
            nextAcknowledgementAttemptAt = nextAcknowledgementAttemptAt,
            lastAcknowledgementError = lastAcknowledgementError,
        )
    }

    private fun noBackendRecordResult(): PremiumBackendVerificationResult {
        return PremiumBackendEntitlementMapper.fromEntitlementResponse(
            expectedPackageName = PACKAGE_NAME,
            response = PremiumBackendEntitlementResponse(
                hasPremium = false,
                status = PremiumSubscriptionStatus.Unknown,
                source = PremiumEntitlementSource.None,
            ),
            now = NOW,
        )
    }

    private companion object {
        const val PACKAGE_NAME = "com.brianyeh.justnotes"
        const val PURCHASE_TOKEN = "purchase-token"
        const val NOW = 1_762_000_000_000L
    }
}
