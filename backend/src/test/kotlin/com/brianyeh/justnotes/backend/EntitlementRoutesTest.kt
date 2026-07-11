package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerificationResult
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.auth.VerifiedGoogleIdentity
import com.brianyeh.justnotes.backend.billing.BillingVerificationOrchestrator
import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.AcknowledgementCompletionResult
import com.brianyeh.justnotes.backend.entitlement.AcknowledgementClaimResult
import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.entitlement.EntitlementReconciliationResult
import com.brianyeh.justnotes.backend.entitlement.EntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.InMemoryEntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.PurchaseOwnershipValidator
import com.brianyeh.justnotes.backend.entitlement.SubscriptionRecord
import com.brianyeh.justnotes.backend.entitlement.SubscriptionWriteResult
import com.brianyeh.justnotes.backend.play.NoopPlaySubscriptionVerifier
import com.brianyeh.justnotes.backend.play.PlaySubscriptionAcknowledgementResult
import com.brianyeh.justnotes.backend.play.PlaySubscriptionAcknowledger
import com.brianyeh.justnotes.backend.play.PlayAcknowledgementFailureCode
import com.brianyeh.justnotes.backend.play.PlayAcknowledgementState
import com.brianyeh.justnotes.backend.play.PlayExternalAccountIdentifiers
import com.brianyeh.justnotes.backend.play.PlaySubscriptionLineItem
import com.brianyeh.justnotes.backend.play.PlaySubscriptionState
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerification
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerificationResult
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerifier
import com.brianyeh.justnotes.backend.routes.justNotesRoutes as productionJustNotesRoutes
import com.brianyeh.justnotes.backend.security.ObfuscatedAccountIdDeriver
import com.brianyeh.justnotes.backend.security.PurchaseTokenCipher
import com.brianyeh.justnotes.backend.security.TokenCiphertext
import io.ktor.client.request.get
import io.ktor.client.request.header
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

