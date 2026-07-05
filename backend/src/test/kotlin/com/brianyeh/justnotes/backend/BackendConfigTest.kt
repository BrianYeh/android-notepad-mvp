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
    fun configAcceptsOnlyKnownCatalogValues() {
        val config = BackendConfig.fromEnvironment(mapOf("GOOGLE_WEB_CLIENT_ID" to "web-client"))

        assertNull(
            config.validateCatalog(
                packageName = "com.brianyeh.justnotes",
                productId = "just_notes_premium",
                basePlanId = "monthly",
            ),
        )
        assertEquals("Package name is not allowed.", config.validateCatalog("com.attacker", "just_notes_premium", "monthly"))
        assertEquals("Product ID is not allowed.", config.validateCatalog("com.brianyeh.justnotes", "other", "monthly"))
        assertEquals("Base plan ID is not allowed.", config.validateCatalog("com.brianyeh.justnotes", "just_notes_premium", "weekly"))
    }
}
