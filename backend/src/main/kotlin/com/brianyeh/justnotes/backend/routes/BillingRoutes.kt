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

fun Application.justNotesRoutes(
    config: BackendConfig,
    idTokenVerifier: GoogleIdTokenVerifier,
    entitlementRepository: EntitlementRepository,
    @Suppress("UNUSED_PARAMETER") playSubscriptionVerifier: PlaySubscriptionVerifier,
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
                            source = BackendEntitlementSource.BackendVerified,
                        )
                    call.respondText(
                        entitlement.toJson(),
                        contentType = io.ktor.http.ContentType.Application.Json,
                    )
                }
            }
        }

        post("/v1/billing/verify") {
            call.receiveText()
            call.respondText(
                """{"error":"billing_verify_not_enabled"}""",
                status = HttpStatusCode.NotImplemented,
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

private fun EntitlementRecord.toJson(): String {
    return buildString {
        append("{")
        append(""""hasPremium":$hasPremium""")
        append(""","status":"${json(status.name)}"""")
        append(""","source":"${json(source.name)}"""")
        appendNullableString("packageName", packageName)
        appendNullableString("productId", productId)
        appendNullableString("basePlanId", basePlanId)
        appendNullableString("offerId", offerId)
        appendNullableLong("expiryTime", expiryTime)
        appendNullableLong("lastVerifiedAt", lastVerifiedAt)
        appendNullableString("purchaseTokenHash", purchaseTokenHash)
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
