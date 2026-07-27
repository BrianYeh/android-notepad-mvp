package com.example.notepad.data

import android.content.Context
import androidx.core.content.edit

object FirstRunPreferences {
    internal const val PREFERENCES_NAME = "first_run_experience"
    internal const val COMPLETED_KEY = "completed"

    fun hasCompleted(context: Context): Boolean {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(COMPLETED_KEY, false)
    }

    fun markCompleted(context: Context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) {
                putBoolean(COMPLETED_KEY, true)
            }
    }
}
