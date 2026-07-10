package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerificationResult
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.auth.VerifiedGoogleIdentity
import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.AcknowledgementCompletionResult
import com.brianyeh.justnotes.backend.entitlement.AcknowledgementClaimResult
import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.entitlement.EntitlementReconciliationResult
import com.brianyeh.justnotes.backend.entitlement.EntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.SubscriptionRecord
import com.brianyeh.justnotes.backend.entitlement.SubscriptionWriteResult
import com.brianyeh.justnotes.backend.play.NoopPlaySubscriptionVerifier
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerificationResult
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerifier
import com.brianyeh.justnotes.backend.routes.justNotesRoutes
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EntitlementRoutesTest {
    @Test
    fun entitlementRequiresAuthorization() = testApplication {
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = EmptyRepository,
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
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
                clock = { NOW },
            )
        }

        val response = client.get("/v1/entitlement") {
            header(HttpHeaders.Authorization, "Bearer id-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), """"hasPremium":false""")
        assertContains(response.bodyAsText(), """"status":"Unknown"""")
        assertContains(response.bodyAsText(), """"source":"None"""")
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
                            expiryTime = NOW + 1_000L,
                            lastVerifiedAt = NOW,
                            purchaseTokenHash = "token-hash",
                            acknowledgementState = BackendAcknowledgementState.Acknowledged,
                        )
                    }
                },
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
            )
        }

        val response = client.get("/v1/entitlement") {
            header(HttpHeaders.Authorization, "Bearer id-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), """"hasPremium":true""")
        assertContains(response.bodyAsText(), """"schemaVersion":1""")
        assertContains(response.bodyAsText(), """"packageName":"com.brianyeh.justnotes"""")
        assertContains(response.bodyAsText(), """"purchaseTokenHash":"token-hash"""")
    }

    @Test
    fun entitlementWithInvalidPersistedCatalogDoesNotGrantPremium() = testApplication {
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
                            basePlanId = "annual",
                            offerId = "trial10d",
                            expiryTime = NOW + 1_000L,
                            lastVerifiedAt = NOW,
                            purchaseTokenHash = "token-hash",
                            acknowledgementState = BackendAcknowledgementState.Acknowledged,
                        )
                    }
                },
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
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
    fun entitlementClampsUnacknowledgedRecordToNoPremium() = testApplication {
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
                            expiryTime = NOW + 1_000L,
                            lastVerifiedAt = NOW,
                            purchaseTokenHash = "token-hash",
                            acknowledgementState = BackendAcknowledgementState.Pending,
                        )
                    }
                },
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
            )
        }

        val response = client.get("/v1/entitlement") {
            header(HttpHeaders.Authorization, "Bearer id-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), """"hasPremium":false""")
        assertContains(response.bodyAsText(), """"status":"VerificationPending"""")
    }

    @Test
    fun entitlementClampsExpiredCacheToNoPremium() = testApplication {
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
                            expiryTime = NOW - 1L,
                            lastVerifiedAt = NOW,
                            purchaseTokenHash = "token-hash",
                            acknowledgementState = BackendAcknowledgementState.Acknowledged,
                        )
                    }
                },
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
            )
        }

        val response = client.get("/v1/entitlement") {
            header(HttpHeaders.Authorization, "Bearer id-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), """"hasPremium":false""")
        assertContains(response.bodyAsText(), """"status":"Expired"""")
    }

    @Test
    fun entitlementMarksStaleCacheWithinMaxStale() = testApplication {
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
                            expiryTime = NOW + 1_000L,
                            lastVerifiedAt = NOW - (7L * 60L * 60L * 1_000L),
                            purchaseTokenHash = "token-hash",
                            acknowledgementState = BackendAcknowledgementState.Acknowledged,
                        )
                    }
                },
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
            )
        }

        val response = client.get("/v1/entitlement") {
            header(HttpHeaders.Authorization, "Bearer id-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), """"hasPremium":true""")
        assertContains(response.bodyAsText(), """"stale":true""")
    }

    @Test
    fun entitlementBeyondMaxStaleDoesNotGrantPremium() = testApplication {
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
                            expiryTime = NOW + 1_000L,
                            lastVerifiedAt = NOW - (25L * 60L * 60L * 1_000L),
                            purchaseTokenHash = "token-hash",
                            acknowledgementState = BackendAcknowledgementState.Acknowledged,
                        )
                    }
                },
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
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
    fun entitlementWithMissingLastVerifiedAtDoesNotGrantPremium() = testApplication {
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
                            expiryTime = NOW + 1_000L,
                            lastVerifiedAt = null,
                            purchaseTokenHash = "token-hash",
                            acknowledgementState = BackendAcknowledgementState.Acknowledged,
                        )
                    }
                },
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
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
    fun postVerifyIsNonGranting() = testApplication {
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = EmptyRepository,
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
            )
        }

        val response = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer id-token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "purchaseToken": "raw-token",
                  "packageName": "com.brianyeh.justnotes",
                  "productId": "just_notes_premium",
                  "basePlanId": "monthly",
                  "offerId": "trial10d",
                  "appVersion": "1.0.7",
                  "versionCode": 5,
                  "deviceLocale": "zh-TW"
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertContains(response.bodyAsText(), """"hasPremium":false""")
        assertContains(response.bodyAsText(), """"status":"VerificationPending"""")
        assertContains(response.bodyAsText(), """"errorCode":"POST_VERIFY_DISABLED"""")
        assertFalse(response.bodyAsText().contains("raw-token"))
    }

    @Test
    fun postVerifyDoesNotInvokeProductionPlayAdapterBeforeV107() = testApplication {
        val verifier = object : PlaySubscriptionVerifier {
            override suspend fun verify(
                packageName: String,
                purchaseToken: String,
            ): PlaySubscriptionVerificationResult {
                error("The Play adapter must remain unreachable before v1.0.7.")
            }
        }
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = EmptyRepository,
                playSubscriptionVerifier = verifier,
                clock = { NOW },
            )
        }

        val response = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer id-token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "purchaseToken": "raw-token",
                  "packageName": "com.brianyeh.justnotes",
                  "productId": "just_notes_premium",
                  "basePlanId": "monthly",
                  "offerId": null,
                  "appVersion": "1.0.7",
                  "versionCode": 5,
                  "deviceLocale": "zh-TW"
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertContains(response.bodyAsText(), "\"errorCode\":\"POST_VERIFY_DISABLED\"")
        assertFalse(response.bodyAsText().contains("raw-token"))
    }

    @Test
    fun postVerifyCatalogMismatchIsStillNonGranting() = testApplication {
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = EmptyRepository,
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
            )
        }

        val response = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer id-token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "purchaseToken": "raw-token",
                  "packageName": "com.brianyeh.justnotes",
                  "productId": "just_notes_premium",
                  "basePlanId": "annual",
                  "offerId": "trial10d",
                  "appVersion": "1.0.7",
                  "versionCode": 5,
                  "deviceLocale": "zh-TW"
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertContains(response.bodyAsText(), """"hasPremium":false""")
        assertContains(response.bodyAsText(), """"errorCode":"INVALID_CATALOG"""")
        assertFalse(response.bodyAsText().contains("raw-token"))
    }

    @Test
    fun postVerifyDoesNotWriteGrantingEntitlement() = testApplication {
        val repository = RecordingRepository()
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = repository,
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
            )
        }

        val response = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer id-token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "purchaseToken": "raw-token",
                  "packageName": "com.brianyeh.justnotes",
                  "productId": "just_notes_premium",
                  "basePlanId": "monthly",
                  "offerId": null,
                  "appVersion": "1.0.7",
                  "versionCode": 5,
                  "deviceLocale": "zh-TW"
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals(null, repository.lastUpsert)
    }

    @Test
    fun postVerifyRequiresAuthorization() = testApplication {
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = EmptyRepository,
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
            )
        }

        val response = client.post("/v1/billing/verify") {
            contentType(ContentType.Application.Json)
            setBody("""{"purchaseToken":"raw-token"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertFalse(response.bodyAsText().contains("raw-token"))
    }

    private fun testConfig(): BackendConfig {
        return BackendConfig.fromEnvironment(
            mapOf("GOOGLE_WEB_CLIENT_ID" to "test-web-client.apps.googleusercontent.com"),
        )
    }

    private companion object {
        const val NOW = 1_762_000_000_000L
    }
}

private class FakeVerifier : GoogleIdTokenVerifier {
    override suspend fun verify(idToken: String): GoogleIdTokenVerificationResult {
        return GoogleIdTokenVerificationResult.Success(VerifiedGoogleIdentity("google-sub"))
    }
}

private object EmptyRepository : EntitlementRepository {
    override suspend fun getEntitlement(googleSub: String): EntitlementRecord? = null

    override suspend fun upsertEntitlement(record: EntitlementRecord): EntitlementRecord = record

    override suspend fun getSubscription(purchaseTokenHash: String): SubscriptionRecord? = null

    override suspend fun upsertSubscriptionForOwner(record: SubscriptionRecord): SubscriptionWriteResult {
        return SubscriptionWriteResult.Created
    }

    override suspend fun claimSubscriptionAcknowledgement(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        now: Long,
        leaseUntil: Long,
    ): AcknowledgementClaimResult = AcknowledgementClaimResult.Missing

    override suspend fun completeSubscriptionAcknowledgement(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        generation: Long,
        acknowledgementState: BackendAcknowledgementState,
        acknowledgementAttemptCount: Int,
        nextAcknowledgementAttemptAt: Long?,
        lastAcknowledgementErrorCode: String?,
    ): AcknowledgementCompletionResult = AcknowledgementCompletionResult.Missing

    override suspend fun reconcileEntitlementFromSubscription(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        now: Long,
    ): EntitlementReconciliationResult = EntitlementReconciliationResult.Missing
}

private class RecordingRepository : EntitlementRepository {
    var lastUpsert: EntitlementRecord? = null

    override suspend fun getEntitlement(googleSub: String): EntitlementRecord? = null

    override suspend fun upsertEntitlement(record: EntitlementRecord): EntitlementRecord {
        lastUpsert = record
        return record
    }

    override suspend fun getSubscription(purchaseTokenHash: String): SubscriptionRecord? = null

    override suspend fun upsertSubscriptionForOwner(record: SubscriptionRecord): SubscriptionWriteResult {
        return SubscriptionWriteResult.Created
    }

    override suspend fun claimSubscriptionAcknowledgement(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        now: Long,
        leaseUntil: Long,
    ): AcknowledgementClaimResult = AcknowledgementClaimResult.Missing

    override suspend fun completeSubscriptionAcknowledgement(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        generation: Long,
        acknowledgementState: BackendAcknowledgementState,
        acknowledgementAttemptCount: Int,
        nextAcknowledgementAttemptAt: Long?,
        lastAcknowledgementErrorCode: String?,
    ): AcknowledgementCompletionResult = AcknowledgementCompletionResult.Missing

    override suspend fun reconcileEntitlementFromSubscription(
        purchaseTokenHash: String,
        ownerGoogleSub: String,
        now: Long,
    ): EntitlementReconciliationResult = EntitlementReconciliationResult.Missing
}
