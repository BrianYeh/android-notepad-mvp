package com.example.notepad.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotepadDatabaseTest {
    private lateinit var database: NotepadDatabase
    private lateinit var dao: NotepadDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotepadDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.notepadDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deletingFolderMovesNotesToUncategorized() = runTest {
        dao.ensureDefaultFolder(now = 1L)
        val folderId = dao.insertFolder(
            FolderEntity(
                name = "Work",
                createdAt = 2L,
                updatedAt = 2L,
            ),
        )
        val noteId = dao.insertNote(
            NoteEntity(
                folderId = folderId,
                type = NoteTypes.TEXT,
                title = "Plan",
                textContent = "Draft",
                drawingData = null,
                createdAt = 3L,
                updatedAt = 3L,
            ),
        )

        dao.deleteFolderAndMoveNotes(folderId = folderId, now = 4L)

        assertNull(dao.getFolder(folderId))
        assertEquals(DEFAULT_FOLDER_ID, dao.getNote(noteId)?.folderId)
    }

    @Test
    fun drawingJsonRoundTripsStrokeData() {
        val strokes = listOf(
            DrawingStroke(
                points = listOf(
                    DrawingPoint(1.5f, 2.5f),
                    DrawingPoint(3.5f, 4.5f),
                ),
            ),
        )

        assertEquals(strokes, DrawingJson.decode(DrawingJson.encode(strokes)))
    }

    @Test
    fun backupJsonRestoresFoldersAndNotes() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val folderId = dao.insertFolder(
            FolderEntity(
                name = "Projects",
                createdAt = 2L,
                updatedAt = 2L,
            ),
        )
        val noteId = dao.insertNote(
            NoteEntity(
                folderId = folderId,
                type = NoteTypes.TEXT,
                title = "Backup note",
                textContent = "Draft",
                drawingData = null,
                createdAt = 3L,
                updatedAt = 3L,
            ),
        )
        val backupJson = repository.exportBackupJson()

        val extraFolderId = dao.insertFolder(
            FolderEntity(
                name = "Temporary",
                createdAt = 4L,
                updatedAt = 4L,
            ),
        )
        dao.insertNote(
            NoteEntity(
                folderId = extraFolderId,
                type = NoteTypes.TEXT,
                title = "Should be replaced",
                textContent = "Old",
                drawingData = null,
                createdAt = 5L,
                updatedAt = 5L,
            ),
        )

        repository.importBackupJson(backupJson)

        assertEquals(listOf(DEFAULT_FOLDER_ID, folderId), dao.getAllFolders().map { it.id })
        assertEquals(noteId, dao.getAllNotes().single().id)
        assertEquals("Draft", dao.getAllNotes().single().textContent)
    }

    @Test
    fun softDeletedNoteCanBeRestoredOrPermanentlyDeleted() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val noteId = repository.createTextNote(DEFAULT_FOLDER_ID)

        repository.deleteNote(noteId)

        val deletedNote = dao.getNote(noteId)
        assertEquals(true, deletedNote?.isDeleted)

        repository.restoreNote(noteId)

        val restoredNote = dao.getNote(noteId)
        assertEquals(false, restoredNote?.isDeleted)
        assertNull(restoredNote?.deletedAt)

        repository.deleteNote(noteId)
        repository.permanentlyDeleteNote(noteId)

        assertNull(dao.getNote(noteId))
    }

    @Test
    fun notePinnedStatePersists() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val noteId = repository.createTextNote(DEFAULT_FOLDER_ID)

        repository.setNotePinned(noteId, true)

        assertEquals(true, dao.getNote(noteId)?.isPinned)
    }
}
