package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.auth.GoogleIdTokenDelegate
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenDelegateException
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenErrorCode
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerificationResult
import com.brianyeh.justnotes.backend.auth.OfficialGoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.auth.VerifiedGoogleIdTokenPayload
import com.brianyeh.justnotes.backend.config.BackendConfig
import kotlinx.coroutines.runBlocking
import java.security.GeneralSecurityException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoogleIdTokenVerifierTest {
    @Test
    fun validTokenIsAccepted() = runBlocking {
        val result = verifier().verify("id-token")

        assertTrue(result is GoogleIdTokenVerificationResult.Success)
        assertEquals("google-sub", result.identity.googleSub)
    }

    @Test
    fun invalidSignatureIsRejected() = runBlocking {
        val result = verifier(delegate = ThrowingDelegate(GeneralSecurityException("bad signature"))).verify("id-token")

        assertFailure(GoogleIdTokenErrorCode.INVALID_ID_TOKEN, result)
    }

    @Test
    fun wrongAudienceIsRejected() = runBlocking {
        val result = verifier(payload = validPayload(audience = "wrong")).verify("id-token")

        assertFailure(GoogleIdTokenErrorCode.INVALID_AUDIENCE, result)
    }

    @Test
    fun wrongIssuerIsRejected() = runBlocking {
        val result = verifier(payload = validPayload(issuer = "https://issuer.example")).verify("id-token")

        assertFailure(GoogleIdTokenErrorCode.INVALID_ISSUER, result)
    }

    @Test
    fun expiredTokenIsRejected() = runBlocking {
        val result = verifier(payload = validPayload(expirationTimeMillis = NOW - 1)).verify("id-token")

        assertFailure(GoogleIdTokenErrorCode.ID_TOKEN_EXPIRED, result)
    }

    @Test
    fun missingSubjectIsRejected() = runBlocking {
        val result = verifier(payload = validPayload(subject = "")).verify("id-token")

        assertFailure(GoogleIdTokenErrorCode.INVALID_SUBJECT, result)
    }

    @Test
    fun malformedTokenIsRejected() = runBlocking {
        val result = verifier(
            delegate = ThrowingDelegate(
                GoogleIdTokenDelegateException(GoogleIdTokenErrorCode.INVALID_ID_TOKEN, "malformed"),
            ),
        ).verify("malformed")

        assertFailure(GoogleIdTokenErrorCode.INVALID_ID_TOKEN, result)
    }

    @Test
    fun parserRuntimeFailureIsRejected() = runBlocking {
        val result = verifier(delegate = ThrowingDelegate(IllegalArgumentException("malformed"))).verify("malformed")

        assertFailure(GoogleIdTokenErrorCode.INVALID_ID_TOKEN, result)
    }

    @Test
    fun missingConfigFailsClosed() = runBlocking {
        val result = OfficialGoogleIdTokenVerifier(
            config = BackendConfig.fromEnvironment(emptyMap()),
            clock = { NOW },
            delegate = PayloadDelegate(validPayload()),
        ).verify("id-token")

        assertFailure(GoogleIdTokenErrorCode.INVALID_AUDIENCE, result)
    }

    private fun verifier(
        payload: VerifiedGoogleIdTokenPayload = validPayload(),
        delegate: GoogleIdTokenDelegate = PayloadDelegate(payload),
    ): OfficialGoogleIdTokenVerifier {
        return OfficialGoogleIdTokenVerifier(
            config = BackendConfig.fromEnvironment(mapOf("GOOGLE_WEB_CLIENT_ID" to "web-client")),
            clock = { NOW },
            delegate = delegate,
        )
    }

    private fun validPayload(
        audience: String? = "web-client",
        issuer: String? = "accounts.google.com",
        expirationTimeMillis: Long = NOW + 60_000L,
        subject: String? = "google-sub",
    ): VerifiedGoogleIdTokenPayload {
        return VerifiedGoogleIdTokenPayload(
            audience = audience,
            issuer = issuer,
            expirationTimeMillis = expirationTimeMillis,
            subject = subject,
        )
    }

    private fun assertFailure(
        expected: GoogleIdTokenErrorCode,
        result: GoogleIdTokenVerificationResult,
    ) {
        assertTrue(result is GoogleIdTokenVerificationResult.Failure)
        assertEquals(expected, result.code)
    }

    private class PayloadDelegate(
        private val payload: VerifiedGoogleIdTokenPayload?,
    ) : GoogleIdTokenDelegate {
        override fun verify(idToken: String): VerifiedGoogleIdTokenPayload? = payload
    }

    private class ThrowingDelegate(
        private val throwable: Exception,
    ) : GoogleIdTokenDelegate {
        override fun verify(idToken: String): VerifiedGoogleIdTokenPayload? {
            throw throwable
        }
    }

    private companion object {
        const val NOW = 1_762_000_000_000L
    }
}
