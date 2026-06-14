package com.example.notepad.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NotepadDatabase
import com.example.notepad.data.ReminderRepeat
import com.example.notepad.data.normalizedReminderRepeat
import java.util.Calendar

object ReminderScheduler {
    const val CHANNEL_ID = "note_reminders"
    const val EXTRA_NOTE_ID = "note_id"
    const val EXTRA_FIRED_REMINDER_AT = "fired_reminder_at"
    const val ACTION_SHOW_SNOOZED = "com.brianyeh.justnotes.reminder.SHOW_SNOOZED"
    const val ACTION_SNOOZE = "com.brianyeh.justnotes.reminder.SNOOZE"
    const val ACTION_CLEAR = "com.brianyeh.justnotes.reminder.CLEAR"
    const val SNOOZE_MILLIS = 10 * 60 * 1000L

    enum class NotificationDeliveryStatus {
        Ready,
        PermissionRequired,
        AppNotificationsDisabled,
        ReminderChannelDisabled,
    }

    fun schedule(context: Context, note: NoteEntity) {
        val reminderAt = note.reminderAt
        if (note.isDeleted || reminderAt == null || reminderAt <= System.currentTimeMillis()) {
            cancel(context, note.id)
            return
        }

        ensureNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminderAt,
            reminderPendingIntent(context, note.id, reminderAt),
        )
    }

    fun cancel(context: Context, noteId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(reminderPendingIntent(context, noteId))
        alarmManager.cancel(snoozedReminderPendingIntent(context, noteId))
    }

    fun cancelNotification(context: Context, noteId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(noteId.requestCode())
    }

    fun scheduleSnoozedReminder(context: Context, noteId: Long, reminderAt: Long) {
        ensureNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminderAt,
            snoozedReminderPendingIntent(context, noteId, reminderAt),
        )
    }

    fun snoozePendingIntent(context: Context, noteId: Long, firedReminderAt: Long): PendingIntent {
        return actionPendingIntent(context, noteId, ACTION_SNOOZE, firedReminderAt, 10_000)
    }

    fun clearPendingIntent(context: Context, noteId: Long, firedReminderAt: Long): PendingIntent {
        return actionPendingIntent(context, noteId, ACTION_CLEAR, firedReminderAt, 20_000)
    }

    fun nextRepeatTime(reminderAt: Long, reminderRepeat: String, now: Long = System.currentTimeMillis()): Long? {
        val field = when (normalizedReminderRepeat(reminderRepeat)) {
            ReminderRepeat.Daily.code -> Calendar.DAY_OF_YEAR
            ReminderRepeat.Weekly.code -> Calendar.WEEK_OF_YEAR
            ReminderRepeat.Monthly.code -> Calendar.MONTH
            else -> return null
        }
        return Calendar.getInstance().apply {
            timeInMillis = reminderAt
            while (timeInMillis <= now) {
                add(field, 1)
            }
        }.timeInMillis
    }

    fun nextScheduledReminderTime(note: NoteEntity, now: Long = System.currentTimeMillis()): Long? {
        val reminderAt = note.reminderAt ?: return null
        if (note.isDeleted) return null
        return if (reminderAt > now) {
            reminderAt
        } else {
            nextRepeatTime(reminderAt, note.reminderRepeat, now)
        }
    }

    suspend fun rescheduleFutureReminders(context: Context) {
        ensureNotificationChannel(context)
        val dao = NotepadDatabase.getInstance(context).notepadDao()
        val now = System.currentTimeMillis()
        dao.getReminderNotes().forEach { note ->
            val reminderAt = note.reminderAt ?: return@forEach
            val nextReminderAt = nextScheduledReminderTime(note, now) ?: return@forEach
            if (nextReminderAt == reminderAt) {
                schedule(context, note)
                note.reminderSnoozeUntil?.takeIf { it > now }?.let { snoozeUntil ->
                    scheduleSnoozedReminder(context, note.id, snoozeUntil)
                }
                return@forEach
            }

            dao.updateReminderOccurrence(
                noteId = note.id,
                reminderAt = nextReminderAt,
                reminderRepeat = normalizedReminderRepeat(note.reminderRepeat),
                updatedAt = System.currentTimeMillis(),
            )
            dao.getNote(note.id)?.let { schedule(context, it) }
            note.reminderSnoozeUntil?.takeIf { it > now }?.let { snoozeUntil ->
                scheduleSnoozedReminder(context, note.id, snoozeUntil)
            }
        }
    }

    suspend fun cancelFutureReminders(context: Context) {
        val notes = NotepadDatabase.getInstance(context)
            .notepadDao()
            .getReminderNotes()
        notes.forEach { note ->
            cancel(context, note.id)
            cancelNotification(context, note.id)
        }
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Note reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        notificationManager.createNotificationChannel(channel)
    }

    fun notificationDeliveryStatus(context: Context): NotificationDeliveryStatus {
        ensureNotificationChannel(context)
        val hasRuntimePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val channelImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID)
                ?.importance
        } else {
            null
        }
        return notificationDeliveryStatusFor(
            requiresRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            hasRuntimePermission = hasRuntimePermission,
            appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            channelImportance = channelImportance,
        )
    }

    fun notificationDeliveryStatusFor(
        requiresRuntimePermission: Boolean,
        hasRuntimePermission: Boolean,
        appNotificationsEnabled: Boolean,
        channelImportance: Int?,
    ): NotificationDeliveryStatus {
        return when {
            requiresRuntimePermission && !hasRuntimePermission -> NotificationDeliveryStatus.PermissionRequired
            !appNotificationsEnabled -> NotificationDeliveryStatus.AppNotificationsDisabled
            channelImportance == NotificationManager.IMPORTANCE_NONE -> NotificationDeliveryStatus.ReminderChannelDisabled
            else -> NotificationDeliveryStatus.Ready
        }
    }

    private fun reminderPendingIntent(context: Context, noteId: Long, firedReminderAt: Long? = null): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_NOTE_ID, noteId)
        firedReminderAt?.let { intent.putExtra(EXTRA_FIRED_REMINDER_AT, it) }
        return PendingIntent.getBroadcast(
            context,
            noteId.requestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun snoozedReminderPendingIntent(
        context: Context,
        noteId: Long,
        firedReminderAt: Long? = null,
    ): PendingIntent {
        return actionPendingIntent(context, noteId, ACTION_SHOW_SNOOZED, firedReminderAt, 30_000)
    }

    private fun actionPendingIntent(
        context: Context,
        noteId: Long,
        action: String,
        firedReminderAt: Long? = null,
        offset: Int,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_NOTE_ID, noteId)
        firedReminderAt?.let { intent.putExtra(EXTRA_FIRED_REMINDER_AT, it) }
        return PendingIntent.getBroadcast(
            context,
            noteId.requestCode() + offset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

internal fun Long.requestCode(): Int {
    return (this xor (this ushr 32)).toInt()
}
