package com.example.notepad.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NotepadDatabase

object ReminderScheduler {
    const val CHANNEL_ID = "note_reminders"
    const val EXTRA_NOTE_ID = "note_id"

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
            reminderPendingIntent(context, note.id),
        )
    }

    fun cancel(context: Context, noteId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(reminderPendingIntent(context, noteId))
    }

    suspend fun rescheduleFutureReminders(context: Context) {
        ensureNotificationChannel(context)
        val notes = NotepadDatabase.getInstance(context)
            .notepadDao()
            .getFutureReminderNotes(System.currentTimeMillis())
        notes.forEach { note -> schedule(context, note) }
    }

    suspend fun cancelFutureReminders(context: Context) {
        val notes = NotepadDatabase.getInstance(context)
            .notepadDao()
            .getFutureReminderNotes(System.currentTimeMillis())
        notes.forEach { note -> cancel(context, note.id) }
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

    private fun reminderPendingIntent(context: Context, noteId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_NOTE_ID, noteId)
        return PendingIntent.getBroadcast(
            context,
            noteId.requestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

internal fun Long.requestCode(): Int {
    return (this xor (this ushr 32)).toInt()
}
