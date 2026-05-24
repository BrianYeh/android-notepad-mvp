package com.example.notepad.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.notepad.R
import com.example.notepad.data.ChecklistJson
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NoteTypes
import com.example.notepad.data.NotepadDatabase
import com.example.notepad.data.PrivacyPreferences
import kotlinx.coroutines.runBlocking

class NoteListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NoteListRemoteViewsFactory(applicationContext)
    }
}

private class NoteListRemoteViewsFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {
    private var notes: List<NoteEntity> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        if (PrivacyPreferences.requireDeviceUnlock(context)) {
            notes = emptyList()
            return
        }
        notes = runBlocking {
            NotepadDatabase.getInstance(context).notepadDao().getWidgetNotes()
        }
    }

    override fun onDestroy() {
        notes = emptyList()
    }

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews {
        val note = notes.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.note_list_widget_row)
        val views = RemoteViews(context.packageName, R.layout.note_list_widget_row)
        views.setTextViewText(R.id.widget_note_title, noteWidgetTitle(note))
        views.setTextViewText(R.id.widget_note_preview, noteWidgetPreview(note))
        views.setViewVisibility(R.id.widget_pin_indicator, if (note.isPinned) View.VISIBLE else View.GONE)

        val fillInIntent = Intent().apply {
            putExtra(EXTRA_WIDGET_NOTE_ID, note.id)
        }
        views.setOnClickFillInIntent(R.id.widget_note_row, fillInIntent)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = notes.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

private fun noteWidgetTitle(note: NoteEntity): String {
    return note.title.ifBlank { "Untitled" }
}

private fun noteWidgetPreview(note: NoteEntity): String {
    return when (note.type) {
        NoteTypes.DRAWING -> "Drawing note"
        NoteTypes.CHECKLIST -> ChecklistJson.preview(note.textContent)
        else -> note.textContent.orEmpty()
    }.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}
