package com.brianyeh.justnotes.backend.routes

import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerificationResult
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.auth.VerifiedGoogleIdentity
import com.brianyeh.justnotes.backend.billing.BillingApiJson
import com.brianyeh.justnotes.backend.billing.BillingContextResponse
import com.brianyeh.justnotes.backend.billing.BillingErrorCode
import com.brianyeh.justnotes.backend.billing.BillingVerificationOrchestrator
import com.brianyeh.justnotes.backend.billing.BillingVerifyRequestParseResult
import com.brianyeh.justnotes.backend.billing.BillingVerifyResponse
import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendEntitlementSource
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.entitlement.EntitlementRepository
import com.brianyeh.justnotes.backend.security.ObfuscatedAccountIdDeriver
import com.brianyeh.justnotes.backend.rtdn.RtdnJson
import com.brianyeh.justnotes.backend.rtdn.RtdnNotificationProcessor
import com.brianyeh.justnotes.backend.rtdn.RtdnParseResult
import com.brianyeh.justnotes.backend.rtdn.RtdnProcessResult
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException

fun Application.justNotesRoutes(
    config: BackendConfig,
    idTokenVerifier: GoogleIdTokenVerifier,
    entitlementRepository: EntitlementRepository,
    billingVerificationOrchestrator: BillingVerificationOrchestrator,
    obfuscatedAccountIdDeriver: ObfuscatedAccountIdDeriver,
    rtdnProcessor: RtdnNotificationProcessor? = null,
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

        get("/v1/billing/context") {
            call.response.headers.append(HttpHeaders.CacheControl, NO_STORE)
            val identity = call.authenticatedIdentity(idTokenVerifier) ?: return@get
            val obfuscatedAccountId = try {
                obfuscatedAccountIdDeriver.derive(identity.googleSub).takeIf { it.isNotBlank() }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                null
            }
            if (obfuscatedAccountId == null) {
                call.respondText(
                    """{"error":"billing_context_unavailable"}""",
                    status = HttpStatusCode.ServiceUnavailable,
                    contentType = ContentType.Application.Json,
                )
                return@get
            }
            call.respondText(
                BillingApiJson.encodeContextResponse(
                    BillingContextResponse(obfuscatedExternalAccountId = obfuscatedAccountId),
                ),
                contentType = ContentType.Application.Json,
            )
        }

        post("/v1/billing/verify") {
            call.response.headers.append(HttpHeaders.CacheControl, NO_STORE)
            val identity = call.authenticatedIdentity(idTokenVerifier) ?: return@post
            val requestContentType = runCatching { call.request.contentType() }.getOrNull()
            if (
                requestContentType == null ||
                requestContentType == ContentType.Any ||
                !requestContentType.match(ContentType.Application.Json)
            ) {
                call.respondInvalidVerifyRequest(clock())
                return@post
            }
            val body = call.receiveUtf8BodyWithinLimit(MAX_VERIFY_BODY_BYTES)
            if (body == null) {
                call.respondInvalidVerifyRequest(clock())
                return@post
            }
            val request = when (val parsed = BillingApiJson.parseVerifyRequest(body)) {
                is BillingVerifyRequestParseResult.Failure -> {
                    call.respondInvalidVerifyRequest(clock())
                    return@post
                }
                is BillingVerifyRequestParseResult.Success -> parsed.request
            }
            val outcome = billingVerificationOrchestrator.verify(identity.googleSub, request)
            call.respondText(
                BillingApiJson.encodeVerifyResponse(outcome.response),
                status = HttpStatusCode.fromValue(outcome.httpStatus),
                contentType = ContentType.Application.Json,
            )
        }

    }
    justNotesRtdnRoutes(config, rtdnProcessor)
}

