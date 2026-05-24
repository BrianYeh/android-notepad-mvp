package com.example.notepad

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.notepad.data.DrawingPoint
import com.example.notepad.data.DrawingStroke
import com.example.notepad.data.DrawingTools
import com.example.notepad.ui.cursorScrollTarget
import com.example.notepad.ui.drawingExportCanvasSizePx
import com.example.notepad.ui.drawingRequiredCanvasHeightPx
import com.example.notepad.ui.drawingViewportScale
import com.example.notepad.ui.findHighlightedLinkedText
import com.example.notepad.ui.findMatchScrollTarget
import com.example.notepad.ui.findInNoteMatches
import com.example.notepad.ui.formatFindMatchStatus
import com.example.notepad.ui.highlightRanges
import com.example.notepad.ui.nextFindMatchIndex
import com.example.notepad.ui.previousFindMatchIndex
import com.example.notepad.ui.webUrlAt
import com.example.notepad.ui.webUrlRanges
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextInputTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun verticalScrollValue(tag: String, useUnmergedTree: Boolean = false): Float {
        val range = composeRule.onNodeWithTag(tag, useUnmergedTree = useUnmergedTree)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        return range?.value?.invoke() ?: 0f
    }

    private fun openAddMenuItem(menuItemTag: String) {
        composeRule.onNodeWithTag("add_note_button").performClick()
        waitForTag(menuItemTag)
        composeRule.onNodeWithTag(menuItemTag).performClick()
    }

    @Test
    fun textNoteTitleAndContentAcceptInput() {
        val suffix = System.currentTimeMillis()
        val title = "中文標題 $suffix"
        val body = "這是中文內容 $suffix"

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_title")

        composeRule.onNodeWithTag("text_note_title")
            .assertIsDisplayed()
            .performTextInput(title)

        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .performTextInput(body)
        composeRule.onNodeWithTag("text_note_compact_title").assertTextContains(title)
        composeRule.onNodeWithTag("text_note_content").assertTextContains(body)

        composeRule.onNodeWithTag("text_note_compact_metadata").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_save_status").assertIsDisplayed()
        composeRule.onNodeWithTag("text_editor_accessory_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_metadata_button").performClick()
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
    }

    @Test
    fun newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode() {
        val suffix = System.currentTimeMillis()
        val title = "Friendly read title $suffix"
        val body = "Friendly read body $suffix"

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_title")
        composeRule.onNodeWithTag("text_note_title").assertIsDisplayed().performTextInput(title)
        composeRule.onNodeWithTag("text_note_content").assertIsDisplayed().performTextInput(body)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("text_note_title").fetchSemanticsNodes().isEmpty() &&
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
    fun existingTextNoteCanEnterEditModeFromTitleAndContent() {
        val suffix = System.currentTimeMillis()
        val title = "Tap edit title $suffix"
        val body = "Tap edit body $suffix"

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_title")
        composeRule.onNodeWithTag("text_note_title").performTextInput(title)
        composeRule.onNodeWithTag("text_note_content").performTextInput(body)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("text_note_title").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithTag("text_note_read_mode").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_read_title").performClick()
        composeRule.onNodeWithTag("text_note_title")
            .assertIsDisplayed()
            .assertTextContains(title)
            .assertIsFocused()
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("text_note_title").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithTag("text_note_read_mode").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_read_content").performClick()
        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .assertTextContains(body)
            .assertIsFocused()
    }

    @Test
    fun noteContentUrlsAreAnnotatedForDirectOpening() {
        val content = "Open https://example.com/path, then www.justnotes.app/help. Also justnotes.app."

        val urlRanges = content.webUrlRanges()

        assertEquals(3, urlRanges.size)
        assertEquals("https://example.com/path", urlRanges[0].normalizedUrl)
        assertEquals("www.justnotes.app/help", content.substring(urlRanges[1].range))
        assertEquals("https://www.justnotes.app/help", urlRanges[1].normalizedUrl)
        assertEquals("https://justnotes.app", urlRanges[2].normalizedUrl)
    }

    @Test
    fun noteContentUrlsIgnoreDottedEmailLocalParts() {
        val content = "Email user.name@example.com before visiting example.com"

        val urlRanges = content.webUrlRanges()

        assertEquals(1, urlRanges.size)
        assertEquals("example.com", content.substring(urlRanges[0].range))
        assertEquals("https://example.com", urlRanges[0].normalizedUrl)
    }

    @Test
    fun noteContentUrlsIgnoreCommonDottedFileAndPackageText() {
        val content = "Files README.md and app-debug.apk live near com.example.notepad, not example.com"

        val urlRanges = content.webUrlRanges()

        assertEquals(1, urlRanges.size)
        assertEquals("example.com", content.substring(urlRanges[0].range))
        assertEquals("https://example.com", urlRanges[0].normalizedUrl)
    }

    @Test
    fun noteContentUrlsPreserveBalancedParenthesesAndTrimSentencePunctuation() {
        val content = "Read https://en.wikipedia.org/wiki/Foo_(bar), then (https://example.com/path)."

        val urlRanges = content.webUrlRanges()

        assertEquals(2, urlRanges.size)
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", content.substring(urlRanges[0].range))
        assertEquals("https://example.com/path", content.substring(urlRanges[1].range))
    }

    @Test
    fun highlightedLinkedTextKeepsClickableUrlAnnotation() {
        val content = "Visit https://example.com/docs for docs"
        val urlStart = content.indexOf("https://")
        val annotated = findHighlightedLinkedText(
            value = content,
            query = "docs",
            activeMatchIndex = 0,
            matchColor = Color.Yellow,
            activeMatchColor = Color.Green,
            linkColor = Color.Blue,
        )

        assertEquals("https://example.com/docs", annotated.webUrlAt(urlStart))
        assertEquals("https://example.com/docs", annotated.webUrlAt(urlStart + 10))
        assertEquals(null, annotated.webUrlAt(content.indexOf("Visit")))
        assertEquals(null, annotated.webUrlAt(content.length))
    }

    @Test
    fun longPressEnablesMultiSelectAndDeletesSelectedNotes() {
        val suffix = System.currentTimeMillis()
        val firstTitle = "Multi select first $suffix"
        val secondTitle = "Multi select second $suffix"

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_title")
        composeRule.onNodeWithTag("text_note_title").performTextInput(firstTitle)
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(firstTitle).fetchSemanticsNodes().isNotEmpty()
        }

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_title")
        composeRule.onNodeWithTag("text_note_title").performTextInput(secondTitle)
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(secondTitle).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(firstTitle).performTouchInput {
            down(center)
            advanceEventTime(1_200)
            up()
        }
        composeRule.onNodeWithTag("selected_notes_count").assertTextEquals("1 selected")
        composeRule.onNodeWithText(secondTitle).performClick()
        composeRule.onNodeWithTag("selected_notes_count").assertTextEquals("2 selected")
        composeRule.onNodeWithTag("delete_selected_notes_button").performClick()
        composeRule.onNodeWithTag("confirm_dialog_confirm_button").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(firstTitle).fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText(secondTitle).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun backExitsMultiSelectModeWithoutOpeningOrDeletingNote() {
        val title = "Back exits multi select ${System.currentTimeMillis()}"

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_title")
        composeRule.onNodeWithTag("text_note_title").performTextInput(title)
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(title).performTouchInput { longClick() }
        composeRule.onNodeWithTag("selected_notes_count").assertTextEquals("1 selected")

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("selected_notes_count").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("add_note_button").assertIsDisplayed()
        composeRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun textEditorFocusWritingModeKeepsContentAndSaveStatusAvailable() {
        val suffix = System.currentTimeMillis()
        val title = "Focus writer title $suffix"
        val body = "Focus writer body $suffix"

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_title")
        composeRule.onNodeWithTag("text_note_title").performTextInput(title)
        composeRule.onNodeWithTag("text_note_content").performTextInput(body)
        composeRule.onNodeWithTag("text_note_compact_metadata").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_compact_title").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("text_note_title").assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithTag("text_note_content").performClick()
        composeRule.onNodeWithTag("text_note_compact_metadata").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_save_status").assertIsDisplayed()
        composeRule.onNodeWithTag("text_editor_accessory_bar").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("quick_insert_numbered_button").fetchSemanticsNodes().size,
        )
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("text_note_edit_metadata").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("quick_insert_checkbox_button").performClick()
        composeRule.onNodeWithTag("text_note_content").assertTextContains("- [ ]", substring = true)

        composeRule.onNodeWithTag("toggle_metadata_button").performClick()
        composeRule.onNodeWithTag("text_note_edit_metadata").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_updated_time").assertIsDisplayed()

        composeRule.onNodeWithTag("toggle_focus_writer_button").performClick()
        composeRule.onNodeWithTag("text_note_edit_metadata").assertIsDisplayed()
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("add_note_button").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithTag("text_note_read_content")
            .assertTextContains("Focus writer body", substring = true)
        composeRule.onNodeWithTag("text_note_read_content").assertTextContains("- [ ]", substring = true)
    }

    @Test
    fun textNoteEditsPersistAfterAppBackAndSystemBack() {
        val suffix = System.currentTimeMillis()
        val firstTitle = "Persist title $suffix"
        val firstContent = "Persist content before back $suffix"
        val secondTitle = "Persist updated title $suffix"
        val secondContent = "Persist updated content after system back $suffix"

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_title")
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

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_title")
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
    fun findInNoteNextScrollsReadViewportAndNavigatesEditMatches() {
        val suffix = System.currentTimeMillis()
        val title = "Find scroll title $suffix"
        val filler = (1..90).joinToString(separator = "\n") { index ->
            "filler line $index keeps the next match below the visible area"
        }
        val body = "needle top\n$filler\nneedle bottom"

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_title")
        composeRule.onNodeWithTag("text_note_title").performTextInput(title)
        composeRule.onNodeWithTag("text_note_content").performTextReplacement(body)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithTag("find_in_note_button").performClick()
        composeRule.onNodeWithTag("find_in_note_input").performTextInput("needle")
        composeRule.onNodeWithTag("find_match_status").assertTextEquals("1/2")
        val initialReadScroll = verticalScrollValue("text_note_read_scroll", useUnmergedTree = true)

        composeRule.onNodeWithTag("next_find_match_button").performClick()
        composeRule.onNodeWithTag("find_match_status").assertTextEquals("2/2")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            verticalScrollValue("text_note_read_scroll", useUnmergedTree = true) > initialReadScroll + 20f
        }

        composeRule.onNodeWithTag("edit_note_button").performClick()
        waitForTag("text_note_content_scroll")
        composeRule.onNodeWithTag("previous_find_match_button").performClick()
        composeRule.onNodeWithTag("find_match_status").assertTextEquals("1/2")

        composeRule.onNodeWithTag("next_find_match_button").performClick()
        composeRule.onNodeWithTag("find_match_status").assertTextEquals("2/2")
        composeRule.onNodeWithTag("previous_find_match_button").performClick()
        composeRule.onNodeWithTag("find_match_status").assertTextEquals("1/2")
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("add_note_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun appDoesNotExposeInAppLanguageSelector() {
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("language_button").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun settingsExposeEditorFontSizeChoices() {
        composeRule.onNodeWithTag("settings_button").performClick()

        composeRule.onNodeWithTag("font_size_Large")
            .assertIsDisplayed()
            .performClick()
    }

    @Test
    fun settingsExposeManualBackupControls() {
        composeRule.onNodeWithTag("settings_button").performClick()

        composeRule.onNodeWithTag("online_sync_title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("online_sync_target_status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("online_sync_note_count").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("online_sync_auto_checkbox").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("backup_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("restore_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("choose_sync_file_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("account_settings_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun premiumTabShowsSubscriptionPreview() {
        composeRule.onNodeWithTag("premium_tab").performClick()

        composeRule.onNodeWithTag("premium_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("annual_plan_option").assertIsDisplayed()
        composeRule.onNodeWithTag("monthly_plan_option").assertIsDisplayed()
        composeRule.onNodeWithTag("premium_subscribe_button").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("$480.00").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Start a 10-day free trial.").fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("notes_tab").performClick()
        composeRule.onNodeWithTag("add_note_button").assertIsDisplayed()
    }

    @Test
    fun addMenuShowsOcrFromImageAction() {
        composeRule.onNodeWithTag("add_note_button").performClick()
        waitForTag("ocr_from_image_menu_item")

        composeRule.onNodeWithTag("ocr_from_image_menu_item").assertIsDisplayed()
    }

    @Test
    fun drawingEditorShowsUpgradedDrawingTools() {
        openAddMenuItem("new_drawing_note_menu_item")
        waitForTag("exit_fullscreen_drawing_button")
        composeRule.onNodeWithTag("exit_fullscreen_drawing_button").performClick()
        waitForTag("drawing_undo_button")

        composeRule.onNodeWithTag("drawing_fullscreen_button").assertIsDisplayed()
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
    fun drawingEditorCanUseFullscreenCanvasMode() {
        openAddMenuItem("new_drawing_note_menu_item")
        waitForTag("fullscreen_drawing_mode")

        composeRule.onNodeWithTag("fullscreen_drawing_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("exit_fullscreen_drawing_button").assertIsDisplayed()
        composeRule.onNodeWithTag("drawing_tool_Pen").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("drawing_note_title").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("share_drawing_png_button").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag("exit_fullscreen_drawing_button").performClick()
        waitForTag("drawing_fullscreen_button")

        composeRule.onNodeWithTag("drawing_fullscreen_button").performClick()
        waitForTag("fullscreen_drawing_mode")

        composeRule.onNodeWithTag("fullscreen_drawing_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("exit_fullscreen_drawing_button").assertIsDisplayed()
    }

    @Test
    fun searchFindsTextNoteContent() {
        val suffix = System.currentTimeMillis()
        val title = "Searchable test note $suffix"
        val contentNeedle = "content-needle-$suffix"

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_title")

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
    fun mainScreenShowsKnowledgeHeaderAndScannableNoteTypeChip() {
        val suffix = System.currentTimeMillis()
        val title = "Header scan note $suffix"

        composeRule.onNodeWithTag("knowledge_header").assertIsDisplayed()
        composeRule.onNodeWithTag("note_result_count").assertIsDisplayed()

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_title")
        composeRule.onNodeWithTag("text_note_title").performTextInput(title)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("add_note_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithTag("note_type_chip", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun searchQuickFiltersAndRecentlyUpdatedWorkTogether() {
        val suffix = System.currentTimeMillis()
        val textTitle = "Alpha knowledge note $suffix"
        val textBody = "personal knowledge alpha body $suffix"
        val drawingTitle = "Alpha sketch $suffix"

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_title")
        composeRule.onNodeWithTag("text_note_title").performTextInput(textTitle)
        composeRule.onNodeWithTag("text_note_content").performTextInput(textBody)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(textTitle).fetchSemanticsNodes().isNotEmpty()
        }

        openAddMenuItem("new_drawing_note_menu_item")
        waitForTag("drawing_note_title")
        composeRule.onNodeWithTag("drawing_note_title").performTextInput(drawingTitle)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(drawingTitle).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("recently_updated_chip").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("note_search_input").performTextInput(suffix.toString())
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
    fun findMatchScrollTargetKeepsActiveMatchVisible() {
        assertEquals(
            676,
            findMatchScrollTarget(
                currentScroll = 0,
                viewportHeight = 400,
                matchTop = 950f,
                matchBottom = 980f,
                maxScroll = 2_000,
                viewportPaddingPx = 96f,
            ),
        )
        assertEquals(
            104,
            findMatchScrollTarget(
                currentScroll = 800,
                viewportHeight = 400,
                matchTop = 200f,
                matchBottom = 230f,
                maxScroll = 2_000,
                viewportPaddingPx = 96f,
            ),
        )
        assertEquals(
            null,
            findMatchScrollTarget(
                currentScroll = 500,
                viewportHeight = 400,
                matchTop = 650f,
                matchBottom = 680f,
                maxScroll = 2_000,
                viewportPaddingPx = 96f,
            ),
        )
    }

    @Test
    fun findMatchScrollTargetUsesSmallerPaddingForShortViewports() {
        assertEquals(
            270,
            findMatchScrollTarget(
                currentScroll = 0,
                viewportHeight = 180,
                matchTop = 360f,
                matchBottom = 390f,
                maxScroll = 1_000,
                viewportPaddingPx = 96f,
            ),
        )
    }

    @Test
    fun cursorScrollTargetKeepsTypingCaretVisibleNearViewportBottom() {
        assertEquals(
            656,
            cursorScrollTarget(
                currentScroll = 0,
                viewportHeight = 400,
                cursorTop = 980f,
                cursorBottom = 1_000f,
                maxScroll = 2_000,
                viewportBottomPaddingPx = 56f,
            ),
        )
        assertEquals(
            76,
            cursorScrollTarget(
                currentScroll = 500,
                viewportHeight = 400,
                cursorTop = 100f,
                cursorBottom = 120f,
                maxScroll = 2_000,
                viewportTopPaddingPx = 24f,
            ),
        )
        assertEquals(
            null,
            cursorScrollTarget(
                currentScroll = 500,
                viewportHeight = 400,
                cursorTop = 620f,
                cursorBottom = 650f,
                maxScroll = 2_000,
                viewportBottomPaddingPx = 56f,
            ),
        )
    }

    @Test
    fun drawingViewportScaleKeepsTallSavedStrokesVisibleWithoutResizingCanvas() {
        val tallStroke = DrawingStroke(
            points = listOf(DrawingPoint(40f, 20f), DrawingPoint(180f, 960f)),
            widthPx = 20f,
        )

        assertEquals(
            1_018f,
            drawingRequiredCanvasHeightPx(
                strokes = listOf(tallStroke),
                minimumHeightPx = 420f,
            ),
            0.001f,
        )
        assertEquals(
            0.619f,
            drawingViewportScale(
                strokes = listOf(tallStroke),
                measuredCanvasSize = IntSize(width = 360, height = 600),
            ),
            0.001f,
        )
    }

    @Test
    fun drawingExportCanvasSizePreservesTallSavedStrokeBottom() {
        val tallStroke = DrawingStroke(
            points = listOf(DrawingPoint(40f, 20f), DrawingPoint(180f, 960f)),
            widthPx = 20f,
        )

        assertEquals(
            IntSize(width = 360, height = 1_018),
            drawingExportCanvasSizePx(
                strokes = listOf(tallStroke),
                measuredCanvasSize = IntSize(width = 360, height = 600),
                fallbackWidthPx = 1080,
                fallbackHeightPx = 1440,
            ),
        )
    }

    @Test
    fun drawingBoundsIgnoreInvisibleEraserStrokes() {
        val penStroke = DrawingStroke(
            points = listOf(DrawingPoint(40f, 20f), DrawingPoint(180f, 300f)),
            widthPx = 20f,
        )
        val eraserStroke = DrawingStroke(
            points = listOf(DrawingPoint(40f, 20f), DrawingPoint(180f, 960f)),
            widthPx = 120f,
            tool = DrawingTools.ERASER,
        )

        assertEquals(
            358f,
            drawingRequiredCanvasHeightPx(
                strokes = listOf(penStroke, eraserStroke),
                minimumHeightPx = 320f,
            ),
            0.001f,
        )
        assertEquals(
            1f,
            drawingViewportScale(
                strokes = listOf(penStroke, eraserStroke),
                measuredCanvasSize = IntSize(width = 360, height = 600),
            ),
            0.001f,
        )
    }

    @Test
    fun drawingExportCanvasSizeCapsHugeRestoredCoordinates() {
        val hugeStroke = DrawingStroke(
            points = listOf(DrawingPoint(40f, 20f), DrawingPoint(500_000f, 900_000f)),
            widthPx = 20f,
        )

        assertEquals(
            IntSize(width = 4_096, height = 4_096),
            drawingExportCanvasSizePx(
                strokes = listOf(hugeStroke),
                measuredCanvasSize = IntSize(width = 360, height = 600),
                fallbackWidthPx = 1080,
                fallbackHeightPx = 1440,
                maxDimensionPx = 4_096,
            ),
        )
    }

    @Test
    fun drawingExportCanvasSizeRoundsFractionalBoundsUp() {
        val fractionalStroke = DrawingStroke(
            points = listOf(DrawingPoint(100.1f, 50.1f)),
            widthPx = 5f,
        )

        assertEquals(
            IntSize(width = 151, height = 101),
            drawingExportCanvasSizePx(
                strokes = listOf(fractionalStroke),
                measuredCanvasSize = IntSize(width = 100, height = 100),
                fallbackWidthPx = 100,
                fallbackHeightPx = 100,
            ),
        )
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
