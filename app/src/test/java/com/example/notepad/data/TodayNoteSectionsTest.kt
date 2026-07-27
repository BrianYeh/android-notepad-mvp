package com.example.notepad.data

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayNoteSectionsTest {
    @Test
    fun sectionsFollowPriorityAndDeduplicateNotes() {
        val zone = ZoneId.of("UTC")
        val now = millis(2026, 7, 27, 12, 0, zone)
        val overduePinned = note(1, reminderAt = millis(2026, 7, 26, 9, 0, zone), pinned = true, updatedAt = 50)
        val todayPinned = note(2, reminderAt = millis(2026, 7, 27, 15, 0, zone), pinned = true, updatedAt = 40)
        val pinned = note(3, pinned = true, updatedAt = 30)
        val recent = note(4, updatedAt = 20)

        val sections = buildTodayNoteSections(
            notes = listOf(recent, pinned, todayPinned, overduePinned),
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(listOf(1L), sections.overdue.map(NoteEntity::id))
        assertEquals(listOf(2L), sections.dueToday.map(NoteEntity::id))
        assertEquals(listOf(3L), sections.pinned.map(NoteEntity::id))
        assertEquals(listOf(4L), sections.recent.map(NoteEntity::id))
    }

    @Test
    fun localDayBoundaryUsesZoneAndExcludesDeletedNotes() {
        val zone = ZoneId.of("America/New_York")
        val now = millis(2026, 3, 8, 12, 0, zone)
        val beforeMidnight = note(1, reminderAt = millis(2026, 3, 7, 23, 59, zone))
        val today = note(2, reminderAt = millis(2026, 3, 8, 0, 0, zone))
        val tomorrow = note(3, reminderAt = millis(2026, 3, 9, 0, 0, zone))
        val deleted = note(4, reminderAt = millis(2026, 3, 8, 8, 0, zone), deleted = true)

        val sections = buildTodayNoteSections(
            notes = listOf(beforeMidnight, today, tomorrow, deleted),
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(listOf(1L), sections.overdue.map(NoteEntity::id))
        assertEquals(listOf(2L), sections.dueToday.map(NoteEntity::id))
        assertTrue(sections.recent.map(NoteEntity::id).contains(3L))
        assertFalseContains(sections, 4L)
    }

    @Test
    fun recurringReminderUsesFiredOccurrenceUntilAttentionIsCleared() {
        val zone = ZoneId.of("UTC")
        val now = millis(2026, 7, 27, 12, 0, zone)
        val firedToday = millis(2026, 7, 27, 9, 0, zone)
        val nextOccurrence = millis(2026, 7, 28, 9, 0, zone)
        val recurring = note(
            id = 10,
            reminderAt = nextOccurrence,
            activeReminderFiredAt = firedToday,
        )

        val sections = buildTodayNoteSections(listOf(recurring), now, zone)

        assertEquals(listOf(10L), sections.dueToday.map(NoteEntity::id))
        assertTrue(sections.recent.isEmpty())
    }

    @Test
    fun recurringSnoozeUsesSnoozeTimeBeforeAndAfterItFires() {
        val zone = ZoneId.of("UTC")
        val now = millis(2026, 7, 27, 12, 0, zone)
        val snoozeToday = millis(2026, 7, 27, 14, 0, zone)
        val firedSnoozeToday = millis(2026, 7, 27, 10, 0, zone)
        val nextOccurrence = millis(2026, 7, 28, 9, 0, zone)
        val beforeFiring = note(
            id = 11,
            reminderAt = nextOccurrence,
            reminderSnoozeUntil = snoozeToday,
        )
        val afterFiring = note(
            id = 12,
            reminderAt = nextOccurrence,
            reminderSnoozeUntil = snoozeToday,
            activeReminderFiredAt = snoozeToday,
        )
        val firedAndStillCurrent = note(
            id = 14,
            reminderAt = nextOccurrence,
            reminderSnoozeUntil = firedSnoozeToday,
            activeReminderFiredAt = firedSnoozeToday,
        )

        val sections = buildTodayNoteSections(
            listOf(beforeFiring, afterFiring, firedAndStillCurrent),
            now,
            zone,
        )

        assertEquals(listOf(14L, 11L, 12L), sections.dueToday.map(NoteEntity::id))
    }

    @Test
    fun staleSnoozeDoesNotOverrideNewerActiveOrDueRecurringOccurrence() {
        val zone = ZoneId.of("UTC")
        val now = millis(2026, 7, 27, 12, 0, zone)
        val staleSnooze = millis(2026, 7, 26, 10, 0, zone)
        val activeToday = millis(2026, 7, 27, 9, 0, zone)
        val dueToday = millis(2026, 7, 27, 10, 0, zone)
        val nextOccurrence = millis(2026, 7, 28, 9, 0, zone)
        val newerActiveOccurrence = note(
            id = 15,
            reminderAt = nextOccurrence,
            reminderSnoozeUntil = staleSnooze,
            activeReminderFiredAt = activeToday,
        )
        val newerDueOccurrence = note(
            id = 16,
            reminderAt = dueToday,
            reminderSnoozeUntil = staleSnooze,
        )

        val sections = buildTodayNoteSections(
            listOf(newerActiveOccurrence, newerDueOccurrence),
            now,
            zone,
        )

        assertTrue(sections.overdue.isEmpty())
        assertEquals(listOf(15L, 16L), sections.dueToday.map(NoteEntity::id))
    }

    @Test
    fun snoozeAcrossMidnightMovesAttentionToTheNextLocalDay() {
        val zone = ZoneId.of("America/New_York")
        val todayNow = millis(2026, 11, 1, 21, 0, zone)
        val tomorrowNow = millis(2026, 11, 2, 9, 0, zone)
        val snoozeTomorrow = millis(2026, 11, 2, 8, 0, zone)
        val note = note(
            id = 13,
            reminderAt = millis(2026, 11, 8, 8, 0, zone),
            reminderSnoozeUntil = snoozeTomorrow,
        )

        val todaySections = buildTodayNoteSections(listOf(note), todayNow, zone)
        val tomorrowSections = buildTodayNoteSections(listOf(note), tomorrowNow, zone)

        assertTrue(todaySections.dueToday.isEmpty())
        assertEquals(listOf(13L), tomorrowSections.dueToday.map(NoteEntity::id))
    }

    private fun assertFalseContains(sections: TodayNoteSections, id: Long) {
        val ids = sections.overdue + sections.dueToday + sections.pinned + sections.recent
        assertTrue(ids.none { note -> note.id == id })
    }

    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: ZoneId,
    ): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun note(
        id: Long,
        reminderAt: Long? = null,
        pinned: Boolean = false,
        updatedAt: Long = id,
        deleted: Boolean = false,
        reminderSnoozeUntil: Long? = null,
        activeReminderFiredAt: Long? = null,
    ): NoteEntity {
        return NoteEntity(
            id = id,
            folderId = DEFAULT_FOLDER_ID,
            type = NoteTypes.TEXT,
            title = "Note $id",
            textContent = "",
            drawingData = null,
            createdAt = id,
            updatedAt = updatedAt,
            isDeleted = deleted,
            reminderAt = reminderAt,
            isPinned = pinned,
            reminderSnoozeUntil = reminderSnoozeUntil,
            activeReminderFiredAt = activeReminderFiredAt,
        )
    }
}
