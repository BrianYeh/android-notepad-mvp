package com.example.notepad.billing

enum class PremiumPlan(
    val primaryProductId: String,
    val basePlanId: String,
    private val fallbackProductIds: List<String> = emptyList(),
) {
    Monthly(
        primaryProductId = PremiumCatalog.PREFERRED_PRODUCT_ID,
        basePlanId = "monthly",
        fallbackProductIds = listOf("just_notes_premium_monthly"),
    ),
    Annual(
        primaryProductId = PremiumCatalog.PREFERRED_PRODUCT_ID,
        basePlanId = "annual",
        fallbackProductIds = listOf("just_notes_premium_annual"),
    );

    val productIdsInPreferenceOrder: List<String>
        get() = listOf(primaryProductId) + fallbackProductIds
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

    val productIdsToQuery: List<String> =
        PremiumPlan.entries
            .flatMap { it.productIdsInPreferenceOrder }
            .distinct()

    fun isPremiumProduct(productId: String): Boolean {
        return PremiumPlan.entries.any { plan -> productId in plan.productIdsInPreferenceOrder }
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
        for (productId in plan.productIdsInPreferenceOrder) {
            val matchingBasePlans = candidates.filter { candidate ->
                candidate.productId == productId &&
                    candidate.basePlanId == plan.basePlanId &&
                    candidate.offerId == null
            }
            if (matchingBasePlans.size == 1) return matchingBasePlans[0]
            if (matchingBasePlans.size > 1) return null
        }
        return null
    }
}
