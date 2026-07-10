package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.security.CachingSecretManagerSecretValueProvider
import com.brianyeh.justnotes.backend.security.SecretManagerAccessResult
import com.brianyeh.justnotes.backend.security.SecretManagerGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SecretManagerAdapterTest {
    @Test
    fun secretIsLoadedWithVersionAndCached() {
        var now = 1_000L
        var calls = 0
        val provider = CachingSecretManagerSecretValueProvider(
            resourceName = RESOURCE,
            gateway = SecretManagerGateway {
                calls += 1
                SecretManagerAccessResult("pepper-value", calls.toString())
            },
            cacheTtlMillis = 100L,
            clock = { now },
        )

        assertEquals("pepper-value", provider.currentSecret().value)
        assertEquals("1", provider.currentSecret().version)
        assertEquals(1, calls)

        now += 101L
        assertEquals("2", provider.currentSecret().version)
        assertEquals(2, calls)
    }

    @Test
    fun secretValueIsRedactedFromStringRepresentations() {
        val provider = CachingSecretManagerSecretValueProvider(
            resourceName = RESOURCE,
            gateway = SecretManagerGateway { SecretManagerAccessResult("do-not-print", "7") },
        )

        val secret = provider.currentSecret()

        assertFalse(secret.toString().contains("do-not-print"))
        assertFalse(SecretManagerAccessResult("do-not-print", "7").toString().contains("do-not-print"))
    }

    @Test
    fun malformedResourceAndEmptyPayloadFailClosed() {
        assertFailsWith<IllegalArgumentException> {
            CachingSecretManagerSecretValueProvider(
                resourceName = "secret-value",
                gateway = SecretManagerGateway { error("must not run") },
            )
        }
        val provider = CachingSecretManagerSecretValueProvider(
            resourceName = RESOURCE,
            gateway = SecretManagerGateway { SecretManagerAccessResult("", "1") },
        )

        assertFailsWith<IllegalStateException> { provider.currentSecret() }
    }

    private companion object {
        const val RESOURCE = "projects/project-id/secrets/pepper/versions/latest"
    }
}
