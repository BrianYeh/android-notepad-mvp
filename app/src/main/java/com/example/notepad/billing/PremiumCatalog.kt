package com.example.notepad.billing

enum class PremiumPlan(
    val primaryProductId: String,
    val basePlanId: String,
) {
    Monthly(
        primaryProductId = PremiumCatalog.PREFERRED_PRODUCT_ID,
        basePlanId = "monthly",
    ),
    Annual(
        primaryProductId = PremiumCatalog.PREFERRED_PRODUCT_ID,
        basePlanId = "annual",
    );

    val productIdsInPreferenceOrder: List<String>
        get() = listOf(primaryProductId)
}

data class PremiumOfferCandidate(
    val productId: String,
    val basePlanId: String,
    val offerId: String?,
    val offerToken: String,
    val formattedPrice: String?,
)

object PremiumCatalog {
    const val PREFERRED_PRODUCT_ID = "just_notes_premium"
    private val legacyProductIds = setOf(
        "just_notes_premium_monthly",
        "just_notes_premium_annual",
    )

    val productIdsToQuery: List<String> = listOf(PREFERRED_PRODUCT_ID)

    fun isPremiumProduct(productId: String): Boolean {
        return productId == PREFERRED_PRODUCT_ID
    }

    fun isLegacyPremiumProduct(productId: String): Boolean {
        return productId in legacyProductIds
    }

    fun matchingPremiumProductId(productIds: Iterable<String>): String? {
        val purchasedProductIds = productIds.toSet()
        for (plan in PremiumPlan.entries) {
            for (productId in plan.productIdsInPreferenceOrder) {
                if (productId in purchasedProductIds) return productId
            }
        }
        return null
    }

    fun selectBasePlanOffer(plan: PremiumPlan, candidates: Iterable<PremiumOfferCandidate>): PremiumOfferCandidate? {
        val matchingBasePlans = candidates.filter { candidate ->
            candidate.productId == PREFERRED_PRODUCT_ID &&
                candidate.basePlanId == plan.basePlanId &&
                candidate.offerId == null
        }
        if (matchingBasePlans.size == 1) return matchingBasePlans[0]
        return null
    }

    fun selectDisplayPrice(plan: PremiumPlan, candidates: Iterable<PremiumOfferCandidate>): String? {
        val matchingBasePlans = candidates.filter { candidate ->
            candidate.productId == PREFERRED_PRODUCT_ID &&
                candidate.basePlanId == plan.basePlanId &&
                !candidate.formattedPrice.isNullOrBlank()
        }
        val basePlanPrice = matchingBasePlans.firstOrNull { candidate -> candidate.offerId == null }
        return (basePlanPrice ?: matchingBasePlans.firstOrNull())?.formattedPrice
    }
}
