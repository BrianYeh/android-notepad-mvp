package com.brianyeh.justnotes.backend.routes

import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerificationResult
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.billing.BillingApiJson
import com.brianyeh.justnotes.backend.billing.BillingVerifyRequest
import com.brianyeh.justnotes.backend.billing.BillingVerifyRequestParseResult
import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendEntitlementSource
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.entitlement.EntitlementRepository
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerifier
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun Application.justNotesRoutes(
    config: BackendConfig,
    idTokenVerifier: GoogleIdTokenVerifier,
    entitlementRepository: EntitlementRepository,
    @Suppress("UNUSED_PARAMETER") playSubscriptionVerifier: PlaySubscriptionVerifier,
    clock: () -> Long = System::currentTimeMillis,
) {
    routing {
        get("/v1/health") {
            call.respondText(
                """{"ok":true,"environment":"${json(config.environment)}"}""",
                contentType = io.ktor.http.ContentType.Application.Json,
            )
        }

        get("/v1/entitlement") {
            val idToken = call.request.headers["Authorization"]
                ?.removePrefix("Bearer ")
                ?.takeIf { it.isNotBlank() }
            if (idToken == null) {
                call.respondText(
                    """{"error":"missing_authorization"}""",
                    status = HttpStatusCode.Unauthorized,
                    contentType = io.ktor.http.ContentType.Application.Json,
                )
                return@get
            }
            when (val result = idTokenVerifier.verify(idToken)) {
                is GoogleIdTokenVerificationResult.Failure -> {
                    call.respondText(
                        """{"error":"unauthorized"}""",
                        status = HttpStatusCode.Unauthorized,
                        contentType = io.ktor.http.ContentType.Application.Json,
                    )
                }
                is GoogleIdTokenVerificationResult.Success -> {
                    val entitlement = entitlementRepository.getEntitlement(result.identity.googleSub)
                        ?: EntitlementRecord(
                            googleSub = result.identity.googleSub,
                            hasPremium = false,
                            status = BackendSubscriptionStatus.Unknown,
                            source = BackendEntitlementSource.None,
                        )
                    call.respondText(
                        entitlement.sanitizedForGet(clock(), config).toJson(),
                        contentType = io.ktor.http.ContentType.Application.Json,
                    )
                }
            }
        }

        post("/v1/billing/verify") {
            val idToken = call.request.headers["Authorization"]
                ?.removePrefix("Bearer ")
                ?.takeIf { it.isNotBlank() }
            if (idToken == null) {
                call.respondText(
                    """{"error":"missing_authorization"}""",
                    status = HttpStatusCode.Unauthorized,
                    contentType = io.ktor.http.ContentType.Application.Json,
                )
                return@post
            }
            if (idTokenVerifier.verify(idToken) is GoogleIdTokenVerificationResult.Failure) {
                call.respondText(
                    """{"error":"unauthorized"}""",
                    status = HttpStatusCode.Unauthorized,
                    contentType = io.ktor.http.ContentType.Application.Json,
                )
                return@post
            }
            val body = call.receiveText()
            val request = when (val parsed = BillingApiJson.parseVerifyRequest(body)) {
                is BillingVerifyRequestParseResult.Failure -> null
                is BillingVerifyRequestParseResult.Success -> parsed.request
            }
            val catalogError = request?.let {
                config.validateCatalog(
                    packageName = it.packageName,
                    productId = it.productId,
                    basePlanId = it.basePlanId,
                    offerId = it.offerId,
                )
            }
            call.respondText(
                nonGrantingVerifyResponse(
                    now = clock(),
                    request = request,
                    errorCode = catalogError?.let { "INVALID_CATALOG" } ?: "POST_VERIFY_DISABLED",
                    reason = catalogError ?: "Billing verify is non-granting until v1.0.7 internal purchase flow.",
                ),
                status = HttpStatusCode.Accepted,
                contentType = io.ktor.http.ContentType.Application.Json,
            )
        }

        post("/v1/play/rtdn") {
            call.respondText(
                """{"error":"rtdn_not_enabled"}""",
                status = HttpStatusCode.NotImplemented,
                contentType = io.ktor.http.ContentType.Application.Json,
            )
        }
    }
}

