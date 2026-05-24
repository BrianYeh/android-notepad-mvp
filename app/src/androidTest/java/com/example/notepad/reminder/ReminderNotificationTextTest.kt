package com.example.notepad.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.notepad.data.DEFAULT_FOLDER_ID
import com.example.notepad.data.ChecklistItem
import com.example.notepad.data.ChecklistJson
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NoteTypes
import com.example.notepad.data.ReminderRepeat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

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

    @Test
    fun visibleChecklistReminderFormatsChecklistItems() {
        val note = note(
            title = "Groceries",
            textContent = ChecklistJson.encode(
                listOf(
                    ChecklistItem(text = "Milk", checked = true),
                    ChecklistItem(text = "Eggs", checked = false),
                ),
            ),
            type = NoteTypes.CHECKLIST,
        )

        val text = reminderNotificationText(
            note = note,
            appName = "Just Notes",
            hideContent = false,
        )

        assertEquals("Groceries", text.title)
        assertEquals("[x] Milk\n[ ] Eggs", text.body)
    }

    @Test
    fun dailyReminderRepeatAdvancesToNextFutureTime() {
        val start = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 20, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 25, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expected = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 26, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(expected, ReminderScheduler.nextRepeatTime(start, ReminderRepeat.Daily.code, now))
    }

    @Test
    fun overdueRecurringReminderReturnsNextScheduledOccurrence() {
        val start = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 20, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 25, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expected = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 26, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val note = note(
            title = "Daily",
            textContent = "Check",
            reminderAt = start,
            reminderRepeat = ReminderRepeat.Daily.code,
        )

        assertEquals(expected, ReminderScheduler.nextScheduledReminderTime(note, now))
    }

    @Test
    fun overdueOneShotReminderDoesNotReschedule() {
        val note = note(
            title = "Once",
            textContent = "Check",
            reminderAt = 1_000L,
            reminderRepeat = ReminderRepeat.None.code,
        )

        assertEquals(null, ReminderScheduler.nextScheduledReminderTime(note, now = 2_000L))
    }

    @Test
    fun futureReminderKeepsExistingScheduledTime() {
        val note = note(
            title = "Future",
            textContent = "Check",
            reminderAt = 3_000L,
            reminderRepeat = ReminderRepeat.Weekly.code,
        )

        assertEquals(3_000L, ReminderScheduler.nextScheduledReminderTime(note, now = 2_000L))
    }

    @Test
    fun reminderActionRequiresCurrentFiredToken() {
        val note = note(
            title = "Current",
            textContent = "Check",
            activeReminderFiredAt = 3_000L,
        )

        assertEquals(true, isCurrentReminderAction(note, firedReminderAt = 3_000L))
        assertEquals(false, isCurrentReminderAction(note, firedReminderAt = 2_000L))
        assertEquals(false, isCurrentReminderAction(note, firedReminderAt = -1L))
    }

    private fun note(
        title: String,
        textContent: String?,
        type: String = NoteTypes.TEXT,
        reminderAt: Long = 1L,
        reminderRepeat: String = ReminderRepeat.None.code,
        activeReminderFiredAt: Long? = null,
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
            reminderAt = reminderAt,
            reminderRepeat = reminderRepeat,
            activeReminderFiredAt = activeReminderFiredAt,
        )
    }
}
