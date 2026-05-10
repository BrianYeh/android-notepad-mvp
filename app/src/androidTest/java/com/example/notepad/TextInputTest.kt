package com.example.notepad

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.notepad.ui.findInNoteMatches
import com.example.notepad.ui.formatFindMatchStatus
import com.example.notepad.ui.highlightRanges
import com.example.notepad.ui.nextFindMatchIndex
import com.example.notepad.ui.previousFindMatchIndex
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextInputTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun textNoteTitleAndContentAcceptInput() {
        composeRule.onNodeWithTag("add_note_button").performClick()
        composeRule.onNodeWithTag("new_text_note_menu_item").performClick()

        composeRule.onNodeWithTag("text_note_title")
            .assertIsDisplayed()
            .performTextInput("中文標題")

        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .performTextInput("這是中文內容")

        composeRule.onNodeWithTag("text_note_save_status").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_updated_time").assertIsDisplayed()
        composeRule.onNodeWithTag("note_reminder_status").assertIsDisplayed()
        composeRule.onNodeWithTag("find_in_note_button").performClick()
        composeRule.onNodeWithTag("find_in_note_input")
            .assertIsDisplayed()
            .performTextInput("中文")
        composeRule.onNodeWithTag("find_match_status").assertTextEquals("1/1")
        composeRule.onNodeWithTag("next_find_match_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("find_match_status").assertTextEquals("1/1")
        composeRule.onNodeWithTag("previous_find_match_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("find_match_status").assertTextEquals("1/1")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("share_text_note_button").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("export_text_note_button").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("more_note_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("share_text_note_menu_item").assertIsDisplayed()
        composeRule.onNodeWithTag("export_text_note_menu_item").assertIsDisplayed()
        composeRule.onNodeWithTag("set_reminder_menu_item").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_pin_menu_item").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_text_note_menu_item").assertIsDisplayed()
        composeRule.onNodeWithText("中文標題").assertIsDisplayed()
        composeRule.onNodeWithText("這是中文內容").assertIsDisplayed()
    }

    @Test
    fun newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode() {
        val suffix = System.currentTimeMillis()
        val title = "Friendly read title $suffix"
        val body = "Friendly read body $suffix"

        composeRule.onNodeWithTag("add_note_button").performClick()
        composeRule.onNodeWithTag("new_text_note_menu_item").performClick()
        composeRule.onNodeWithTag("text_note_title").assertIsDisplayed().performTextInput(title)
        composeRule.onNodeWithTag("text_note_content").assertIsDisplayed().performTextInput(body)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithTag("text_note_read_mode").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_read_title").assertTextContains(title)
        composeRule.onNodeWithTag("text_note_read_content").assertTextContains(body)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("text_note_title").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag("edit_note_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("text_note_title").assertIsDisplayed().assertTextContains(title)
        composeRule.onNodeWithTag("text_note_content").assertIsDisplayed().assertTextContains(body)
    }

    @Test
    fun textNoteEditsPersistAfterAppBackAndSystemBack() {
        val suffix = System.currentTimeMillis()
        val firstTitle = "Persist title $suffix"
        val firstContent = "Persist content before back $suffix"
        val secondTitle = "Persist updated title $suffix"
        val secondContent = "Persist updated content after system back $suffix"

        composeRule.onNodeWithTag("add_note_button").performClick()
        composeRule.onNodeWithTag("new_text_note_menu_item").performClick()
        composeRule.onNodeWithTag("text_note_title").performTextInput(firstTitle)
        composeRule.onNodeWithTag("text_note_content").performTextInput(firstContent)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(firstTitle).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(firstTitle).performClick()
        composeRule.onNodeWithTag("text_note_read_mode").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_read_title").assertTextContains(firstTitle)
        composeRule.onNodeWithTag("text_note_read_content").assertTextContains(firstContent)
        composeRule.onNodeWithTag("edit_note_button").performClick()
        composeRule.onNodeWithTag("text_note_title").assertTextContains(firstTitle)
        composeRule.onNodeWithTag("text_note_content").assertTextContains(firstContent)

        composeRule.onNodeWithTag("text_note_title").performTextReplacement(secondTitle)
        composeRule.onNodeWithTag("text_note_content").performTextReplacement(secondContent)
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(secondTitle).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(secondTitle).performClick()
        composeRule.onNodeWithTag("text_note_read_mode").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_read_title").assertTextContains(secondTitle)
        composeRule.onNodeWithTag("text_note_read_content").assertTextContains(secondContent)
        composeRule.onNodeWithTag("edit_note_button").performClick()
        composeRule.onNodeWithTag("text_note_title").assertTextContains(secondTitle)
        composeRule.onNodeWithTag("text_note_content").assertTextContains(secondContent)
    }

    @Test
    fun findInNoteOpensFromReadModeAndEditMode() {
        val suffix = System.currentTimeMillis()
        val title = "Find flow title $suffix"
        val body = "banana alpha banana beta banana"

        composeRule.onNodeWithTag("add_note_button").performClick()
        composeRule.onNodeWithTag("new_text_note_menu_item").performClick()
        composeRule.onNodeWithTag("text_note_title").performTextInput(title)
        composeRule.onNodeWithTag("text_note_content").performTextInput(body)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithTag("find_in_note_button").performClick()
        composeRule.onNodeWithTag("find_in_note_input").assertIsDisplayed().performTextInput("banana")
        composeRule.onNodeWithTag("find_match_status").assertTextEquals("1/3")

        composeRule.onNodeWithTag("edit_note_button").performClick()
        composeRule.onNodeWithTag("find_in_note_input").assertIsDisplayed()
        composeRule.onNodeWithTag("next_find_match_button").performClick()
        composeRule.onNodeWithTag("find_match_status").assertTextEquals("2/3")
    }

    @Test
    fun userCanSwitchToTraditionalChinese() {
        composeRule.onNodeWithTag("language_button").performClick()
        composeRule.onNodeWithText("繁體中文").performClick()

        composeRule.onNodeWithText("本機記事").assertIsDisplayed()
        composeRule.onNodeWithText("全部記事").assertIsDisplayed()
    }

    @Test
    fun settingsExposeEditorFontSizeChoices() {
        composeRule.onNodeWithTag("settings_button").performClick()

        composeRule.onNodeWithTag("font_size_Large")
            .assertIsDisplayed()
            .performClick()
    }

    @Test
    fun addMenuShowsOcrFromImageAction() {
        composeRule.onNodeWithTag("add_note_button").performClick()

        composeRule.onNodeWithTag("ocr_from_image_menu_item").assertIsDisplayed()
    }

    @Test
    fun drawingEditorShowsUpgradedDrawingTools() {
        composeRule.onNodeWithTag("add_note_button").performClick()
        composeRule.onNodeWithTag("new_drawing_note_menu_item").performClick()

        composeRule.onNodeWithTag("drawing_undo_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("drawing_redo_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("drawing_tool_Pen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("drawing_tool_Eraser").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("drawing_brush_Thin").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("drawing_color_Red").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("drawing_tool_Eraser").performScrollTo().performClick()
        composeRule.onNodeWithTag("drawing_eraser_hint").assertIsDisplayed()
        composeRule.onNodeWithTag("share_drawing_png_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("export_drawing_png_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun searchFindsTextNoteContent() {
        val title = "Searchable test note"
        val contentNeedle = "content-needle-20260510"

        composeRule.onNodeWithTag("add_note_button").performClick()
        composeRule.onNodeWithTag("new_text_note_menu_item").performClick()

        composeRule.onNodeWithTag("text_note_title").performTextInput(title)
        composeRule.onNodeWithTag("text_note_content").performTextInput(contentNeedle)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("note_search_input")
            .performTextInput("missing-needle-20260510")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag("note_search_input")
            .performTextReplacement(contentNeedle)
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(contentNeedle).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun searchQuickFiltersAndRecentlyUpdatedWorkTogether() {
        val textTitle = "Alpha knowledge note"
        val textBody = "personal knowledge alpha body"
        val drawingTitle = "Alpha sketch"

        composeRule.onNodeWithTag("add_note_button").performClick()
        composeRule.onNodeWithTag("new_text_note_menu_item").performClick()
        composeRule.onNodeWithTag("text_note_title").performTextInput(textTitle)
        composeRule.onNodeWithTag("text_note_content").performTextInput(textBody)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(textTitle).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("add_note_button").performClick()
        composeRule.onNodeWithTag("new_drawing_note_menu_item").performClick()
        composeRule.onNodeWithTag("drawing_note_title").performTextInput(drawingTitle)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(drawingTitle).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("recently_updated_chip").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("note_search_input").performTextInput("alpha")
        composeRule.onNodeWithTag("quick_filter_Text").performScrollTo().performClick()
        composeRule.onNodeWithText(textTitle).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(drawingTitle).fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag("quick_filter_Drawing").performScrollTo().performClick()
        composeRule.onNodeWithText(drawingTitle).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(textTitle).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun highlightRangesAreCaseInsensitive() {
        assertEquals(listOf(0..4, 11..15), "Alpha note alpha".highlightRanges("alpha"))
        assertEquals(listOf(0..1, 4..5), "中文內容中文".highlightRanges("中文"))
    }

    @Test
    fun findInNoteMatchesAreCaseInsensitiveAndSupportChinese() {
        assertEquals(listOf(0..4, 11..15), findInNoteMatches("Alpha note alpha", "ALPHA"))
        assertEquals(listOf(0..1, 4..5), findInNoteMatches("中文內容中文", "中文"))
    }

    @Test
    fun findInNoteNavigationWrapsAround() {
        assertEquals(0, nextFindMatchIndex(4, 5))
        assertEquals(4, previousFindMatchIndex(0, 5))
        assertEquals(2, nextFindMatchIndex(1, 5))
        assertEquals(1, previousFindMatchIndex(2, 5))
    }

    @Test
    fun findInNoteNoMatchesAndEmptyQueryAreHandled() {
        assertEquals(emptyList<IntRange>(), findInNoteMatches("Alpha note", "missing"))
        assertEquals(emptyList<IntRange>(), findInNoteMatches("Alpha note", ""))
        assertEquals(-1, nextFindMatchIndex(0, 0))
        assertEquals(-1, previousFindMatchIndex(0, 0))
        assertEquals("No matches", formatFindMatchStatus(0, 0, "No matches"))
    }
}
