package com.example.notepad.debug

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DebugGoogleSyncAccess {
    const val isAvailable: Boolean = true
    private val entryState = MutableStateFlow(false)
    private var loaded = false

    fun observe(context: Context): StateFlow<Boolean> {
        ensureLoaded(context)
        return entryState
    }

    fun read(context: Context): Boolean {
        ensureLoaded(context)
        return entryState.value
    }

    fun write(context: Context, enabled: Boolean) {
        ensureLoaded(context)
        preferences(context)
            .edit()
            .putBoolean(GOOGLE_SYNC_ENTRY_ENABLED_KEY, enabled)
            .apply()
        entryState.value = enabled
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        entryState.value = preferences(context).getBoolean(GOOGLE_SYNC_ENTRY_ENABLED_KEY, false)
        loaded = true
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "debug_google_sync_access"
    private const val GOOGLE_SYNC_ENTRY_ENABLED_KEY = "google_sync_entry_enabled"
}
