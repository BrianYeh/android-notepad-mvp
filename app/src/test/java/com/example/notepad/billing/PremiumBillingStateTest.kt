package com.example.notepad.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumBillingStateTest {
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
            ),
        )

        assertTrue(state.hasPremiumAccess)
    }

    @Test
    fun hasPremiumAccessRejectsExpiredBackendVerifiedCache() {
        val state = PremiumBillingState(
            subscription = PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.Active,
                source = PremiumEntitlementSource.BackendVerified,
                expiryTime = System.currentTimeMillis() - 1_000L,
            ),
        )

        assertFalse(state.hasPremiumAccess)
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
    fun catalogCanDisplayRecurringPriceFromTrialOfferWithoutSelectingPurchaseOffer() {
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
        assertEquals(null, PremiumCatalog.selectBasePlanOffer(PremiumPlan.Monthly, candidates))
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
}
