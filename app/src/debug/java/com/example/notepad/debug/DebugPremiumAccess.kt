package com.example.notepad.debug

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DebugPremiumAccess {
    const val isAvailable: Boolean = true
    private val overrideState = MutableStateFlow(false)
    @Volatile
    private var billingConnectionSuppressed = false
    private var loaded = false

    fun observe(context: Context): StateFlow<Boolean> {
        ensureLoaded(context)
        return overrideState
    }

    fun read(context: Context): Boolean {
        ensureLoaded(context)
        return overrideState.value
    }

    fun write(context: Context, enabled: Boolean) {
        ensureLoaded(context)
        preferences(context)
            .edit()
            .putBoolean(PREMIUM_OVERRIDE_ENABLED_KEY, enabled)
            .apply()
        overrideState.value = enabled
    }

    fun shouldConnectBilling(): Boolean {
        return !billingConnectionSuppressed
    }

    fun suppressBillingConnectionForTests(suppressed: Boolean) {
        billingConnectionSuppressed = suppressed
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        overrideState.value = preferences(context).getBoolean(PREMIUM_OVERRIDE_ENABLED_KEY, false)
        loaded = true
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "debug_premium_access"
    private const val PREMIUM_OVERRIDE_ENABLED_KEY = "premium_override_enabled"
}