fun Application.justNotesRtdnRoutes(
    config: BackendConfig,
    rtdnProcessor: RtdnNotificationProcessor?,
) {
    routing {
        post("/v1/play/rtdn") {
            call.response.headers.append(HttpHeaders.CacheControl, NO_STORE)
            if (!config.rtdnEnabled) {
                call.respondText(
                    """{"error":"rtdn_not_enabled"}""",
                    status = HttpStatusCode.NotImplemented,
                    contentType = ContentType.Application.Json,
                )
                return@post
            }
            val requestContentType = runCatching { call.request.contentType() }.getOrNull()
            if (
                requestContentType == null ||
                requestContentType == ContentType.Any ||
                !requestContentType.match(ContentType.Application.Json)
            ) {
                call.respondRtdnError(HttpStatusCode.BadRequest, "invalid_envelope")
                return@post
            }
            val body = call.receiveUtf8BodyWithinLimit(RtdnJson.MAX_HTTP_BODY_BYTES)
            if (body == null) {
                call.respondRtdnError(HttpStatusCode.BadRequest, "invalid_envelope")
                return@post
            }
            when (
                val parsed = RtdnJson.parse(
                    body = body,
                    expectedPackageName = config.allowedPackageName,
                    expectedSubscription = requireNotNull(config.rtdnExpectedSubscription),
                )
            ) {
                is RtdnParseResult.Failure ->
                    call.respondRtdnError(HttpStatusCode.BadRequest, parsed.errorCode.name.lowercase())
                is RtdnParseResult.Ignored ->
                    call.respondText("", status = HttpStatusCode.NoContent)
                is RtdnParseResult.Success -> {
                    val result = try {
                        requireNotNull(rtdnProcessor).process(parsed.envelope, parsed.notification)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        call.respondRtdnError(HttpStatusCode.InternalServerError, "internal_error")
                        return@post
                    }
                    when (result) {
                        is RtdnProcessResult.Completed,
                        is RtdnProcessResult.Ignored,
                        -> call.respondText("", status = HttpStatusCode.NoContent)
                        is RtdnProcessResult.RetryableFailure -> {
                            call.response.headers.append(
                                HttpHeaders.RetryAfter,
                                result.retryAfterSeconds.toString(),
                            )
                            call.respondRtdnError(
                                HttpStatusCode.ServiceUnavailable,
                                result.errorCode.name.lowercase(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun ApplicationCall.respondRtdnError(status: HttpStatusCode, code: String) {
    respondText(
        """{"error":"${json(code)}"}""",
        status = status,
        contentType = ContentType.Application.Json,
    )
}

private suspend fun ApplicationCall.authenticatedIdentity(
    idTokenVerifier: GoogleIdTokenVerifier,
): VerifiedGoogleIdentity? {
    val authorization = request.headers[HttpHeaders.Authorization]
    val idToken = authorization
        ?.takeIf { it.startsWith(BEARER_PREFIX) }
        ?.removePrefix(BEARER_PREFIX)
        ?.takeIf { it.isNotBlank() }
    if (idToken == null) {
        respondText(
            """{"error":"missing_authorization"}""",
            status = HttpStatusCode.Unauthorized,
            contentType = ContentType.Application.Json,
        )
        return null
    }
    return when (val result = idTokenVerifier.verify(idToken)) {
        is GoogleIdTokenVerificationResult.Failure -> {
            respondText(
                """{"error":"unauthorized"}""",
                status = HttpStatusCode.Unauthorized,
                contentType = ContentType.Application.Json,
            )
            null
        }
        is GoogleIdTokenVerificationResult.Success -> result.identity
    }
}

private suspend fun ApplicationCall.receiveUtf8BodyWithinLimit(maxBytes: Int): String? {
    val channel = receiveChannel()
    val output = ByteArrayOutputStream(minOf(maxBytes, READ_BUFFER_BYTES))
    val buffer = ByteArray(READ_BUFFER_BYTES)
    var totalBytes = 0
    while (true) {
        val readLimit = minOf(buffer.size, maxBytes - totalBytes + 1)
        val bytesRead = channel.readAvailable(buffer, 0, readLimit)
        if (bytesRead == -1) break
        if (bytesRead == 0) continue
        totalBytes += bytesRead
        if (totalBytes > maxBytes) return null
        output.write(buffer, 0, bytesRead)
    }
    return String(output.toByteArray(), StandardCharsets.UTF_8)
}

private suspend fun ApplicationCall.respondInvalidVerifyRequest(now: Long) {
    respondText(
        BillingApiJson.encodeVerifyResponse(
            BillingVerifyResponse(
                hasPremium = false,
                status = BackendSubscriptionStatus.Unknown,
                source = BackendEntitlementSource.None,
                packageName = null,
                productId = null,
                basePlanId = null,
                offerId = null,
                expiryTime = null,
                lastVerifiedAt = now,
                purchaseTokenHash = null,
                acknowledgementState = null,
                retryable = false,
                retryAfterSeconds = null,
                errorCode = BillingErrorCode.INVALID_REQUEST,
                reason = INVALID_REQUEST_REASON,
            ),
        ),
        status = HttpStatusCode.BadRequest,
        contentType = ContentType.Application.Json,
    )
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

private const val MAX_VERIFY_BODY_BYTES = 8 * 1024
private const val READ_BUFFER_BYTES = 1024
private const val BEARER_PREFIX = "Bearer "
private const val NO_STORE = "no-store"
private const val INVALID_REQUEST_REASON = "Request is invalid."
