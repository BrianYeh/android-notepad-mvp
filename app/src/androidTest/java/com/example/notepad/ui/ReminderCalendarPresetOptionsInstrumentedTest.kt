package com.example.notepad.ui

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCalendarPresetOptionsInstrumentedTest {
    @Test
    fun todayNearMidnightReturnsNoCrossDayFallback() {
        val nowMillis = timeOnDay(hour = 23, minute = 30)
        val dayStart = startOfDayMillisForTest(nowMillis)

        val options = calendarReminderPresetOptions(dayStart, nowMillis, EnglishText)

        assertTrue(options.isEmpty())
    }

    @Test
    fun todayLateEveningReturnsSameDayNextHourFallback() {
        val nowMillis = timeOnDay(hour = 22, minute = 30)
        val dayStart = startOfDayMillisForTest(nowMillis)

        val options = calendarReminderPresetOptions(dayStart, nowMillis, EnglishText)

        assertEquals(listOf("calendar_preset_next_hour"), options.map { it.tag })
        assertEquals(timeOnDay(hour = 23, minute = 0), options.single().reminderAt)
        assertEquals(dayStart, startOfDayMillisForTest(options.single().reminderAt))
    }

    @Test
    fun todayNearMidnightCannotAddEmptyPresetReminder() {
        val nowMillis = timeOnDay(hour = 23, minute = 30)
        val dayStart = startOfDayMillisForTest(nowMillis)

        assertEquals(false, calendarCanAddReminderOnDay(dayStart, nowMillis, EnglishText))
    }

    @Test
    fun futureDayCanAddPresetReminder() {
        val nowMillis = timeOnDay(hour = 23, minute = 30)
        val tomorrowStart = Calendar.getInstance().apply {
            timeInMillis = startOfDayMillisForTest(nowMillis)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        assertEquals(true, calendarCanAddReminderOnDay(tomorrowStart, nowMillis, EnglishText))
    }

    private fun timeOnDay(hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 9, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun startOfDayMillisForTest(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
