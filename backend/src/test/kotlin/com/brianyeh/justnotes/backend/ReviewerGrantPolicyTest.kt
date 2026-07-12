package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.auth.VerifiedGoogleIdentity
import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendEntitlementSource
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.reviewer.SecretBackedReviewerGrantPolicy
import com.brianyeh.justnotes.backend.security.SecretValueProvider
import com.brianyeh.justnotes.backend.security.VersionedSecret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ReviewerGrantPolicyTest {
    @Test
    fun matchingUnexpiredHashReturnsReviewerGrant() {
        val policy = policy(config(grant(HASH, EXPIRES_AT)))

        val entitlement = policy.entitlementFor(identity(HASH), NOW)

        assertEquals(BackendEntitlementSource.ReviewerGrant, entitlement?.source)
        assertEquals(BackendSubscriptionStatus.Active, entitlement?.status)
        assertEquals(BackendAcknowledgementState.NotRequired, entitlement?.acknowledgementState)
        assertEquals(EXPIRES_AT, entitlement?.expiryTime)
        assertEquals(NOW, entitlement?.lastVerifiedAt)
        assertEquals(null, entitlement?.purchaseTokenHash)
        assertEquals(null, entitlement?.productId)
    }

    @Test
    fun missingIdentityHashFailsClosed() {
        val policy = policy(config(grant(HASH, EXPIRES_AT)))

        assertNull(policy.entitlementFor(VerifiedGoogleIdentity("google-sub"), NOW))
    }

    @Test
    fun expiredGrantFailsClosed() {
        val policy = policy(config(grant(HASH, NOW)))

        assertNull(policy.entitlementFor(identity(HASH), NOW))
    }

    @Test
    fun malformedJsonFailsClosed() {
        assertNull(policy("not-json").entitlementFor(identity(HASH), NOW))
        assertNull(policy("{}").entitlementFor(identity(HASH), NOW))
        assertNull(policy("[]").entitlementFor(identity(HASH), NOW))
    }

    @Test
    fun unknownRootOrGrantFieldsFailClosed() {
        val unknownRoot =
            """{"schemaVersion":1,"grants":[${grant(HASH, EXPIRES_AT)}],"extra":true}"""
        val unknownGrant =
            """{"schemaVersion":1,"grants":[{"emailHash":"$HASH","expiresAt":$EXPIRES_AT,"extra":true}]}"""

        assertNull(policy(unknownRoot).entitlementFor(identity(HASH), NOW))
        assertNull(policy(unknownGrant).entitlementFor(identity(HASH), NOW))
    }

    @Test
    fun duplicateHashesFailClosed() {
        val duplicate = config(
            grant(HASH, EXPIRES_AT),
            grant(HASH, EXPIRES_AT + 1L),
        )

        assertNull(policy(duplicate).entitlementFor(identity(HASH), NOW))
    }

    @Test
    fun invalidHashLengthFailsClosed() {
        val invalid = config(grant("short", EXPIRES_AT))

        assertNull(policy(invalid).entitlementFor(identity(HASH), NOW))
    }

    @Test
    fun secretProviderFailureFailsClosed() {
        val policy = SecretBackedReviewerGrantPolicy(
            secretProvider = object : SecretValueProvider {
                override fun currentSecret(): VersionedSecret = error("secret unavailable")
            },
        )

        assertNull(policy.entitlementFor(identity(HASH), NOW))
    }

    @Test
    fun grantToStringDoesNotExposeHash() {
        val entitlement = policy(config(grant(HASH, EXPIRES_AT)))
            .entitlementFor(identity(HASH), NOW)

        assertFalse(entitlement.toString().contains(HASH))
    }

    private fun policy(value: String): SecretBackedReviewerGrantPolicy {
        return SecretBackedReviewerGrantPolicy(
            secretProvider = object : SecretValueProvider {
                override fun currentSecret() = VersionedSecret(value, "1")
            },
        )
    }

    private fun identity(emailHash: String): VerifiedGoogleIdentity {
        return VerifiedGoogleIdentity(
            googleSub = "google-sub",
            emailHash = emailHash,
        )
    }

    private fun config(vararg grants: String): String {
        return """{"schemaVersion":1,"grants":[${grants.joinToString(",")}]}"""
    }

    private fun grant(emailHash: String, expiresAt: Long): String {
        return """{"emailHash":"$emailHash","expiresAt":$expiresAt}"""
    }

    private companion object {
        const val NOW = 1_762_000_000_000L
        const val EXPIRES_AT = NOW + 60_000L
        const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
