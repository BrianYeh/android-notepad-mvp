package com.example.notepad.billing

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumEntitlementStoreTest {
    @Test
    fun constructionPurgesLegacyRawTokensAndScopedAcknowledgementKeys() {
        val preferences = FakeSharedPreferences(
            mutableMapOf(
                "subscription_status" to PremiumSubscriptionStatus.Active.name,
                "subscription_ack_purchase_token" to RAW_TOKEN,
                "subscription_ack_purchase_tokens" to setOf(RAW_TOKEN, "second-token"),
                "subscription_ack_product_id" to PremiumCatalog.PREFERRED_PRODUCT_ID,
                "subscription_ack_product_id_hash" to PremiumCatalog.PREFERRED_PRODUCT_ID,
                "subscription_ack_attempt_count_hash" to 2,
                "subscription_ack_next_attempt_at_hash" to 123L,
                "subscription_ack_last_error_hash" to "old error",
            ),
        )

        val store = PremiumEntitlementStore(preferences)

        assertEquals(PremiumSubscriptionStatus.Active, store.loadSubscription().status)
        assertFalse(preferences.all.containsKey("subscription_ack_purchase_token"))
        assertFalse(preferences.all.containsKey("subscription_ack_purchase_tokens"))
        assertFalse(preferences.all.containsKey("subscription_ack_product_id"))
        assertFalse(preferences.all.keys.any { key ->
            key.startsWith("subscription_ack_product_id_") ||
                key.startsWith("subscription_ack_attempt_count_") ||
                key.startsWith("subscription_ack_next_attempt_at_") ||
                key.startsWith("subscription_ack_last_error_")
        })
    }

    @Test
    fun savingBackendSnapshotNeverPersistsRawPurchaseToken() {
        val preferences = FakeSharedPreferences(
            mutableMapOf(
                "subscription_ack_purchase_token" to RAW_TOKEN,
                "subscription_ack_purchase_tokens" to setOf(RAW_TOKEN),
            ),
        )
        val store = PremiumEntitlementStore(preferences)

        store.saveSubscription(
            PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.Active,
                source = PremiumEntitlementSource.BackendVerified,
                productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
                basePlanId = "monthly",
                purchaseTokenHash = "backend-hmac-hash",
                expiryTime = 1_900_000_000_000L,
                acknowledgementStatus = PremiumAcknowledgementStatus.Acknowledged,
            ),
        )

        assertFalse(preferences.all.keys.any { it == "subscription_ack_purchase_token" })
        assertFalse(preferences.all.keys.any { it == "subscription_ack_purchase_tokens" })
        assertFalse(preferences.all.values.any { value ->
            value == RAW_TOKEN || (value is Set<*> && RAW_TOKEN in value)
        })
        assertTrue(preferences.all.values.contains("backend-hmac-hash"))
    }

    private class FakeSharedPreferences(
        private val values: MutableMap<String, Any?> = mutableMapOf(),
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defaultValue: String?): String? =
            values[key] as? String ?: defaultValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defaultValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? Set<String>)?.toMutableSet() ?: defaultValues
        override fun getInt(key: String?, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
        override fun getLong(key: String?, defaultValue: Long): Long = values[key] as? Long ?: defaultValue
        override fun getFloat(key: String?, defaultValue: Float): Float = values[key] as? Float ?: defaultValue
        override fun getBoolean(key: String?, defaultValue: Boolean): Boolean =
            values[key] as? Boolean ?: defaultValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(values)
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
    }

    private class Editor(
        private val target: MutableMap<String, Any?>,
    ) : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            requireNotNull(key)
            updates[key] = value
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            requireNotNull(key)
            updates[key] = values?.toSet()
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            requireNotNull(key)
            updates[key] = value
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            requireNotNull(key)
            updates[key] = value
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            requireNotNull(key)
            updates[key] = value
        }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            requireNotNull(key)
            updates[key] = value
        }
        override fun remove(key: String?): SharedPreferences.Editor = apply {
            requireNotNull(key)
            removals += key
        }
        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }
        override fun commit(): Boolean {
            if (clearRequested) target.clear()
            removals.forEach(target::remove)
            updates.forEach { (key, value) ->
                if (value == null) target.remove(key) else target[key] = value
            }
            return true
        }
        override fun apply() {
            commit()
        }
    }

    private companion object {
        const val RAW_TOKEN = "raw-sensitive-purchase-token"
    }
}
