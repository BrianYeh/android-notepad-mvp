package com.example.notepad.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PremiumCatalogTest {
    @Test
    fun monthlyPrefersExactlyOneTrial10dOffer() {
        val base = candidate(basePlanId = "monthly", offerId = null, token = "base")
        val trial = candidate(basePlanId = "monthly", offerId = "trial10d", token = "trial")

        assertEquals(trial, PremiumCatalog.selectBasePlanOffer(PremiumPlan.Monthly, listOf(base, trial)))
    }

    @Test
    fun monthlyFallsBackToExactlyOneNoOfferBasePlan() {
        val base = candidate(basePlanId = "monthly", offerId = null, token = "base")

        assertEquals(base, PremiumCatalog.selectBasePlanOffer(PremiumPlan.Monthly, listOf(base)))
    }

    @Test
    fun annualAcceptsOnlyNoOfferBasePlan() {
        val base = candidate(basePlanId = "annual", offerId = null, token = "base")
        val promotion = candidate(basePlanId = "annual", offerId = "promotion", token = "promo")

        assertEquals(base, PremiumCatalog.selectBasePlanOffer(PremiumPlan.Annual, listOf(base, promotion)))
        assertNull(PremiumCatalog.selectBasePlanOffer(PremiumPlan.Annual, listOf(promotion)))
    }

    @Test
    fun duplicateOffersAtSelectedPriorityFailClosed() {
        assertNull(
            PremiumCatalog.selectBasePlanOffer(
                PremiumPlan.Monthly,
                listOf(
                    candidate(basePlanId = "monthly", offerId = "trial10d", token = "trial-a"),
                    candidate(basePlanId = "monthly", offerId = "trial10d", token = "trial-b"),
                    candidate(basePlanId = "monthly", offerId = null, token = "base"),
                ),
            ),
        )
        assertNull(
            PremiumCatalog.selectLaunchDisplayPrice(
                PremiumPlan.Monthly,
                listOf(
                    candidate(basePlanId = "monthly", offerId = "trial10d", token = "trial-a"),
                    candidate(basePlanId = "monthly", offerId = "trial10d", token = "trial-b"),
                ),
            ),
        )
        assertNull(
            PremiumCatalog.selectBasePlanOffer(
                PremiumPlan.Annual,
                listOf(
                    candidate(basePlanId = "annual", offerId = null, token = "annual-a"),
                    candidate(basePlanId = "annual", offerId = null, token = "annual-b"),
                ),
            ),
        )
    }

    @Test
    fun wrongProductAndBasePlanFailClosed() {
        assertNull(
            PremiumCatalog.selectBasePlanOffer(
                PremiumPlan.Monthly,
                listOf(
                    candidate(productId = "wrong", basePlanId = "monthly", offerId = "trial10d"),
                    candidate(basePlanId = "annual", offerId = null),
                ),
            ),
        )
    }

    @Test
    fun trialCopyIsShownOnlyForSelectedTrial10d() {
        assertEquals(
            true,
            PremiumCatalog.showsTrialCopy(
                PremiumCatalog.selectBasePlanOffer(
                    PremiumPlan.Monthly,
                    listOf(candidate(basePlanId = "monthly", offerId = "trial10d")),
                ),
            ),
        )
        assertEquals(
            false,
            PremiumCatalog.showsTrialCopy(
                PremiumCatalog.selectBasePlanOffer(
                    PremiumPlan.Monthly,
                    listOf(candidate(basePlanId = "monthly", offerId = null)),
                ),
            ),
        )
        assertEquals(false, PremiumCatalog.showsTrialCopy(null))
    }

    private fun candidate(
        productId: String = PremiumCatalog.PREFERRED_PRODUCT_ID,
        basePlanId: String,
        offerId: String?,
        token: String = "token",
    ) = PremiumOfferCandidate(
        productId = productId,
        basePlanId = basePlanId,
        offerId = offerId,
        offerToken = token,
        formattedPrice = "$1.99",
    )
}
