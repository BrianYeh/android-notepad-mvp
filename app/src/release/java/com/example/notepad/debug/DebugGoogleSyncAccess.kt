package com.example.notepad.debug

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DebugGoogleSyncAccess {
    const val isAvailable: Boolean = false
    private val entryState = MutableStateFlow(false)

    fun observe(context: Context): StateFlow<Boolean> {
        return entryState
    }

    fun read(context: Context): Boolean {
        return false
    }

    fun write(context: Context, enabled: Boolean) = Unit
}