private fun nonGrantingVerifyResponse(
    now: Long,
    request: BillingVerifyRequest?,
    errorCode: String,
    reason: String,
): String {
    return buildString {
        append("{")
        append(""""schemaVersion":1""")
        append(""","hasPremium":false""")
        append(""","status":"${BackendSubscriptionStatus.VerificationPending.name}"""")
        append(""","source":"${BackendEntitlementSource.BackendVerified.name}"""")
        appendNullableString("productId", request?.productId)
        appendNullableString("basePlanId", request?.basePlanId)
        appendNullableString("offerId", request?.offerId)
        append(""","expiryTime":null""")
        append(""","lastVerifiedAt":$now""")
        append(""","acknowledgementState":"Pending"""")
        append(""","purchaseTokenHash":null""")
        append(""","errorCode":"${json(errorCode)}"""")
        append(""","reason":"${json(reason)}"""")
        append("}")
    }
}

private fun EntitlementRecord.sanitizedForGet(now: Long, config: BackendConfig): EntitlementRecord {
    val catalogValid = config.validateCatalog(
        packageName = packageName,
        productId = productId,
        basePlanId = basePlanId,
        offerId = offerId,
    ) == null
    val grantableStatus = status == BackendSubscriptionStatus.Active ||
        status == BackendSubscriptionStatus.GracePeriod ||
        status == BackendSubscriptionStatus.CanceledActiveUntilExpiry
    val expiryValid = expiryTime?.let { it > now } == true
    val verifiedAt = lastVerifiedAt
    val age = verifiedAt?.let { now - it }
    val withinMaxStale = age?.let { it <= config.entitlementMaxStaleMillis } == true
    val stale = age?.let { it > config.entitlementReverifyTtlMillis && it <= config.entitlementMaxStaleMillis } == true
    val sourceVerified = source == BackendEntitlementSource.BackendVerified
    val acknowledged = acknowledgementState == BackendAcknowledgementState.Acknowledged
    val effectivePremium = hasPremium &&
        catalogValid &&
        sourceVerified &&
        acknowledged &&
        grantableStatus &&
        expiryValid &&
        withinMaxStale
    val effectiveStatus = when {
        effectivePremium && status == BackendSubscriptionStatus.CanceledActiveUntilExpiry -> BackendSubscriptionStatus.Active
        effectivePremium -> status
        !catalogValid -> BackendSubscriptionStatus.Unknown
        !expiryValid && grantableStatus -> BackendSubscriptionStatus.Expired
        !withinMaxStale -> BackendSubscriptionStatus.Unknown
        !sourceVerified -> BackendSubscriptionStatus.Unknown
        !acknowledged && grantableStatus -> BackendSubscriptionStatus.VerificationPending
        else -> status
    }
    return copy(
        hasPremium = effectivePremium,
        status = effectiveStatus,
        stale = effectivePremium && stale,
    )
}

private fun EntitlementRecord.toJson(): String {
    return buildString {
        append("{")
        append(""""schemaVersion":1""")
        append(""","hasPremium":$hasPremium""")
        append(""","status":"${json(status.name)}"""")
        append(""","source":"${json(source.name)}"""")
        appendNullableString("packageName", packageName)
        appendNullableString("productId", productId)
        appendNullableString("basePlanId", basePlanId)
        appendNullableString("offerId", offerId)
        appendNullableLong("expiryTime", expiryTime)
        appendNullableLong("lastVerifiedAt", lastVerifiedAt)
        append(""","stale":$stale""")
        appendNullableString("purchaseTokenHash", purchaseTokenHash)
        appendNullableString("acknowledgementState", acknowledgementState?.name)
        append("}")
    }
}

private fun StringBuilder.appendNullableString(name: String, value: String?) {
    append(""","$name":""")
    if (value == null) {
        append("null")
    } else {
        append(""""${json(value)}"""")
    }
}

private fun StringBuilder.appendNullableLong(name: String, value: Long?) {
    append(""","$name":""")
    append(value?.toString() ?: "null")
}

private fun json(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}
