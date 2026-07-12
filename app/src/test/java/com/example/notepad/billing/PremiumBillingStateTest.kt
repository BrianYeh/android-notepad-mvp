package com.example.notepad.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumBillingStateTest {
    @Test
    fun obfuscatedAccountIdAcceptsOnlyPlaySafeOneTo64CharacterValues() {
        assertTrue(isValidObfuscatedExternalAccountId("a"))
        assertTrue(isValidObfuscatedExternalAccountId("A_z-9"))
        assertTrue(isValidObfuscatedExternalAccountId("x".repeat(64)))
        assertFalse(isValidObfuscatedExternalAccountId(""))
        assertFalse(isValidObfuscatedExternalAccountId("x".repeat(65)))
        assertFalse(isValidObfuscatedExternalAccountId("contains space"))
        assertFalse(isValidObfuscatedExternalAccountId("contains+plus"))
    }

    @Test
    fun backendPurchaseLaunchRequiresEveryBackendAndBillingPrerequisite() {
        val ready = PremiumBillingState(
            billingAvailable = true,
            backendPurchaseReady = true,
            loading = false,
        )

        assertTrue(ready.canLaunchPurchase(enableBackendPurchaseFlow = true))
        assertFalse(ready.copy(backendPurchaseReady = false).canLaunchPurchase(true))
        assertFalse(ready.copy(billingAvailable = false).canLaunchPurchase(true))
        assertFalse(ready.copy(purchaseLaunching = true).canLaunchPurchase(true))
        assertFalse(ready.copy(purchaseVerificationInFlight = true).canLaunchPurchase(true))
        assertFalse(ready.canLaunchPurchase(enableBackendPurchaseFlow = false))
    }

    @Test
    fun pendingAndVerificationPendingSubscriptionsBlockAnotherLaunch() {
        val ready = PremiumBillingState(billingAvailable = true, backendPurchaseReady = true)

        assertFalse(
            ready.copy(
                subscription = PremiumSubscriptionSnapshot(status = PremiumSubscriptionStatus.PendingPurchase),
            ).canLaunchPurchase(true),
        )
        assertFalse(
            ready.copy(
                subscription = PremiumSubscriptionSnapshot(status = PremiumSubscriptionStatus.VerificationPending),
            ).canLaunchPurchase(true),
        )
    }

    @Test
    fun purchasedCallbackEmitsBufferedCandidateWithLaunchMetadata() = runBlocking {
        val channel = Channel<BackendPurchaseCandidate>(Channel.BUFFERED)
        val emitted = emitBackendPurchaseCandidates(
            channel = channel,
            responseCode = BillingClient.BillingResponseCode.OK,
            purchases = listOf(purchaseObservation()),
            launchMetadata = PendingLaunchMetadata(
                productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
                basePlanId = "monthly",
                offerId = "trial10d",
            ),
            isRestore = false,
            appVersion = "1.0.7",
            versionCode = 5L,
            deviceLocale = "zh-TW",
        )

        assertEquals(1, emitted)
        val candidate = channel.receive()
        assertEquals(RAW_TOKEN, candidate.purchaseToken)
        assertEquals("monthly", candidate.basePlanId)
        assertEquals("trial10d", candidate.offerId)
    }

    @Test
    fun pendingAndCanceledUpdatesEmitNoVerificationCandidate() {
        val channel = Channel<BackendPurchaseCandidate>(Channel.BUFFERED)
        val pending = emitBackendPurchaseCandidates(
            channel = channel,
            responseCode = BillingClient.BillingResponseCode.OK,
            purchases = listOf(purchaseObservation(state = Purchase.PurchaseState.PENDING)),
            launchMetadata = null,
            isRestore = false,
            appVersion = "1.0.7",
            versionCode = 5L,
            deviceLocale = "zh-TW",
        )
        val canceled = emitBackendPurchaseCandidates(
            channel = channel,
            responseCode = BillingClient.BillingResponseCode.USER_CANCELED,
            purchases = listOf(purchaseObservation()),
            launchMetadata = null,
            isRestore = false,
            appVersion = "1.0.7",
            versionCode = 5L,
            deviceLocale = "zh-TW",
        )

        assertEquals(0, pending)
        assertEquals(0, canceled)
        assertTrue(channel.tryReceive().isFailure)
    }

    @Test
    fun duplicateCallbacksMayEmitAgainAndRestoreUsesNullPlanHints() = runBlocking {
        val channel = Channel<BackendPurchaseCandidate>(Channel.BUFFERED)
        val metadata = PendingLaunchMetadata(
            productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
            basePlanId = "monthly",
            offerId = "trial10d",
        )

        repeat(2) {
            assertEquals(
                1,
                emitBackendPurchaseCandidates(
                    channel = channel,
                    responseCode = BillingClient.BillingResponseCode.OK,
                    purchases = listOf(purchaseObservation()),
                    launchMetadata = metadata,
                    isRestore = false,
                    appVersion = "1.0.7",
                    versionCode = 5L,
                    deviceLocale = "zh-TW",
                ),
            )
        }
        assertEquals(RAW_TOKEN, channel.receive().purchaseToken)
        assertEquals(RAW_TOKEN, channel.receive().purchaseToken)

        assertEquals(
            1,
            emitBackendPurchaseCandidates(
                channel = channel,
                responseCode = BillingClient.BillingResponseCode.OK,
                purchases = listOf(purchaseObservation()),
                launchMetadata = metadata,
                isRestore = true,
                appVersion = "1.0.7",
                versionCode = 5L,
                deviceLocale = "zh-TW",
            ),
        )
        val restored = channel.receive()
        assertEquals(null, restored.basePlanId)
        assertEquals(null, restored.offerId)
    }

    @Test
    fun oneCallbackAppliesLaunchMetadataToOnlyTheNewestMatchingPurchase() = runBlocking {
        val channel = Channel<BackendPurchaseCandidate>(Channel.BUFFERED)

        assertEquals(
            2,
            emitBackendPurchaseCandidates(
                channel = channel,
                responseCode = BillingClient.BillingResponseCode.OK,
                purchases = listOf(
                    purchaseObservation(token = "older-token", purchaseTime = 100L),
                    purchaseObservation(token = "newer-token", purchaseTime = 200L),
                ),
                launchMetadata = PendingLaunchMetadata(
                    productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
                    basePlanId = "monthly",
                    offerId = "trial10d",
                ),
                isRestore = false,
                appVersion = "1.0.7",
                versionCode = 5L,
                deviceLocale = "zh-TW",
            ),
        )
        val candidates = listOf(channel.receive(), channel.receive()).associateBy { it.purchaseToken }

        assertEquals(null, candidates.getValue("older-token").basePlanId)
        assertEquals("monthly", candidates.getValue("newer-token").basePlanId)
        assertEquals("trial10d", candidates.getValue("newer-token").offerId)
    }

    @Test
    fun hasPremiumAccessDefaultsToFalse() {
        assertFalse(PremiumBillingState().hasPremiumAccess)
    }

    @Test
    fun hasPremiumAccessUsesBackendVerifiedActiveSubscription() {
        val state = PremiumBillingState(
            subscription = PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.Active,
                source = PremiumEntitlementSource.BackendVerified,
                expiryTime = System.currentTimeMillis() + 60_000L,
                acknowledgementStatus = PremiumAcknowledgementStatus.Acknowledged,
            ),
        )

        assertTrue(state.hasPremiumAccess)
    }

    @Test
    fun hasPremiumAccessUsesBackendVerifiedGracePeriodSubscription() {
        val state = PremiumBillingState(
            subscription = PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.GracePeriod,
                source = PremiumEntitlementSource.BackendVerified,
                expiryTime = System.currentTimeMillis() + 60_000L,
                acknowledgementStatus = PremiumAcknowledgementStatus.Acknowledged,
            ),
        )

        assertTrue(state.hasPremiumAccess)
    }

    @Test
    fun reviewerGrantRequiresActiveFutureExpiryAndNotRequiredAcknowledgement() {
        val now = System.currentTimeMillis()
        val valid = PremiumSubscriptionSnapshot(
            status = PremiumSubscriptionStatus.Active,
            source = PremiumEntitlementSource.ReviewerGrant,
            expiryTime = now + 60_000L,
            acknowledgementStatus = PremiumAcknowledgementStatus.NotRequired,
        )

        assertTrue(valid.hasPremiumAccess(allowClientObservedAccess = false, now = now))
        assertFalse(valid.copy(status = PremiumSubscriptionStatus.GracePeriod)
            .hasPremiumAccess(allowClientObservedAccess = false, now = now))
        assertFalse(valid.copy(expiryTime = now)
            .hasPremiumAccess(allowClientObservedAccess = false, now = now))
        assertFalse(valid.copy(acknowledgementStatus = PremiumAcknowledgementStatus.Acknowledged)
            .hasPremiumAccess(allowClientObservedAccess = false, now = now))
    }

    @Test
    fun hasPremiumAccessRejectsExpiredBackendVerifiedCache() {
        val state = PremiumBillingState(
            subscription = PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.Active,
                source = PremiumEntitlementSource.BackendVerified,
                expiryTime = System.currentTimeMillis() - 1_000L,
                acknowledgementStatus = PremiumAcknowledgementStatus.Acknowledged,
            ),
        )

        assertFalse(state.hasPremiumAccess)
    }

    @Test
    fun backendVerifiedGrantableStatesRequireAcknowledgement() {
        val grantableStatuses = listOf(
            PremiumSubscriptionStatus.Active,
            PremiumSubscriptionStatus.GracePeriod,
        )
        val unacknowledgedStatuses = listOf(
            PremiumAcknowledgementStatus.Pending,
            PremiumAcknowledgementStatus.Failed,
            PremiumAcknowledgementStatus.Unknown,
            PremiumAcknowledgementStatus.NotRequired,
        )

        grantableStatuses.forEach { status ->
            unacknowledgedStatuses.forEach { acknowledgementStatus ->
                val subscription = PremiumSubscriptionSnapshot(
                    status = status,
                    source = PremiumEntitlementSource.BackendVerified,
                    expiryTime = System.currentTimeMillis() + 60_000L,
                    acknowledgementStatus = acknowledgementStatus,
                )

                assertFalse(
                    "$status with $acknowledgementStatus must fail closed",
                    subscription.hasPremiumAccess(allowClientObservedAccess = false),
                )
            }
        }
    }

    @Test
    fun canceledActiveUntilExpiryGrantsOnlyWhileAcknowledgedAndUnexpired() {
        val now = System.currentTimeMillis()
        val acknowledged = PremiumSubscriptionSnapshot(
            status = PremiumSubscriptionStatus.CanceledActiveUntilExpiry,
            source = PremiumEntitlementSource.BackendVerified,
            expiryTime = now + 60_000L,
            acknowledgementStatus = PremiumAcknowledgementStatus.Acknowledged,
        )
        val expired = acknowledged.copy(expiryTime = now - 1L)
        val pending = acknowledged.copy(
            acknowledgementStatus = PremiumAcknowledgementStatus.Pending,
        )

        assertTrue(acknowledged.hasPremiumAccess(allowClientObservedAccess = false, now = now))
        assertFalse(expired.hasPremiumAccess(allowClientObservedAccess = false, now = now))
        assertFalse(pending.hasPremiumAccess(allowClientObservedAccess = false, now = now))
    }

    @Test
    fun pausedBackendVerifiedSubscriptionDoesNotGrantPremium() {
        val subscription = PremiumSubscriptionSnapshot(
            status = PremiumSubscriptionStatus.Paused,
            source = PremiumEntitlementSource.BackendVerified,
            expiryTime = System.currentTimeMillis() + 60_000L,
            acknowledgementStatus = PremiumAcknowledgementStatus.Acknowledged,
        )

        assertFalse(subscription.hasPremiumAccess(allowClientObservedAccess = false))
    }

    @Test
    fun hasPremiumAccessRejectsClientObservedPurchaseBeforeAcknowledgement() {
        val state = PremiumBillingState(
            subscription = PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.VerificationPending,
                source = PremiumEntitlementSource.ClientObserved,
                acknowledgementStatus = PremiumAcknowledgementStatus.Pending,
            ),
        )

        assertFalse(state.hasPremiumAccess)
    }

    @Test
    fun hasPremiumAccessRejectsClientObservedPurchaseWithoutExplicitBuildGate() {
        val subscription = PremiumSubscriptionSnapshot(
            status = PremiumSubscriptionStatus.Active,
            source = PremiumEntitlementSource.ClientObserved,
            acknowledgementStatus = PremiumAcknowledgementStatus.Acknowledged,
        )

        assertFalse(subscription.hasPremiumAccess(allowClientObservedAccess = false))
    }

    @Test
    fun hasPremiumAccessRejectsPendingPurchase() {
        val state = PremiumBillingState(
            subscription = PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.PendingPurchase,
                source = PremiumEntitlementSource.ClientObserved,
            ),
        )

        assertFalse(state.hasPremiumAccess)
    }

    @Test
    fun canLaunchPurchaseRejectsInFlightStates() {
        assertFalse(
            PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.PendingPurchase,
            ).canLaunchPurchase(allowClientOnlyBilling = true),
        )
        assertFalse(
            PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.VerificationPending,
            ).canLaunchPurchase(allowClientOnlyBilling = true),
        )
    }

    @Test
    fun hasPremiumAccessUsesDebugOverrideWithoutChangingSubscription() {
        val state = PremiumBillingState(debugPremiumOverride = true)

        assertFalse(state.isPremium)
        assertTrue(state.hasPremiumAccess)
    }

    @Test
    fun catalogSelectsPreferredNoOfferBasePlan() {
        val selected = PremiumCatalog.selectBasePlanOffer(
            plan = PremiumPlan.Monthly,
            candidates = listOf(
                PremiumOfferCandidate(
                    productId = "just_notes_premium",
                    basePlanId = "monthly",
                    offerId = "intro",
                    offerToken = "intro-token",
                    formattedPrice = "\$0.99",
                ),
                PremiumOfferCandidate(
                    productId = "just_notes_premium",
                    basePlanId = "monthly",
                    offerId = null,
                    offerToken = "base-token",
                    formattedPrice = "\$1.99",
                ),
            ),
        )

        assertTrue(selected?.offerToken == "base-token")
    }

    @Test
    fun catalogDoesNotSelectAmbiguousDuplicateBasePlans() {
        val selected = PremiumCatalog.selectBasePlanOffer(
            plan = PremiumPlan.Annual,
            candidates = listOf(
                PremiumOfferCandidate(
                    productId = "just_notes_premium",
                    basePlanId = "annual",
                    offerId = null,
                    offerToken = "annual-one",
                    formattedPrice = "\$19.99",
                ),
                PremiumOfferCandidate(
                    productId = "just_notes_premium",
                    basePlanId = "annual",
                    offerId = null,
                    offerToken = "annual-two",
                    formattedPrice = "\$20.99",
                ),
            ),
        )

        assertFalse(selected != null)
    }

    @Test
    fun catalogDoesNotFallBackWhenPreferredProductBasePlanIsAmbiguous() {
        val selected = PremiumCatalog.selectBasePlanOffer(
            plan = PremiumPlan.Monthly,
            candidates = listOf(
                PremiumOfferCandidate(
                    productId = "just_notes_premium",
                    basePlanId = "monthly",
                    offerId = null,
                    offerToken = "preferred-one",
                    formattedPrice = "\$1.99",
                ),
                PremiumOfferCandidate(
                    productId = "just_notes_premium",
                    basePlanId = "monthly",
                    offerId = null,
                    offerToken = "preferred-two",
                    formattedPrice = "\$2.99",
                ),
                PremiumOfferCandidate(
                    productId = "just_notes_premium_monthly",
                    basePlanId = "monthly",
                    offerId = null,
                    offerToken = "fallback",
                    formattedPrice = "\$1.99",
                ),
            ),
        )

        assertFalse(selected != null)
    }

    @Test
    fun catalogQueriesOnlyPreferredSubscriptionProduct() {
        assertEquals(listOf(PremiumCatalog.PREFERRED_PRODUCT_ID), PremiumCatalog.productIdsToQuery)
    }

    @Test
    fun catalogDoesNotTreatLegacyProductsAsNewPremiumProducts() {
        assertFalse(PremiumCatalog.isPremiumProduct("just_notes_premium_monthly"))
        assertFalse(PremiumCatalog.isPremiumProduct("just_notes_premium_annual"))
        assertEquals(null, PremiumCatalog.matchingPremiumProductId(listOf("just_notes_premium_monthly")))
    }

    @Test
    fun catalogDisplaysAndSelectsTheConfiguredMonthlyTrialOffer() {
        val candidates = listOf(
            PremiumOfferCandidate(
                productId = "just_notes_premium",
                basePlanId = "monthly",
                offerId = "trial10d",
                offerToken = "trial-token",
                formattedPrice = "TWD 33/month",
            ),
        )

        assertEquals("TWD 33/month", PremiumCatalog.selectDisplayPrice(PremiumPlan.Monthly, candidates))
        assertEquals(
            "trial-token",
            PremiumCatalog.selectBasePlanOffer(PremiumPlan.Monthly, candidates)?.offerToken,
        )
    }

    @Test
    fun catalogDoesNotDisplayAnnualTrialWhenOnlyMonthlyTrialExists() {
        val candidates = listOf(
            PremiumOfferCandidate(
                productId = "just_notes_premium",
                basePlanId = "monthly",
                offerId = "trial10d",
                offerToken = "trial-token",
                formattedPrice = "TWD 33/month",
            ),
            PremiumOfferCandidate(
                productId = "just_notes_premium",
                basePlanId = "annual",
                offerId = null,
                offerToken = "annual-token",
                formattedPrice = "TWD 330/year",
            ),
        )

        assertEquals("TWD 330/year", PremiumCatalog.selectDisplayPrice(PremiumPlan.Annual, candidates))
        assertEquals("annual-token", PremiumCatalog.selectBasePlanOffer(PremiumPlan.Annual, candidates)?.offerToken)
    }

    private fun purchaseObservation(
        state: Int = Purchase.PurchaseState.PURCHASED,
        token: String = RAW_TOKEN,
        purchaseTime: Long = 1_762_000_000_000L,
    ): PremiumPlayPurchaseObservation {
        return PremiumPlayPurchaseObservation(
            purchaseToken = token,
            products = listOf(PremiumCatalog.PREFERRED_PRODUCT_ID),
            purchaseState = state,
            purchaseTime = purchaseTime,
            isSuspended = false,
        )
    }

    private companion object {
        const val RAW_TOKEN = "transient-raw-token"
    }
}
