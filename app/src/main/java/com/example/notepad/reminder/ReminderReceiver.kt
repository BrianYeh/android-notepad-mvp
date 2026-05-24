package com.example.notepad.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.notepad.MainActivity
import com.example.notepad.R
import com.example.notepad.data.NotepadDatabase
import com.example.notepad.data.PrivacyPreferences
import com.example.notepad.data.ReminderRepeat
import com.example.notepad.data.normalizedReminderRepeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1L)
        if (noteId <= 0L) return
        val firedReminderAt = intent.getLongExtra(ReminderScheduler.EXTRA_FIRED_REMINDER_AT, -1L)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ReminderScheduler.ACTION_SHOW_SNOOZED -> showReminderNotification(
                        context = context.applicationContext,
                        noteId = noteId,
                        firedReminderAt = firedReminderAt,
                        allowFutureReminder = true,
                        advanceRepeatingReminder = false,
                        snoozed = true,
                    )
                    ReminderScheduler.ACTION_SNOOZE -> snoozeReminder(context.applicationContext, noteId, firedReminderAt)
                    ReminderScheduler.ACTION_CLEAR -> clearReminder(context.applicationContext, noteId, firedReminderAt)
                    else -> showReminderNotification(context.applicationContext, noteId, firedReminderAt)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun snoozeReminder(context: Context, noteId: Long, firedReminderAt: Long) {
        val dao = NotepadDatabase.getInstance(context).notepadDao()
        val note = dao.getNote(noteId) ?: return
        if (note.isDeleted || !isCurrentReminderAction(note, firedReminderAt)) return

        val reminderRepeat = normalizedReminderRepeat(note.reminderRepeat)
        val snoozedUntil = System.currentTimeMillis() + ReminderScheduler.SNOOZE_MILLIS
        if (reminderRepeat == ReminderRepeat.None.code) {
            dao.setNoteReminder(noteId, snoozedUntil, ReminderRepeat.None.code, System.currentTimeMillis())
            dao.getNote(noteId)?.let { ReminderScheduler.schedule(context, it) }
        } else {
            dao.setReminderSnoozeUntil(noteId, snoozedUntil)
            dao.setActiveReminderFiredAt(noteId, null)
            ReminderScheduler.scheduleSnoozedReminder(context, noteId, snoozedUntil)
        }
        context.getSystemService(NotificationManager::class.java).cancel(noteId.requestCode())
    }

    private suspend fun clearReminder(context: Context, noteId: Long, firedReminderAt: Long) {
        val dao = NotepadDatabase.getInstance(context).notepadDao()
        val note = dao.getNote(noteId) ?: return
        if (note.isDeleted || !isCurrentReminderAction(note, firedReminderAt)) return

        dao.setNoteReminder(noteId, null, ReminderRepeat.None.code, System.currentTimeMillis())
        ReminderScheduler.cancel(context, noteId)
        context.getSystemService(NotificationManager::class.java).cancel(noteId.requestCode())
    }

    private suspend fun showReminderNotification(
        context: Context,
        noteId: Long,
        firedReminderAt: Long,
        allowFutureReminder: Boolean = false,
        advanceRepeatingReminder: Boolean = true,
        snoozed: Boolean = false,
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val note = NotepadDatabase.getInstance(context).notepadDao().getNote(noteId) ?: return
        val reminderAt = if (snoozed) note.reminderSnoozeUntil else note.reminderAt
        reminderAt ?: return
        val expectedReminderAt = if (firedReminderAt > 0L) firedReminderAt else reminderAt
        if (expectedReminderAt != reminderAt) return
        val now = System.currentTimeMillis()
        if (note.isDeleted || (!allowFutureReminder && reminderAt > now)) return

        val dao = NotepadDatabase.getInstance(context).notepadDao()
        val activeTokenSet = if (snoozed) {
            dao.setActiveSnoozedReminderFiredAtIfCurrent(
                noteId = noteId,
                expectedSnoozeUntil = expectedReminderAt,
                firedAt = expectedReminderAt,
            )
        } else {
            dao.setActiveReminderFiredAtIfCurrent(
                noteId = noteId,
                expectedReminderAt = expectedReminderAt,
                expectedReminderRepeat = normalizedReminderRepeat(note.reminderRepeat),
                firedAt = expectedReminderAt,
            )
        }
        if (activeTokenSet == 0) return
        ReminderScheduler.ensureNotificationChannel(context)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val openAppIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context,
            noteId.requestCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificationText = reminderNotificationText(
            note = note,
            appName = context.getString(R.string.app_name),
            hideContent = PrivacyPreferences.hideReminderNotificationContent(context),
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notificationText.title)
            .setContentText(notificationText.body)
            .setContentIntent(contentIntent)
            .addAction(
                R.mipmap.ic_launcher,
                context.getString(R.string.reminder_snooze_10_min),
                ReminderScheduler.snoozePendingIntent(context, noteId, expectedReminderAt),
            )
            .addAction(
                R.mipmap.ic_launcher,
                context.getString(R.string.reminder_clear),
                ReminderScheduler.clearPendingIntent(context, noteId, expectedReminderAt),
            )
            .setAutoCancel(true)
            .build()

        notificationManager.notify(noteId.requestCode(), notification)
        if (!advanceRepeatingReminder) return

        ReminderScheduler.nextRepeatTime(reminderAt, note.reminderRepeat, now)?.let { nextReminderAt ->
            val updated = dao.updateReminderOccurrenceIfCurrent(
                noteId = noteId,
                expectedReminderAt = expectedReminderAt,
                expectedReminderRepeat = normalizedReminderRepeat(note.reminderRepeat),
                expectedActiveReminderFiredAt = expectedReminderAt,
                reminderAt = nextReminderAt,
                reminderRepeat = normalizedReminderRepeat(note.reminderRepeat),
                updatedAt = System.currentTimeMillis(),
            )
            if (updated == 0) {
                notificationManager.cancel(noteId.requestCode())
                return
            }
            dao.getNote(noteId)?.let { ReminderScheduler.schedule(context, it) }
        }
    }
}

internal fun isCurrentReminderAction(note: com.example.notepad.data.NoteEntity, firedReminderAt: Long): Boolean {
    return firedReminderAt > 0L && note.activeReminderFiredAt == firedReminderAt
}
