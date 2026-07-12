package com.brianyeh.justnotes.backend.security

import java.util.Locale

fun interface EmailHashDeriver {
    fun derive(normalizedEmail: String): String
}

class HmacSha256EmailHashDeriver(
    private val secretProvider: SecretValueProvider,
) : EmailHashDeriver {
    override fun derive(normalizedEmail: String): String {
        require(normalizedEmail == normalizeGoogleEmail(normalizedEmail)) {
            "Email must be normalized before hashing."
        }
        val secret = secretProvider.currentSecret()
            ?: error("Email hash pepper is not configured.")
        return hmacSha256UrlSafe(secret.value, normalizedEmail)
    }
}

fun normalizeGoogleEmail(email: String): String = email.trim().lowercase(Locale.ROOT)
