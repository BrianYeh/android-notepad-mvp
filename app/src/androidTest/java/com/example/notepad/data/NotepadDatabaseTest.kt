package com.example.notepad.data

import android.graphics.BitmapFactory
import android.graphics.Color
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
                colorArgb = 0xFFE53935.toInt(),
                widthPx = 10f,
                tool = DrawingTools.PEN,
            ),
            DrawingStroke(
                points = listOf(
                    DrawingPoint(8f, 9f),
                    DrawingPoint(10f, 11f),
                ),
                widthPx = 12f,
                tool = DrawingTools.ERASER,
            ),
        )

        assertEquals(strokes, DrawingJson.decode(DrawingJson.encode(strokes)))
    }

    @Test
    fun drawingJsonDecodesLegacyStrokeDataWithFallbackStyle() {
        val legacyJson = """
            [
              {
                "points": [
                  { "x": 1.5, "y": 2.5 },
                  { "x": 3.5, "y": 4.5 }
                ]
              }
            ]
        """.trimIndent()

        val stroke = DrawingJson.decode(legacyJson).single()

        assertEquals(DEFAULT_DRAWING_COLOR_ARGB, stroke.colorArgb)
        assertEquals(DEFAULT_DRAWING_STROKE_WIDTH, stroke.widthPx, 0.01f)
        assertEquals(DrawingTools.PEN, stroke.tool)
    }

    @Test
    fun drawingPngRendersBlankCanvasStyledStrokesAndEraserStrokes() {
        val blankPng = renderDrawingPng(emptyList(), width = 120, height = 80)
        val styledPng = renderDrawingPng(
            strokes = listOf(
                DrawingStroke(
                    points = listOf(DrawingPoint(10f, 40f), DrawingPoint(70f, 40f)),
                    colorArgb = Color.BLACK,
                    widthPx = 16f,
                    tool = DrawingTools.PEN,
                ),
                DrawingStroke(
                    points = listOf(DrawingPoint(40f, 10f), DrawingPoint(40f, 70f)),
                    widthPx = 18f,
                    tool = DrawingTools.ERASER,
                ),
            ),
            width = 80,
            height = 80,
        )
        val bitmap = BitmapFactory.decodeByteArray(styledPng, 0, styledPng.size)

        assertEquals(0x89.toByte(), blankPng.first())
        assertEquals(0x89.toByte(), styledPng.first())
        assertEquals(Color.WHITE, bitmap.getPixel(40, 40))
        assertEquals(Color.BLACK, bitmap.getPixel(20, 40))
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
                reminderAt = 10_000L,
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
        assertEquals(10_000L, dao.getAllNotes().single().reminderAt)
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

    @Test
    fun noteReminderCanBeSetAndCleared() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val noteId = repository.createTextNote(DEFAULT_FOLDER_ID)

        repository.setNoteReminder(noteId, 10_000L)

        assertEquals(10_000L, dao.getNote(noteId)?.reminderAt)

        repository.setNoteReminder(noteId, null)

        assertNull(dao.getNote(noteId)?.reminderAt)
    }

    @Test
    fun sharedTextNoteUsesSubjectAndUncategorizedFolder() = runTest {
        val repository = NotepadRepository(dao)
        val title = buildSharedNoteTitle(
            subject = "Shared subject",
            sharedText = "Shared body",
            defaultTitle = "Shared Note",
        )

        val noteId = repository.createSharedTextNote(title, "Shared body")

        val note = dao.getNote(noteId)
        assertEquals(DEFAULT_FOLDER_ID, note?.folderId)
        assertEquals("Shared subject", note?.title)
        assertEquals("Shared body", note?.textContent)
    }

    @Test
    fun sharedTextTitleFallsBackToBodyPreview() {
        assertEquals(
            "Body preview",
            buildSharedNoteTitle(
                subject = null,
                sharedText = "\n Body preview\nSecond line",
                defaultTitle = "Shared Note",
            ),
        )
    }
}
