package com.brianyeh.justnotes.backend.security

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class TokenHash(
    val value: String,
    val hashVersion: String,
    val pepperVersion: String,
)

interface SecretValueProvider {
    fun currentSecret(): VersionedSecret?
}

data class VersionedSecret(
    val value: String,
    val version: String,
)

interface PurchaseTokenHasher {
    fun hashPurchaseToken(purchaseToken: String): TokenHash
}

class HmacSha256PurchaseTokenHasher(
    private val secretProvider: SecretValueProvider,
    private val hashVersion: String = "hmac-sha256-v1",
) : PurchaseTokenHasher {
    override fun hashPurchaseToken(purchaseToken: String): TokenHash {
        val secret = secretProvider.currentSecret()
            ?: error("Purchase token hash pepper is not configured.")
        return TokenHash(
            value = hmacSha256UrlSafe(secret.value, purchaseToken),
            hashVersion = hashVersion,
            pepperVersion = secret.version,
        )
    }
}

interface ObfuscatedAccountIdDeriver {
    fun derive(googleSub: String): String
}

class HmacSha256ObfuscatedAccountIdDeriver(
    private val secretProvider: SecretValueProvider,
) : ObfuscatedAccountIdDeriver {
    override fun derive(googleSub: String): String {
        val secret = secretProvider.currentSecret()
            ?: error("Obfuscated account ID pepper is not configured.")
        return hmacSha256UrlSafe(secret.value, googleSub)
    }
}

data class TokenCiphertext(
    val tokenCiphertext: String,
    val keyVersion: String,
    val encryptedAt: Long,
    val encryptionAlgorithm: String,
)

interface PurchaseTokenEncryptor {
    fun encrypt(purchaseToken: String, now: Long): TokenCiphertext
}

class FakePurchaseTokenEncryptor(
    private val keyVersion: String = "fake-local-key",
) : PurchaseTokenEncryptor {
    override fun encrypt(purchaseToken: String, now: Long): TokenCiphertext {
        return TokenCiphertext(
            tokenCiphertext = "fake-ciphertext:${purchaseToken.length}",
            keyVersion = keyVersion,
            encryptedAt = now,
            encryptionAlgorithm = "fake-local",
        )
    }
}

class StaticSecretValueProvider(
    private val secret: VersionedSecret?,
) : SecretValueProvider {
    override fun currentSecret(): VersionedSecret? = secret
}

fun hmacSha256UrlSafe(secret: String, value: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(mac.doFinal(value.toByteArray(Charsets.UTF_8)))
}
