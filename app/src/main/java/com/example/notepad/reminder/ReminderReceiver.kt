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
import com.example.notepad.data.NoteTypes
import com.example.notepad.data.NotepadDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1L)
        if (noteId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                showReminderNotification(context.applicationContext, noteId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun showReminderNotification(context: Context, noteId: Long) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val note = NotepadDatabase.getInstance(context).notepadDao().getNote(noteId) ?: return
        val reminderAt = note.reminderAt ?: return
        if (note.isDeleted || reminderAt > System.currentTimeMillis()) return

        ReminderScheduler.ensureNotificationChannel(context)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val openAppIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context,
            noteId.requestCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = note.title.ifBlank { context.getString(R.string.app_name) }
        val body = when (note.type) {
            NoteTypes.DRAWING -> "Drawing note reminder"
            else -> note.textContent.orEmpty().ifBlank { "Note reminder" }
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(noteId.requestCode(), notification)
    }
}
