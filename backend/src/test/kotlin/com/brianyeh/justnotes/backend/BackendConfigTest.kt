package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.config.BackendConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
}
