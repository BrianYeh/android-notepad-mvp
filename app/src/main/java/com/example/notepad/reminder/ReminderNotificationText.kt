package com.example.notepad.reminder

import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NoteTypes
import com.example.notepad.data.ChecklistJson

data class ReminderNotificationText(
    val title: String,
    val body: String,
)

fun reminderNotificationText(
    note: NoteEntity,
    appName: String,
    hideContent: Boolean,
): ReminderNotificationText {
    if (hideContent) {
        return ReminderNotificationText(
            title = appName,
            body = "Reminder",
        )
    }

    val title = note.title.ifBlank { appName }
    val body = when (note.type) {
        NoteTypes.DRAWING -> "Drawing note reminder"
        NoteTypes.CHECKLIST -> ChecklistJson.plainText(note.textContent).ifBlank { "Checklist reminder" }
        else -> note.textContent.orEmpty().ifBlank { "Note reminder" }
    }
    return ReminderNotificationText(title = title, body = body)
}
