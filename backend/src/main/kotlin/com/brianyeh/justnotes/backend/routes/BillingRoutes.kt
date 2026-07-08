package com.brianyeh.justnotes.backend.routes

import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerificationResult
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.config.BackendConfig
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
            val request = BillingVerifyRequest.parse(body)
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

private data class BillingVerifyRequest(
    val packageName: String?,
    val productId: String?,
    val basePlanId: String?,
    val offerId: String?,
) {
    companion object {
        fun parse(body: String): BillingVerifyRequest? {
            if (body.isBlank()) return null
            return runCatching {
                val root = Json.parseToJsonElement(body).jsonObject
                BillingVerifyRequest(
                    packageName = root.optionalString("packageName"),
                    productId = root.optionalString("productId"),
                    basePlanId = root.optionalString("basePlanId"),
                    offerId = root.optionalString("offerId"),
                )
            }.getOrNull()
        }
    }
}

private fun JsonObject.optionalString(name: String): String? {
    return (this[name] as? JsonPrimitive)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
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
    val grantableStatus = status == BackendSubscriptionStatus.Active ||
        status == BackendSubscriptionStatus.GracePeriod ||
        status == BackendSubscriptionStatus.CanceledActiveUntilExpiry
    val expiryValid = expiryTime?.let { it > now } == true
    val verifiedAt = lastVerifiedAt
    val age = verifiedAt?.let { now - it }
    val withinMaxStale = age?.let { it <= config.entitlementMaxStaleMillis } == true
    val stale = age?.let { it > config.entitlementReverifyTtlMillis && it <= config.entitlementMaxStaleMillis } == true
    val effectivePremium = hasPremium && grantableStatus && expiryValid && withinMaxStale
    val effectiveStatus = when {
        effectivePremium && status == BackendSubscriptionStatus.CanceledActiveUntilExpiry -> BackendSubscriptionStatus.Active
        effectivePremium -> status
        !expiryValid && grantableStatus -> BackendSubscriptionStatus.Expired
        !withinMaxStale -> BackendSubscriptionStatus.Unknown
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
