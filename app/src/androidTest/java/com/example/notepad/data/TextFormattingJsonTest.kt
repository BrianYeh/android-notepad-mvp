package com.example.notepad.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextFormattingJsonTest {
    @Test
    fun encodeDecodePreservesSupportedRanges() {
        val ranges = listOf(
            TextFormatRange(start = 0, end = 5, type = TextFormatType.Bold),
            TextFormatRange(start = 6, end = 10, type = TextFormatType.Link, url = "https://example.com"),
        )

        val decoded = TextFormattingJson.decode(TextFormattingJson.encode(ranges), textLength = 20)

        assertEquals(ranges, decoded)
    }

    @Test
    fun sanitizeDropsInvalidRangesAndClampsToTextLength() {
        val ranges = listOf(
            TextFormatRange(start = 4, end = 4, type = TextFormatType.Bold),
            TextFormatRange(start = 2, end = 20, type = TextFormatType.Highlight),
        )

        val sanitized = TextFormattingJson.sanitize(ranges, textLength = 8)

        assertEquals(listOf(TextFormatRange(start = 2, end = 8, type = TextFormatType.Highlight)), sanitized)
        assertNull(TextFormattingJson.encode(emptyList()))
    }

    @Test
    fun toggleSubrangePreservesSameTypeFormattingAroundSelection() {
        val ranges = listOf(TextFormatRange(start = 0, end = 10, type = TextFormatType.Bold))

        val toggled = TextFormattingJson.toggle(
            ranges = ranges,
            range = 3 until 7,
            type = TextFormatType.Bold,
            textLength = 10,
        )

        assertEquals(
            listOf(
                TextFormatRange(start = 0, end = 3, type = TextFormatType.Bold),
                TextFormatRange(start = 7, end = 10, type = TextFormatType.Bold),
            ),
            toggled,
        )
    }

    @Test
    fun clearSubrangePreservesFormattingAroundSelection() {
        val ranges = listOf(
            TextFormatRange(start = 0, end = 10, type = TextFormatType.Highlight),
            TextFormatRange(start = 0, end = 10, type = TextFormatType.Italic),
        )

        val cleared = TextFormattingJson.clear(
            ranges = ranges,
            range = 4 until 6,
            textLength = 10,
        )

        assertEquals(
            listOf(
                TextFormatRange(start = 0, end = 4, type = TextFormatType.Highlight),
                TextFormatRange(start = 0, end = 4, type = TextFormatType.Italic),
                TextFormatRange(start = 6, end = 10, type = TextFormatType.Highlight),
                TextFormatRange(start = 6, end = 10, type = TextFormatType.Italic),
            ),
            cleared,
        )
    }

    @Test
    fun adjustTextFormattingAfterEditShiftsAndClampsRanges() {
        val ranges = listOf(
            TextFormatRange(start = 0, end = 5, type = TextFormatType.Bold),
            TextFormatRange(start = 6, end = 11, type = TextFormatType.Underline),
        )

        val adjusted = adjustTextFormattingAfterEdit(
            ranges = ranges,
            oldText = "hello world",
            newText = "hello brave world",
        )

        assertEquals(
            listOf(
                TextFormatRange(start = 0, end = 5, type = TextFormatType.Bold),
                TextFormatRange(start = 12, end = 17, type = TextFormatType.Underline),
            ),
            adjusted,
        )
    }
}
