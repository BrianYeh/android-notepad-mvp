package com.example.notepad.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class NoteListWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateWidgets(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
            widgetIds.forEach { widgetId ->
                manager.updateAppWidget(widgetId, NotepadWidgets.buildWidgetViews(context, widgetId))
            }
        }
    }
}
