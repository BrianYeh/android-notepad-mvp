package com.brianyeh.justnotes.backend.reviewer

import com.brianyeh.justnotes.backend.auth.VerifiedGoogleIdentity
import com.brianyeh.justnotes.backend.entitlement.BackendAcknowledgementState
import com.brianyeh.justnotes.backend.entitlement.BackendEntitlementSource
import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.entitlement.EntitlementRecord
import com.brianyeh.justnotes.backend.security.SecretValueProvider
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

fun interface ReviewerGrantPolicy {
    fun entitlementFor(identity: VerifiedGoogleIdentity, now: Long): EntitlementRecord?
}

object NoReviewerGrantPolicy : ReviewerGrantPolicy {
    override fun entitlementFor(identity: VerifiedGoogleIdentity, now: Long): EntitlementRecord? = null
}

class SecretBackedReviewerGrantPolicy(
    private val secretProvider: SecretValueProvider,
) : ReviewerGrantPolicy {
    override fun entitlementFor(identity: VerifiedGoogleIdentity, now: Long): EntitlementRecord? {
        val identityHash = identity.emailHash?.takeIf(EMAIL_HASH_PATTERN::matches) ?: return null
        val secret = runCatching { secretProvider.currentSecret() }.getOrNull() ?: return null
        val grants = parseReviewerGrants(secret.value) ?: return null
        val grant = grants.firstOrNull { candidate ->
            candidate.expiresAt > now && constantTimeEquals(candidate.emailHash, identityHash)
        } ?: return null

        return EntitlementRecord(
            googleSub = identity.googleSub,
            hasPremium = true,
            status = BackendSubscriptionStatus.Active,
            source = BackendEntitlementSource.ReviewerGrant,
            expiryTime = grant.expiresAt,
            lastVerifiedAt = now,
            acknowledgementState = BackendAcknowledgementState.NotRequired,
        )
    }

    private fun parseReviewerGrants(value: String): List<ParsedReviewerGrant>? {
        val root = runCatching { Json.parseToJsonElement(value).jsonObject }.getOrNull() ?: return null
        if (root.keys != ROOT_FIELDS) return null
        val schemaVersion = root.numericLong("schemaVersion") ?: return null
        if (schemaVersion != SCHEMA_VERSION) return null
        val grantArray = root["grants"] as? JsonArray ?: return null
        if (grantArray.size !in MIN_GRANTS..MAX_GRANTS) return null

        val grants = mutableListOf<ParsedReviewerGrant>()
        for (element in grantArray) {
            val grantObject = element as? JsonObject ?: return null
            if (grantObject.keys != GRANT_FIELDS) return null
            val emailHash = grantObject.string("emailHash")
                ?.takeIf(EMAIL_HASH_PATTERN::matches)
                ?: return null
            val expiresAt = grantObject.numericLong("expiresAt")
                ?.takeIf { it > 0L }
                ?: return null
            grants += ParsedReviewerGrant(emailHash, expiresAt)
        }
        if (grants.map { it.emailHash }.toSet().size != grants.size) return null
        return grants
    }

    private fun JsonObject.string(name: String): String? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        if (!primitive.isString) return null
        return primitive.content
    }

    private fun JsonObject.numericLong(name: String): Long? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.longOrNull
    }

    private fun constantTimeEquals(left: String, right: String): Boolean {
        return MessageDigest.isEqual(
            left.toByteArray(Charsets.UTF_8),
            right.toByteArray(Charsets.UTF_8),
        )
    }

    private class ParsedReviewerGrant(
        val emailHash: String,
        val expiresAt: Long,
    ) {
        override fun toString(): String =
            "ParsedReviewerGrant(emailHash=[REDACTED], expiresAt=$expiresAt)"
    }

    private companion object {
        const val SCHEMA_VERSION = 1L
        const val MIN_GRANTS = 1
        const val MAX_GRANTS = 5
        val ROOT_FIELDS = setOf("schemaVersion", "grants")
        val GRANT_FIELDS = setOf("emailHash", "expiresAt")
        val EMAIL_HASH_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")
    }
}
