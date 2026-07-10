package com.brianyeh.justnotes.backend.config

data class BackendConfig(
    val environment: String,
    val allowedPackageName: String,
    val allowedProductId: String,
    val allowedBasePlanIds: Set<String>,
    val allowedOffersByBasePlanId: Map<String, Set<String?>>,
    val googleWebClientId: String?,
    val issuerAllowlist: Set<String>,
    val firestoreProjectId: String?,
    val entitlementReverifyTtlMillis: Long,
    val entitlementMaxStaleMillis: Long,
) {
    fun validateForIdTokenVerification(): String? {
        val clientId = googleWebClientId?.trim()
        if (clientId.isNullOrBlank()) return "Google web client ID is not configured."
        if (!GOOGLE_WEB_CLIENT_ID_PATTERN.matches(clientId)) {
            return "Google web client ID format is invalid."
        }
        if (issuerAllowlist.isEmpty()) return "Google ID token issuer allowlist is not configured."
        return null
    }

    fun validateCatalog(
        packageName: String?,
        productId: String?,
        basePlanId: String?,
        offerId: String? = null,
    ): String? {
        if (packageName != allowedPackageName) return "Package name is not allowed."
        if (productId != allowedProductId) return "Product ID is not allowed."
        val planId = basePlanId ?: return "Base plan ID is required."
        if (planId !in allowedBasePlanIds) return "Base plan ID is not allowed."
        val allowedOffers = allowedOffersByBasePlanId[planId].orEmpty()
        if (offerId !in allowedOffers) return "Offer ID is not allowed."
        return null
    }

    companion object {
        const val DEFAULT_PACKAGE_NAME = "com.brianyeh.justnotes"
        const val DEFAULT_PRODUCT_ID = "just_notes_premium"
        private val GOOGLE_WEB_CLIENT_ID_PATTERN =
            Regex("^[A-Za-z0-9-]+\\.apps\\.googleusercontent\\.com$")

        fun fromEnvironment(environment: Map<String, String> = System.getenv()): BackendConfig {
            return BackendConfig(
                environment = environment["JUST_NOTES_ENVIRONMENT"].orEmpty().ifBlank { "local" },
                allowedPackageName = DEFAULT_PACKAGE_NAME,
                allowedProductId = DEFAULT_PRODUCT_ID,
                allowedBasePlanIds = setOf("monthly", "annual"),
                allowedOffersByBasePlanId = mapOf(
                    "monthly" to setOf(null, "trial10d"),
                    "annual" to setOf(null),
                ),
                googleWebClientId = environment["GOOGLE_WEB_CLIENT_ID"]?.trim()?.takeIf { it.isNotBlank() },
                issuerAllowlist = setOf("accounts.google.com", "https://accounts.google.com"),
                firestoreProjectId = environment["FIRESTORE_PROJECT_ID"]?.trim()?.takeIf { it.isNotBlank() },
                entitlementReverifyTtlMillis = 6L * 60L * 60L * 1_000L,
                entitlementMaxStaleMillis = 24L * 60L * 60L * 1_000L,
            )
        }
    }
}
