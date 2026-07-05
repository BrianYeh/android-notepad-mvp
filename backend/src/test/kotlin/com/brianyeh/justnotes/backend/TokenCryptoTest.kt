package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.entitlement.PurchaseOwnershipErrorCode
import com.brianyeh.justnotes.backend.entitlement.PurchaseOwnershipResult
import com.brianyeh.justnotes.backend.entitlement.PurchaseOwnershipValidator
import com.brianyeh.justnotes.backend.play.PlayExternalAccountIdentifiers
import com.brianyeh.justnotes.backend.security.HmacSha256ObfuscatedAccountIdDeriver
import com.brianyeh.justnotes.backend.security.HmacSha256PurchaseTokenHasher
import com.brianyeh.justnotes.backend.security.StaticSecretValueProvider
import com.brianyeh.justnotes.backend.security.VersionedSecret
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TokenCryptoTest {
    @Test
    fun hmacTokenHashIsDeterministicWithinEnvironment() {
        val hasher = hasher("pepper-a")

        assertEquals(
            hasher.hashPurchaseToken("purchase-token").value,
            hasher.hashPurchaseToken("purchase-token").value,
        )
    }

    @Test
    fun hmacTokenHashIsNotRawOrPlainShaOnly() {
        val token = "purchase-token"
        val hash = hasher("pepper-a").hashPurchaseToken(token)
        val plainSha = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

        assertNotEquals(token, hash.value)
        assertNotEquals(plainSha, hash.value)
    }

    @Test
    fun differentPepperGivesDifferentHash() {
        assertNotEquals(
            hasher("pepper-a").hashPurchaseToken("purchase-token").value,
            hasher("pepper-b").hashPurchaseToken("purchase-token").value,
        )
    }

    @Test
    fun matchingObfuscatedAccountIdIsVerified() {
        val deriver = deriver()
        val expected = deriver.derive("google-sub")
        val result = PurchaseOwnershipValidator(deriver).validate(
            googleSub = "google-sub",
            externalAccountIdentifiers = PlayExternalAccountIdentifiers(expected),
        )

        assertEquals(PurchaseOwnershipResult.Verified, result)
    }

    @Test
    fun missingObfuscatedAccountIdDoesNotVerifyOwnership() {
        val result = PurchaseOwnershipValidator(deriver()).validate(
            googleSub = "google-sub",
            externalAccountIdentifiers = PlayExternalAccountIdentifiers(null),
        )

        assertTrue(result is PurchaseOwnershipResult.Failure)
        assertEquals(PurchaseOwnershipErrorCode.MISSING_OBFUSCATED_ACCOUNT_ID, result.code)
    }

    @Test
    fun mismatchedObfuscatedAccountIdDoesNotVerifyOwnership() {
        val result = PurchaseOwnershipValidator(deriver()).validate(
            googleSub = "google-sub",
            externalAccountIdentifiers = PlayExternalAccountIdentifiers("other"),
        )

        assertTrue(result is PurchaseOwnershipResult.Failure)
        assertEquals(PurchaseOwnershipErrorCode.OWNER_MISMATCH, result.code)
    }

    private fun hasher(pepper: String): HmacSha256PurchaseTokenHasher {
        return HmacSha256PurchaseTokenHasher(StaticSecretValueProvider(VersionedSecret(pepper, "v1")))
    }

    private fun deriver(): HmacSha256ObfuscatedAccountIdDeriver {
        return HmacSha256ObfuscatedAccountIdDeriver(StaticSecretValueProvider(VersionedSecret("obfuscation-pepper", "v1")))
    }
}
