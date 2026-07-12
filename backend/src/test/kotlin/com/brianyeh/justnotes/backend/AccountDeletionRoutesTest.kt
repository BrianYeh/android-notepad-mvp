package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.account.AccountDeletionRepository
import com.brianyeh.justnotes.backend.account.AccountDeletionResult
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerificationResult
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.auth.VerifiedGoogleIdentity
import com.brianyeh.justnotes.backend.billing.BillingVerificationOrchestrator
import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.InMemoryEntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.PurchaseOwnershipValidator
import com.brianyeh.justnotes.backend.play.NoopPlaySubscriptionVerifier
import com.brianyeh.justnotes.backend.play.PlaySubscriptionAcknowledgementResult
import com.brianyeh.justnotes.backend.play.PlaySubscriptionAcknowledger
import com.brianyeh.justnotes.backend.routes.justNotesRoutes
import com.brianyeh.justnotes.backend.security.ObfuscatedAccountIdDeriver
import com.brianyeh.justnotes.backend.security.PurchaseTokenCipher
import com.brianyeh.justnotes.backend.security.TokenCiphertext
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AccountDeletionRoutesTest {
    @Test
    fun exactGithubPagesOriginCanPreflightDeletion() = testApplication {
        application { installRoutes() }

        val response = client.options(PATH) {
            header(HttpHeaders.Origin, ALLOWED_ORIGIN)
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "Authorization, Content-Type")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(ALLOWED_ORIGIN, response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertEquals("POST, OPTIONS", response.headers[HttpHeaders.AccessControlAllowMethods])
        assertEquals("Authorization, Content-Type", response.headers[HttpHeaders.AccessControlAllowHeaders])
        assertEquals("Origin", response.headers[HttpHeaders.Vary])
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun missingOrDifferentOriginIsForbidden() = testApplication {
        var calls = 0
        application {
            installRoutes(AccountDeletionRepository { _, _ ->
                calls += 1
                AccountDeletionResult.Deleted
            })
        }

        listOf(null, "https://attacker.example").forEach { origin ->
            val response = client.post(PATH) {
                origin?.let { header(HttpHeaders.Origin, it) }
                header(HttpHeaders.Authorization, "Bearer id-token")
                contentType(ContentType.Application.Json)
                setBody(CONFIRMATION)
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(null, response.headers[HttpHeaders.AccessControlAllowOrigin])
        }
        assertEquals(0, calls)
    }

    @Test
    fun validIdentityAndLiteralConfirmationCanDelete() = testApplication {
        var deletedSubject: String? = null
        application {
            installRoutes(AccountDeletionRepository { googleSub, now ->
                deletedSubject = googleSub
                assertEquals(NOW, now)
                AccountDeletionResult.Deleted
            })
        }

        val response = client.postDeletion(CONFIRMATION)

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("google-sub", deletedSubject)
        assertEquals("", response.bodyAsText())
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun invalidConfirmationDoesNotCallRepository() = testApplication {
        var calls = 0
        application {
            installRoutes(AccountDeletionRepository { _, _ ->
                calls += 1
                AccountDeletionResult.Deleted
            })
        }

        listOf(
            "{}",
            """{"confirmation":"delete"}""",
            """{"confirmation":"DELETE","extra":true}""",
            "not-json",
        ).forEach { body ->
            assertEquals(HttpStatusCode.BadRequest, client.postDeletion(body).status)
        }
        assertEquals(0, calls)
    }

    @Test
    fun activeSubscriptionReturnsConflictWithoutIdentityDetails() = testApplication {
        application {
            installRoutes(
                AccountDeletionRepository { _, _ ->
                    AccountDeletionResult.BlockedByNonterminalSubscription
                },
            )
        }

        val response = client.postDeletion(CONFIRMATION)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("""{"error":"active_subscription"}""", response.bodyAsText())
        assertFalse(response.bodyAsText().contains("google-sub"))
    }

    @Test
    fun invalidTokenAndRepositoryFailureNeverReflectSensitiveValues() = testApplication {
        application {
            installRoutes(
                accountDeletionRepository = AccountDeletionRepository { _, _ ->
                    AccountDeletionResult.FailedClosed
                },
            )
        }

        val unavailable = client.postDeletion(CONFIRMATION, bearer = "sensitive-id-token")
        assertEquals(HttpStatusCode.ServiceUnavailable, unavailable.status)
        assertEquals("""{"error":"deletion_unavailable"}""", unavailable.bodyAsText())
        assertFalse(unavailable.bodyAsText().contains("sensitive-id-token"))

        val unauthorized = client.post(PATH) {
            header(HttpHeaders.Origin, ALLOWED_ORIGIN)
            header(HttpHeaders.Authorization, "Bearer invalid-token")
            contentType(ContentType.Application.Json)
            setBody(CONFIRMATION)
        }
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)
        assertFalse(unauthorized.bodyAsText().contains("invalid-token"))
    }

    private fun Application.installRoutes(
        accountDeletionRepository: AccountDeletionRepository = AccountDeletionRepository { _, _ ->
            AccountDeletionResult.Deleted
        },
    ) {
        val repository = InMemoryEntitlementRepository()
        val deriver = object : ObfuscatedAccountIdDeriver {
            override fun derive(googleSub: String): String = "a".repeat(43)
        }
        val orchestrator = BillingVerificationOrchestrator(
            config = config(),
            entitlementRepository = repository,
            playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
            playSubscriptionAcknowledger = object : PlaySubscriptionAcknowledger {
                override suspend fun acknowledge(
                    packageName: String,
                    productId: String,
                    purchaseToken: String,
                ): PlaySubscriptionAcknowledgementResult = PlaySubscriptionAcknowledgementResult.Acknowledged
            },
            ownershipValidator = PurchaseOwnershipValidator(deriver),
            purchaseTokenCipher = object : PurchaseTokenCipher {
                override fun encrypt(purchaseToken: String, now: Long): TokenCiphertext = error("unused")
                override fun decrypt(ciphertext: TokenCiphertext): String = error("unused")
            },
        )
        justNotesRoutes(
            config = config(),
            idTokenVerifier = FakeDeletionVerifier,
            entitlementRepository = repository,
            billingVerificationOrchestrator = orchestrator,
            obfuscatedAccountIdDeriver = deriver,
            accountDeletionRepository = accountDeletionRepository,
            clock = { NOW },
        )
    }

    private suspend fun io.ktor.client.HttpClient.postDeletion(
        body: String,
        bearer: String = "id-token",
    ) = post(PATH) {
        header(HttpHeaders.Origin, ALLOWED_ORIGIN)
        header(HttpHeaders.Authorization, "Bearer $bearer")
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun config() = BackendConfig.fromEnvironment(
        mapOf("GOOGLE_WEB_CLIENT_ID" to "test-web-client.apps.googleusercontent.com"),
    )

    private object FakeDeletionVerifier : GoogleIdTokenVerifier {
        override suspend fun verify(idToken: String): GoogleIdTokenVerificationResult {
            return if (idToken == "invalid-token") {
                GoogleIdTokenVerificationResult.Failure("invalid")
            } else {
                GoogleIdTokenVerificationResult.Success(VerifiedGoogleIdentity("google-sub"))
            }
        }
    }

    private companion object {
        const val PATH = "/v1/account/delete"
        const val ALLOWED_ORIGIN = "https://brianyeh.github.io"
        const val CONFIRMATION = """{"confirmation":"DELETE"}"""
        const val NOW = 1_762_000_000_000L
    }
}
