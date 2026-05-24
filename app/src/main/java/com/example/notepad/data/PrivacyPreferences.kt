package com.example.notepad.data

import android.content.Context

object PrivacyPreferences {
    const val PREFERENCES_NAME = "ui_settings"
    const val HIDE_REMINDER_NOTIFICATION_CONTENT_KEY = "hide_reminder_notification_content"
    const val HIDE_REMINDER_NOTIFICATION_CONTENT_DEFAULT = true
    const val REQUIRE_DEVICE_UNLOCK_KEY = "require_device_unlock"
    const val REQUIRE_DEVICE_UNLOCK_DEFAULT = false

    fun hideReminderNotificationContent(context: Context): Boolean {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(
                HIDE_REMINDER_NOTIFICATION_CONTENT_KEY,
                HIDE_REMINDER_NOTIFICATION_CONTENT_DEFAULT,
            )
    }

    fun requireDeviceUnlock(context: Context): Boolean {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(REQUIRE_DEVICE_UNLOCK_KEY, REQUIRE_DEVICE_UNLOCK_DEFAULT)
    }
}
