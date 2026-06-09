package com.example.notepad

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.notepad.data.DrawingPoint
import com.example.notepad.data.DrawingStroke
import com.example.notepad.data.DrawingTools
import com.example.notepad.data.DEFAULT_FOLDER_ID
import com.example.notepad.data.NotepadDatabase
import com.example.notepad.data.NotepadRepository
import com.example.notepad.data.ReminderRepeat
import com.example.notepad.data.TextFormatRange
import com.example.notepad.data.TextFormatType
import com.example.notepad.data.TextFormattingJson
import com.example.notepad.debug.DebugPremiumAccess
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
import com.example.notepad.debug.DebugSaveFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class TextInputTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetDebugPremiumOverride() {
        DebugPremiumAccess.write(composeRule.activity, false)
        DebugSaveFailure.clear()
    }

    @After
    fun clearDebugPremiumOverride() {
        DebugPremiumAccess.write(composeRule.activity, false)
        DebugSaveFailure.clear()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun showTextNoteMetadata() {
        if (composeRule.onAllNodesWithTag("text_note_title").fetchSemanticsNodes().isEmpty()) {
            waitForTag("toggle_metadata_button")
            composeRule.onNodeWithTag("toggle_metadata_button").performClick()
        }
        waitForTag("text_note_title")
    }

    private fun verticalScrollValue(tag: String, useUnmergedTree: Boolean = false): Float {
        val range = composeRule.onNodeWithTag(tag, useUnmergedTree = useUnmergedTree)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        return range?.value?.invoke() ?: 0f
    }

    private fun openAddMenuItem(menuItemTag: String) {
        waitForTag("add_note_button")
        composeRule.onNodeWithTag("add_note_button").performClick()
        waitForTag(menuItemTag)
        composeRule.onNodeWithTag(menuItemTag).performClick()
    }

    private fun openSearchPanel() {
        if (composeRule.onAllNodesWithTag("note_search_input").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("search_tab").performClick()
            waitForTag("note_search_input")
        }
    }

    private fun openFilterPanel() {
        if (composeRule.onAllNodesWithTag("recently_updated_chip").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("filter_panel_toggle").performClick()
            waitForTag("recently_updated_chip")
        }
    }

    private fun debugPremiumSwitchState(): ToggleableState? {
        return composeRule.onNodeWithTag("debug_premium_switch")
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ToggleableState)
    }

    private fun enableDebugPremiumAccess() {
        DebugPremiumAccess.write(composeRule.activity, true)
        composeRule.waitForIdle()
    }

    private fun tagCount(tag: String): Int {
        return composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size
    }

    private fun assertTagAbsent(tag: String) {
        assertEquals(0, tagCount(tag))
    }

    private fun assertExactTextAbsent(value: String) {
        assertEquals(
            "Unexpected raw label '$value' is visible",
            0,
            composeRule.onAllNodesWithText(
                text = value,
                substring = false,
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size,
        )
    }

    private fun assertContentDescriptionAbsent(value: String) {
        assertEquals(
            "Unexpected content description '$value' is exposed",
            0,
            composeRule.onAllNodes(
                matcher = hasContentDescription(value, substring = false),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size,
        )
    }

    private fun assertTaggedContentDescription(tag: String, expected: String) {
        val descriptions = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            .orEmpty()
        assertTrue(
            "$tag content descriptions were $descriptions, expected $expected",
            descriptions.contains(expected),
        )
    }

    private fun assertTaggedTouchTargetAtLeast48Dp(tag: String) {
        val minimumPx = with(composeRule.density) { 48.dp.roundToPx() }
        val size = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .size
        assertTrue(
            "$tag touch target was ${size.width}x${size.height}px, expected at least ${minimumPx}px",
            size.width >= minimumPx && size.height >= minimumPx,
        )
    }

    private fun assertIconControl(tag: String, contentDescription: String, scrollTo: Boolean = false) {
        if (scrollTo) {
            composeRule.onNodeWithTag(tag).performScrollTo()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        assertTaggedContentDescription(tag, contentDescription)
        assertTaggedTouchTargetAtLeast48Dp(tag)
    }

    private fun resetFoldersToDefault() {
        runBlocking {
            withContext(Dispatchers.IO) {
                val dao = NotepadDatabase.getInstance(composeRule.activity).notepadDao()
                val repository = NotepadRepository(dao)
                repository.ensureDefaultFolder()
                dao.getAllFolders()
                    .filter { folder -> folder.id != DEFAULT_FOLDER_ID && !folder.isDeleted }
                    .forEach { folder -> repository.deleteFolder(folder.id) }
            }
        }
    }

    private fun createFolder(name: String): Long {
        return runBlocking {
            withContext(Dispatchers.IO) {
                NotepadRepository(NotepadDatabase.getInstance(composeRule.activity).notepadDao())
                    .createFolder(name)
            }
        }
    }

    private fun createTextNote(
        title: String,
        body: String,
        folderId: Long = DEFAULT_FOLDER_ID,
        reminderAt: Long? = null,
        reminderRepeat: String = ReminderRepeat.None.code,
    ): Long {
        return runBlocking {
            withContext(Dispatchers.IO) {
                val repository = NotepadRepository(NotepadDatabase.getInstance(composeRule.activity).notepadDao())
                repository.ensureDefaultFolder()
                val noteId = repository.createTextNote(folderId)
                repository.saveTextNote(noteId, title, body)
                if (reminderAt != null) {
                    repository.setNoteReminder(noteId, reminderAt, reminderRepeat)
                }
                noteId
            }
        }
    }

    private fun noteIds(): Set<Long> {
        return runBlocking {
            withContext(Dispatchers.IO) {
                NotepadDatabase.getInstance(composeRule.activity)
                    .notepadDao()
                    .getAllNotes()
                    .map { it.id }
                    .toSet()
            }
        }
    }

    private fun noteTombstoneCount(): Int {
        return runBlocking {
            withContext(Dispatchers.IO) {
                NotepadDatabase.getInstance(composeRule.activity)
                    .notepadDao()
                    .getNoteTombstones()
                    .size
            }
        }
    }

    private fun noteTextContent(noteId: Long): String? {
        return runBlocking {
            withContext(Dispatchers.IO) {
                NotepadDatabase.getInstance(composeRule.activity)
                    .notepadDao()
                    .getNote(noteId)
                    ?.textContent
            }
        }
    }

    private fun waitForSingleNewNoteId(beforeIds: Set<Long>): Long {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            (noteIds() - beforeIds).size == 1
        }
        return (noteIds() - beforeIds).single()
    }

    private fun waitForNoteFolder(noteId: Long, folderId: Long) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                withContext(Dispatchers.IO) {
                    NotepadDatabase.getInstance(composeRule.activity)
                        .notepadDao()
                        .getNote(noteId)
                        ?.folderId == folderId
                }
            }
        }
    }

    private fun exitInitialDrawingFocusModeIfNeeded() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("drawing_note_title").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("exit_fullscreen_drawing_button").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("exit_fullscreen_drawing_button").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("exit_fullscreen_drawing_button").performClick()
        }
        waitForTag("drawing_note_title")
    }

    @Test
    fun textNoteTitleAndContentAcceptInput() {
        val suffix = System.currentTimeMillis()
        val title = "中文標題 $suffix"
        val body = "這是中文內容 $suffix"

        openAddMenuItem("new_text_note_menu_item")
        showTextNoteMetadata()

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
    fun textFormattingControlsRouteNonPremiumUsersToPremium() {
        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_content")

        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .performTextInput("Format me")

        composeRule.onNodeWithTag("quick_insert_checkbox_button").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_insert_bullet_button").assertIsDisplayed()
        composeRule.onNodeWithTag("hide_keyboard_button").performScrollTo().assertIsDisplayed()
        assertTagAbsent("format_heading_1_button")
        assertTagAbsent("format_heading_2_button")
        assertTagAbsent("format_bold_button")
        assertTagAbsent("format_italic_button")
        assertTagAbsent("format_underline_button")
        assertTagAbsent("format_highlight_button")
        assertTagAbsent("format_link_button")
        assertTagAbsent("clear_formatting_button")

        composeRule.onNodeWithTag("formatting_premium_entry_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        waitForTag("premium_screen")
        composeRule.onNodeWithTag("premium_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("notes_tab").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("text_note_content").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("text_note_read_content").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("text_note_content").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("text_note_content").assertTextContains("Format me")
        } else {
            composeRule.onNodeWithTag("text_note_read_content").assertTextContains("Format me")
        }
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForTag("add_note_button")
    }

    @Test
    fun textEditorFindAndAccessoryChromeUseIconSemanticsWithoutRawLabels() {
        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_content")

        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .performTextInput("Icon chrome body")
        composeRule.onNodeWithTag("text_editor_accessory_bar").assertIsDisplayed()

        assertIconControl("find_in_note_button", "Find in note")
        assertIconControl("more_note_button", "More")
        assertIconControl("quick_insert_checkbox_button", "Checkbox", scrollTo = true)
        assertIconControl("quick_insert_bullet_button", "Bullet", scrollTo = true)
        assertIconControl("formatting_premium_entry_button", "Premium formatting", scrollTo = true)
        assertIconControl("hide_keyboard_button", "Hide keyboard", scrollTo = true)

        composeRule.onNodeWithTag("find_in_note_button").performClick()
        composeRule.onNodeWithTag("find_in_note_input")
            .assertIsDisplayed()
            .performTextInput("Icon")
        assertIconControl("previous_find_match_button", "Previous match")
        assertIconControl("next_find_match_button", "Next match")
        assertIconControl("clear_find_in_note_button", "Clear search")

        listOf("...", "<", ">", "x", "HL", "Tx", "Text formatting Premium").forEach(::assertExactTextAbsent)
        assertContentDescriptionAbsent("Text formatting Premium")
    }

    @Test
    fun premiumTextFormattingAccessoryChromeOmitsOldRawLabels() {
        enableDebugPremiumAccess()
        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_content")

        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .performTextInput("Premium icon chrome body")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("format_highlight_button") > 0
        }
        composeRule.onNodeWithTag("text_editor_accessory_bar").assertIsDisplayed()

        assertTagAbsent("formatting_premium_entry_button")
        assertIconControl("format_highlight_button", "Highlight", scrollTo = true)
        assertIconControl("format_link_button", "Link", scrollTo = true)
        assertIconControl("clear_formatting_button", "Clear format", scrollTo = true)
        listOf("HL", "Tx", "Text formatting Premium").forEach { rawLabel ->
            assertExactTextAbsent(rawLabel)
            assertContentDescriptionAbsent(rawLabel)
        }
    }

    @Test
    fun debugPremiumSwitchUnlocksTextFormattingWithoutSubscription() {
        val body = "Debug format body ${System.currentTimeMillis()}"

        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.onNodeWithTag("debug_premium_section").assertIsDisplayed()
        composeRule.onNodeWithTag("debug_premium_switch").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            debugPremiumSwitchState() == ToggleableState.On
        }
        assertTrue(DebugPremiumAccess.read(composeRule.activity))
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForTag("add_note_button")

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_content")
        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .performTextInput(body)
        composeRule.onNodeWithTag("format_heading_1_button")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("premium_screen").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("text_note_content").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                withContext(Dispatchers.IO) {
                    NotepadDatabase.getInstance(composeRule.activity)
                        .notepadDao()
                        .getAllNotes()
                        .any { note ->
                            note.textContent == body &&
                                note.textFormattingJson?.contains("HEADING_1") == true
                        }
                }
            }
        }
        val formattedNote = runBlocking {
            withContext(Dispatchers.IO) {
                NotepadDatabase.getInstance(composeRule.activity)
                    .notepadDao()
                    .getAllNotes()
                    .first { note -> note.textContent == body }
            }
        }
        assertTrue(formattedNote.textFormattingJson?.contains("HEADING_1") == true)
    }

    @Test
    fun freeDefaultOnlyFolderUiIsHidden() {
        resetFoldersToDefault()
        val suffix = System.currentTimeMillis()
        val title = "Default folder note $suffix"
        val noteId = createTextNote(title = title, body = "Default folder body")

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        assertTagAbsent("folder_filter_row")
        assertTagAbsent("folder_action_row")
        assertTagAbsent("move_note_$noteId")

        composeRule.onNodeWithTag("add_note_button").performClick()
        assertTagAbsent("new_folder_menu_item")
        composeRule.onNodeWithTag("new_text_note_menu_item").performClick()
        waitForTag("text_note_content")
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForTag("add_note_button")

        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithTag("edit_note_button").assertIsDisplayed().performClick()
        showTextNoteMetadata()
        assertTagAbsent("note_folder_selector_button")
    }

    @Test
    fun freeExistingFolderCanFilterAndMoveBackToDefaultOnly() {
        resetFoldersToDefault()
        val suffix = System.currentTimeMillis()
        val folderId = createFolder("Legacy folder $suffix")
        val title = "Legacy folder note $suffix"
        val noteId = createTextNote(
            title = title,
            body = "Legacy folder body",
            folderId = folderId,
        )

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("folder_filter_row").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("folder_filter_row").assertIsDisplayed()
        composeRule.onNodeWithTag("folder_filter_$folderId").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("folder_action_row").assertIsDisplayed()
        assertTagAbsent("rename_folder_button")
        assertTagAbsent("delete_folder_button")

        composeRule.onNodeWithTag("add_note_button").performClick()
        assertTagAbsent("new_folder_menu_item")
        composeRule.onNodeWithTag("new_text_note_menu_item").performClick()
        waitForTag("text_note_content")
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForTag("folder_filter_row")

        composeRule.onNodeWithTag("move_note_$noteId").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("move_note_target_$DEFAULT_FOLDER_ID").assertIsDisplayed()
        assertTagAbsent("move_note_target_$folderId")
        composeRule.onNodeWithTag("move_note_target_$DEFAULT_FOLDER_ID").performClick()
        waitForNoteFolder(noteId, DEFAULT_FOLDER_ID)
    }

    @Test
    fun debugPremiumKeepsFolderCreationAndFolderRowVisible() {
        resetFoldersToDefault()
        val suffix = System.currentTimeMillis()
        val folderName = "Debug premium folder $suffix"
        DebugPremiumAccess.write(composeRule.activity, true)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("folder_filter_row").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("folder_filter_row").assertIsDisplayed()
        composeRule.onNodeWithTag("add_note_button").performClick()
        composeRule.onNodeWithTag("new_folder_menu_item").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("folder_name_input").assertIsDisplayed().performTextInput(folderName)
        composeRule.onNodeWithTag("folder_name_confirm_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(folderName).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(folderName).assertIsDisplayed()
    }

    @Test
    fun reminderControlsRouteNonPremiumUsersToPremium() {
        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_content")
        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .performTextInput("Reminder gate draft")

        composeRule.onNodeWithTag("more_note_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("set_reminder_menu_item")
            .assertIsDisplayed()
            .assertTextContains("Premium", substring = true)
            .performClick()

        waitForTag("premium_screen")
        composeRule.onNodeWithTag("premium_screen").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("text_note_content").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("text_note_read_content").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("text_note_content").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("text_note_content").assertTextContains("Reminder gate draft")
        } else {
            composeRule.onNodeWithTag("text_note_read_content").assertTextContains("Reminder gate draft")
        }
    }

    @Test
    fun freeReminderClearWorksAndRepeatControlsAreHidden() {
        val suffix = System.currentTimeMillis()
        val title = "Existing reminder $suffix"
        val reminderAt = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val noteId = createTextNote(
            title = title,
            body = "Reminder body",
            reminderAt = reminderAt,
            reminderRepeat = ReminderRepeat.Daily.code,
        )

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(title).performClick()
        waitForTag("text_note_read_mode")
        composeRule.onNodeWithTag("note_reminder_status").assertIsDisplayed()
        composeRule.onNodeWithTag("more_note_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("set_reminder_menu_item").assertTextContains("Premium", substring = true)
        assertTagAbsent("text_reminder_repeat_None")
        assertTagAbsent("text_reminder_repeat_Daily")
        assertTagAbsent("text_reminder_repeat_Weekly")
        assertTagAbsent("text_reminder_repeat_Monthly")
        composeRule.onNodeWithTag("clear_reminder_menu_item").assertIsDisplayed().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                withContext(Dispatchers.IO) {
                    NotepadDatabase.getInstance(composeRule.activity)
                        .notepadDao()
                        .getNote(noteId)
                        ?.reminderAt == null
                }
            }
        }
    }

    @Test
    fun drawingReminderGateSavesDraftBeforePremium() {
        val title = "Drawing premium gate ${System.currentTimeMillis()}"

        openAddMenuItem("new_drawing_note_menu_item")
        exitInitialDrawingFocusModeIfNeeded()
        composeRule.onNodeWithTag("drawing_note_title")
            .assertIsDisplayed()
            .performTextInput(title)
        composeRule.onNodeWithTag("set_reminder_button")
            .assertIsDisplayed()
            .assertTextContains("Premium", substring = true)
            .performClick()

        waitForTag("premium_screen")
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForTag("drawing_note_title")
        composeRule.onNodeWithTag("drawing_note_title")
            .assertIsDisplayed()
            .assertTextContains(title)
    }

    @Test
    fun checklistReminderGateSavesDraftBeforePremium() {
        val title = "Checklist premium gate ${System.currentTimeMillis()}"

        openAddMenuItem("new_checklist_note_menu_item")
        waitForTag("checklist_note_title")
        composeRule.onNodeWithTag("checklist_note_title")
            .assertIsDisplayed()
            .performTextInput(title)
        composeRule.onNodeWithTag("set_reminder_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Premium", substring = true)
            .performClick()

        waitForTag("premium_screen")
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForTag("checklist_note_title")
        composeRule.onNodeWithTag("checklist_note_title")
            .assertIsDisplayed()
            .assertTextContains(title)
    }

    @Test
    fun highlightLinkAndClearFormattingPersistThroughEditor() {
        val suffix = System.currentTimeMillis()
        val title = "Format full $suffix"
        val body = "example.com"
        DebugPremiumAccess.write(composeRule.activity, true)

        openAddMenuItem("new_text_note_menu_item")
        showTextNoteMetadata()
        composeRule.onNodeWithTag("text_note_title").performTextInput(title)
        composeRule.onNodeWithTag("text_note_content").assertIsDisplayed().performTextInput(body)
        composeRule.onNodeWithTag("format_highlight_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("format_link_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("format_link_url_input").assertIsDisplayed().performTextInput("example.com/docs")
        composeRule.onNodeWithTag("apply_link_format_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                withContext(Dispatchers.IO) {
                    NotepadDatabase.getInstance(composeRule.activity)
                        .notepadDao()
                        .getAllNotes()
                        .any { note ->
                            val ranges = TextFormattingJson.decode(note.textFormattingJson, note.textContent.orEmpty().length)
                            note.title == title &&
                                note.textContent == body &&
                                ranges.any { it.type == TextFormatType.Highlight } &&
                                ranges.any { it.type == TextFormatType.Link && it.url == "https://example.com/docs" }
                        }
                }
            }
        }
        val formattedNote = runBlocking {
            withContext(Dispatchers.IO) {
                NotepadDatabase.getInstance(composeRule.activity)
                    .notepadDao()
                    .getAllNotes()
                    .first { note -> note.title == title && note.textContent == body }
            }
        }
        val formattedText = findHighlightedLinkedText(
            value = formattedNote.textContent.orEmpty(),
            query = "",
            activeMatchIndex = 0,
            formattingRanges = TextFormattingJson.decode(formattedNote.textFormattingJson, formattedNote.textContent.orEmpty().length),
            matchColor = Color.Yellow,
            activeMatchColor = Color.Green,
            linkColor = Color.Blue,
            linkifyUrls = false,
        )
        assertEquals("https://example.com/docs", formattedText.webUrlAt(0))

        composeRule.onNodeWithTag("clear_formatting_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                withContext(Dispatchers.IO) {
                    NotepadDatabase.getInstance(composeRule.activity)
                        .notepadDao()
                        .getAllNotes()
                        .firstOrNull { note -> note.title == title && note.textContent == body }
                        ?.textFormattingJson
                        .isNullOrBlank()
                }
            }
        }
    }

    @Test
    fun headingFormattingPersistsAfterLeavingAndReopeningNote() {
        val suffix = System.currentTimeMillis()
        val title = "Heading persist $suffix"
        val body = "Heading line $suffix"
        DebugPremiumAccess.write(composeRule.activity, true)

        openAddMenuItem("new_text_note_menu_item")
        showTextNoteMetadata()
        composeRule.onNodeWithTag("text_note_title").performTextInput(title)
        composeRule.onNodeWithTag("text_note_content").assertIsDisplayed().performTextInput(body)
        composeRule.onNodeWithTag("format_heading_1_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("add_note_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(title).performClick()
        waitForTag("text_note_read_content")
        composeRule.onNodeWithTag("text_note_read_content").assertTextContains(body)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                withContext(Dispatchers.IO) {
                    NotepadDatabase.getInstance(composeRule.activity)
                        .notepadDao()
                        .getAllNotes()
                        .any { note ->
                            note.title == title &&
                                note.textContent == body &&
                                note.textFormattingJson?.contains("HEADING_1") == true
                        }
                }
            }
        }
        val reopenedNote = runBlocking {
            withContext(Dispatchers.IO) {
                NotepadDatabase.getInstance(composeRule.activity)
                    .notepadDao()
                    .getAllNotes()
                    .first { note -> note.title == title && note.textContent == body }
            }
        }
        val reopenedReadText = findHighlightedLinkedText(
            value = reopenedNote.textContent.orEmpty(),
            query = "",
            activeMatchIndex = 0,
            formattingRanges = TextFormattingJson.decode(reopenedNote.textFormattingJson, reopenedNote.textContent.orEmpty().length),
            matchColor = Color.Yellow,
            activeMatchColor = Color.Green,
            linkColor = Color.Blue,
            linkifyUrls = false,
        )
        val reopenedHeadingStyle = reopenedReadText.spanStyles.first { it.start == 0 && it.end == body.length }.item
        assertEquals(1.35f.em, reopenedHeadingStyle.fontSize)
    }

    @Test
    fun checklistNoteCanAddCheckAndPersistItems() {
        val suffix = System.currentTimeMillis()
        val title = "Checklist $suffix"
        val firstItem = "Milk $suffix"
        val secondItem = "Eggs $suffix"

        openAddMenuItem("new_checklist_note_menu_item")
        waitForTag("checklist_note_title")

        composeRule.onNodeWithTag("checklist_note_title")
            .assertIsDisplayed()
            .performTextInput(title)
        composeRule.onAllNodesWithTag("checklist_item_text")[0]
            .assertIsDisplayed()
            .performTextInput(firstItem)
        composeRule.onNodeWithTag("add_checklist_item_button")
            .performScrollTo()
            .performClick()
        composeRule.onAllNodesWithTag("checklist_item_text")[1]
            .assertIsDisplayed()
            .performTextInput(secondItem)
        composeRule.onAllNodesWithTag("checklist_item_checkbox")[0]
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("checklist_progress").assertIsDisplayed()
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        openSearchPanel()
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithTag("note_search_input").performTextInput(secondItem)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(title).performClick()
        waitForTag("checklist_editor")
        composeRule.onNodeWithText(firstItem).assertIsDisplayed()
        composeRule.onNodeWithText(secondItem).assertIsDisplayed()
    }

    @Test
    fun checklistBlankAddedRowPersistsAfterImmediateBack() {
        val suffix = System.currentTimeMillis()
        val title = "Checklist blank row $suffix"

        openAddMenuItem("new_checklist_note_menu_item")
        waitForTag("checklist_note_title")

        composeRule.onNodeWithTag("checklist_note_title")
            .assertIsDisplayed()
            .performTextInput(title)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("add_checklist_item_button")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(title).performClick()
        waitForTag("checklist_editor")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("checklist_item_text").fetchSemanticsNodes().size == 2
        }
        assertEquals(2, composeRule.onAllNodesWithTag("checklist_item_text").fetchSemanticsNodes().size)
    }

    @Test
    fun settingsCanToggleHiddenReminderNotificationContent() {
        composeRule.onNodeWithTag("settings_button").performClick()
        waitForTag("hide_reminder_content_row")

        composeRule.onNodeWithTag("hide_reminder_content_checkbox")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("hide_reminder_content_checkbox")
            .performClick()
            .performClick()
        composeRule.onNodeWithTag("require_device_unlock_checkbox")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun freeUsersDoNotSeeCalendarViewChip() {
        openFilterPanel()

        assertTagAbsent("calendar_view_chip")
        assertTagAbsent("quick_filter_HasReminder")
    }

    @Test
    fun reminderCalendarShowsTodayReminder() {
        val suffix = System.currentTimeMillis()
        val title = "Calendar reminder $suffix"
        val todayReminderAt = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        runBlocking {
            withContext(Dispatchers.IO) {
                val repository = NotepadRepository(NotepadDatabase.getInstance(composeRule.activity).notepadDao())
                repository.ensureDefaultFolder()
                val noteId = repository.createTextNote(DEFAULT_FOLDER_ID)
                repository.saveTextNote(noteId, title, "Calendar reminder body")
                repository.setNoteReminder(
                    noteId = noteId,
                    reminderAt = todayReminderAt,
                    reminderRepeat = ReminderRepeat.None.code,
                )
            }
        }
        DebugPremiumAccess.write(composeRule.activity, true)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        openFilterPanel()
        composeRule.onNodeWithTag("calendar_view_chip").performClick()
        composeRule.onNodeWithTag("reminder_calendar").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar_selected_day_count").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(title).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode() {
        val suffix = System.currentTimeMillis()
        val title = "Friendly read title $suffix"
        val body = "Friendly read body $suffix"

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_content")
        composeRule.onNodeWithTag("text_note_content").assertIsFocused()
        showTextNoteMetadata()
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
        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .assertTextContains(body)
            .assertIsFocused()
    }

    @Test
    fun bodyOnlyTextNoteUsesFirstContentLineAsTitle() {
        val suffix = System.currentTimeMillis()
        val firstLine = "Body first title $suffix"
        val body = "$firstLine\nSecond line"

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_content")
        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .assertIsFocused()
            .performTextInput(body)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(firstLine).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(firstLine).performClick()
        composeRule.onNodeWithTag("text_note_read_title").assertTextContains(firstLine)
        composeRule.onNodeWithTag("text_note_read_content").assertTextContains("Second line", substring = true)
    }

    @Test
    fun blankNewTextDraftIsDiscardedInsteadOfMovedToTrash() {
        val beforeIds = noteIds()
        val beforeTombstones = noteTombstoneCount()

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_content")
        composeRule.onNodeWithTag("back_button").performClick()
        waitForTag("add_note_button")

        composeRule.waitUntil(timeoutMillis = 5_000) {
            noteIds() == beforeIds
        }
        assertEquals(beforeTombstones, noteTombstoneCount())
    }

    @Test
    fun whitespaceOnlyNewTextDraftIsDiscardedWithoutSaveFailure() {
        val beforeIds = noteIds()

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_content")
        val draftId = waitForSingleNewNoteId(beforeIds)
        composeRule.onNodeWithTag("text_note_content")
            .performTextInput(" \n  ")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            noteTextContent(draftId)?.let { savedContent ->
                savedContent.isNotEmpty() && savedContent.isBlank()
            } == true
        }

        composeRule.onNodeWithTag("back_button").performClick()
        waitForTag("add_note_button")

        composeRule.waitUntil(timeoutMillis = 5_000) {
            noteIds() == beforeIds
        }
    }

    @Test
    fun blankNewTextDraftIsDiscardedWhenActivityStops() {
        val beforeIds = noteIds()

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_content")
        waitForSingleNewNoteId(beforeIds)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            noteIds() == beforeIds
        }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @Test
    fun newDraftThatHadContentIsDiscardedAfterBeingCleared() {
        val beforeIds = noteIds()
        val temporaryContent = "temporary content"

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_content")
        val draftId = waitForSingleNewNoteId(beforeIds)
        composeRule.onNodeWithTag("text_note_content")
            .performTextInput(temporaryContent)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            noteTextContent(draftId) == temporaryContent
        }
        composeRule.onNodeWithTag("text_note_content")
            .performTextReplacement("")
        composeRule.onNodeWithTag("back_button").performClick()
        waitForTag("add_note_button")

        composeRule.waitUntil(timeoutMillis = 5_000) {
            noteIds() == beforeIds
        }
    }

    @Test
    fun existingTextNoteStaysReadOnlyUntilEditButton() {
        val suffix = System.currentTimeMillis()
        val title = "Tap edit title $suffix"
        val body = "Tap edit body $suffix"

        openAddMenuItem("new_text_note_menu_item")
        showTextNoteMetadata()
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
        assertTagAbsent("text_note_content")
        assertTagAbsent("text_note_title")

        composeRule.onNodeWithTag("text_note_read_mode").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_read_content").performClick()
        assertTagAbsent("text_note_content")

        composeRule.onNodeWithTag("edit_note_button").assertIsDisplayed().performClick()
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
    fun headingFormattingUsesRelativeFontScale() {
        val heading1Content = "Heading body"
        val heading1 = findHighlightedLinkedText(
            value = heading1Content,
            query = "",
            activeMatchIndex = 0,
            formattingRanges = listOf(TextFormatRange(0, 7, TextFormatType.Heading1)),
            matchColor = Color.Yellow,
            activeMatchColor = Color.Green,
            linkColor = Color.Blue,
            linkifyUrls = false,
        )
        val heading2 = findHighlightedLinkedText(
            value = heading1Content,
            query = "",
            activeMatchIndex = 0,
            formattingRanges = listOf(TextFormatRange(0, 7, TextFormatType.Heading2)),
            matchColor = Color.Yellow,
            activeMatchColor = Color.Green,
            linkColor = Color.Blue,
            linkifyUrls = false,
        )

        val heading1Style = heading1.spanStyles.first { it.start == 0 && it.end == 7 }.item
        val heading2Style = heading2.spanStyles.first { it.start == 0 && it.end == 7 }.item
        assertEquals(1.35f.em, heading1Style.fontSize)
        assertEquals(1.18f.em, heading2Style.fontSize)
    }

    @Test
    fun longPressEnablesMultiSelectAndDeletesSelectedNotes() {
        val suffix = System.currentTimeMillis()
        val firstTitle = "Multi select first $suffix"
        val secondTitle = "Multi select second $suffix"

        openAddMenuItem("new_text_note_menu_item")
        showTextNoteMetadata()
        composeRule.onNodeWithTag("text_note_title").performTextInput(firstTitle)
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(firstTitle).fetchSemanticsNodes().isNotEmpty()
        }

        openAddMenuItem("new_text_note_menu_item")
        showTextNoteMetadata()
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
        showTextNoteMetadata()
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
        showTextNoteMetadata()
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
        composeRule.onNodeWithTag("text_note_content").performTextInput("\n")
        composeRule.onNodeWithTag("quick_insert_checkbox_button").performClick()
        composeRule.onNodeWithTag("text_note_content").assertTextContains("- [ ]", substring = true)

        composeRule.onNodeWithTag("toggle_metadata_button").performClick()
        composeRule.onNodeWithTag("text_note_edit_metadata").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_updated_time").assertIsDisplayed()
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("add_note_button").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithTag("text_note_read_content")
            .assertTextContains("Focus writer body", substring = true)
    }

    @Test
    fun readModeCheckboxTogglePersists() {
        val suffix = System.currentTimeMillis()
        val title = "Read checkbox $suffix"
        createTextNote(title = title, body = "- [ ] Task $suffix")

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithTag("text_note_read_checkbox_0")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                withContext(Dispatchers.IO) {
                    NotepadDatabase.getInstance(composeRule.activity)
                        .notepadDao()
                        .getAllNotes()
                        .any { note -> note.title == title && note.textContent.orEmpty().contains("- [x] ") }
                }
            }
        }
    }

    @Test
    fun readModeCheckboxSaveFailureShowsRetryAndCanRetry() {
        val suffix = System.currentTimeMillis()
        val title = "Read checkbox retry $suffix"
        val noteId = createTextNote(title = title, body = "- [ ] Retry task $suffix")

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithTag("text_note_read_checkbox_0").assertIsDisplayed()

        DebugSaveFailure.failNextTextSave(noteId)
        composeRule.onNodeWithTag("text_note_read_checkbox_0").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("text_note_read_retry_save_button") > 0
        }
        composeRule.onNodeWithTag("text_note_read_save_status").assertTextContains("Save failed")
        assertTrue(noteTextContent(noteId).orEmpty().contains("- [ ] Retry task"))

        composeRule.onNodeWithTag("text_note_read_retry_save_button")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            noteTextContent(noteId).orEmpty().contains("- [x] Retry task")
        }
    }

    @Test
    fun textNoteEditsPersistAfterAppBackAndSystemBack() {
        val suffix = System.currentTimeMillis()
        val firstTitle = "Persist title $suffix"
        val firstContent = "Persist content before back $suffix"
        val secondTitle = "Persist updated title $suffix"
        val secondContent = "Persist updated content after system back $suffix"

        openAddMenuItem("new_text_note_menu_item")
        showTextNoteMetadata()
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
        composeRule.onNodeWithTag("text_note_content")
            .assertTextContains(firstContent)
            .assertIsFocused()
        showTextNoteMetadata()
        composeRule.onNodeWithTag("text_note_title").assertTextContains(firstTitle)

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
        composeRule.onNodeWithTag("text_note_content")
            .assertTextContains(secondContent)
            .assertIsFocused()
        showTextNoteMetadata()
        composeRule.onNodeWithTag("text_note_title").assertTextContains(secondTitle)
    }

    @Test
    fun findInNoteOpensFromReadModeAndEditMode() {
        val suffix = System.currentTimeMillis()
        val title = "Find flow title $suffix"
        val body = "banana alpha banana beta banana"

        openAddMenuItem("new_text_note_menu_item")
        showTextNoteMetadata()
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
    fun findInNoteOpensFromOverflowMenu() {
        val suffix = System.currentTimeMillis()
        val title = "Find menu title $suffix"
        val body = "menu search target"

        openAddMenuItem("new_text_note_menu_item")
        showTextNoteMetadata()
        composeRule.onNodeWithTag("text_note_title").performTextInput(title)
        composeRule.onNodeWithTag("text_note_content").performTextInput(body)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("add_note_button").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(title).assertIsDisplayed()

        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithTag("more_note_button").performClick()
        composeRule.onNodeWithTag("find_in_note_menu_item").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("find_in_note_input").assertIsDisplayed().performTextInput("target")
        composeRule.onNodeWithTag("find_match_status").assertTextEquals("1/1")
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
        showTextNoteMetadata()
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

        composeRule.onNodeWithTag("google_account_sync_title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("google_sync_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("online_sync_title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("online_sync_target_status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("online_sync_note_count").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("online_sync_auto_checkbox").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("backup_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("restore_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("choose_sync_file_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("account_settings_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("import_export_title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("batch_export_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("batch_import_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun premiumFallbackHidesCommerceAndShowsAllowedBenefits() {
        composeRule.onNodeWithTag("premium_tab").performClick()

        composeRule.onNodeWithTag("premium_screen").assertIsDisplayed()
        assertTagAbsent("annual_plan_option")
        assertTagAbsent("monthly_plan_option")
        assertTagAbsent("premium_subscribe_button")
        composeRule.onNodeWithTag("premium_restore_button").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Price not available").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Folders").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Text formatting").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reminder/calendar tools").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("premium_format_sample_h1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("premium_format_sample_h2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("premium_format_sample_bold").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("premium_format_sample_italic").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("premium_format_sample_underline").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("premium_format_sample_link").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("premium_format_sample_highlight").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("premium_folder_sample").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("premium_schedule_sample").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Import / Export").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Import and export").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Writing assistant").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Checklist notes").fetchSemanticsNodes().size)
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
        showTextNoteMetadata()

        composeRule.onNodeWithTag("text_note_title").performTextInput(title)
        composeRule.onNodeWithTag("text_note_content").performTextInput(contentNeedle)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        openSearchPanel()
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
        showTextNoteMetadata()
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
        showTextNoteMetadata()
        composeRule.onNodeWithTag("text_note_title").performTextInput(textTitle)
        composeRule.onNodeWithTag("text_note_content").performTextInput(textBody)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(textTitle).fetchSemanticsNodes().isNotEmpty()
        }

        openAddMenuItem("new_drawing_note_menu_item")
        exitInitialDrawingFocusModeIfNeeded()
        composeRule.onNodeWithTag("drawing_note_title").performTextInput(drawingTitle)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(drawingTitle).fetchSemanticsNodes().isNotEmpty()
        }

        openFilterPanel()
        composeRule.onNodeWithTag("recently_updated_chip").assertIsDisplayed().performClick()
        openSearchPanel()
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
