package com.example.notepad.billing

import android.content.SharedPreferences
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.min

internal data class PendingAcknowledgement(
    val purchaseToken: String,
    val tokenHash: String,
    val productId: String?,
    val attemptCount: Int,
    val nextAttemptAt: Long,
    val lastError: String?,
)

internal class PremiumEntitlementStore(
    private val preferences: SharedPreferences,
) {
    fun loadSubscription(): PremiumSubscriptionSnapshot {
        val storedStatus = preferences.getString(STATUS_KEY, null)
        if (storedStatus == null && preferences.getBoolean(LEGACY_PREMIUM_ENTITLED_KEY, false)) {
            return PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.VerificationPending,
                source = PremiumEntitlementSource.ClientObserved,
                acknowledgementStatus = PremiumAcknowledgementStatus.BackendRequired,
            )
        }
        return PremiumSubscriptionSnapshot(
            status = enumValueOrDefault(storedStatus, PremiumSubscriptionStatus.Unknown),
            source = enumValueOrDefault(preferences.getString(SOURCE_KEY, null), PremiumEntitlementSource.None),
            productId = preferences.getString(PRODUCT_ID_KEY, null),
            basePlanId = preferences.getString(BASE_PLAN_ID_KEY, null),
            offerId = preferences.getString(OFFER_ID_KEY, null),
            purchaseTokenHash = preferences.getString(PURCHASE_TOKEN_HASH_KEY, null),
            purchaseTime = preferences.getLongOrNull(PURCHASE_TIME_KEY),
            expiryTime = preferences.getLongOrNull(EXPIRY_TIME_KEY),
            lastPlayQueryAt = preferences.getLongOrNull(LAST_PLAY_QUERY_AT_KEY),
            lastBackendVerifiedAt = preferences.getLongOrNull(LAST_BACKEND_VERIFIED_AT_KEY),
            lastEntitlementChangeAt = preferences.getLongOrNull(LAST_ENTITLEMENT_CHANGE_AT_KEY),
            acknowledgementStatus = enumValueOrDefault(
                preferences.getString(ACK_STATUS_KEY, null),
                PremiumAcknowledgementStatus.NotRequired,
            ),
            acknowledgementAttemptCount = preferences.getInt(ACK_ATTEMPT_COUNT_KEY, 0),
            nextAcknowledgementAttemptAt = preferences.getLongOrNull(ACK_NEXT_ATTEMPT_AT_KEY),
            lastAcknowledgementError = preferences.getString(ACK_LAST_ERROR_KEY, null),
        )
    }

    fun saveSubscription(
        snapshot: PremiumSubscriptionSnapshot,
        preservePendingAcknowledgement: Boolean = false,
    ) {
        val editor = preferences.edit()
            .putString(STATUS_KEY, snapshot.status.name)
            .putString(SOURCE_KEY, snapshot.source.name)
            .putNullableString(PRODUCT_ID_KEY, snapshot.productId)
            .putNullableString(BASE_PLAN_ID_KEY, snapshot.basePlanId)
            .putNullableString(OFFER_ID_KEY, snapshot.offerId)
            .putNullableString(PURCHASE_TOKEN_HASH_KEY, snapshot.purchaseTokenHash)
            .putNullableLong(PURCHASE_TIME_KEY, snapshot.purchaseTime)
            .putNullableLong(EXPIRY_TIME_KEY, snapshot.expiryTime)
            .putNullableLong(LAST_PLAY_QUERY_AT_KEY, snapshot.lastPlayQueryAt)
            .putNullableLong(LAST_BACKEND_VERIFIED_AT_KEY, snapshot.lastBackendVerifiedAt)
            .putNullableLong(LAST_ENTITLEMENT_CHANGE_AT_KEY, snapshot.lastEntitlementChangeAt)
            .putString(ACK_STATUS_KEY, snapshot.acknowledgementStatus.name)
            .remove(LEGACY_PREMIUM_ENTITLED_KEY)
        if (!preservePendingAcknowledgement) {
            editor
                .putInt(ACK_ATTEMPT_COUNT_KEY, snapshot.acknowledgementAttemptCount)
                .putNullableLong(ACK_NEXT_ATTEMPT_AT_KEY, snapshot.nextAcknowledgementAttemptAt)
                .putNullableString(ACK_LAST_ERROR_KEY, snapshot.lastAcknowledgementError)
        }
        if (!preservePendingAcknowledgement && !snapshot.needsPendingAcknowledgementToken()) {
            editor
                .remove(ACK_PURCHASE_TOKEN_KEY)
                .remove(ACK_PURCHASE_TOKENS_KEY)
                .remove(ACK_PRODUCT_ID_KEY)
        }
        editor.apply()
    }

    fun loadPendingAcknowledgement(): PendingAcknowledgement? {
        return loadPendingAcknowledgements().firstOrNull()
    }

    fun loadPendingAcknowledgement(purchaseToken: String): PendingAcknowledgement? {
        return loadPendingAcknowledgements().firstOrNull { it.purchaseToken == purchaseToken }
    }

    private fun loadPendingAcknowledgements(): List<PendingAcknowledgement> {
        return pendingPurchaseTokens().map { token ->
            val tokenHash = hashPurchaseToken(token)
            val legacyToken = preferences.getString(ACK_PURCHASE_TOKEN_KEY, null)
            val isLegacyToken = legacyToken == token
            PendingAcknowledgement(
                purchaseToken = token,
                tokenHash = tokenHash,
                productId = preferences.getString(scopedAckProductIdKey(tokenHash), null)
                    ?: preferences.getString(ACK_PRODUCT_ID_KEY, null).takeIf { isLegacyToken },
                attemptCount = if (preferences.contains(scopedAckAttemptCountKey(tokenHash))) {
                    preferences.getInt(scopedAckAttemptCountKey(tokenHash), 0)
                } else {
                    preferences.getInt(ACK_ATTEMPT_COUNT_KEY, 0).takeIf { isLegacyToken } ?: 0
                },
                nextAttemptAt = if (preferences.contains(scopedAckNextAttemptAtKey(tokenHash))) {
                    preferences.getLong(scopedAckNextAttemptAtKey(tokenHash), 0L)
                } else {
                    preferences.getLong(ACK_NEXT_ATTEMPT_AT_KEY, 0L).takeIf { isLegacyToken } ?: 0L
                },
                lastError = preferences.getString(scopedAckLastErrorKey(tokenHash), null)
                    ?: preferences.getString(ACK_LAST_ERROR_KEY, null).takeIf { isLegacyToken },
            )
        }
    }

    fun recordPendingAcknowledgement(purchaseToken: String, productId: String?) {
        val tokenHash = hashPurchaseToken(purchaseToken)
        val pendingTokens = pendingPurchaseTokens().toMutableSet()
        if (purchaseToken in pendingTokens) return
        pendingTokens += purchaseToken
        preferences.edit()
            .putStringSet(ACK_PURCHASE_TOKENS_KEY, pendingTokens)
            .putNullableString(scopedAckProductIdKey(tokenHash), productId)
            .putInt(scopedAckAttemptCountKey(tokenHash), 0)
            .putLong(scopedAckNextAttemptAtKey(tokenHash), 0L)
            .remove(scopedAckLastErrorKey(tokenHash))
            .apply()
    }

    fun markAcknowledgementSucceeded(purchaseToken: String) {
        val pendingTokens = pendingPurchaseTokens().toMutableSet()
        if (purchaseToken !in pendingTokens) return
        pendingTokens -= purchaseToken
        val tokenHash = hashPurchaseToken(purchaseToken)
        val legacyToken = preferences.getString(ACK_PURCHASE_TOKEN_KEY, null)
        val editor = preferences.edit()
            .putStringSet(ACK_PURCHASE_TOKENS_KEY, pendingTokens)
            .remove(scopedAckProductIdKey(tokenHash))
            .remove(scopedAckAttemptCountKey(tokenHash))
            .remove(scopedAckNextAttemptAtKey(tokenHash))
            .remove(scopedAckLastErrorKey(tokenHash))
        if (legacyToken == purchaseToken) {
            editor.clearLegacyPendingAcknowledgement()
        }
        if (pendingTokens.isEmpty()) {
            editor.remove(ACK_PURCHASE_TOKENS_KEY)
        }
        editor.apply()
    }

    fun markAcknowledgementFailed(purchaseToken: String, error: String, now: Long): PendingAcknowledgement? {
        val current = loadPendingAcknowledgement(purchaseToken) ?: return null
        val nextAttemptCount = current.attemptCount + 1
        val retryDelayMillis = retryDelayMillis(nextAttemptCount)
        val updated = current.copy(
            attemptCount = nextAttemptCount,
            nextAttemptAt = now + retryDelayMillis,
            lastError = error,
        )
        preferences.edit()
            .putInt(scopedAckAttemptCountKey(updated.tokenHash), updated.attemptCount)
            .putLong(scopedAckNextAttemptAtKey(updated.tokenHash), updated.nextAttemptAt)
            .putString(scopedAckLastErrorKey(updated.tokenHash), updated.lastError)
            .apply()
        return updated
    }

    private fun pendingPurchaseTokens(): Set<String> {
        val pendingTokens = preferences.getStringSet(ACK_PURCHASE_TOKENS_KEY, null)?.toMutableSet() ?: mutableSetOf()
        preferences.getString(ACK_PURCHASE_TOKEN_KEY, null)?.let(pendingTokens::add)
        return pendingTokens
    }

    private fun PremiumSubscriptionSnapshot.needsPendingAcknowledgementToken(): Boolean {
        return acknowledgementStatus == PremiumAcknowledgementStatus.Pending ||
            acknowledgementStatus == PremiumAcknowledgementStatus.RetryScheduled ||
            acknowledgementStatus == PremiumAcknowledgementStatus.BackendRequired
    }

    private fun SharedPreferences.Editor.clearLegacyPendingAcknowledgement(): SharedPreferences.Editor {
        return remove(ACK_PURCHASE_TOKEN_KEY)
            .remove(ACK_PRODUCT_ID_KEY)
            .remove(ACK_ATTEMPT_COUNT_KEY)
            .remove(ACK_NEXT_ATTEMPT_AT_KEY)
            .remove(ACK_LAST_ERROR_KEY)
    }

    private fun scopedAckProductIdKey(tokenHash: String): String {
        return "${ACK_PRODUCT_ID_KEY}_$tokenHash"
    }

    private fun scopedAckAttemptCountKey(tokenHash: String): String {
        return "${ACK_ATTEMPT_COUNT_KEY}_$tokenHash"
    }

    private fun scopedAckNextAttemptAtKey(tokenHash: String): String {
        return "${ACK_NEXT_ATTEMPT_AT_KEY}_$tokenHash"
    }

    private fun scopedAckLastErrorKey(tokenHash: String): String {
        return "${ACK_LAST_ERROR_KEY}_$tokenHash"
    }

    companion object {
        const val PREFERENCES_NAME = "billing_entitlement"
        private const val LEGACY_PREMIUM_ENTITLED_KEY = "premium_entitled"
        private const val STATUS_KEY = "subscription_status"
        private const val SOURCE_KEY = "subscription_source"
        private const val PRODUCT_ID_KEY = "subscription_product_id"
        private const val BASE_PLAN_ID_KEY = "subscription_base_plan_id"
        private const val OFFER_ID_KEY = "subscription_offer_id"
        private const val PURCHASE_TOKEN_HASH_KEY = "subscription_purchase_token_hash"
        private const val PURCHASE_TIME_KEY = "subscription_purchase_time"
        private const val EXPIRY_TIME_KEY = "subscription_expiry_time"
        private const val LAST_PLAY_QUERY_AT_KEY = "subscription_last_play_query_at"
        private const val LAST_BACKEND_VERIFIED_AT_KEY = "subscription_last_backend_verified_at"
        private const val LAST_ENTITLEMENT_CHANGE_AT_KEY = "subscription_last_entitlement_change_at"
        private const val ACK_STATUS_KEY = "subscription_ack_status"
        private const val ACK_ATTEMPT_COUNT_KEY = "subscription_ack_attempt_count"
        private const val ACK_NEXT_ATTEMPT_AT_KEY = "subscription_ack_next_attempt_at"
        private const val ACK_LAST_ERROR_KEY = "subscription_ack_last_error"
        private const val ACK_PURCHASE_TOKEN_KEY = "subscription_ack_purchase_token"
        private const val ACK_PURCHASE_TOKENS_KEY = "subscription_ack_purchase_tokens"
        private const val ACK_PRODUCT_ID_KEY = "subscription_ack_product_id"

        fun hashPurchaseToken(purchaseToken: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(purchaseToken.toByteArray(Charsets.UTF_8))
            return digest.joinToString(separator = "") { byte ->
                String.format(Locale.US, "%02x", byte.toInt() and 0xff)
            }
        }

        private fun retryDelayMillis(attemptCount: Int): Long {
            val shift = (attemptCount - 1).coerceIn(0, 5)
            val minutes = min(15L * (1L shl shift), 6L * 60L)
            return minutes * 60_000L
        }

        private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, defaultValue: T): T {
            return value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: defaultValue
        }

        private fun SharedPreferences.getLongOrNull(key: String): Long? {
            return if (contains(key)) getLong(key, 0L) else null
        }

        private fun SharedPreferences.Editor.putNullableString(
            key: String,
            value: String?,
        ): SharedPreferences.Editor {
            return if (value == null) remove(key) else putString(key, value)
        }

        private fun SharedPreferences.Editor.putNullableLong(
            key: String,
            value: Long?,
        ): SharedPreferences.Editor {
            return if (value == null) remove(key) else putLong(key, value)
        }
    }
}
