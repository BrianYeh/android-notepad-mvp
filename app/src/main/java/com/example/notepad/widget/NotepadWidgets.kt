package com.example.notepad.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.notepad.MainActivity
import com.example.notepad.R
import com.example.notepad.data.PrivacyPreferences

object NotepadWidgets {
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, NoteListWidgetProvider::class.java)
        val widgetIds = manager.getAppWidgetIds(component)
        if (widgetIds.isEmpty()) return
        manager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_note_list)
        NoteListWidgetProvider.updateWidgets(appContext, manager, widgetIds)
    }

    internal fun buildWidgetViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.note_list_widget)
        views.setTextViewText(
            R.id.widget_empty,
            if (PrivacyPreferences.requireDeviceUnlock(context)) {
                context.getString(R.string.widget_locked_notes)
            } else {
                context.getString(R.string.widget_empty_notes)
            },
        )
        val serviceIntent = Intent(context, NoteListWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_note_list, serviceIntent)
        views.setEmptyView(R.id.widget_note_list, R.id.widget_empty)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
        }
        views.setOnClickPendingIntent(
            R.id.widget_header,
            pendingActivity(context, appWidgetId * 10 + 1, launchIntent),
        )

        val newNoteIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_WIDGET_NEW_TEXT_NOTE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(
            R.id.widget_new_note,
            pendingActivity(context, appWidgetId * 10 + 2, newNoteIntent),
        )

        val openNoteTemplate = Intent(context, MainActivity::class.java).apply {
            action = ACTION_WIDGET_OPEN_NOTE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setPendingIntentTemplate(
            R.id.widget_note_list,
            pendingActivity(context, appWidgetId * 10 + 3, openNoteTemplate, mutable = true),
        )
        return views
    }

    internal fun pendingActivity(
        context: Context,
        requestCode: Int,
        intent: Intent,
        mutable: Boolean = false,
    ): PendingIntent {
        val mutabilityFlag = when {
            mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> PendingIntent.FLAG_MUTABLE
            mutable -> 0
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> 0
            else -> PendingIntent.FLAG_IMMUTABLE
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }
}
