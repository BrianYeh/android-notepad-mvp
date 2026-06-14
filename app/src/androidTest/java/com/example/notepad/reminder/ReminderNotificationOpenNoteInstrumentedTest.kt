package com.example.notepad.reminder

import android.content.Context
import android.content.Intent
import android.app.KeyguardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.notepad.MainActivity
import com.example.notepad.data.NotepadDatabase
import com.example.notepad.data.NotepadRepository
import com.example.notepad.data.PrivacyPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderNotificationOpenNoteInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val createdNoteIds = mutableSetOf<Long>()

    @Before
    fun disablePrivacyLock() {
        setPrivacyLockRequired(false)
    }

    @After
    fun cleanUp() {
        setPrivacyLockRequired(false)
        runBlocking {
            withContext(Dispatchers.IO) {
                val dao = NotepadDatabase.getInstance(context).notepadDao()
                createdNoteIds.forEach { noteId -> dao.deleteNote(noteId) }
            }
        }
    }

    @Test
    fun reminderOpenIntentContractUsesExpectedActionAndExtraNames() {
        assertEquals("com.brianyeh.justnotes.reminder.OPEN_NOTE", ACTION_REMINDER_OPEN_NOTE)
        assertEquals("com.brianyeh.justnotes.reminder.NOTE_ID", EXTRA_REMINDER_NOTE_ID)
    }

    @Test
    fun coldStartReminderIntentOpensExactTextNote() {
        val suffix = System.currentTimeMillis()
        val note = createTextNote(
            title = "Reminder cold title $suffix",
            body = "Reminder cold body $suffix",
        )

        val scenario = ActivityScenario.launch<MainActivity>(reminderOpenIntent(context, note.id))
        try {
            waitForOpenTextNote(note.title, note.body)
        } finally {
            scenario.finishActivity()
        }
    }

    @Test
    fun runningSingleTopActivityReceivesReminderIntentAndOpensExactTextNote() {
        val suffix = System.currentTimeMillis()
        val firstNote = createTextNote(
            title = "Reminder first title $suffix",
            body = "Reminder first body $suffix",
        )
        val secondNote = createTextNote(
            title = "Reminder second title $suffix",
            body = "Reminder second body $suffix",
        )

        val scenario = ActivityScenario.launch<MainActivity>(reminderOpenIntent(context, firstNote.id))
        try {
            waitForOpenTextNote(firstNote.title, firstNote.body)

            scenario.onActivity { activity ->
                activity.startActivity(reminderOpenIntent(activity, secondNote.id))
            }

            waitForOpenTextNote(secondNote.title, secondNote.body)
        } finally {
            scenario.finishActivity()
        }
    }

    @Test
    fun missingOrDeletedReminderNoteIdStaysOnMainList() {
        val suffix = System.currentTimeMillis()
        val activeNote = createTextNote(
            title = "Reminder active title $suffix",
            body = "Reminder active body $suffix",
        )
        val deletedNote = createTextNote(
            title = "Reminder deleted title $suffix",
            body = "Reminder deleted body $suffix",
        )
        deleteNote(deletedNote.id)
        val missingNoteId = Long.MAX_VALUE - suffix % 1_000_000L

        val scenario = ActivityScenario.launch<MainActivity>(reminderOpenIntent(context, activeNote.id))
        try {
            waitForOpenTextNote(activeNote.title, activeNote.body)

            scenario.onActivity { activity ->
                activity.startActivity(reminderOpenIntent(activity, missingNoteId))
            }

            waitForMainListWithoutTextEditor()

            scenario.onActivity { activity ->
                activity.startActivity(reminderOpenIntent(activity, activeNote.id))
            }

            waitForOpenTextNote(activeNote.title, activeNote.body)

            scenario.onActivity { activity ->
                activity.startActivity(reminderOpenIntent(activity, deletedNote.id))
            }

            waitForMainListWithoutTextEditor()
            assertTagAbsent("note_card_${deletedNote.id}")
        } finally {
            scenario.finishActivity()
        }
    }

    @Test
    fun reminderIntentDoesNotRevealNoteWhilePrivacyLocked() {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        assumeFalse("System credential UI is not stable for this instrumentation test", keyguardManager.isDeviceSecure)
        val suffix = System.currentTimeMillis()
        val note = createTextNote(
            title = "Reminder locked title $suffix",
            body = "Reminder locked body $suffix",
        )
        setPrivacyLockRequired(true)

        val scenario = ActivityScenario.launch<MainActivity>(reminderOpenIntent(context, note.id))
        try {
            waitForTag("privacy_unlock_button")
            assertTagAbsent("text_note_read_mode")
            assertTextAbsent(note.title)
            assertTextAbsent(note.body)
        } finally {
            scenario.finishActivity()
        }
    }

    private fun reminderOpenIntent(context: Context, noteId: Long): Intent {
        return Intent(context, MainActivity::class.java)
            .setAction(ACTION_REMINDER_OPEN_NOTE)
            .putExtra(EXTRA_REMINDER_NOTE_ID, noteId)
    }

    private fun createTextNote(title: String, body: String): TestNote {
        return runBlocking {
            withContext(Dispatchers.IO) {
                val repository = NotepadRepository(NotepadDatabase.getInstance(context).notepadDao())
                repository.ensureDefaultFolder()
                val noteId = repository.createTextNote(folderId = null)
                createdNoteIds += noteId
                repository.saveTextNote(noteId, title, body)
                TestNote(id = noteId, title = title, body = body)
            }
        }
    }

    private fun deleteNote(noteId: Long) {
        runBlocking {
            withContext(Dispatchers.IO) {
                NotepadRepository(NotepadDatabase.getInstance(context).notepadDao()).deleteNote(noteId)
            }
        }
    }

    private fun waitForOpenTextNote(title: String, body: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("text_note_read_title").assertTextContains(title)
                composeRule.onNodeWithTag("text_note_read_content").assertTextContains(body)
            }.isSuccess
        }
        composeRule.onNodeWithTag("text_note_read_title").assertTextContains(title)
        composeRule.onNodeWithTag("text_note_read_content").assertTextContains(body)
    }

    private fun waitForMainList() {
        waitForTag("add_note_button")
        composeRule.onNodeWithTag("add_note_button").assertIsDisplayed()
    }

    private fun waitForMainListWithoutTextEditor() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("add_note_button").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("text_note_read_mode").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("text_note_content").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNoTextEditor() {
        assertTagAbsent("text_note_read_mode")
        assertTagAbsent("text_note_content")
    }

    private fun assertTagAbsent(tag: String) {
        assertEquals(0, composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size)
    }

    private fun assertTextAbsent(text: String) {
        assertEquals(0, composeRule.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().size)
    }

    private fun setPrivacyLockRequired(required: Boolean) {
        context.getSharedPreferences(PrivacyPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PrivacyPreferences.REQUIRE_DEVICE_UNLOCK_KEY, required)
            .apply()
    }

    private fun ActivityScenario<MainActivity>.finishActivity() {
        runCatching {
            onActivity { activity -> activity.finish() }
        }
    }

    private data class TestNote(
        val id: Long,
        val title: String,
        val body: String,
    )
}
