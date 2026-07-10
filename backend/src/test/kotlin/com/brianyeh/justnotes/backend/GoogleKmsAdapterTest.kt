package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.security.GoogleKmsPurchaseTokenCipher
import com.brianyeh.justnotes.backend.security.KmsEncryptResult
import com.brianyeh.justnotes.backend.security.KmsGateway
import com.brianyeh.justnotes.backend.security.TokenCiphertext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class GoogleKmsAdapterTest {
    @Test
    fun purchaseTokenRoundTripsWithoutPersistingRawToken() {
        val cipher = GoogleKmsPurchaseTokenCipher(KEY_RESOURCE, ReversingKmsGateway())

        val encrypted = cipher.encrypt("purchase-token", NOW)

        assertNotEquals("purchase-token", encrypted.tokenCiphertext)
        assertEquals("$KEY_RESOURCE/cryptoKeyVersions/3", encrypted.keyVersion)
        assertEquals(NOW, encrypted.encryptedAt)
        assertEquals(GoogleKmsPurchaseTokenCipher.ENCRYPTION_ALGORITHM, encrypted.encryptionAlgorithm)
        assertEquals("purchase-token", cipher.decrypt(encrypted))
    }

    @Test
    fun wrongKeyVersionAndMalformedCiphertextFailClosed() {
        val cipher = GoogleKmsPurchaseTokenCipher(KEY_RESOURCE, ReversingKmsGateway())

        assertFailsWith<IllegalArgumentException> {
            cipher.decrypt(
                TokenCiphertext(
                    tokenCiphertext = "AA==",
                    keyVersion = "projects/other/locations/asia-east1/keyRings/ring/cryptoKeys/key/cryptoKeyVersions/1",
                    encryptedAt = NOW,
                    encryptionAlgorithm = GoogleKmsPurchaseTokenCipher.ENCRYPTION_ALGORITHM,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            cipher.decrypt(
                TokenCiphertext(
                    tokenCiphertext = "not-base64!",
                    keyVersion = "$KEY_RESOURCE/cryptoKeyVersions/3",
                    encryptedAt = NOW,
                    encryptionAlgorithm = GoogleKmsPurchaseTokenCipher.ENCRYPTION_ALGORITHM,
                ),
            )
        }
    }

    @Test
    fun kmsReturningAnotherKeyVersionFailsClosed() {
        val cipher = GoogleKmsPurchaseTokenCipher(
            KEY_RESOURCE,
            object : KmsGateway {
                override fun encrypt(keyResourceName: String, plaintext: ByteArray): KmsEncryptResult {
                    return KmsEncryptResult(byteArrayOf(1), "projects/other/cryptoKeyVersions/1")
                }

                override fun decrypt(keyResourceName: String, ciphertext: ByteArray): ByteArray = byteArrayOf()
            },
        )

        assertFailsWith<IllegalStateException> { cipher.encrypt("purchase-token", NOW) }
    }

    private class ReversingKmsGateway : KmsGateway {
        override fun encrypt(keyResourceName: String, plaintext: ByteArray): KmsEncryptResult {
            return KmsEncryptResult(plaintext.reversedArray(), "$keyResourceName/cryptoKeyVersions/3")
        }

        override fun decrypt(keyResourceName: String, ciphertext: ByteArray): ByteArray {
            return ciphertext.reversedArray()
        }
    }

    private companion object {
        const val KEY_RESOURCE =
            "projects/project-id/locations/asia-east1/keyRings/ring/cryptoKeys/token-key"
        const val NOW = 1_762_000_000_000L
    }
}
