package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.config.BackendConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class BackendConfigTest {
    @Test
    fun configFailsClosedWithoutGoogleWebClientId() {
        val config = BackendConfig.fromEnvironment(emptyMap())

        assertNotNull(config.validateForIdTokenVerification())
    }

    @Test
    fun configFailsClosedForMalformedGoogleWebClientId() {
        val config = BackendConfig.fromEnvironment(
            mapOf("GOOGLE_WEB_CLIENT_ID" to "android-client-id"),
        )

        assertEquals(
            "Google web client ID format is invalid.",
            config.validateForIdTokenVerification(),
        )
    }

    @Test
    fun configAcceptsGoogleWebClientIdAndTrimsEnvironmentValues() {
        val config = BackendConfig.fromEnvironment(
            mapOf(
                "GOOGLE_WEB_CLIENT_ID" to "  test-web-client.apps.googleusercontent.com  ",
                "FIRESTORE_PROJECT_ID" to "  gen-lang-client-0599059254  ",
            ),
        )

        assertNull(config.validateForIdTokenVerification())
        assertEquals("test-web-client.apps.googleusercontent.com", config.googleWebClientId)
        assertEquals("gen-lang-client-0599059254", config.firestoreProjectId)
    }

    @Test
    fun configAcceptsOnlyKnownCatalogValues() {
        val config = BackendConfig.fromEnvironment(
            mapOf("GOOGLE_WEB_CLIENT_ID" to "test-web-client.apps.googleusercontent.com"),
        )

        assertNull(
            config.validateCatalog(
                packageName = "com.brianyeh.justnotes",
                productId = "just_notes_premium",
                basePlanId = "monthly",
                offerId = "trial10d",
            ),
        )
        assertNull(config.validateCatalog("com.brianyeh.justnotes", "just_notes_premium", "monthly", null))
        assertNull(config.validateCatalog("com.brianyeh.justnotes", "just_notes_premium", "annual", null))
        assertEquals("Package name is not allowed.", config.validateCatalog("com.attacker", "just_notes_premium", "monthly", null))
        assertEquals("Product ID is not allowed.", config.validateCatalog("com.brianyeh.justnotes", "other", "monthly", null))
        assertEquals("Base plan ID is not allowed.", config.validateCatalog("com.brianyeh.justnotes", "just_notes_premium", "weekly", null))
        assertEquals("Offer ID is not allowed.", config.validateCatalog("com.brianyeh.justnotes", "just_notes_premium", "annual", "trial10d"))
        assertEquals("Offer ID is not allowed.", config.validateCatalog("com.brianyeh.justnotes", "just_notes_premium", "monthly", "unknown"))
    }

    @Test
    fun productionAdapterConfigFailsClosedWhenCloudResourcesAreMissing() {
        val errors = BackendConfig.fromEnvironment(emptyMap()).validateForProductionAdapters()

        assertTrue(errors.any { it.contains("Firestore project ID") })
        assertTrue(errors.any { it.contains("Google web client ID") })
        assertTrue(errors.any { it.contains("Token hash secret") })
        assertTrue(errors.any { it.contains("KMS token encryption key") })
    }

    @Test
    fun productionAdapterConfigAcceptsOnlyResourceNamesNotSecretValues() {
        val config = BackendConfig.fromEnvironment(productionEnvironment())

        assertEquals(emptyList(), config.validateForProductionAdapters())
        assertEquals("(default)", config.firestoreDatabaseId)
        assertEquals(
            "projects/project-id/secrets/token-hash/versions/1",
            config.tokenHashSecretResource,
        )
    }

    @Test
    fun absentReviewerGrantResourceKeepsFeatureDisabledWithoutBreakingProductionAdapters() {
        val config = BackendConfig.fromEnvironment(productionEnvironment())

        assertNull(config.reviewerGrantSecretResource)
        assertEquals(emptyList(), config.validateForProductionAdapters())
    }

    @Test
    fun malformedReviewerGrantResourceFailsProductionValidation() {
        val malformed = BackendConfig.fromEnvironment(
            productionEnvironment() + ("REVIEWER_GRANT_SECRET_RESOURCE" to "raw-secret-value"),
        )
        val valid = BackendConfig.fromEnvironment(
            productionEnvironment() +
                ("REVIEWER_GRANT_SECRET_RESOURCE" to
                    "projects/project-id/secrets/reviewer-grants/versions/1"),
        )

        assertTrue(
            malformed.validateForProductionAdapters().any { it.contains("Reviewer grant secret") },
        )
        assertEquals(emptyList(), valid.validateForProductionAdapters())
        assertEquals(
            "projects/project-id/secrets/reviewer-grants/versions/1",
            valid.reviewerGrantSecretResource,
        )
    }

    @Test
    fun malformedSecretAndKmsResourcesFailClosed() {
        val config = BackendConfig.fromEnvironment(
            productionEnvironment() + mapOf(
                "TOKEN_HASH_SECRET_RESOURCE" to "raw-secret-value",
                "KMS_TOKEN_ENCRYPTION_KEY_RESOURCE" to "kms-key",
            ),
        )

        val errors = config.validateForProductionAdapters()

        assertTrue(errors.any { it.contains("Token hash secret") })
        assertTrue(errors.any { it.contains("KMS token encryption key") })
    }

    @Test
    fun productionAdapterConfigRejectsMovingLatestSecretAliases() {
        val config = BackendConfig.fromEnvironment(
            productionEnvironment() + mapOf(
                "TOKEN_HASH_SECRET_RESOURCE" to
                    "projects/project-id/secrets/token-hash/versions/latest",
            ),
        )

        assertTrue(
            config.validateForProductionAdapters().any { it.contains("Token hash secret") },
        )
    }

    @Test
    fun productionAdapterConfigRejectsMalformedFirestoreIdentifiers() {
        val uppercaseProject = BackendConfig.fromEnvironment(
            productionEnvironment() + ("FIRESTORE_PROJECT_ID" to "Invalid_Project"),
        )
        val shortDatabase = BackendConfig.fromEnvironment(
            productionEnvironment() + ("FIRESTORE_DATABASE_ID" to "abc"),
        )
        val uuidDatabase = BackendConfig.fromEnvironment(
            productionEnvironment() +
                ("FIRESTORE_DATABASE_ID" to "f47ac10b-58cc-0372-8567-0e02b2c3d479"),
        )

        assertTrue(
            uppercaseProject.validateForProductionAdapters().any { it.contains("Firestore project ID") },
        )
        assertTrue(
            shortDatabase.validateForProductionAdapters().any { it.contains("Firestore database ID") },
        )
        assertTrue(
            uuidDatabase.validateForProductionAdapters().any { it.contains("Firestore database ID") },
        )
    }

    @Test
    fun rtdnConfigIsDisabledByDefaultAndValidatesExactPrivatePushSettings() {
        val disabled = BackendConfig.fromEnvironment(emptyMap())
        val enabled = BackendConfig.fromEnvironment(
            mapOf(
                "RTDN_ENABLED" to "true",
                "RTDN_EXPECTED_SUBSCRIPTION" to
                    "projects/gen-lang-client-0599059254/subscriptions/just-notes-rtdn-push-dev",
                "RTDN_EVENT_TTL_DAYS" to "30",
                "RTDN_PROCESSING_LEASE_SECONDS" to "60",
            ),
        )

        assertFalse(disabled.rtdnEnabled)
        assertEquals(emptyList(), disabled.validateForRtdn())
        assertTrue(enabled.rtdnEnabled)
        assertEquals(emptyList(), enabled.validateForRtdn())
        assertEquals(30, enabled.rtdnEventTtlDays)
        assertEquals(60, enabled.rtdnProcessingLeaseSeconds)
    }

    @Test
    fun enabledRtdnFailsClosedForMissingOrMalformedValues() {
        val missing = BackendConfig.fromEnvironment(mapOf("RTDN_ENABLED" to "true"))
        val malformed = BackendConfig.fromEnvironment(
            mapOf(
                "RTDN_ENABLED" to "true",
                "RTDN_EXPECTED_SUBSCRIPTION" to "wrong",
            ),
        )

        assertTrue(missing.validateForRtdn().any { it.contains("subscription") })
        assertTrue(malformed.validateForRtdn().any { it.contains("subscription") })
        assertFailsWith<IllegalArgumentException> {
            BackendConfig.fromEnvironment(mapOf("RTDN_ENABLED" to "yes"))
        }
        assertFailsWith<IllegalArgumentException> {
            BackendConfig.fromEnvironment(mapOf("RTDN_EVENT_TTL_DAYS" to "0"))
        }
    }

    private fun productionEnvironment(): Map<String, String> {
        return mapOf(
            "GOOGLE_WEB_CLIENT_ID" to "test-web-client.apps.googleusercontent.com",
            "FIRESTORE_PROJECT_ID" to "project-id",
            "FIRESTORE_DATABASE_ID" to "(default)",
            "TOKEN_HASH_SECRET_RESOURCE" to "projects/project-id/secrets/token-hash/versions/1",
            "OBFUSCATED_ACCOUNT_SECRET_RESOURCE" to "projects/project-id/secrets/account/versions/1",
            "EMAIL_HASH_SECRET_RESOURCE" to "projects/project-id/secrets/email/versions/1",
            "KMS_TOKEN_ENCRYPTION_KEY_RESOURCE" to
                "projects/project-id/locations/asia-east1/keyRings/ring/cryptoKeys/key",
        )
    }
}
