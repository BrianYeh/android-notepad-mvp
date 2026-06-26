package com.example.notepad

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.notepad.data.DEFAULT_FOLDER_ID
import com.example.notepad.data.ChecklistJson
import com.example.notepad.data.DrawingJson
import com.example.notepad.data.DrawingPoint
import com.example.notepad.data.DrawingStroke
import com.example.notepad.data.DrawingTools
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NotepadDatabase
import com.example.notepad.data.NotepadRepository
import com.example.notepad.data.ReminderRepeat
import com.example.notepad.data.TextFormatRange
import com.example.notepad.data.TextFormatType
import com.example.notepad.data.TextFormattingJson
import com.example.notepad.debug.DebugPremiumAccess
import com.example.notepad.debug.DebugSaveFailure
import com.example.notepad.ui.cropTextFormatRangesForSegment
import com.example.notepad.ui.findHighlightedLinkedText
import com.example.notepad.ui.findHighlightedLinkedTextSegment
import com.example.notepad.ui.findInNoteMatches
import com.example.notepad.ui.formatFindMatchStatus
import com.example.notepad.ui.nextFindMatchIndex
import com.example.notepad.ui.previousFindMatchIndex
import com.example.notepad.ui.readContentLines
import com.example.notepad.ui.readContentMatchTargetForRange
import com.example.notepad.ui.webUrlAt
import com.example.notepad.ui.webUrlRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        resetDrawingToolPreferences()
    }

    @After
    fun clearDebugPremiumOverride() {
        DebugPremiumAccess.write(composeRule.activity, false)
        DebugSaveFailure.clear()
        resetDrawingToolPreferences()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun resetDrawingToolPreferences() {
        composeRule.activity.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
            .edit()
            .remove("last_drawing_pen_brush_size")
            .remove("last_drawing_pen_color")
            .apply()
    }

    private fun showTextNoteMetadata() {
        if (composeRule.onAllNodesWithTag("text_note_title").fetchSemanticsNodes().isEmpty()) {
            if (composeRule.onAllNodesWithTag("toggle_metadata_button").fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithTag("toggle_metadata_button").performClick()
            } else {
                waitForTag("more_note_button")
                composeRule.onNodeWithTag("more_note_button").performClick()
                waitForTag("text_note_edit_details_menu_item")
                composeRule.onNodeWithTag("text_note_edit_details_menu_item").performClick()
            }
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

    private fun createTextNoteViaFab() {
        waitForTag("add_note_button")
        composeRule.onNodeWithTag("add_note_button").performClick()
        waitForTag("text_note_content")
    }

    private fun openCreationMenuItem(menuItemTag: String) {
        waitForTag("add_note_options_button")
        composeRule.onNodeWithTag("add_note_options_button").performClick()
        waitForTag(menuItemTag)
        composeRule.onNodeWithTag(menuItemTag).performClick()
    }

    private fun openAddMenuItem(menuItemTag: String) {
        if (menuItemTag == "new_text_note_menu_item") {
            createTextNoteViaFab()
        } else {
            openCreationMenuItem(menuItemTag)
        }
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

    private fun submitSearchImeAction() {
        composeRule.onNodeWithTag("note_search_input").performImeAction()
        composeRule.waitForIdle()
    }

    private fun selectReminderFilter(filterName: String) {
        composeRule.onNodeWithTag("reminder_filter_selector").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reminder_$filterName", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("reminder_$filterName", useUnmergedTree = true).performClick()
    }

    private fun clickFirstCalendarPreset() {
        listOf(
            "calendar_preset_morning",
            "calendar_preset_afternoon",
            "calendar_preset_evening",
            "calendar_preset_next_hour",
        ).firstOrNull { tag -> composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
            ?.let { tag -> composeRule.onNodeWithTag(tag).performClick() }
            ?: error("No calendar reminder preset was shown")
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

    private fun addDaysForTest(dayStart: Long, days: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, days)
        }.timeInMillis
    }

    private fun moveCalendarToDay(dayStart: Long) {
        var selectedDayStart = startOfDayMillisForTest(System.currentTimeMillis())
        while (selectedDayStart < dayStart) {
            composeRule.onNodeWithTag("calendar_next_day").performScrollTo().performClick()
            selectedDayStart = addDaysForTest(selectedDayStart, 1)
        }
        while (selectedDayStart > dayStart) {
            composeRule.onNodeWithTag("calendar_previous_day").performScrollTo().performClick()
            selectedDayStart = addDaysForTest(selectedDayStart, -1)
        }
    }

    private fun waitForCalendarDayChange(previousTitle: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onAllNodesWithTag("calendar_add_reminder").fetchSemanticsNodes().isNotEmpty() &&
                    nodeText("calendar_selected_day_title") != previousTitle
            }.getOrDefault(false)
        }
    }

    private fun debugPremiumSwitchState(): ToggleableState? {
        return composeRule.onNodeWithTag("debug_premium_switch")
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ToggleableState)
    }

    private fun nodeText(tag: String): String {
        return composeRule.onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { it.text }
            .orEmpty()
    }

    private fun enableDebugPremiumAccess() {
        DebugPremiumAccess.write(composeRule.activity, true)
        composeRule.waitForIdle()
    }

    private fun pressDeviceBack() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_BACK")
            .close()
        composeRule.waitForIdle()
    }

    private fun pressActivityBack() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun exitFullscreenDrawingFromUi() {
        waitForTag("drawing_fullscreen_details_button")
        composeRule.onNodeWithTag("drawing_fullscreen_details_button").performClick()
        waitForTag("drawing_note_title")
    }

    private fun grantPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                    composeRule.activity.packageName,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            }
            runCatching {
                InstrumentationRegistry.getInstrumentation().uiAutomation
                    .executeShellCommand("pm grant ${composeRule.activity.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
                    .close()
            }
            runCatching {
                InstrumentationRegistry.getInstrumentation().uiAutomation
                    .executeShellCommand("appops set ${composeRule.activity.packageName} POST_NOTIFICATION allow")
                    .close()
            }
        }
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

    private fun assertTaggedSelected(tag: String, expected: Boolean) {
        val selected = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Selected)
        assertEquals("$tag selected state", expected, selected)
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
        isPinned: Boolean = false,
        textFormattingJson: String? = null,
    ): Long {
        return runBlocking {
            withContext(Dispatchers.IO) {
                val repository = NotepadRepository(NotepadDatabase.getInstance(composeRule.activity).notepadDao())
                repository.ensureDefaultFolder()
                val noteId = repository.createTextNote(folderId)
                repository.saveTextNote(noteId, title, body, textFormattingJson)
                if (isPinned) {
                    repository.setNotePinned(noteId, true)
                }
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

    private fun noteById(noteId: Long): NoteEntity? {
        return runBlocking {
            withContext(Dispatchers.IO) {
                NotepadDatabase.getInstance(composeRule.activity)
                    .notepadDao()
                    .getNote(noteId)
            }
        }
    }

    private fun drawingStrokes(noteId: Long): List<DrawingStroke> {
        return DrawingJson.decode(noteById(noteId)?.drawingData)
    }

    private fun encodedTestStroke(startX: Float, startY: Float): String {
        return DrawingJson.encode(
            listOf(
                DrawingStroke(
                    points = listOf(
                        DrawingPoint(startX, startY),
                        DrawingPoint(startX + 24f, startY + 16f),
                    ),
                ),
            ),
        )
    }

    private fun createDrawingNote(
        title: String = "",
        drawingData: String = "[]",
        folderId: Long = DEFAULT_FOLDER_ID,
    ): Long {
        return runBlocking {
            withContext(Dispatchers.IO) {
                val repository = NotepadRepository(NotepadDatabase.getInstance(composeRule.activity).notepadDao())
                repository.ensureDefaultFolder()
                val noteId = repository.createDrawingNote(folderId)
                if (title.isNotEmpty() || drawingData != "[]") {
                    repository.saveDrawingNote(noteId, title, drawingData)
                }
                noteId
            }
        }
    }

    private fun replaceDrawingNoteRow(noteId: Long, title: String, drawingData: String) {
        runBlocking {
            withContext(Dispatchers.IO) {
                val dao = NotepadDatabase.getInstance(composeRule.activity).notepadDao()
                val current = dao.getNote(noteId) ?: error("Missing note $noteId")
                dao.updateNote(
                    current.copy(
                        title = title,
                        textContent = null,
                        drawingData = drawingData,
                        updatedAt = maxOf(System.currentTimeMillis(), current.updatedAt + 1L),
                    ),
                )
            }
        }
    }

    private fun deleteNoteRow(noteId: Long) {
        runBlocking {
            withContext(Dispatchers.IO) {
                NotepadDatabase.getInstance(composeRule.activity)
                    .notepadDao()
                    .deleteNote(noteId)
            }
        }
    }

    private fun softDeleteNote(noteId: Long) {
        runBlocking {
            withContext(Dispatchers.IO) {
                NotepadRepository(NotepadDatabase.getInstance(composeRule.activity).notepadDao())
                    .deleteNote(noteId)
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

    private fun drawShortStrokeOnFullscreenCanvas() {
        composeRule.onNodeWithTag("fullscreen_drawing_canvas")
            .assertIsDisplayed()
            .performTouchInput {
                down(center)
                moveBy(Offset(80f, 40f))
                up()
            }
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
        assertEquals(1, tagCount("note_reminder_status"))
        composeRule.onNodeWithTag("note_reminder_status").assertIsDisplayed()
        composeRule.onNodeWithTag("set_reminder_button").assertIsDisplayed()
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
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            tagCount("add_note_button") > 0 && tagCount("text_note_content") == 0
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

        composeRule.onNodeWithTag("add_note_options_button").performClick()
        assertTagAbsent("new_folder_menu_item")
        composeRule.onNodeWithTag("new_checklist_note_menu_item").performClick()
        waitForTag("checklist_note_title")
        composeRule.onNodeWithTag("back_button").performClick()
        waitForTag("add_note_button")

        composeRule.onNodeWithTag("note_more_$noteId").assertIsDisplayed().performClick()
        assertTagAbsent("move_note_$noteId")
        composeRule.onNodeWithTag("pin_note_$noteId").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("pin_note_$noteId") == 0
        }
        createTextNoteViaFab()
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

        composeRule.onNodeWithTag("add_note_options_button").performClick()
        assertTagAbsent("new_folder_menu_item")
        composeRule.onNodeWithTag("new_checklist_note_menu_item").performClick()
        waitForTag("checklist_note_title")
        composeRule.onNodeWithTag("back_button").performClick()
        waitForTag("folder_filter_row")
        createTextNoteViaFab()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForTag("folder_filter_row")

        composeRule.onNodeWithTag("note_more_$noteId").assertIsDisplayed().performClick()
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
        composeRule.onNodeWithTag("add_note_options_button").performClick()
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
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
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
    fun blankDrawingReminderPremiumGateCancelStillDeletesDraft() {
        val beforeIds = noteIds()

        openAddMenuItem("new_drawing_note_menu_item")
        val noteId = waitForSingleNewNoteId(beforeIds)
        exitInitialDrawingFocusModeIfNeeded()
        composeRule.onNodeWithTag("set_reminder_button")
            .assertIsDisplayed()
            .assertTextContains("Premium", substring = true)
            .performClick()

        waitForTag("premium_screen")
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            tagCount("add_note_button") > 0 && noteIds() == beforeIds
        }
        assertNull(noteById(noteId))
    }

    @Test
    fun blankDrawingPremiumReminderPickerCancelKeepsDraft() {
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
        val beforeIds = noteIds()

        openAddMenuItem("new_drawing_note_menu_item")
        val noteId = waitForSingleNewNoteId(beforeIds)
        exitInitialDrawingFocusModeIfNeeded()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !nodeText("set_reminder_button").contains("Premium")
        }
        composeRule.onNodeWithTag("set_reminder_button")
            .assertIsDisplayed()
            .performClick()

        pressDeviceBack()
        waitForTag("drawing_note_title")
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            tagCount("add_note_button") > 0
        }
        assertTrue(noteById(noteId) != null)
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
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            tagCount("add_note_button") > 0 && tagCount("checklist_editor") == 0
        }
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

        composeRule.waitUntil(timeoutMillis = 15_000) {
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
        waitForTag("checklist_use_mode")
        composeRule.onNodeWithTag("checklist_use_title").assertTextContains(title)
        composeRule.onNodeWithText(firstItem).assertIsDisplayed()
        composeRule.onNodeWithText(secondItem).assertIsDisplayed()
        assertTagAbsent("checklist_note_title")
        assertTagAbsent("add_checklist_item_button")
        assertTagAbsent("delete_checklist_item_button")
        composeRule.onAllNodesWithTag("checklist_use_item_checkbox")[1]
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                withContext(Dispatchers.IO) {
                    NotepadDatabase.getInstance(composeRule.activity)
                        .notepadDao()
                        .getAllNotes()
                        .firstOrNull { it.title == title }
                        ?.textContent
                        ?.let { ChecklistJson.decode(it).filter { item -> item.text.isNotBlank() } }
                        ?.map { it.checked } == listOf(true, true)
                }
            }
        }
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(title).performClick()
        waitForTag("checklist_use_mode")
        composeRule.onNodeWithTag("checklist_progress").assertTextContains("2/2", substring = true)
        composeRule.onNodeWithTag("edit_checklist_button").performClick()
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
        waitForTag("checklist_use_mode")
        composeRule.onNodeWithTag("empty_checklist_edit_button")
            .assertIsDisplayed()
            .performClick()
        waitForTag("checklist_editor")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("checklist_item_text").fetchSemanticsNodes().size == 2
        }
        assertEquals(2, composeRule.onAllNodesWithTag("checklist_item_text").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            tagCount("add_note_button") > 0 && tagCount("checklist_editor") == 0
        }
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

        assertTagAbsent("home_reminders_button")
        assertTagAbsent("calendar_view_chip")
        assertTagAbsent("quick_filter_HasReminder")
    }

    @Test
    fun premiumHomeReminderButtonOpensCalendar() {
        enableDebugPremiumAccess()

        waitForTag("home_reminders_button")
        composeRule.onNodeWithTag("home_reminders_button").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("reminder_calendar").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar_selected_day_title").performScrollTo().assertIsDisplayed()
        assertIconControl("calendar_previous_day", "Previous day", scrollTo = true)
        assertIconControl("calendar_next_day", "Next day", scrollTo = true)
        composeRule.onNodeWithTag("calendar_add_reminder").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reminderCalendarShowsScheduledDayReminder() {
        val suffix = System.currentTimeMillis()
        val title = "Calendar reminder $suffix"
        val reminderAt = System.currentTimeMillis() + 600_000
        val reminderDayStart = startOfDayMillisForTest(reminderAt)
        val noteId = runBlocking {
            withContext(Dispatchers.IO) {
                val repository = NotepadRepository(NotepadDatabase.getInstance(composeRule.activity).notepadDao())
                repository.ensureDefaultFolder()
                val noteId = repository.createTextNote(DEFAULT_FOLDER_ID)
                repository.saveTextNote(noteId, title, "Calendar reminder body")
                repository.setNoteReminder(
                    noteId = noteId,
                    reminderAt = reminderAt,
                    reminderRepeat = ReminderRepeat.None.code,
                )
                noteId
            }
        }
        DebugPremiumAccess.write(composeRule.activity, true)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        waitForTag("home_reminders_button")
        composeRule.onNodeWithTag("home_reminders_button").performClick()
        composeRule.onNodeWithTag("reminder_calendar").assertIsDisplayed()
        moveCalendarToDay(reminderDayStart)
        composeRule.onNodeWithTag("calendar_selected_day_title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("calendar_selected_day_count").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("note_reminder_summary_$noteId", useUnmergedTree = true)
            .performScrollTo()
            .assertTextContains("Upcoming", substring = true)
    }

    @Test
    fun calendarAddCreatesReminderDraftForSelectedFutureDay() {
        enableDebugPremiumAccess()
        grantPostNotificationsIfNeeded()
        val beforeIds = noteIds()

        waitForTag("home_reminders_button")
        composeRule.onNodeWithTag("home_reminders_button").performClick()
        waitForTag("reminder_calendar")
        composeRule.onNodeWithTag("calendar_selected_day_title").performScrollTo()
        val currentDayTitle = nodeText("calendar_selected_day_title")
        val expectedReminderDayStart = addDaysForTest(startOfDayMillisForTest(System.currentTimeMillis()), 1)
        composeRule.onNodeWithTag("calendar_next_day").performScrollTo().performClick()
        waitForCalendarDayChange(currentDayTitle)
        composeRule.onNodeWithTag("calendar_add_reminder").performScrollTo().assertIsDisplayed().performClick()
        waitForTag("calendar_add_reminder_dialog")
        clickFirstCalendarPreset()

        waitForTag("text_note_content")
        showTextNoteMetadata()
        composeRule.onNodeWithTag("note_reminder_status")
            .assertIsDisplayed()
            .assertTextContains("Upcoming", substring = true)
        val noteId = waitForSingleNewNoteId(beforeIds)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                withContext(Dispatchers.IO) {
                    NotepadDatabase.getInstance(composeRule.activity)
                        .notepadDao()
                        .getNote(noteId)
                        ?.reminderAt != null
                }
            }
        }
        assertEquals(expectedReminderDayStart, startOfDayMillisForTest(noteById(noteId)?.reminderAt ?: 0L))
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                withContext(Dispatchers.IO) {
                    NotepadDatabase.getInstance(composeRule.activity)
                        .notepadDao()
                        .getNote(noteId) == null
                }
            }
        }
    }

    @Test
    fun premiumReminderFiltersSeparateWithOverdueAndUpcomingNotes() {
        enableDebugPremiumAccess()
        val suffix = System.currentTimeMillis()
        val noReminderTitle = "Reminder filter none $suffix"
        val overdueTitle = "Reminder filter overdue $suffix"
        val upcomingTitle = "Reminder filter upcoming $suffix"
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val overdueId = createTextNote(
            title = overdueTitle,
            body = "Overdue body $suffix",
            reminderAt = todayStart,
        )
        val upcomingId = createTextNote(
            title = upcomingTitle,
            body = "Upcoming body $suffix",
            reminderAt = System.currentTimeMillis() + 86_400_000,
            reminderRepeat = ReminderRepeat.Daily.code,
        )
        createTextNote(
            title = noReminderTitle,
            body = "No reminder body $suffix",
        )

        openSearchPanel()
        composeRule.onNodeWithTag("note_search_input").performTextInput(suffix.toString())
        submitSearchImeAction()
        openFilterPanel()

        selectReminderFilter("WithReminder")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(overdueTitle).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(upcomingTitle).fetchSemanticsNodes().isNotEmpty()
        }
        assertExactTextAbsent(noReminderTitle)

        selectReminderFilter("Overdue")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(overdueTitle).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(upcomingTitle).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("note_reminder_summary_$overdueId", useUnmergedTree = true)
            .performScrollTo()
            .assertTextContains("Overdue", substring = true)

        selectReminderFilter("Upcoming")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(upcomingTitle).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(overdueTitle).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("note_reminder_summary_$upcomingId", useUnmergedTree = true)
            .performScrollTo()
            .assertTextContains("Upcoming", substring = true)
            .assertTextContains("Daily", substring = true)

        composeRule.onNodeWithTag("home_reminders_button").performClick()
        waitForTag("reminder_calendar")
        composeRule.onNodeWithText(overdueTitle).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun overdueReminderRepeatCanBeChangedFromTextOverflow() {
        enableDebugPremiumAccess()
        grantPostNotificationsIfNeeded()
        val title = "Overdue repeat edit ${System.currentTimeMillis()}"
        val noteId = createTextNote(
            title = title,
            body = "Overdue repeat body",
            reminderAt = System.currentTimeMillis() - 60_000,
        )

        openSearchPanel()
        composeRule.onNodeWithTag("note_search_input").performTextReplacement(title)
        submitSearchImeAction()
        openFilterPanel()
        selectReminderFilter("All")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        waitForTag("more_note_button")
        composeRule.onNodeWithTag("more_note_button").performClick()
        composeRule.onNodeWithTag("text_reminder_repeat_Daily").assertIsDisplayed()
        composeRule.onNodeWithTag("text_reminder_repeat_Daily").performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            runBlocking {
                withContext(Dispatchers.IO) {
                    val note = NotepadDatabase.getInstance(composeRule.activity)
                        .notepadDao()
                        .getNote(noteId)
                    note?.reminderRepeat == ReminderRepeat.Daily.code &&
                        (note.reminderAt ?: 0L) > System.currentTimeMillis()
                }
            }
        }
    }

    @Test
    fun newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode() {
        val suffix = System.currentTimeMillis()
        val title = "Friendly read title $suffix"
        val body = "Friendly read body $suffix"

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_content")
        composeRule.onNodeWithTag("text_note_content").assertIsFocused()
        assertTagAbsent("text_note_title")
        assertTagAbsent("text_note_focus_mode")
        assertTagAbsent("text_note_compact_metadata")
        assertTagAbsent("text_note_edit_metadata")
        assertTagAbsent("text_note_top_save_status")
        assertTagAbsent("text_editor_accessory_bar")
        assertTagAbsent("formatting_premium_entry_button")
        assertExactTextAbsent("Untitled text note")
        composeRule.onNodeWithTag("more_note_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("text_note_edit_details_menu_item").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("text_note_title").assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithTag("text_note_title").assertIsDisplayed().performTextInput(title)
        composeRule.onNodeWithTag("text_note_content").assertIsDisplayed().performTextInput(body)
        composeRule.onNodeWithTag("text_note_top_save_status").assertIsDisplayed()
        composeRule.onNodeWithTag("text_editor_accessory_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("text_note_title").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithTag("text_note_read_mode").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_read_title").assertTextContains(title)
        composeRule.onNodeWithTag("text_note_read_content").assertTextContains(body)
        assertTagAbsent("note_reminder_status")
        assertTagAbsent("text_note_read_save_status")
        assertTagAbsent("text_note_read_retry_save_button")
        assertTagAbsent("text_note_pinned_indicator")
        composeRule.onNodeWithTag("more_note_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("text_note_details_menu_item").assertIsDisplayed().performClick()
        waitForTag("text_note_details_dialog")
        composeRule.onNodeWithTag("text_note_details_folder").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_details_updated").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_details_save_status").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_details_reminder").assertTextContains("No reminder", substring = true)
        composeRule.onNodeWithTag("text_note_details_done_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("text_note_title").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag("edit_note_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .assertTextContains(body)
            .assertIsFocused()
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            tagCount("add_note_button") > 0 && tagCount("text_note_content") == 0
        }
    }

    @Test
    fun bodyOnlyTextNoteUsesFirstContentLineAsTitle() {
        val suffix = System.currentTimeMillis()
        val firstLine = "Body first title $suffix"
        val body = "$firstLine\nSecond line"
        val beforeIds = noteIds()

        openAddMenuItem("new_text_note_menu_item")
        waitForTag("text_note_content")
        val noteId = waitForSingleNewNoteId(beforeIds)
        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .assertIsFocused()
            .performTextInput(body)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(firstLine).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(firstLine).performClick()
        assertTagAbsent("text_note_read_title")
        composeRule.onNodeWithTag("text_note_read_content").assertTextContains(firstLine, substring = true)
        composeRule.onNodeWithTag("text_note_read_content").assertTextContains("Second line", substring = true)
        composeRule.onNodeWithTag("text_note_read_content").performTouchInput {
            down(Offset(center.x, center.y * 0.4f))
            up()
        }
        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .assertTextContains(body, substring = true)
            .assertIsFocused()
        assertEquals("", noteById(noteId)?.title)
    }

    @Test
    fun readModeDetailsShowPinnedMetadataOffMainPage() {
        val suffix = System.currentTimeMillis()
        val title = "Pinned details $suffix"
        val noteId = createTextNote(
            title = title,
            body = "Pinned details body $suffix",
            isPinned = true,
        )

        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("note_card_$noteId") > 0
        }
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        waitForTag("text_note_read_mode")
        assertTagAbsent("text_note_pinned_indicator")

        composeRule.onNodeWithTag("more_note_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("text_note_details_menu_item").assertIsDisplayed().performClick()
        waitForTag("text_note_details_dialog")
        composeRule.onNodeWithTag("text_note_details_pinned").assertTextContains("Pinned", substring = true)
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
        composeRule.waitUntil(timeoutMillis = 15_000) {
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
    fun existingTextNoteSupportsReadModeTapToEdit() {
        val suffix = System.currentTimeMillis()
        val title = "Tap edit title $suffix"
        val body = "Tap edit body $suffix"
        val noteId = createTextNote(title = title, body = body)

        waitForTag("note_card_$noteId")
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        composeRule.onNodeWithTag("text_note_read_mode").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_read_title").performClick()
        composeRule.onNodeWithTag("text_note_title")
            .assertIsDisplayed()
            .assertTextContains(title)
            .assertIsFocused()
        composeRule.onNodeWithTag("back_button").performClick()

        waitForTag("note_card_$noteId")
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        composeRule.onNodeWithTag("text_note_read_mode").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_read_content").performClick()
        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .assertTextContains(body)
            .assertIsFocused()
        composeRule.onNodeWithTag("back_button").performClick()

        waitForTag("note_card_$noteId")
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        composeRule.onNodeWithTag("text_note_read_mode").assertIsDisplayed()
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
    fun readContentLinesPreserveOffsetsAndTrailingBlankLines() {
        val content = "intro\n- [ ] task\n\n- [X] done\n"

        val lines = readContentLines(content)

        assertEquals(5, lines.size)
        assertEquals(0, lines[0].lineIndex)
        assertEquals(0, lines[0].start)
        assertEquals("intro".length, lines[0].endExclusive)
        assertNull(lines[0].checkbox)

        assertEquals(1, lines[1].lineIndex)
        assertEquals(content.indexOf("- [ ] task"), lines[1].start)
        assertEquals(lines[1].start + "- [ ] task".length, lines[1].endExclusive)
        assertEquals(false, lines[1].checkbox?.checked)
        assertEquals("task", lines[1].checkbox?.label)
        assertEquals(lines[1].start + 6, lines[1].labelStart)
        assertEquals("task", lines[1].displayText)

        assertEquals(2, lines[2].lineIndex)
        assertEquals(lines[2].start, lines[2].endExclusive)
        assertNull(lines[2].checkbox)

        assertEquals(true, lines[3].checkbox?.checked)
        assertEquals("done", lines[3].displayText)
        assertEquals(content.length, lines[4].start)
        assertEquals(content.length, lines[4].endExclusive)
    }

    @Test
    fun readContentLinesTreatCrLfAndCrAsLineDelimiters() {
        val content = "intro\r\n- [ ] task\rplain\r"

        val lines = readContentLines(content)

        assertEquals(4, lines.size)
        assertEquals("intro", lines[0].text)
        assertEquals(0, lines[0].start)
        assertEquals(5, lines[0].endExclusive)
        assertEquals("- [ ] task", lines[1].text)
        assertEquals(content.indexOf("- [ ] task"), lines[1].start)
        assertEquals(lines[1].start + "- [ ] task".length, lines[1].endExclusive)
        assertEquals("task", lines[1].displayText)
        assertEquals("plain", lines[2].text)
        assertEquals(content.indexOf("plain"), lines[2].start)
        assertEquals(content.indexOf("plain") + "plain".length, lines[2].endExclusive)
        assertEquals(content.length, lines[3].start)
        assertEquals(content.length, lines[3].endExclusive)
    }

    @Test
    fun cropTextFormatRangesForSegmentKeepsVisibleOverlap() {
        val content = "- [ ] formatted label"
        val line = readContentLines(content).single()
        val cropped = cropTextFormatRangesForSegment(
            ranges = listOf(
                TextFormatRange(1, line.labelStart + 4, TextFormatType.Highlight),
                TextFormatRange(0, line.labelStart - 1, TextFormatType.Bold),
                TextFormatRange(line.labelStart + 10, line.endExclusive, TextFormatType.Link, " https://example.com "),
            ),
            contentLength = content.length,
            segmentStart = line.labelStart,
            segmentEndExclusive = line.endExclusive,
            displayedStart = line.labelStart,
        )

        assertEquals(2, cropped.size)
        assertEquals(TextFormatType.Highlight, cropped[0].type)
        assertEquals(0, cropped[0].start)
        assertEquals(4, cropped[0].end)
        assertEquals(TextFormatType.Link, cropped[1].type)
        assertEquals(10, cropped[1].start)
        assertEquals(line.endExclusive - line.labelStart, cropped[1].end)
        assertEquals("https://example.com", cropped[1].url)
    }

    @Test
    fun findHighlightedLinkedTextSegmentUsesGlobalActiveMatch() {
        val content = "hit before\n- [ ] hit label"
        val line = readContentLines(content)[1]
        val matches = findInNoteMatches(content, "hit")
        val annotated = findHighlightedLinkedTextSegment(
            value = line.displayText,
            absoluteStart = line.displayStart,
            absoluteEndExclusive = line.endExclusive,
            contentLength = content.length,
            globalMatches = matches,
            activeMatchIndex = 1,
            formattingRanges = emptyList(),
            matchColor = Color.Yellow,
            activeMatchColor = Color.Green,
            linkColor = Color.Blue,
            linkifyUrls = false,
        )

        val activeStyle = annotated.spanStyles.first { it.start == 0 && it.end == 3 }.item
        assertEquals(Color.Green, activeStyle.background)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, activeStyle.fontWeight)
    }

    @Test
    fun segmentAnnotatedTextKeepsExplicitAndAutoUrlAnnotations() {
        val content = "- [ ] linked docs and example.com"
        val line = readContentLines(content).single()
        val explicitStart = content.indexOf("linked")
        val autoLocalStart = line.displayText.indexOf("example.com")
        val annotated = findHighlightedLinkedTextSegment(
            value = line.displayText,
            absoluteStart = line.displayStart,
            absoluteEndExclusive = line.endExclusive,
            contentLength = content.length,
            globalMatches = emptyList(),
            activeMatchIndex = 0,
            formattingRanges = listOf(
                TextFormatRange(explicitStart, explicitStart + "linked".length, TextFormatType.Link, "https://example.com/docs"),
            ),
            matchColor = Color.Yellow,
            activeMatchColor = Color.Green,
            linkColor = Color.Blue,
        )

        assertEquals("https://example.com/docs", annotated.webUrlAt(0))
        assertEquals("https://example.com", annotated.webUrlAt(autoLocalStart))
        assertNull(annotated.webUrlAt(line.displayText.indexOf("and")))
    }

    @Test
    fun markerOnlyFindMatchTargetsRowWithoutVisibleFragment() {
        val content = "- [ ] task"
        val lines = readContentLines(content)
        val matches = findInNoteMatches(content, "[ ]")
        val target = readContentMatchTargetForRange(lines, matches.single())
        val annotated = findHighlightedLinkedTextSegment(
            value = lines.single().displayText,
            absoluteStart = lines.single().displayStart,
            absoluteEndExclusive = lines.single().endExclusive,
            contentLength = content.length,
            globalMatches = matches,
            activeMatchIndex = 0,
            formattingRanges = emptyList(),
            matchColor = Color.Yellow,
            activeMatchColor = Color.Green,
            linkColor = Color.Blue,
            linkifyUrls = false,
        )

        assertEquals(0, target?.lineIndex)
        assertEquals(false, target?.hasVisibleText)
        assertEquals(0, target?.localStart)
        assertEquals(0, target?.localEndExclusive)
        assertTrue(annotated.spanStyles.isEmpty())
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

        val beforeFirstIds = noteIds()
        openAddMenuItem("new_text_note_menu_item")
        val firstNoteId = waitForSingleNewNoteId(beforeFirstIds)
        showTextNoteMetadata()
        composeRule.onNodeWithTag("text_note_title").performTextInput(firstTitle)
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("note_card_$firstNoteId") > 0
        }

        val beforeSecondIds = noteIds()
        openAddMenuItem("new_text_note_menu_item")
        val secondNoteId = waitForSingleNewNoteId(beforeSecondIds)
        showTextNoteMetadata()
        composeRule.onNodeWithTag("text_note_title").performTextInput(secondTitle)
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("note_card_$secondNoteId") > 0
        }

        composeRule.onNodeWithTag("note_card_$firstNoteId").assertIsDisplayed().performTouchInput { longClick() }
        composeRule.onNodeWithTag("selected_notes_count").assertTextEquals("1 selected")
        composeRule.onNodeWithTag("note_card_$secondNoteId").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("selected_notes_count").assertTextEquals("2 selected")
        composeRule.onNodeWithTag("delete_selected_notes_button").performClick()
        composeRule.onNodeWithTag("confirm_dialog_confirm_button").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            tagCount("note_card_$firstNoteId") == 0 &&
                tagCount("note_card_$secondNoteId") == 0
        }
    }

    @Test
    fun backExitsMultiSelectModeWithoutOpeningOrDeletingNote() {
        val title = "Back exits multi select ${System.currentTimeMillis()}"
        val beforeIds = noteIds()

        openAddMenuItem("new_text_note_menu_item")
        val noteId = waitForSingleNewNoteId(beforeIds)
        showTextNoteMetadata()
        composeRule.onNodeWithTag("text_note_title").performTextInput(title)
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("note_card_$noteId").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("note_card_$noteId").performTouchInput { longClick() }
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
    fun mixedTextNoteCheckboxRowsRenderWithPlainBlankUrlAndTrailingText() {
        val suffix = System.currentTimeMillis()
        val title = "Mixed markdown checkbox $suffix"
        val body = listOf(
            "Plain intro $suffix",
            "",
            "- [ ] Task $suffix",
            "Visit https://example.com/$suffix",
            "- [x] Done $suffix",
            "Trailing text $suffix",
            "",
        ).joinToString("\n")
        val noteId = createTextNote(title = title, body = body)

        waitForTag("note_card_$noteId")
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        waitForTag("text_note_read_content")

        composeRule.onNodeWithTag("text_note_read_content")
            .assertTextContains("Plain intro $suffix", substring = true)
            .assertTextContains("Task $suffix", substring = true)
            .assertTextContains("Visit https://example.com/$suffix", substring = true)
            .assertTextContains("Trailing text $suffix", substring = true)
        composeRule.onNodeWithTag("text_note_read_checkbox_2").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("text_note_read_checkbox_4").assertIsDisplayed()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            noteTextContent(noteId).orEmpty().contains("- [x] Task $suffix")
        }
        val updatedContent = noteTextContent(noteId).orEmpty()
        assertTrue(updatedContent.contains("Plain intro $suffix"))
        assertTrue(updatedContent.contains("Visit https://example.com/$suffix"))
        assertTrue(updatedContent.endsWith("\n"))
    }

    @Test
    fun findInNoteScrollsWhileMixedCheckboxRowsAreRendered() {
        val suffix = System.currentTimeMillis()
        val title = "Row find markdown $suffix"
        val filler = (1..80).joinToString(separator = "\n") { index ->
            "filler row $index keeps row mode scrolling"
        }
        val body = "- [ ] visible checkbox $suffix\nneedle top\n$filler\nneedle bottom"
        val noteId = createTextNote(title = title, body = body)

        waitForTag("note_card_$noteId")
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        composeRule.onNodeWithTag("find_in_note_button").performClick()
        composeRule.onNodeWithTag("find_in_note_input").performTextInput("needle")
        composeRule.onNodeWithTag("find_match_status").assertTextEquals("1/2")
        composeRule.onNodeWithTag("text_note_read_checkbox_0").assertIsDisplayed()
        val initialReadScroll = verticalScrollValue("text_note_read_scroll", useUnmergedTree = true)

        composeRule.onNodeWithTag("next_find_match_button").performClick()
        composeRule.onNodeWithTag("find_match_status").assertTextEquals("2/2")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            verticalScrollValue("text_note_read_scroll", useUnmergedTree = true) > initialReadScroll + 20f
        }
    }

    @Test
    fun mixedCheckboxRowsStillRenderWhenFormattingExists() {
        val suffix = System.currentTimeMillis()
        val title = "Mixed formatting checkbox $suffix"
        val body = "- [ ] Bold task $suffix\nPlain $suffix"
        val boldStart = body.indexOf("Bold")
        val formattingJson = TextFormattingJson.encode(
            listOf(TextFormatRange(boldStart, boldStart + "Bold".length, TextFormatType.Bold)),
        )
        val noteId = createTextNote(
            title = title,
            body = body,
            textFormattingJson = formattingJson,
        )

        waitForTag("note_card_$noteId")
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        composeRule.onNodeWithTag("text_note_read_checkbox_0").assertIsDisplayed()
        composeRule.onNodeWithTag("text_note_read_content")
            .assertTextContains("Bold task $suffix", substring = true)
    }

    @Test
    fun readModeCheckboxTogglePreservesCrLfCrContentAndFormattingOffsets() {
        val suffix = System.currentTimeMillis()
        val title = "CR checkbox formatting $suffix"
        val formattedText = "Formatted tail $suffix"
        val body = "Intro $suffix\r\n- [ ] Task $suffix\r$formattedText"
        val formattedStart = body.indexOf(formattedText)
        val expectedRange = TextFormatRange(
            formattedStart,
            formattedStart + "Formatted".length,
            TextFormatType.Bold,
        )
        val noteId = createTextNote(
            title = title,
            body = body,
            textFormattingJson = TextFormattingJson.encode(listOf(expectedRange)),
        )
        val expectedContent = body.replace("- [ ] Task $suffix", "- [x] Task $suffix")

        waitForTag("note_card_$noteId")
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        composeRule.onNodeWithTag("text_note_read_checkbox_1").assertIsDisplayed().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            noteById(noteId)?.textContent == expectedContent
        }
        val savedNote = noteById(noteId)
        val savedRanges = TextFormattingJson.decode(
            savedNote?.textFormattingJson,
            savedNote?.textContent.orEmpty().length,
        )
        assertEquals(expectedContent, savedNote?.textContent)
        assertEquals(listOf(expectedRange), savedRanges)
        assertEquals("Formatted", savedNote?.textContent?.substring(savedRanges.single().start, savedRanges.single().end))
    }

    @Test
    fun checkboxLabelTapEntersEditModeAtBodyText() {
        val suffix = System.currentTimeMillis()
        val label = "Tap label $suffix"
        val noteId = createTextNote(title = "Label tap $suffix", body = "- [ ] $label")

        waitForTag("note_card_$noteId")
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        composeRule.onNodeWithTag("text_note_read_checkbox_0").assertIsDisplayed()
        composeRule.onNodeWithText(label, useUnmergedTree = true)
            .performTouchInput {
                down(center)
                up()
            }

        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .assertTextContains("- [ ] $label", substring = true)
            .assertIsFocused()
    }

    @Test
    fun uppercaseMarkdownCheckboxRendersCheckedAndTogglesUnchecked() {
        val suffix = System.currentTimeMillis()
        val title = "Upper checkbox $suffix"
        val noteId = createTextNote(title = title, body = "- [X] Upper task $suffix")

        waitForTag("note_card_$noteId")
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        val checkboxNode = composeRule.onNodeWithTag("text_note_read_checkbox_0")
        checkboxNode.assertIsDisplayed()
        assertEquals(
            ToggleableState.On,
            checkboxNode.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState),
        )
        checkboxNode.performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            noteTextContent(noteId).orEmpty().contains("- [ ] Upper task $suffix")
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
        composeRule.onNodeWithTag("clear_find_in_note_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("find_in_note_bar") == 0
        }
    }

    @Test
    fun findInNoteNextScrollsReadViewportAndNavigatesEditMatches() {
        val suffix = System.currentTimeMillis()
        val title = "Find scroll title $suffix"
        val filler = (1..90).joinToString(separator = "\n") { index ->
            "filler line $index keeps the next match below the visible area"
        }
        val body = "needle top\n$filler\nneedle bottom"
        val beforeIds = noteIds()

        openAddMenuItem("new_text_note_menu_item")
        showTextNoteMetadata()
        composeRule.onNodeWithTag("text_note_title").performTextInput(title)
        composeRule.onNodeWithTag("text_note_content").performTextReplacement(body)
        composeRule.onNodeWithTag("back_button").performClick()

        val noteId = waitForSingleNewNoteId(beforeIds)
        waitForTag("note_card_$noteId")

        composeRule.onNodeWithTag("note_card_$noteId").performClick()
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
    fun settingsExposeGoogleSyncByDefaultAndManualBackupControls() {
        composeRule.onNodeWithTag("settings_button").performClick()

        composeRule.onNodeWithTag("google_account_sync_title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("google_account_status").performScrollTo().assertTextContains("Not signed in")
        assertTagAbsent("google_last_sync_status")
        assertTagAbsent("google_sync_progress")
        assertTagAbsent("google_sync_error")
        composeRule.onNodeWithTag("google_sync_button").performScrollTo().assertTextContains("Sign in with Google")
        assertTagAbsent("google_sign_out_button")

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
    fun settingsDoesNotExposeDeadGoogleSyncDebugSwitch() {
        composeRule.onNodeWithTag("settings_button").performClick()

        assertTagAbsent("debug_google_sync_entry_switch")
        composeRule.onNodeWithTag("google_account_sync_title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("google_account_status").performScrollTo().assertTextContains("Not signed in")
        composeRule.onNodeWithTag("google_sync_button").performScrollTo().assertTextContains("Sign in with Google")
        assertTagAbsent("google_sign_out_button")
    }

    @Test
    fun premiumFallbackHidesCommerceAndShowsAllowedBenefits() {
        composeRule.onNodeWithTag("premium_tab").performClick()

        composeRule.onNodeWithTag("premium_screen").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("premium_preview_title").fetchSemanticsNodes().isNotEmpty()
        }
        assertTagAbsent("annual_plan_option")
        assertTagAbsent("monthly_plan_option")
        assertTagAbsent("premium_subscribe_button")
        assertTagAbsent("premium_restore_button")
        composeRule.onNodeWithTag("premium_preview_title").assertIsDisplayed()
        composeRule.onNodeWithTag("premium_preview_body").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Price not available").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Subscribe (setup pending)").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("No trial or introductory offer is configured.").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Privacy Policy").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Terms of Service").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Google Play", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("backend verification", substring = true).fetchSemanticsNodes().size)
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
    fun fabSingleTapCreatesFocusedTextNoteAndOptionsMenuKeepsAlternateTypes() {
        val beforeIds = noteIds()

        composeRule.onNodeWithTag("add_note_button").performClick()
        waitForTag("text_note_content")
        composeRule.onNodeWithTag("text_note_content").assertIsFocused()
        waitForSingleNewNoteId(beforeIds)

        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("add_note_button") > 0 && noteIds() == beforeIds
        }

        composeRule.onNodeWithTag("add_note_options_button").performClick()
        waitForTag("new_checklist_note_menu_item")
        composeRule.onNodeWithTag("new_drawing_note_menu_item").assertIsDisplayed()
        composeRule.onNodeWithTag("ocr_from_image_menu_item").assertIsDisplayed()
        assertTagAbsent("new_text_note_menu_item")
    }

    @Test
    fun addMenuShowsOcrFromImageAction() {
        composeRule.onNodeWithTag("add_note_options_button").performClick()
        waitForTag("ocr_from_image_menu_item")

        composeRule.onNodeWithTag("ocr_from_image_menu_item").assertIsDisplayed()
        assertTagAbsent("new_text_note_menu_item")
    }

    @Test
    fun newBlankDrawingHardwareBackExitsFullscreenThenDeletesDraftWithoutTombstone() {
        val beforeIds = noteIds()
        val beforeTombstones = noteTombstoneCount()

        openAddMenuItem("new_drawing_note_menu_item")
        val draftId = waitForSingleNewNoteId(beforeIds)
        waitForTag("fullscreen_drawing_mode")
        waitForTag("drawing_fullscreen_details_button")
        assertTagAbsent("drawing_note_save_status")
        assertEquals(0, composeRule.onAllNodesWithText("Untitled drawing").fetchSemanticsNodes().size)

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForTag("drawing_note_title")
        assertTrue(noteById(draftId) != null)

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("add_note_button") > 0 && noteIds() == beforeIds
        }

        assertNull(noteById(draftId))
        assertEquals(beforeTombstones, noteTombstoneCount())
    }

    @Test
    fun newDrawingDraftWithTitleIsKeptAfterBack() {
        val beforeIds = noteIds()
        val title = "Drawing title ${System.currentTimeMillis()}"

        openAddMenuItem("new_drawing_note_menu_item")
        val noteId = waitForSingleNewNoteId(beforeIds)
        exitInitialDrawingFocusModeIfNeeded()
        composeRule.onNodeWithTag("drawing_note_title")
            .assertIsDisplayed()
            .performTextInput(title)
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("add_note_button") > 0 && noteById(noteId)?.title == title
        }
        composeRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun newDrawingDraftWithEraserOnlyStrokeIsKeptAfterBack() {
        val beforeIds = noteIds()

        openAddMenuItem("new_drawing_note_menu_item")
        val noteId = waitForSingleNewNoteId(beforeIds)
        waitForTag("fullscreen_drawing_mode")
        composeRule.onNodeWithTag("drawing_tool_Eraser").assertIsDisplayed().performClick()
        drawShortStrokeOnFullscreenCanvas()

        pressActivityBack()
        waitForTag("drawing_note_title")
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val savedStrokes = drawingStrokes(noteId)
            tagCount("add_note_button") > 0 &&
                savedStrokes.isNotEmpty() &&
                savedStrokes.all { it.tool == DrawingTools.ERASER }
        }
    }

    @Test
    fun existingBlankDrawingOpenedFromListIsKeptAfterBack() {
        waitForTag("add_note_button")
        val noteId = createDrawingNote()

        waitForTag("note_card_$noteId")
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        waitForTag("fullscreen_drawing_mode")

        pressActivityBack()
        waitForTag("drawing_note_title")
        composeRule.onNodeWithTag("back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("add_note_button") > 0 && noteById(noteId) != null
        }
        assertEquals(emptyList<DrawingStroke>(), drawingStrokes(noteId))
    }

    @Test
    fun drawingSaveDoesNotOverwriteNewerExternalDrawingUpdate() {
        val originalTitle = "Original drawing ${System.currentTimeMillis()}"
        val localTitle = "Local stale ${System.currentTimeMillis()}"
        val remoteTitle = "Remote newer ${System.currentTimeMillis()}"
        val originalDrawing = encodedTestStroke(4f, 8f)
        val remoteDrawing = encodedTestStroke(40f, 56f)
        val noteId = createDrawingNote(title = originalTitle, drawingData = originalDrawing)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("note_card_$noteId") > 0
        }
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        exitInitialDrawingFocusModeIfNeeded()

        DebugSaveFailure.delayNextDrawingSave(noteId, 1_500L)
        composeRule.onNodeWithTag("drawing_note_title").performTextReplacement(localTitle)
        replaceDrawingNoteRow(noteId, title = remoteTitle, drawingData = remoteDrawing)

        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag("drawing_note_title").assertTextContains(remoteTitle)
                composeRule.onNodeWithTag("drawing_note_save_status").assertTextContains("Saved")
                assertTagAbsent("drawing_note_retry_save_button")
            }.isSuccess
        }

        val saved = noteById(noteId)
        assertEquals(remoteTitle, saved?.title)
        assertEquals(remoteDrawing, saved?.drawingData)
    }

    @Test
    fun drawingSaveAfterMetadataMoveKeepsLocalContentAndNewFolder() {
        resetFoldersToDefault()
        enableDebugPremiumAccess()
        val folderName = "Sketch folder ${System.currentTimeMillis()}"
        val folderId = createFolder(folderName)
        val localTitle = "Metadata local ${System.currentTimeMillis()}"
        val beforeIds = noteIds()

        openAddMenuItem("new_drawing_note_menu_item")
        val noteId = waitForSingleNewNoteId(beforeIds)
        exitInitialDrawingFocusModeIfNeeded()

        DebugSaveFailure.delayNextDrawingSave(noteId, 1_500L)
        composeRule.onNodeWithTag("drawing_note_title").performTextInput(localTitle)
        composeRule.onNodeWithTag("note_folder_selector_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithText(folderName).assertIsDisplayed().performClick()
        waitForNoteFolder(noteId, folderId)

        composeRule.waitUntil(timeoutMillis = 15_000) {
            val saved = noteById(noteId)
            saved?.title == localTitle &&
                saved.folderId == folderId &&
                saved.drawingData == "[]"
        }
    }

    @Test
    fun deletedOpenDrawingNoteHardwareBackNavigatesToList() {
        val beforeIds = noteIds()

        openAddMenuItem("new_drawing_note_menu_item")
        val noteId = waitForSingleNewNoteId(beforeIds)
        waitForTag("fullscreen_drawing_mode")
        deleteNoteRow(noteId)

        waitForTag("drawing_note_not_found")
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("add_note_button") > 0
        }
    }

    @Test
    fun metadataIntentKeepsOtherwiseBlankNewDrawingDraft() {
        resetFoldersToDefault()
        enableDebugPremiumAccess()
        val beforeIds = noteIds()
        val folderName = "Drawing metadata ${System.currentTimeMillis()}"
        val folderId = createFolder(folderName)

        openAddMenuItem("new_drawing_note_menu_item")
        val noteId = waitForSingleNewNoteId(beforeIds)
        exitInitialDrawingFocusModeIfNeeded()
        composeRule.onNodeWithTag("note_folder_selector_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithText(folderName).assertIsDisplayed().performClick()
        waitForNoteFolder(noteId, folderId)

        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("add_note_button") > 0 && noteById(noteId)?.folderId == folderId
        }
        assertEquals("", noteById(noteId)?.title)
        assertEquals(emptyList<DrawingStroke>(), drawingStrokes(noteId))
    }

    @Test
    fun openingFolderMenuWithoutMoveStillDeletesBlankDrawingDraft() {
        resetFoldersToDefault()
        enableDebugPremiumAccess()
        val beforeIds = noteIds()
        val folderName = "Unused folder ${System.currentTimeMillis()}"
        createFolder(folderName)

        openAddMenuItem("new_drawing_note_menu_item")
        val noteId = waitForSingleNewNoteId(beforeIds)
        exitInitialDrawingFocusModeIfNeeded()
        composeRule.onNodeWithTag("note_folder_selector_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithText(folderName).assertIsDisplayed()
        composeRule.onNodeWithTag("drawing_note_title").performClick()

        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("add_note_button") > 0 && noteIds() == beforeIds
        }

        assertNull(noteById(noteId))
    }

    @Test
    fun drawingTitleAndStrokeShowSavedStatusAndPersistAfterReopen() {
        val beforeIds = noteIds()
        val title = "Saved drawing ${System.currentTimeMillis()}"

        openAddMenuItem("new_drawing_note_menu_item")
        val noteId = waitForSingleNewNoteId(beforeIds)
        waitForTag("fullscreen_drawing_mode")
        drawShortStrokeOnFullscreenCanvas()
        exitFullscreenDrawingFromUi()
        composeRule.onNodeWithTag("drawing_note_title").performTextInput(title)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            noteById(noteId)?.title == title && drawingStrokes(noteId).isNotEmpty()
        }
        composeRule.onNodeWithTag("drawing_note_save_status").assertTextContains("Saved", substring = true)
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("add_note_button") > 0
        }

        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        waitForTag("fullscreen_drawing_mode")
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithTag("drawing_note_save_status").assertTextContains("Saved", substring = true)
        composeRule.onNodeWithTag("drawing_fullscreen_details_button").assertIsDisplayed().performClick()
        waitForTag("drawing_note_title")
        assertEquals(title, noteById(noteId)?.title)
        assertTrue(drawingStrokes(noteId).isNotEmpty())
    }

    @Test
    fun drawingNoteThumbnailAppearsForSavedStrokeAndOpensNote() {
        val noteId = createDrawingNote(drawingData = encodedTestStroke(16f, 24f))

        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("note_card_$noteId") > 0
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("drawing_note_thumbnail_$noteId", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("drawing_note_thumbnail_$noteId", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("note_card_$noteId").performClick()
        waitForTag("fullscreen_drawing_mode")
        composeRule.onNodeWithTag("drawing_fullscreen_details_button").assertIsDisplayed().performClick()
        waitForTag("drawing_note_title")
        composeRule.onNodeWithTag("share_drawing_png_button").assertIsDisplayed()
        composeRule.onNodeWithTag("export_drawing_png_button").assertIsDisplayed()
        assertTrue(drawingStrokes(noteId).isNotEmpty())
    }

    @Test
    fun titleOnlyDrawingReopensInDetailsMode() {
        val title = "Title only drawing ${System.currentTimeMillis()}"
        val noteId = createDrawingNote(title = title)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("note_card_$noteId") > 0
        }
        composeRule.onNodeWithTag("note_card_$noteId").performClick()

        waitForTag("drawing_note_title")
        assertTagAbsent("fullscreen_drawing_mode")
        composeRule.onNodeWithTag("drawing_note_title").assertTextContains(title)
        assertEquals(emptyList<DrawingStroke>(), drawingStrokes(noteId))
    }

    @Test
    fun blankDrawingInitialFullscreenIsCleanAndDetailsOpensNormalMode() {
        val beforeIds = noteIds()

        openAddMenuItem("new_drawing_note_menu_item")
        val noteId = waitForSingleNewNoteId(beforeIds)
        waitForTag("fullscreen_drawing_mode")

        assertTagAbsent("drawing_note_save_status")
        assertEquals(0, composeRule.onAllNodesWithText("Untitled drawing").fetchSemanticsNodes().size)
        assertIconControl("drawing_fullscreen_details_button", "Details")

        composeRule.onNodeWithTag("drawing_fullscreen_details_button").performClick()
        waitForTag("drawing_note_title")
        composeRule.onNodeWithTag("share_drawing_png_button").assertIsDisplayed()
        composeRule.onNodeWithTag("export_drawing_png_button").assertIsDisplayed()

        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("add_note_button") > 0 && noteIds() == beforeIds
        }
        assertNull(noteById(noteId))
    }

    @Test
    fun drawingShareExportControlsDisableWhileRenderingAndFailedSaveStopsShare() {
        val beforeIds = noteIds()
        val title = "Drawing share busy ${System.currentTimeMillis()}"

        openAddMenuItem("new_drawing_note_menu_item")
        val noteId = waitForSingleNewNoteId(beforeIds)
        exitInitialDrawingFocusModeIfNeeded()
        composeRule.onNodeWithTag("drawing_note_title").performTextInput(title)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            noteById(noteId)?.title == title
        }

        DebugSaveFailure.delayNextDrawingSave(noteId, 3_000L)
        DebugSaveFailure.failNextDrawingSave(noteId)
        composeRule.onNodeWithTag("share_drawing_png_button").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("share_drawing_png_button").assertIsNotEnabled()
                composeRule.onNodeWithTag("export_drawing_png_button").assertIsNotEnabled()
            }.isSuccess
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("drawing_note_retry_save_button") > 0
        }
        composeRule.onNodeWithTag("drawing_note_save_status").assertTextContains("Save failed")
    }

    @Test
    fun drawingEditorShowsUpgradedDrawingTools() {
        openAddMenuItem("new_drawing_note_menu_item")
        waitForTag("exit_fullscreen_drawing_button")
        composeRule.onNodeWithTag("exit_fullscreen_drawing_button").performClick()
        waitForTag("drawing_undo_button")

        assertIconControl("drawing_fullscreen_button", "Full-screen writing")
        assertIconControl("drawing_undo_button", "Undo")
        assertIconControl("drawing_redo_button", "Redo")
        assertIconControl("drawing_clear_button", "Clear drawing")
        assertIconControl("share_drawing_png_button", "Share PNG")
        assertIconControl("export_drawing_png_button", "Export PNG")
        composeRule.onNodeWithTag("drawing_tool_Pen").performScrollTo().assertIsDisplayed()
        assertTaggedContentDescription("drawing_tool_Pen", "Pen")
        assertTaggedTouchTargetAtLeast48Dp("drawing_tool_Pen")
        assertTaggedSelected("drawing_tool_Pen", true)
        composeRule.onNodeWithTag("drawing_tool_Eraser").performScrollTo().assertIsDisplayed()
        assertTaggedContentDescription("drawing_tool_Eraser", "Eraser")
        assertTaggedTouchTargetAtLeast48Dp("drawing_tool_Eraser")
        assertTaggedSelected("drawing_tool_Eraser", false)
        composeRule.onNodeWithTag("drawing_brush_Thin").performScrollTo().assertIsDisplayed()
        assertTaggedContentDescription("drawing_brush_Thin", "Thin")
        assertTaggedTouchTargetAtLeast48Dp("drawing_brush_Thin")
        composeRule.onNodeWithTag("drawing_color_Red").performScrollTo().assertIsDisplayed()
        assertTaggedContentDescription("drawing_color_Red", "Red")
        assertTaggedTouchTargetAtLeast48Dp("drawing_color_Red")
        composeRule.onNodeWithTag("drawing_tool_Eraser").performScrollTo().performClick()
        assertTaggedSelected("drawing_tool_Eraser", true)
        composeRule.onNodeWithTag("drawing_eraser_hint").assertIsDisplayed()
    }

    @Test
    fun drawingEditorRemembersLastPenColorAndSizeButStartsWithPen() {
        val beforeIds = noteIds()

        openAddMenuItem("new_drawing_note_menu_item")
        val firstDraftId = waitForSingleNewNoteId(beforeIds)
        waitForTag("fullscreen_drawing_mode")

        composeRule.onNodeWithTag("drawing_brush_Thin").performScrollTo().performClick()
        assertTaggedSelected("drawing_brush_Thin", true)
        composeRule.onNodeWithTag("drawing_color_Red").performScrollTo().performClick()
        assertTaggedSelected("drawing_color_Red", true)
        composeRule.onNodeWithTag("drawing_tool_Eraser").performScrollTo().performClick()
        assertTaggedSelected("drawing_tool_Eraser", true)
        composeRule.onNodeWithTag("drawing_brush_Thick").performScrollTo().performClick()
        assertTaggedSelected("drawing_brush_Thick", true)
        composeRule.onNodeWithTag("drawing_tool_Pen").performScrollTo().performClick()
        assertTaggedSelected("drawing_tool_Pen", true)
        assertTaggedSelected("drawing_brush_Thin", true)
        composeRule.onNodeWithTag("drawing_tool_Eraser").performScrollTo().performClick()
        assertTaggedSelected("drawing_tool_Eraser", true)

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForTag("drawing_note_title")
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("add_note_button") > 0 && noteIds() == beforeIds
        }
        assertNull(noteById(firstDraftId))

        openAddMenuItem("new_drawing_note_menu_item")
        waitForTag("fullscreen_drawing_mode")

        assertTaggedSelected("drawing_tool_Pen", true)
        assertTaggedSelected("drawing_tool_Eraser", false)
        composeRule.onNodeWithTag("drawing_brush_Thin").performScrollTo().assertIsDisplayed()
        assertTaggedSelected("drawing_brush_Thin", true)
        composeRule.onNodeWithTag("drawing_color_Red").performScrollTo().assertIsDisplayed()
        assertTaggedSelected("drawing_color_Red", true)
    }

    @Test
    fun drawingEditorCanUseFullscreenCanvasMode() {
        openAddMenuItem("new_drawing_note_menu_item")
        waitForTag("fullscreen_drawing_mode")

        composeRule.onNodeWithTag("fullscreen_drawing_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("exit_fullscreen_drawing_button").assertIsDisplayed()
        composeRule.onNodeWithTag("drawing_fullscreen_details_button").assertIsDisplayed()
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
        composeRule.onNodeWithTag("drawing_fullscreen_details_button").assertIsDisplayed()
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
        val noteId = runBlocking {
            withContext(Dispatchers.IO) {
                NotepadDatabase.getInstance(composeRule.activity)
                    .notepadDao()
                    .getAllNotes()
                    .first { note -> note.title == title }
                    .id
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("note_preview_$noteId", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("note_preview_$noteId", useUnmergedTree = true)
            .assertTextContains(contentNeedle, substring = true)
    }

    @Test
    fun mainScreenShowsContentFirstHomeCardWithOverflowActions() {
        val suffix = System.currentTimeMillis()
        val title = "Header scan note $suffix"
        val body = "Two line preview first $suffix\nTwo line preview second $suffix"

        composeRule.onNodeWithTag("knowledge_header").assertIsDisplayed()
        composeRule.onNodeWithTag("note_result_count").assertIsDisplayed()

        val noteId = createTextNote(title = title, body = body)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("note_card_$noteId") > 0
        }
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("note_preview_$noteId", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("note_preview_$noteId", useUnmergedTree = true)
            .assertTextContains("Two line preview first", substring = true)
        composeRule.onNodeWithTag("note_relative_updated_$noteId", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        assertTagAbsent("note_type_chip")
        assertTagAbsent("pin_note_$noteId")
        assertTagAbsent("move_note_$noteId")
        assertTagAbsent("delete_note_$noteId")

        composeRule.onNodeWithTag("note_more_$noteId").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("pin_note_$noteId").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_note_$noteId").assertIsDisplayed()
    }

    @Test
    fun trashCardOverflowExposesRestoreAndPermanentDeleteActions() {
        val suffix = System.currentTimeMillis()
        val title = "Trash overflow note $suffix"
        val noteId = createTextNote(title = title, body = "Trash overflow body $suffix")
        softDeleteNote(noteId)

        composeRule.onNodeWithTag("trash_filter").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            tagCount("note_card_$noteId") > 0
        }
        assertTagAbsent("note_restore_$noteId")
        assertTagAbsent("note_permanent_delete_$noteId")

        composeRule.onNodeWithTag("note_more_$noteId").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("note_restore_$noteId").assertIsDisplayed()
        composeRule.onNodeWithTag("note_permanent_delete_$noteId").assertIsDisplayed()
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

}
