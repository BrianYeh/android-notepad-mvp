package com.brianyeh.justnotes.backend.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier.Builder
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.brianyeh.justnotes.backend.config.BackendConfig
import java.io.IOException
import java.security.GeneralSecurityException

data class VerifiedGoogleIdentity(
    val googleSub: String,
    val emailHash: String? = null,
)

enum class GoogleIdTokenErrorCode {
    UNAUTHENTICATED,
    INVALID_ID_TOKEN,
    ID_TOKEN_EXPIRED,
    INVALID_AUDIENCE,
    INVALID_ISSUER,
    INVALID_SUBJECT,
}

sealed class GoogleIdTokenVerificationResult {
    data class Success(val identity: VerifiedGoogleIdentity) : GoogleIdTokenVerificationResult()
    data class Failure(
        val reason: String,
        val code: GoogleIdTokenErrorCode = GoogleIdTokenErrorCode.INVALID_ID_TOKEN,
    ) : GoogleIdTokenVerificationResult()
}

interface GoogleIdTokenVerifier {
    suspend fun verify(idToken: String): GoogleIdTokenVerificationResult
}

class FailClosedGoogleIdTokenVerifier(
    private val config: BackendConfig,
) : GoogleIdTokenVerifier {
    override suspend fun verify(idToken: String): GoogleIdTokenVerificationResult {
        val validation = config.validateForIdTokenVerification()
        if (validation != null) {
            return GoogleIdTokenVerificationResult.Failure(validation, GoogleIdTokenErrorCode.INVALID_AUDIENCE)
        }
        return GoogleIdTokenVerificationResult.Failure(
            reason = "Google ID token verifier is not implemented.",
            code = GoogleIdTokenErrorCode.INVALID_ID_TOKEN,
        )
    }
}

class OfficialGoogleIdTokenVerifier(
    private val config: BackendConfig,
    private val clock: () -> Long = System::currentTimeMillis,
    private val delegate: GoogleIdTokenDelegate = GoogleApiClientIdTokenDelegate(config),
) : GoogleIdTokenVerifier {
    override suspend fun verify(idToken: String): GoogleIdTokenVerificationResult {
        if (idToken.isBlank()) {
            return GoogleIdTokenVerificationResult.Failure(
                reason = "Authorization bearer token is blank.",
                code = GoogleIdTokenErrorCode.UNAUTHENTICATED,
            )
        }
        val validation = config.validateForIdTokenVerification()
        if (validation != null) {
            return GoogleIdTokenVerificationResult.Failure(
                reason = validation,
                code = GoogleIdTokenErrorCode.INVALID_AUDIENCE,
            )
        }

        val payload = try {
            delegate.verify(idToken)
        } catch (exception: GoogleIdTokenDelegateException) {
            return GoogleIdTokenVerificationResult.Failure(exception.reason, exception.code)
        } catch (exception: GeneralSecurityException) {
            return GoogleIdTokenVerificationResult.Failure(
                reason = "Google ID token signature verification failed.",
                code = GoogleIdTokenErrorCode.INVALID_ID_TOKEN,
            )
        } catch (exception: IOException) {
            return GoogleIdTokenVerificationResult.Failure(
                reason = "Google ID token verification failed.",
                code = GoogleIdTokenErrorCode.INVALID_ID_TOKEN,
            )
        } catch (exception: IllegalArgumentException) {
            return GoogleIdTokenVerificationResult.Failure(
                reason = "Google ID token is malformed.",
                code = GoogleIdTokenErrorCode.INVALID_ID_TOKEN,
            )
        } ?: return GoogleIdTokenVerificationResult.Failure(
            reason = "Google ID token signature verification failed.",
            code = GoogleIdTokenErrorCode.INVALID_ID_TOKEN,
        )

        if (payload.audience != config.googleWebClientId) {
            return GoogleIdTokenVerificationResult.Failure("Invalid Google ID token audience.", GoogleIdTokenErrorCode.INVALID_AUDIENCE)
        }
        if (payload.issuer !in config.issuerAllowlist) {
            return GoogleIdTokenVerificationResult.Failure("Invalid Google ID token issuer.", GoogleIdTokenErrorCode.INVALID_ISSUER)
        }
        if (payload.expirationTimeMillis <= clock()) {
            return GoogleIdTokenVerificationResult.Failure("Google ID token is expired.", GoogleIdTokenErrorCode.ID_TOKEN_EXPIRED)
        }
        val subject = payload.subject?.takeIf { it.isNotBlank() }
            ?: return GoogleIdTokenVerificationResult.Failure("Google ID token subject is missing.", GoogleIdTokenErrorCode.INVALID_SUBJECT)

        return GoogleIdTokenVerificationResult.Success(VerifiedGoogleIdentity(googleSub = subject))
    }
}

data class VerifiedGoogleIdTokenPayload(
    val audience: String?,
    val issuer: String?,
    val expirationTimeMillis: Long,
    val subject: String?,
)

interface GoogleIdTokenDelegate {
    @Throws(GeneralSecurityException::class, IOException::class, GoogleIdTokenDelegateException::class)
    fun verify(idToken: String): VerifiedGoogleIdTokenPayload?
}

class GoogleIdTokenDelegateException(
    val code: GoogleIdTokenErrorCode,
    val reason: String,
) : Exception(reason)

class GoogleApiClientIdTokenDelegate(
    config: BackendConfig,
) : GoogleIdTokenDelegate {
    private val verifier = Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
        .setAudience(config.googleWebClientId?.let(::listOf).orEmpty())
        .setIssuers(config.issuerAllowlist)
        .build()

    override fun verify(idToken: String): VerifiedGoogleIdTokenPayload? {
        val verified: GoogleIdToken = verifier.verify(idToken) ?: return null
        val payload = verified.payload
        return VerifiedGoogleIdTokenPayload(
            audience = payload.audienceAsList.firstOrNull(),
            issuer = payload.issuer,
            expirationTimeMillis = payload.expirationTimeSeconds * 1_000L,
            subject = payload.subject,
        )
    }
}
