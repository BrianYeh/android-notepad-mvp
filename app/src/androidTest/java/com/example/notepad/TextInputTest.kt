package com.example.notepad

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        composeRule.onNodeWithTag("set_reminder_button").assertIsDisplayed()
        composeRule.onNodeWithTag("share_text_note_button").assertIsDisplayed()
        composeRule.onNodeWithTag("export_text_note_button").assertIsDisplayed()
        composeRule.onNodeWithText("中文標題").assertIsDisplayed()
        composeRule.onNodeWithText("這是中文內容").assertIsDisplayed()
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
    }
}
