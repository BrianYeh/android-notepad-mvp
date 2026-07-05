package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerificationResult
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.auth.VerifiedGoogleIdentity
import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.entitlement.EntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.SubscriptionBinding
import com.brianyeh.justnotes.backend.entitlement.TokenBindingResult
import com.brianyeh.justnotes.backend.play.NoopPlaySubscriptionVerifier
import com.brianyeh.justnotes.backend.routes.justNotesRoutes
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class EntitlementRoutesTest {
    @Test
    fun entitlementRequiresAuthorization() = testApplication {
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = EmptyRepository,
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
            )
        }

        val response = client.get("/v1/entitlement")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun entitlementReturnsUnknownWhenNoBackendOwnedRecordExists() = testApplication {
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = EmptyRepository,
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
            )
        }

        val response = client.get("/v1/entitlement") {
            header(HttpHeaders.Authorization, "Bearer id-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), """"hasPremium":false""")
        assertContains(response.bodyAsText(), """"status":"Unknown"""")
    }

    @Test
    fun entitlementCanReturnBackendOwnedActiveRecordFromRepository() = testApplication {
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = object : EntitlementRepository by EmptyRepository {
                    override suspend fun getEntitlement(googleSub: String): EntitlementRecord {
                        return EntitlementRecord(
                            googleSub = googleSub,
                            hasPremium = true,
                            status = BackendSubscriptionStatus.Active,
                            packageName = "com.brianyeh.justnotes",
                            productId = "just_notes_premium",
                            basePlanId = "monthly",
                            expiryTime = 1_762_000_000_000L,
                            lastVerifiedAt = 1_761_000_000_000L,
                            purchaseTokenHash = "token-hash",
                        )
                    }
                },
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
            )
        }

        val response = client.get("/v1/entitlement") {
            header(HttpHeaders.Authorization, "Bearer id-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), """"hasPremium":true""")
        assertContains(response.bodyAsText(), """"packageName":"com.brianyeh.justnotes"""")
        assertContains(response.bodyAsText(), """"purchaseTokenHash":"token-hash"""")
    }

    private fun testConfig(): BackendConfig {
        return BackendConfig.fromEnvironment(mapOf("GOOGLE_WEB_CLIENT_ID" to "web-client"))
    }
}

private class FakeVerifier : GoogleIdTokenVerifier {
    override suspend fun verify(idToken: String): GoogleIdTokenVerificationResult {
        return GoogleIdTokenVerificationResult.Success(VerifiedGoogleIdentity("google-sub"))
    }
}

private object EmptyRepository : EntitlementRepository {
    override suspend fun getEntitlement(googleSub: String): EntitlementRecord? = null

    override suspend fun upsertEntitlement(record: EntitlementRecord) = Unit

    override suspend fun bindSubscriptionTokenHash(binding: SubscriptionBinding): TokenBindingResult {
        return TokenBindingResult.Bound
    }
}
