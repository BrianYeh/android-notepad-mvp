package com.example.notepad.data

import android.content.Context

object PrivacyPreferences {
    const val PREFERENCES_NAME = "ui_settings"
    const val HIDE_REMINDER_NOTIFICATION_CONTENT_KEY = "hide_reminder_notification_content"
    const val HIDE_REMINDER_NOTIFICATION_CONTENT_DEFAULT = true

    fun hideReminderNotificationContent(context: Context): Boolean {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(
                HIDE_REMINDER_NOTIFICATION_CONTENT_KEY,
                HIDE_REMINDER_NOTIFICATION_CONTENT_DEFAULT,
            )
    }
}
