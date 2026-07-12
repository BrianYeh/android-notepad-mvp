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
    val firestoreDatabaseId: String,
    val tokenHashSecretResource: String?,
    val obfuscatedAccountSecretResource: String?,
    val emailHashSecretResource: String?,
    val reviewerGrantSecretResource: String? = null,
    val kmsTokenEncryptionKeyResource: String?,
    val entitlementReverifyTtlMillis: Long,
    val entitlementMaxStaleMillis: Long,
    val rtdnEnabled: Boolean = false,
    val rtdnExpectedSubscription: String? = null,
    val rtdnEventTtlDays: Int = 30,
    val rtdnProcessingLeaseSeconds: Long = 60L,
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

    fun validateForProductionAdapters(): List<String> {
        return buildList {
            validateForIdTokenVerification()?.let(::add)
            if (!isValidGoogleCloudProjectId(firestoreProjectId)) {
                add("Firestore project ID is not configured or malformed.")
            }
            if (!isValidFirestoreDatabaseId(firestoreDatabaseId)) {
                add("Firestore database ID is not configured or malformed.")
            }
            validateSecretResource("Token hash secret", tokenHashSecretResource)?.let(::add)
            validateSecretResource("Obfuscated account secret", obfuscatedAccountSecretResource)?.let(::add)
            validateSecretResource("Email hash secret", emailHashSecretResource)?.let(::add)
            reviewerGrantSecretResource?.let { resource ->
                validateSecretResource("Reviewer grant secret", resource)?.let(::add)
            }
            if (kmsTokenEncryptionKeyResource?.matches(KMS_KEY_RESOURCE_PATTERN) != true) {
                add("KMS token encryption key resource is not configured or malformed.")
            }
        }
    }

    fun validateForRtdn(): List<String> {
        if (!rtdnEnabled) return emptyList()
        return buildList {
            if (rtdnExpectedSubscription?.matches(PUBSUB_SUBSCRIPTION_RESOURCE_PATTERN) != true) {
                add("RTDN expected Pub/Sub subscription resource is not configured or malformed.")
            }
            if (rtdnEventTtlDays !in 1..365) {
                add("RTDN event TTL days must be between 1 and 365.")
            }
            if (rtdnProcessingLeaseSeconds !in 10L..600L) {
                add("RTDN processing lease seconds must be between 10 and 600.")
            }
        }
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
        private val SECRET_VERSION_RESOURCE_PATTERN =
            Regex("^projects/[^/]+/secrets/[A-Za-z0-9_-]+/versions/[1-9][0-9]*$")
        private val KMS_KEY_RESOURCE_PATTERN =
            Regex("^projects/[^/]+/locations/[^/]+/keyRings/[^/]+/cryptoKeys/[^/]+$")
        private val GOOGLE_CLOUD_PROJECT_ID_PATTERN =
            Regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$")
        private val FIRESTORE_DATABASE_ID_PATTERN =
            Regex("^[a-z][a-z0-9-]{2,61}[a-z0-9]$")
        private val UUID_LIKE_PATTERN =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        private val RESTRICTED_PROJECT_ID_PARTS = setOf("google", "ssl", "undefined", "null")
        private val PUBSUB_SUBSCRIPTION_RESOURCE_PATTERN =
            Regex("^projects/[a-z][a-z0-9-]{4,28}[a-z0-9]/subscriptions/[A-Za-z][A-Za-z0-9._~+%-]{2,254}$")

        fun fromEnvironment(environment: Map<String, String> = System.getenv()): BackendConfig {
            return BackendConfig(
                environment = environment["JUST_NOTES_ENVIRONMENT"]?.trim().orEmpty().ifBlank { "local" },
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
                firestoreDatabaseId = environment["FIRESTORE_DATABASE_ID"]?.trim().orEmpty().ifBlank { "(default)" },
                tokenHashSecretResource = environment["TOKEN_HASH_SECRET_RESOURCE"]?.trim()?.takeIf { it.isNotBlank() },
                obfuscatedAccountSecretResource = environment["OBFUSCATED_ACCOUNT_SECRET_RESOURCE"]?.trim()?.takeIf { it.isNotBlank() },
                emailHashSecretResource = environment["EMAIL_HASH_SECRET_RESOURCE"]?.trim()?.takeIf { it.isNotBlank() },
                reviewerGrantSecretResource = environment["REVIEWER_GRANT_SECRET_RESOURCE"]?.trim()?.takeIf { it.isNotBlank() },
                kmsTokenEncryptionKeyResource = environment["KMS_TOKEN_ENCRYPTION_KEY_RESOURCE"]?.trim()?.takeIf { it.isNotBlank() },
                entitlementReverifyTtlMillis = 6L * 60L * 60L * 1_000L,
                entitlementMaxStaleMillis = 24L * 60L * 60L * 1_000L,
                rtdnEnabled = parseBoolean(environment, "RTDN_ENABLED", default = false),
                rtdnExpectedSubscription = environment["RTDN_EXPECTED_SUBSCRIPTION"]
                    ?.trim()
                    ?.takeIf { it.isNotBlank() },
                rtdnEventTtlDays = parseIntInRange(
                    environment,
                    "RTDN_EVENT_TTL_DAYS",
                    default = 30,
                    range = 1..365,
                ),
                rtdnProcessingLeaseSeconds = parseLongInRange(
                    environment,
                    "RTDN_PROCESSING_LEASE_SECONDS",
                    default = 60L,
                    range = 10L..600L,
                ),
            )
        }

        private fun parseBoolean(
            environment: Map<String, String>,
            name: String,
            default: Boolean,
        ): Boolean {
            val value = environment[name]?.trim()?.lowercase() ?: return default
            return when (value) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("$name must be true or false.")
            }
        }

        private fun parseIntInRange(
            environment: Map<String, String>,
            name: String,
            default: Int,
            range: IntRange,
        ): Int {
            val raw = environment[name]?.trim() ?: return default
            val value = raw.toIntOrNull()
            require(value != null && value in range) { "$name is outside its allowed range." }
            return value
        }

        private fun parseLongInRange(
            environment: Map<String, String>,
            name: String,
            default: Long,
            range: LongRange,
        ): Long {
            val raw = environment[name]?.trim() ?: return default
            val value = raw.toLongOrNull()
            require(value != null && value in range) { "$name is outside its allowed range." }
            return value
        }

        private fun validateSecretResource(label: String, resource: String?): String? {
            return if (resource?.matches(SECRET_VERSION_RESOURCE_PATTERN) == true) {
                null
            } else {
                "$label resource is not configured or malformed."
            }
        }

        private fun isValidGoogleCloudProjectId(value: String?): Boolean {
            val projectId = value ?: return false
            return projectId.matches(GOOGLE_CLOUD_PROJECT_ID_PATTERN) &&
                RESTRICTED_PROJECT_ID_PARTS.none(projectId::contains)
        }

        private fun isValidFirestoreDatabaseId(value: String): Boolean {
            return value == "(default)" ||
                (value.matches(FIRESTORE_DATABASE_ID_PATTERN) && !value.matches(UUID_LIKE_PATTERN))
        }
    }
}
