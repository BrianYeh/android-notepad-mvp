package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.auth.GoogleIdTokenDelegate
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenDelegateException
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenErrorCode
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerificationResult
import com.brianyeh.justnotes.backend.auth.OfficialGoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.auth.VerifiedGoogleIdTokenPayload
import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.security.EmailHashDeriver
import kotlinx.coroutines.runBlocking
import java.security.GeneralSecurityException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleIdTokenVerifierTest {
    @Test
    fun validTokenIsAccepted() = runBlocking {
        val result = verifier().verify("id-token")

        assertTrue(result is GoogleIdTokenVerificationResult.Success)
        assertEquals("google-sub", result.identity.googleSub)
    }

    @Test
    fun verifiedEmailIsNormalizedAndHashedWithoutLeavingRawEmailInIdentity() = runBlocking {
        val seen = mutableListOf<String>()
        val result = verifier(
            payload = validPayload(
                email = " Reviewer@Example.COM ",
                emailVerified = true,
            ),
            emailHashDeriver = EmailHashDeriver { normalized ->
                seen += normalized
                "h".repeat(43)
            },
        ).verify("id-token")

        assertTrue(result is GoogleIdTokenVerificationResult.Success)
        assertEquals(listOf("reviewer@example.com"), seen)
        assertEquals("h".repeat(43), result.identity.emailHash)
        assertFalse(result.identity.toString().contains("Reviewer@Example.COM"))
    }

    @Test
    fun unverifiedOrMissingEmailNeverProducesEmailHash() = runBlocking {
        listOf(
            validPayload(email = "reviewer@example.com", emailVerified = false),
            validPayload(email = null, emailVerified = true),
        ).forEach { payload ->
            val result = verifier(
                payload = payload,
                emailHashDeriver = EmailHashDeriver { "h".repeat(43) },
            ).verify("id-token")

            assertTrue(result is GoogleIdTokenVerificationResult.Success)
            assertEquals(null, result.identity.emailHash)
        }
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
        emailHashDeriver: EmailHashDeriver? = null,
    ): OfficialGoogleIdTokenVerifier {
        return OfficialGoogleIdTokenVerifier(
            config = BackendConfig.fromEnvironment(
                mapOf("GOOGLE_WEB_CLIENT_ID" to "test-web-client.apps.googleusercontent.com"),
            ),
            clock = { NOW },
            delegate = delegate,
            emailHashDeriver = emailHashDeriver,
        )
    }

    private fun validPayload(
        audience: String? = "test-web-client.apps.googleusercontent.com",
        issuer: String? = "accounts.google.com",
        expirationTimeMillis: Long = NOW + 60_000L,
        subject: String? = "google-sub",
        email: String? = null,
        emailVerified: Boolean = false,
    ): VerifiedGoogleIdTokenPayload {
        return VerifiedGoogleIdTokenPayload(
            audience = audience,
            issuer = issuer,
            expirationTimeMillis = expirationTimeMillis,
            subject = subject,
            email = email,
            emailVerified = emailVerified,
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
