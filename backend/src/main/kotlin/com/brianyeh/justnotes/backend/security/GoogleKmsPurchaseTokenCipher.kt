package com.brianyeh.justnotes.backend.security

import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.protobuf.ByteString
import java.util.Base64

data class KmsEncryptResult(
    val ciphertext: ByteArray,
    val keyVersion: String,
)

interface KmsGateway {
    fun encrypt(keyResourceName: String, plaintext: ByteArray): KmsEncryptResult
    fun decrypt(keyResourceName: String, ciphertext: ByteArray): ByteArray
}

class GoogleCloudKmsGateway(
    private val client: KeyManagementServiceClient,
) : KmsGateway {
    override fun encrypt(keyResourceName: String, plaintext: ByteArray): KmsEncryptResult {
        val response = client.encrypt(keyResourceName, ByteString.copyFrom(plaintext))
        return KmsEncryptResult(
            ciphertext = response.ciphertext.toByteArray(),
            keyVersion = response.name,
        )
    }

    override fun decrypt(keyResourceName: String, ciphertext: ByteArray): ByteArray {
        return client.decrypt(keyResourceName, ByteString.copyFrom(ciphertext)).plaintext.toByteArray()
    }
}

class GoogleKmsPurchaseTokenCipher(
    private val keyResourceName: String,
    private val gateway: KmsGateway,
) : PurchaseTokenCipher {
    init {
        require(KMS_KEY_RESOURCE_PATTERN.matches(keyResourceName)) {
            "KMS CryptoKey resource name is malformed."
        }
    }

    override fun encrypt(purchaseToken: String, now: Long): TokenCiphertext {
        require(purchaseToken.isNotBlank()) { "Purchase token must not be blank." }
        val encrypted = gateway.encrypt(keyResourceName, purchaseToken.toByteArray(Charsets.UTF_8))
        check(encrypted.ciphertext.isNotEmpty()) { "Cloud KMS returned empty ciphertext." }
        check(encrypted.keyVersion.startsWith("$keyResourceName/cryptoKeyVersions/")) {
            "Cloud KMS returned a key version outside the configured CryptoKey."
        }
        return TokenCiphertext(
            tokenCiphertext = Base64.getEncoder().encodeToString(encrypted.ciphertext),
            keyVersion = encrypted.keyVersion,
            encryptedAt = now,
            encryptionAlgorithm = ENCRYPTION_ALGORITHM,
        )
    }

    override fun decrypt(ciphertext: TokenCiphertext): String {
        require(ciphertext.encryptionAlgorithm == ENCRYPTION_ALGORITHM) {
            "Unsupported purchase token encryption algorithm."
        }
        require(ciphertext.keyVersion.startsWith("$keyResourceName/cryptoKeyVersions/")) {
            "Ciphertext key version is outside the configured CryptoKey."
        }
        val encryptedBytes = runCatching { Base64.getDecoder().decode(ciphertext.tokenCiphertext) }
            .getOrElse { throw IllegalArgumentException("Purchase token ciphertext is malformed.", it) }
        check(encryptedBytes.isNotEmpty()) { "Purchase token ciphertext is empty." }
        val plaintext = gateway.decrypt(keyResourceName, encryptedBytes)
        return plaintext.toString(Charsets.UTF_8).also {
            check(it.isNotBlank()) { "Cloud KMS returned an empty purchase token." }
        }
    }

    companion object {
        const val ENCRYPTION_ALGORITHM = "GOOGLE_SYMMETRIC_ENCRYPTION"
        private val KMS_KEY_RESOURCE_PATTERN =
            Regex("^projects/[^/]+/locations/[^/]+/keyRings/[^/]+/cryptoKeys/[^/]+$")
    }
}
