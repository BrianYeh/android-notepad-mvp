package com.example.notepad.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.notepad.data.DEFAULT_FOLDER_ID
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NoteTypes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderNotificationTextTest {
    @Test
    fun hiddenReminderContentUsesGenericNotificationText() {
        val note = note(title = "Private title", textContent = "Private body")

        val text = reminderNotificationText(
            note = note,
            appName = "Just Notes",
            hideContent = true,
        )

        assertEquals("Just Notes", text.title)
        assertEquals("Reminder", text.body)
    }

    @Test
    fun visibleReminderContentUsesNoteTitleAndBody() {
        val note = note(title = "Visible title", textContent = "Visible body")

        val text = reminderNotificationText(
            note = note,
            appName = "Just Notes",
            hideContent = false,
        )

        assertEquals("Visible title", text.title)
        assertEquals("Visible body", text.body)
    }

    private fun note(
        title: String,
        textContent: String?,
        type: String = NoteTypes.TEXT,
    ): NoteEntity {
        return NoteEntity(
            id = 1L,
            folderId = DEFAULT_FOLDER_ID,
            type = type,
            title = title,
            textContent = textContent,
            drawingData = null,
            createdAt = 1L,
            updatedAt = 1L,
            reminderAt = 1L,
        )
    }
}