class EntitlementRoutesTest {
    @Test
    fun billingContextReturnsBackendObfuscatedAccountIdWithoutCaching() = testApplication {
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = EmptyRepository,
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
            )
        }

        val response = client.get("/v1/billing/context") {
            header(HttpHeaders.Authorization, "Bearer id-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(
            response.bodyAsText(),
            "\"obfuscatedExternalAccountId\":\"$ROUTE_ACCOUNT_ID\"",
        )
        assertFalse(response.bodyAsText().contains("google-sub"))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun billingContextRequiresAuthorization() = testApplication {
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = EmptyRepository,
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
            )
        }

        val response = client.get("/v1/billing/context")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun billingContextRejectsInvalidIdentityBeforeDerivation() = testApplication {
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = RejectingVerifier,
                entitlementRepository = EmptyRepository,
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                obfuscatedAccountIdDeriver = RouteAccountIdDeriver(
                    failure = IllegalStateException("secret unavailable"),
                ),
                clock = { NOW },
            )
        }

        val unauthorized = client.get("/v1/billing/context") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)
        assertEquals("no-store", unauthorized.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun billingContextReturnsServiceUnavailableWithoutSecretDetails() = testApplication {
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = EmptyRepository,
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                obfuscatedAccountIdDeriver = RouteAccountIdDeriver(
                    failure = IllegalStateException("secret unavailable"),
                ),
                clock = { NOW },
            )
        }

        val response = client.get("/v1/billing/context") {
            header(HttpHeaders.Authorization, "Bearer id-token")
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertContains(response.bodyAsText(), "billing_context_unavailable")
        assertFalse(response.bodyAsText().contains("secret unavailable"))
        assertFalse(response.bodyAsText().contains("google-sub"))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun postVerifyRejectsMalformedOversizedAndWrongContentTypeBodies() = testApplication {
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = EmptyRepository,
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
            )
        }

        val malformed = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer id-token")
            contentType(ContentType.Application.Json)
            setBody("{")
        }
        val oversized = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer id-token")
            contentType(ContentType.Application.Json)
            setBody(validVerifyJson() + " ".repeat(9_000))
        }
        val wrongContentType = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer id-token")
            contentType(ContentType.Text.Plain)
            setBody(validVerifyJson())
        }
        val missingContentType = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer id-token")
            setBody(validVerifyJson().encodeToByteArray())
        }
        val wildcardContentType = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer id-token")
            header(HttpHeaders.ContentType, "*/*")
            setBody(validVerifyJson())
        }

        listOf(malformed, oversized, wrongContentType, missingContentType, wildcardContentType).forEach { response ->
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "\"errorCode\":\"INVALID_REQUEST\"")
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        }
    }

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
    fun postVerifySuccessPersistsPremiumAndGetReturnsIt() = testApplication {
        val repository = InMemoryEntitlementRepository()
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = repository,
                playSubscriptionVerifier = RoutePlayVerifier(routeVerification()),
                clock = { NOW },
            )
        }

        val response = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer id-token")
            header(HttpHeaders.ContentType, "application/json; charset=UTF-8")
            setBody(validVerifyJson())
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), """"hasPremium":true""")
        assertContains(response.bodyAsText(), """"status":"Active"""")
        assertContains(response.bodyAsText(), """"acknowledgementState":"Acknowledged"""")
        assertFalse(response.bodyAsText().contains("raw-token"))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])

        val entitlement = client.get("/v1/entitlement") {
            header(HttpHeaders.Authorization, "Bearer id-token")
        }
        assertEquals(HttpStatusCode.OK, entitlement.status)
        assertContains(entitlement.bodyAsText(), """"hasPremium":true""")
        assertContains(entitlement.bodyAsText(), """"purchaseTokenHash":"route-token-hash"""")
    }

    @Test
    fun postVerifyPlayFailureReturnsServiceUnavailableWithoutLeakingToken() = testApplication {
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
            setBody(validVerifyJson())
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertContains(response.bodyAsText(), "\"errorCode\":\"PLAY_VERIFICATION_UNAVAILABLE\"")
        assertFalse(response.bodyAsText().contains("raw-token"))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun postVerifyCatalogMismatchReturnsUnprocessableEntity() = testApplication {
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

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertContains(response.bodyAsText(), """"hasPremium":false""")
        assertContains(response.bodyAsText(), """"errorCode":"CATALOG_MISMATCH"""")
        assertFalse(response.bodyAsText().contains("raw-token"))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun postVerifyPendingPurchaseReturnsAcceptedWithoutPremium() = testApplication {
        val repository = InMemoryEntitlementRepository()
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = repository,
                playSubscriptionVerifier = RoutePlayVerifier(
                    routeVerification(
                        state = BackendSubscriptionStatus.PendingPurchase,
                        playState = PlaySubscriptionState.SUBSCRIPTION_STATE_PENDING,
                        acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING,
                    ),
                ),
                clock = { NOW },
            )
        }

        val response = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer id-token")
            contentType(ContentType.Application.Json)
            setBody(validVerifyJson())
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertContains(response.bodyAsText(), """"hasPremium":false""")
        assertContains(response.bodyAsText(), """"status":"PendingPurchase"""")
        assertContains(response.bodyAsText(), """"errorCode":"PURCHASE_PENDING"""")
        assertFalse(response.bodyAsText().contains("raw-token"))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun postVerifyRetryableAcknowledgementReturnsAcceptedWithBackoff() = testApplication {
        val repository = InMemoryEntitlementRepository()
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = repository,
                playSubscriptionVerifier = RoutePlayVerifier(
                    routeVerification(
                        acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_PENDING,
                    ),
                ),
                playSubscriptionAcknowledger = RouteAcknowledger(
                    PlaySubscriptionAcknowledgementResult.Failure(
                        reason = "redacted",
                        retryable = true,
                        code = PlayAcknowledgementFailureCode.PLAY_ACK_UNAVAILABLE,
                    ),
                ),
                clock = { NOW },
            )
        }

        val response = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer id-token")
            contentType(ContentType.Application.Json)
            setBody(validVerifyJson())
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertContains(response.bodyAsText(), """"errorCode":"ACKNOWLEDGEMENT_RETRY"""")
        assertContains(response.bodyAsText(), """"retryAfterSeconds":900""")
        assertFalse(response.bodyAsText().contains("raw-token"))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun postVerifyOwnerMismatchReturnsUnprocessableWithoutSensitiveWrites() = testApplication {
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = EmptyRepository,
                playSubscriptionVerifier = RoutePlayVerifier(
                    routeVerification(obfuscatedAccountId = "different-account-id"),
                ),
                clock = { NOW },
            )
        }

        val response = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer id-token")
            contentType(ContentType.Application.Json)
            setBody(validVerifyJson())
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertContains(response.bodyAsText(), """"errorCode":"OWNER_MISMATCH"""")
        assertFalse(response.bodyAsText().contains("raw-token"))
        assertFalse(response.bodyAsText().contains("google-sub"))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun postVerifyOtherOwnerTokenReturnsConflictWithoutLeakingEitherOwner() = testApplication {
        val repository = object : EntitlementRepository by EmptyRepository {
            override suspend fun getSubscription(purchaseTokenHash: String): SubscriptionRecord {
                return routeSubscription(owner = "other-google-sub")
            }
        }
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = repository,
                playSubscriptionVerifier = RoutePlayVerifier(routeVerification()),
                clock = { NOW },
            )
        }

        val response = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer id-token")
            contentType(ContentType.Application.Json)
            setBody(validVerifyJson())
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertContains(response.bodyAsText(), """"errorCode":"TOKEN_ALREADY_BOUND"""")
        assertFalse(response.bodyAsText().contains("other-google-sub"))
        assertFalse(response.bodyAsText().contains("google-sub"))
        assertFalse(response.bodyAsText().contains("raw-token"))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun postVerifyRejectsInvalidIdentityWithoutReadingSensitiveResponseData() = testApplication {
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = RejectingVerifier,
                entitlementRepository = EmptyRepository,
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
            )
        }

        val response = client.post("/v1/billing/verify") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
            contentType(ContentType.Application.Json)
            setBody(validVerifyJson())
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertFalse(response.bodyAsText().contains("raw-token"))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun rtdnRemainsNotImplemented() = testApplication {
        application {
            justNotesRoutes(
                config = testConfig(),
                idTokenVerifier = FakeVerifier(),
                entitlementRepository = EmptyRepository,
                playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
                clock = { NOW },
            )
        }

        val response = client.post("/v1/play/rtdn")

        assertEquals(HttpStatusCode.NotImplemented, response.status)
        assertContains(response.bodyAsText(), "rtdn_not_enabled")
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
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    private fun testConfig(): BackendConfig {
        return BackendConfig.fromEnvironment(
            mapOf("GOOGLE_WEB_CLIENT_ID" to "test-web-client.apps.googleusercontent.com"),
        )
    }

    private fun validVerifyJson(purchaseToken: String = "raw-token"): String {
        return """
            {
              "purchaseToken": "$purchaseToken",
              "packageName": "com.brianyeh.justnotes",
              "productId": "just_notes_premium",
              "basePlanId": "monthly",
              "offerId": "trial10d",
              "appVersion": "1.0.7",
              "versionCode": 5,
              "deviceLocale": "zh-TW"
            }
        """.trimIndent()
    }

    private companion object {
        const val NOW = 1_762_000_000_000L
    }
}

private fun Application.justNotesRoutes(
    config: BackendConfig,
    idTokenVerifier: GoogleIdTokenVerifier,
    entitlementRepository: EntitlementRepository,
    playSubscriptionVerifier: PlaySubscriptionVerifier,
    playSubscriptionAcknowledger: PlaySubscriptionAcknowledger = RouteAcknowledger(
        PlaySubscriptionAcknowledgementResult.Acknowledged,
    ),
    obfuscatedAccountIdDeriver: ObfuscatedAccountIdDeriver = RouteAccountIdDeriver(),
    purchaseTokenCipher: PurchaseTokenCipher = RoutePurchaseTokenCipher,
    clock: () -> Long,
) {
    val orchestrator = BillingVerificationOrchestrator(
        config = config,
        entitlementRepository = entitlementRepository,
        playSubscriptionVerifier = playSubscriptionVerifier,
        playSubscriptionAcknowledger = playSubscriptionAcknowledger,
        ownershipValidator = PurchaseOwnershipValidator(obfuscatedAccountIdDeriver),
        purchaseTokenCipher = purchaseTokenCipher,
        nowMillis = clock,
    )
    productionJustNotesRoutes(
        config = config,
        idTokenVerifier = idTokenVerifier,
        entitlementRepository = entitlementRepository,
        billingVerificationOrchestrator = orchestrator,
        obfuscatedAccountIdDeriver = obfuscatedAccountIdDeriver,
        clock = clock,
    )
}

private class RouteAccountIdDeriver(
    private val value: String = ROUTE_ACCOUNT_ID,
    private val failure: RuntimeException? = null,
) : ObfuscatedAccountIdDeriver {
    override fun derive(googleSub: String): String {
        failure?.let { throw it }
        return value
    }
}

private class RouteAcknowledger(
    private val result: PlaySubscriptionAcknowledgementResult,
) : PlaySubscriptionAcknowledger {
    override suspend fun acknowledge(
        packageName: String,
        productId: String,
        purchaseToken: String,
    ): PlaySubscriptionAcknowledgementResult = result
}

private class RoutePlayVerifier(
    private val verification: PlaySubscriptionVerification,
) : PlaySubscriptionVerifier {
    override suspend fun verify(
        packageName: String,
        purchaseToken: String,
    ): PlaySubscriptionVerificationResult {
        return PlaySubscriptionVerificationResult.Success(verification)
    }
}

private fun routeVerification(
    state: BackendSubscriptionStatus = BackendSubscriptionStatus.Active,
    playState: PlaySubscriptionState = PlaySubscriptionState.SUBSCRIPTION_STATE_ACTIVE,
    acknowledgementState: PlayAcknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED,
    obfuscatedAccountId: String = ROUTE_ACCOUNT_ID,
): PlaySubscriptionVerification {
    return PlaySubscriptionVerification(
        packageName = "com.brianyeh.justnotes",
        purchaseTokenHash = "route-token-hash",
        subscriptionState = state,
        playSubscriptionState = playState,
        lineItems = listOf(
            PlaySubscriptionLineItem(
                productId = "just_notes_premium",
                basePlanId = "monthly",
                offerId = "trial10d",
                expiryTime = ROUTE_NOW + 30L * 24L * 60L * 60L * 1_000L,
            ),
        ),
        acknowledgementState = acknowledgementState.name,
        playAcknowledgementState = acknowledgementState,
        autoRenewing = true,
        linkedPurchaseTokenHash = null,
        externalAccountIdentifiers = PlayExternalAccountIdentifiers(obfuscatedAccountId),
        canceledButActiveUntilExpiry = false,
        purchaseTokenHashVersion = "hmac-sha256-v1",
        purchaseTokenPepperVersion = "1",
    )
}

private fun routeSubscription(owner: String): SubscriptionRecord {
    return SubscriptionRecord(
        purchaseTokenHash = "route-token-hash",
        hashVersion = "hmac-sha256-v1",
        pepperVersion = "1",
        ownerGoogleSub = owner,
        packageName = "com.brianyeh.justnotes",
        productId = "just_notes_premium",
        basePlanId = "monthly",
        offerId = "trial10d",
        linkedPurchaseTokenHash = null,
        tokenCiphertext = "existing-ciphertext",
        keyVersion = "existing-key-version",
        encryptedAt = ROUTE_NOW,
        encryptionAlgorithm = "GOOGLE_SYMMETRIC_ENCRYPTION",
        acknowledgementState = BackendAcknowledgementState.Acknowledged,
        acknowledgementAttemptCount = 0,
        nextAcknowledgementAttemptAt = null,
        lastAcknowledgementErrorCode = null,
        lastVerifiedAt = ROUTE_NOW,
        status = BackendSubscriptionStatus.Active,
        expiryTime = ROUTE_NOW + 30L * 24L * 60L * 60L * 1_000L,
    )
}

private object RoutePurchaseTokenCipher : PurchaseTokenCipher {
    override fun encrypt(purchaseToken: String, now: Long): TokenCiphertext {
        return TokenCiphertext(
            tokenCiphertext = "test-ciphertext",
            keyVersion = "test-key-version",
            encryptedAt = now,
            encryptionAlgorithm = "GOOGLE_SYMMETRIC_ENCRYPTION",
        )
    }

    override fun decrypt(ciphertext: TokenCiphertext): String {
        error("Decrypt is not used by route tests.")
    }
}

private const val ROUTE_NOW = 1_762_000_000_000L
private const val ROUTE_ACCOUNT_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

private class FakeVerifier : GoogleIdTokenVerifier {
    override suspend fun verify(idToken: String): GoogleIdTokenVerificationResult {
        return GoogleIdTokenVerificationResult.Success(VerifiedGoogleIdentity("google-sub"))
    }
}

private object RejectingVerifier : GoogleIdTokenVerifier {
    override suspend fun verify(idToken: String): GoogleIdTokenVerificationResult {
        return GoogleIdTokenVerificationResult.Failure("invalid token")
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
        maxStaleMillis: Long,
    ): EntitlementReconciliationResult = EntitlementReconciliationResult.Missing
}
