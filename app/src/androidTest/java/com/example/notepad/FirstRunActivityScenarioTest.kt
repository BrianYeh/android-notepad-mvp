package com.example.notepad

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.notepad.data.FirstRunPreferences
import com.example.notepad.data.NotepadDatabase
import com.example.notepad.data.NotepadRepository
import com.example.notepad.data.NoteTombstoneEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstRunActivityScenarioTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun resetAppState() {
        closeScenario()
        clearNotes()
        clearFirstRunCompletion()
    }

    @After
    fun restoreCompletedState() {
        closeScenario()
        FirstRunPreferences.markCompleted(context)
    }

    @Test
    fun cleanColdStartSkipPersistsAndRelaunchShowsStarterHub() {
        launch()
        waitForTag("first_run_welcome")
        composeRule.onNodeWithTag("first_run_skip").performClick()
        waitForTag("starter_hub")

        relaunch()

        waitForTag("starter_hub")
        assertTagAbsent("first_run_welcome")
    }

    @Test
    fun coldStartNewNoteCompletesWelcomeAndDoesNotReturnAfterRelaunch() {
        launch()
        waitForTag("first_run_welcome")
        composeRule.onNodeWithTag("first_run_new_note").performClick()
        waitForTag("text_note_content")
        composeRule.onNodeWithTag("back_button").performClick()
        waitForTag("starter_hub")

        relaunch()

        waitForTag("starter_hub")
        assertTagAbsent("first_run_welcome")
    }

    @Test
    fun coldStartTemplateCompletesWelcomeAndCreatesEditableOrdinaryNote() {
        launch()
        waitForTag("first_run_welcome")
        composeRule.onNodeWithTag("first_run_choose_template").performClick()
        waitForTag("template_picker")
        composeRule.onNodeWithTag("template_DailyChecklist").performClick()
        waitForTag("text_note_content")

        relaunch()

        assertTagAbsent("first_run_welcome")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Daily checklist").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("每日待辦").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun deletingAllNotesAfterCompletionShowsStarterHubWithoutOnboarding() {
        FirstRunPreferences.markCompleted(context)
        createExistingNote("Delete-all upgrade note")
        launch()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Delete-all upgrade note").fetchSemanticsNodes().isNotEmpty()
        }
        closeScenario()
        clearNotes()

        launch()

        waitForTag("starter_hub")
        assertTagAbsent("first_run_welcome")
    }

    @Test
    fun existingDataUpgradeAutoCompletesFirstRunWithoutShowingWelcome() {
        createExistingNote("Existing user note")

        launch()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Existing user note").fetchSemanticsNodes().isNotEmpty()
        }
        assertTagAbsent("first_run_welcome")
        assertTrue(FirstRunPreferences.hasCompleted(context))
    }

    @Test
    fun deletedOnlyExistingDataUpgradeAutoCompletesWithoutShowingWelcome() {
        val noteId = createExistingNote("Existing trashed note")
        deleteExistingNote(noteId)

        launch()

        waitForTag("starter_hub")
        assertTagAbsent("first_run_welcome")
        assertTrue(FirstRunPreferences.hasCompleted(context))
    }

    @Test
    fun nonDefaultFolderOnlyUpgradeAutoCompletesWithoutShowingWelcome() {
        createExistingFolder("Existing empty folder")

        launch()

        waitForTag("starter_hub")
        assertTagAbsent("first_run_welcome")
        assertTrue(FirstRunPreferences.hasCompleted(context))
    }

    @Test
    fun tombstoneOnlyUpgradeAutoCompletesWithoutShowingWelcome() {
        createExistingTombstone()

        launch()

        waitForTag("starter_hub")
        assertTagAbsent("first_run_welcome")
        assertTrue(FirstRunPreferences.hasCompleted(context))
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun relaunch() {
        closeScenario()
        launch()
    }

    private fun closeScenario() {
        scenario?.close()
        scenario = null
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertTagAbsent(tag: String) {
        assertTrue(composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty())
    }

    private fun clearFirstRunCompletion() {
        context.getSharedPreferences(FirstRunPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun clearNotes() {
        runBlocking {
            withContext(Dispatchers.IO) {
                NotepadDatabase.getInstance(context).clearAllTables()
            }
        }
    }

    private fun createExistingNote(title: String): Long {
        return runBlocking {
            withContext(Dispatchers.IO) {
                val repository = NotepadRepository(NotepadDatabase.getInstance(context).notepadDao())
                val noteId = repository.createTextNote(null)
                repository.saveTextNote(noteId, title, "Existing content")
                noteId
            }
        }
    }

    private fun deleteExistingNote(noteId: Long) {
        runBlocking {
            withContext(Dispatchers.IO) {
                val repository = NotepadRepository(NotepadDatabase.getInstance(context).notepadDao())
                repository.deleteNote(noteId)
            }
        }
    }

    private fun createExistingFolder(name: String) {
        runBlocking {
            withContext(Dispatchers.IO) {
                val repository = NotepadRepository(NotepadDatabase.getInstance(context).notepadDao())
                repository.createFolder(name)
            }
        }
    }

    private fun createExistingTombstone() {
        runBlocking {
            withContext(Dispatchers.IO) {
                NotepadDatabase.getInstance(context)
                    .notepadDao()
                    .upsertNoteTombstones(
                        listOf(
                            NoteTombstoneEntity(
                                syncId = "existing-tombstone",
                                deletedAt = System.currentTimeMillis(),
                            ),
                        ),
                    )
            }
        }
    }
}
