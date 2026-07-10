package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.billing.BillingApiJson
import com.brianyeh.justnotes.backend.billing.BillingContextResponse
import com.brianyeh.justnotes.backend.billing.BillingErrorCode
import com.brianyeh.justnotes.backend.billing.BillingVerifyRequestParseResult
import com.brianyeh.justnotes.backend.billing.BillingVerifyResponse
import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendEntitlementSource
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class BillingApiModelsTest {
    @Test
    fun parsesCompletePurchaseRequest() {
        val result = BillingApiJson.parseVerifyRequest(completeRequestJson())

        val request = assertIs<BillingVerifyRequestParseResult.Success>(result).request
        assertEquals("purchase-token", request.purchaseToken)
        assertEquals("com.brianyeh.justnotes", request.packageName)
        assertEquals("just_notes_premium", request.productId)
        assertEquals("monthly", request.basePlanId)
        assertEquals("trial10d", request.offerId)
        assertEquals("1.0.7", request.appVersion)
        assertEquals(5L, request.versionCode)
        assertEquals("zh-TW", request.deviceLocale)
    }

    @Test
    fun parsesRestoreRequestWithNullPlanAndOfferHints() {
        val result = BillingApiJson.parseVerifyRequest(
            completeRequestJson(basePlanId = null, offerId = null),
        )

        val request = assertIs<BillingVerifyRequestParseResult.Success>(result).request
        assertNull(request.basePlanId)
        assertNull(request.offerId)
    }

    @Test
    fun rejectsOfferHintWithoutBasePlanHint() {
        val result = BillingApiJson.parseVerifyRequest(
            completeRequestJson(basePlanId = null, offerId = "trial10d"),
        )

        assertEquals(
            "offerId requires basePlanId.",
            assertIs<BillingVerifyRequestParseResult.Failure>(result).reason,
        )
    }

    @Test
    fun rejectsMissingEmptyAndOversizedTokens() {
        val missing = BillingApiJson.parseVerifyRequest(
            completeRequestJson().replace("\n  \"purchaseToken\": \"purchase-token\",", ""),
        )
        val empty = BillingApiJson.parseVerifyRequest(completeRequestJson(purchaseToken = ""))
        val oversized = BillingApiJson.parseVerifyRequest(
            completeRequestJson(purchaseToken = "x".repeat(4097)),
        )

        assertIs<BillingVerifyRequestParseResult.Failure>(missing)
        assertEquals("purchaseToken is required.", assertIs<BillingVerifyRequestParseResult.Failure>(empty).reason)
        assertEquals("purchaseToken is too long.", assertIs<BillingVerifyRequestParseResult.Failure>(oversized).reason)
    }

    @Test
    fun rejectsUnknownFieldsInsteadOfIgnoringMisspellings() {
        val result = BillingApiJson.parseVerifyRequest(
            completeRequestJson().replace("\n}", ",\n  \"purchaseTokn\": \"typo\"\n}"),
        )

        assertEquals(
            "Request JSON is invalid.",
            assertIs<BillingVerifyRequestParseResult.Failure>(result).reason,
        )
    }

    @Test
    fun rejectsInvalidPackageVersionCodeAndLocale() {
        val invalidPackage = BillingApiJson.parseVerifyRequest(
            completeRequestJson(packageName = "not a package"),
        )
        val invalidVersion = BillingApiJson.parseVerifyRequest(
            completeRequestJson(versionCode = 0),
        )
        val invalidLocale = BillingApiJson.parseVerifyRequest(
            completeRequestJson(deviceLocale = "zh_TW"),
        )

        assertEquals(
            "packageName is invalid.",
            assertIs<BillingVerifyRequestParseResult.Failure>(invalidPackage).reason,
        )
        assertEquals(
            "versionCode must be positive.",
            assertIs<BillingVerifyRequestParseResult.Failure>(invalidVersion).reason,
        )
        assertEquals(
            "deviceLocale is invalid.",
            assertIs<BillingVerifyRequestParseResult.Failure>(invalidLocale).reason,
        )
    }

    @Test
    fun rejectsJsonValuesWithWrongPrimitiveTypes() {
        val numericPackage = BillingApiJson.parseVerifyRequest(
            completeRequestJson().replace(
                "\"packageName\": \"com.brianyeh.justnotes\"",
                "\"packageName\": 7",
            ),
        )
        val stringVersionCode = BillingApiJson.parseVerifyRequest(
            completeRequestJson().replace("\"versionCode\": 5", "\"versionCode\": \"5\""),
        )

        assertEquals(
            "Request JSON is invalid.",
            assertIs<BillingVerifyRequestParseResult.Failure>(numericPackage).reason,
        )
        assertEquals(
            "Request JSON is invalid.",
            assertIs<BillingVerifyRequestParseResult.Failure>(stringVersionCode).reason,
        )
    }

    @Test
    fun requestStringRedactsPurchaseToken() {
        val request = assertIs<BillingVerifyRequestParseResult.Success>(
            BillingApiJson.parseVerifyRequest(completeRequestJson()),
        ).request

        assertContains(request.toString(), "purchaseToken=[REDACTED]")
        assertFalse(request.toString().contains("purchase-token"))
    }

    @Test
    fun serializesStableContextAndVerifyResponseNamesWithEscaping() {
        val contextJson = BillingApiJson.encodeContextResponse(
            BillingContextResponse(obfuscatedExternalAccountId = "account_id-123"),
        )
        val responseJson = BillingApiJson.encodeVerifyResponse(
            BillingVerifyResponse(
                hasPremium = false,
                status = BackendSubscriptionStatus.VerificationPending,
                source = BackendEntitlementSource.BackendVerified,
                packageName = "com.brianyeh.justnotes",
                productId = "just_notes_premium",
                basePlanId = "monthly",
                offerId = "trial10d",
                expiryTime = null,
                lastVerifiedAt = 1_762_000_000_000L,
                purchaseTokenHash = "hash",
                acknowledgementState = BackendAcknowledgementState.Pending,
                retryable = true,
                retryAfterSeconds = 900,
                errorCode = BillingErrorCode.ACKNOWLEDGEMENT_RETRY,
                reason = "Pending \"verification\".",
            ),
        )

        assertEquals(
            "{\"schemaVersion\":1,\"obfuscatedExternalAccountId\":\"account_id-123\"}",
            contextJson,
        )
        assertContains(responseJson, "\"status\":\"VerificationPending\"")
        assertContains(responseJson, "\"source\":\"BackendVerified\"")
        assertContains(responseJson, "\"acknowledgementState\":\"Pending\"")
        assertContains(responseJson, "\"errorCode\":\"ACKNOWLEDGEMENT_RETRY\"")
        assertContains(responseJson, "\"reason\":\"Pending \\\"verification\\\".\"")
        assertContains(responseJson, "\"expiryTime\":null")
    }

    private fun completeRequestJson(
        purchaseToken: String = "purchase-token",
        packageName: String = "com.brianyeh.justnotes",
        basePlanId: String? = "monthly",
        offerId: String? = "trial10d",
        versionCode: Long = 5,
        deviceLocale: String = "zh-TW",
    ): String {
        fun nullable(value: String?): String = value?.let { "\"$it\"" } ?: "null"
        return """
            {
              "purchaseToken": "$purchaseToken",
              "packageName": "$packageName",
              "productId": "just_notes_premium",
              "basePlanId": ${nullable(basePlanId)},
              "offerId": ${nullable(offerId)},
              "appVersion": "1.0.7",
              "versionCode": $versionCode,
              "deviceLocale": "$deviceLocale"
            }
        """.trimIndent()
    }
}
