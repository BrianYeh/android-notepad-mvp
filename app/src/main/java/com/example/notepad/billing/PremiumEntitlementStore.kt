package com.example.notepad.billing

import android.content.SharedPreferences

internal class PremiumEntitlementStore(
    private val preferences: SharedPreferences,
) {
    init {
        purgeLegacyAcknowledgementSecrets()
    }

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
            source = enumValueOrDefault(
                preferences.getString(SOURCE_KEY, null),
                PremiumEntitlementSource.None,
            ),
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

    fun saveSubscription(snapshot: PremiumSubscriptionSnapshot) {
        preferences.edit()
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
            .putInt(ACK_ATTEMPT_COUNT_KEY, snapshot.acknowledgementAttemptCount)
            .putNullableLong(ACK_NEXT_ATTEMPT_AT_KEY, snapshot.nextAcknowledgementAttemptAt)
            .putNullableString(ACK_LAST_ERROR_KEY, snapshot.lastAcknowledgementError)
            .remove(LEGACY_PREMIUM_ENTITLED_KEY)
            .apply()
    }

    private fun purgeLegacyAcknowledgementSecrets() {
        val scopedPrefixes = listOf(
            ACK_PRODUCT_ID_KEY + "_",
            ACK_ATTEMPT_COUNT_KEY + "_",
            ACK_NEXT_ATTEMPT_AT_KEY + "_",
            ACK_LAST_ERROR_KEY + "_",
        )
        val editor = preferences.edit()
            .remove(LEGACY_ACK_TOKEN_KEY)
            .remove(LEGACY_ACK_TOKENS_KEY)
            .remove(ACK_PRODUCT_ID_KEY)
        preferences.all.keys
            .filter { key -> scopedPrefixes.any(key::startsWith) }
            .forEach(editor::remove)
        editor.apply()
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
        private const val ACK_PRODUCT_ID_KEY = "subscription_ack_product_id"
        private const val LEGACY_ACK_TOKEN_KEY = "subscription_ack_" + "purchase_token"
        private const val LEGACY_ACK_TOKENS_KEY = "subscription_ack_" + "purchase_tokens"

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
