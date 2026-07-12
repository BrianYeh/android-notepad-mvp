package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.security.HmacSha256EmailHashDeriver
import com.brianyeh.justnotes.backend.security.SecretValueProvider
import com.brianyeh.justnotes.backend.security.VersionedSecret
import com.brianyeh.justnotes.backend.security.normalizeGoogleEmail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmailHashDeriverTest {
    @Test
    fun normalizationIsLocaleIndependentAndHmacIsUrlSafe() {
        val deriver = HmacSha256EmailHashDeriver(
            secretProvider = object : SecretValueProvider {
                override fun currentSecret() = VersionedSecret("pepper", "1")
            },
        )

        val hash = deriver.derive(normalizeGoogleEmail("  Reviewer@Example.COM  "))

        assertEquals(43, hash.length)
        assertTrue(hash.matches(Regex("^[A-Za-z0-9_-]{43}$")))
        assertEquals(hash, deriver.derive("reviewer@example.com"))
    }
}
