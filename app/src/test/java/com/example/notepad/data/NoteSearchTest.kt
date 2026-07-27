package com.example.notepad.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSearchTest {
    @Test
    fun phraseMatchNormalizesWhitespaceCaseAndDiacritics() {
        val note = note(title = "Café planning", content = "Bring   the reusable cups")

        assertTrue(note.matchesNoteSearch("  CAFE   planning "))
        assertTrue(note.matchesNoteSearch("bring the reusable"))
        assertEquals(listOf(0..3), note.noteSearchMatch("cafe").titleRanges)
        assertEquals(listOf(0..19), note.noteSearchMatch("bring the reusable").contentRanges)
    }

    @Test
    fun tokenFallbackRequiresEveryTokenInAnyOrder() {
        val note = note(title = "Project Aurora", content = "Call Sam before Friday")

        assertTrue(note.matchesNoteSearch("friday aurora"))
        assertFalse(note.matchesNoteSearch("friday missing"))
        val match = note.noteSearchMatch("friday aurora")
        assertEquals(listOf(8..13), match.titleRanges)
        assertEquals(listOf(16..21), match.contentRanges)
    }

    @Test
    fun checklistItemsAreSearchableButDrawingPayloadIsNot() {
        val checklist = note(
            type = NoteTypes.CHECKLIST,
            content = ChecklistJson.encode(listOf(ChecklistItem(text = "Fresh basil"))),
        )
        val drawing = note(
            type = NoteTypes.DRAWING,
            content = "secret drawing payload",
            drawingData = "secret drawing payload",
        )

        assertTrue(checklist.matchesNoteSearch("basil"))
        assertFalse(drawing.matchesNoteSearch("secret"))
    }

    @Test
    fun displayRangesMapCollapsedAndAccentInsensitiveMatchesBackToOriginalText() {
        val value = "Meet José   after class"

        assertEquals(listOf(5..8), findSearchMatchRanges(value, "jose"))
        assertEquals(listOf(0..3, 18..22), findSearchMatchRanges(value, "class meet"))
        assertEquals(listOf(5..22), findSearchMatchRanges(value, "jose after class"))
    }

    @Test
    fun emojiMatchRangesPreserveCompleteSurrogatePairsInTitleAndBody() {
        val title = "Plan 😀 launch"
        val content = "Signal 🧠 insight"
        val note = note(title = title, content = content)

        val titleRange = note.noteSearchMatch("😀").titleRanges.single()
        val contentRange = note.noteSearchMatch("🧠 insight").contentRanges.single()

        assertEquals("😀", title.substring(titleRange.first, titleRange.last + 1))
        assertEquals(5..6, titleRange)
        assertEquals("🧠 insight", content.substring(contentRange.first, contentRange.last + 1))
        assertEquals(7..16, contentRange)
    }

    @Test
    fun supplementaryUnicodeDisplayRangeNeverSplitsItsUtf16Pair() {
        val supplementaryLetter = "𐐷"
        val value = "Read $supplementaryLetter now"

        val range = findSearchMatchRanges(value, supplementaryLetter).single()

        assertEquals(supplementaryLetter, value.substring(range.first, range.last + 1))
        assertEquals(5..6, range)
        assertTrue(Character.isHighSurrogate(value[range.first]))
        assertTrue(Character.isLowSurrogate(value[range.last]))
    }

    private fun note(
        title: String = "",
        content: String = "",
        type: String = NoteTypes.TEXT,
        drawingData: String? = null,
    ): NoteEntity {
        return NoteEntity(
            id = 1,
            folderId = DEFAULT_FOLDER_ID,
            type = type,
            title = title,
            textContent = content,
            drawingData = drawingData,
            createdAt = 1,
            updatedAt = 1,
        )
    }
}
