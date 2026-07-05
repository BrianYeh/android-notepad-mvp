package com.brianyeh.justnotes.backend.auth

import com.brianyeh.justnotes.backend.config.BackendConfig

data class VerifiedGoogleIdentity(
    val googleSub: String,
    val emailHash: String? = null,
)

sealed class GoogleIdTokenVerificationResult {
    data class Success(val identity: VerifiedGoogleIdentity) : GoogleIdTokenVerificationResult()
    data class Failure(val reason: String) : GoogleIdTokenVerificationResult()
}

interface GoogleIdTokenVerifier {
    suspend fun verify(idToken: String): GoogleIdTokenVerificationResult
}

class FailClosedGoogleIdTokenVerifier(
    private val config: BackendConfig,
) : GoogleIdTokenVerifier {
    override suspend fun verify(idToken: String): GoogleIdTokenVerificationResult {
        val validation = config.validateForIdTokenVerification()
        if (validation != null) return GoogleIdTokenVerificationResult.Failure(validation)
        return GoogleIdTokenVerificationResult.Failure("Google ID token verifier is not implemented.")
    }
}
