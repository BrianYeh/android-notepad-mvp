package com.example.notepad.debug

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DebugPremiumAccess {
    const val isAvailable: Boolean = false
    private val overrideState = MutableStateFlow(false)

    fun observe(context: Context): StateFlow<Boolean> {
        return overrideState
    }

    fun read(context: Context): Boolean {
        return false
    }

    fun write(context: Context, enabled: Boolean) = Unit
}
