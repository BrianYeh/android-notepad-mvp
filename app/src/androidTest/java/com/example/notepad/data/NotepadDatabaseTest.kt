package com.example.notepad.data

import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.notepad.ocr.OcrNoteResult
import com.example.notepad.ocr.OcrNoteUseCase
import com.example.notepad.ocr.OcrTextRecognizer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun drawingJsonDecodesLegacyTypeFieldAndPreservesEraserWidth() {
        val legacyTypeJson = """
            [
              {
                "type": "ERASER",
                "widthPx": 48.0,
                "points": [
                  { "x": 10.0, "y": 20.0 },
                  { "x": 30.0, "y": 40.0 }
                ]
              }
            ]
        """.trimIndent()

        val stroke = DrawingJson.decode(legacyTypeJson).single()

        assertEquals(DrawingTools.ERASER, stroke.tool)
        assertEquals(48f, stroke.widthPx, 0.01f)
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
        val originalFolderSyncId = dao.getFolder(folderId)?.syncId
        val originalNoteSyncId = dao.getNote(noteId)?.syncId
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
        assertEquals(originalFolderSyncId, dao.getAllFolders().first { it.id == folderId }.syncId)
        assertEquals(noteId, dao.getAllNotes().single().id)
        assertEquals(originalNoteSyncId, dao.getAllNotes().single().syncId)
        assertEquals("Draft", dao.getAllNotes().single().textContent)
        assertEquals(10_000L, dao.getAllNotes().single().reminderAt)
    }

    @Test
    fun backupRestoreCheckpointCanRollbackLastRestore() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val originalNoteId = dao.insertNote(
            NoteEntity(
                folderId = DEFAULT_FOLDER_ID,
                type = NoteTypes.TEXT,
                title = "Original",
                textContent = "Keep this before restore.",
                drawingData = null,
                createdAt = 2L,
                updatedAt = 2L,
            ),
        )
        val replacementBackup = BackupData(
            folders = listOf(
                FolderEntity(
                    id = DEFAULT_FOLDER_ID,
                    syncId = DEFAULT_FOLDER_SYNC_ID,
                    name = DEFAULT_FOLDER_NAME,
                    createdAt = 10L,
                    updatedAt = 10L,
                ),
            ),
            notes = listOf(
                NoteEntity(
                    id = 42L,
                    syncId = "note:replacement",
                    folderId = DEFAULT_FOLDER_ID,
                    type = NoteTypes.TEXT,
                    title = "Replacement",
                    textContent = "Imported backup note.",
                    drawingData = null,
                    createdAt = 11L,
                    updatedAt = 11L,
                ),
            ),
        )

        val rollbackCheckpoint = repository.importBackupDataWithRollbackCheckpoint(replacementBackup)

        assertEquals("Replacement", dao.getAllNotes().single().title)

        repository.importBackupData(rollbackCheckpoint)

        val restoredNotes = dao.getAllNotes()
        assertEquals(listOf(originalNoteId), restoredNotes.map { it.id })
        assertEquals("Original", restoredNotes.single().title)
        assertEquals("Keep this before restore.", restoredNotes.single().textContent)
    }

    @Test
    fun backupJsonRejectsNonBackupJsonWithoutReplacingExistingNotes() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val noteId = dao.insertNote(
            NoteEntity(
                folderId = DEFAULT_FOLDER_ID,
                type = NoteTypes.TEXT,
                title = "Keep me",
                textContent = "This should survive a bad restore.",
                drawingData = null,
                createdAt = 2L,
                updatedAt = 2L,
            ),
        )

        try {
            repository.importBackupJson("""{"name":"not a Just Notes backup"}""")
            fail("Expected invalid backup JSON to be rejected.")
        } catch (_: IllegalArgumentException) {
        }

        val note = dao.getNote(noteId)
        assertEquals("Keep me", note?.title)
        assertEquals("This should survive a bad restore.", note?.textContent)
    }

    @Test
    fun backupJsonRejectsUnsupportedVersionWithoutReplacingExistingNotes() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val noteId = dao.insertNote(
            NoteEntity(
                folderId = DEFAULT_FOLDER_ID,
                type = NoteTypes.TEXT,
                title = "Version guard",
                textContent = "Body",
                drawingData = null,
                createdAt = 2L,
                updatedAt = 2L,
            ),
        )

        try {
            repository.importBackupJson("""{"version":999,"folders":[],"notes":[]}""")
            fail("Expected unsupported backup version to be rejected.")
        } catch (_: IllegalArgumentException) {
        }

        assertEquals("Version guard", dao.getNote(noteId)?.title)
    }

    @Test
    fun backupJsonRejectsMissingVersionWithoutReplacingExistingNotes() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val noteId = dao.insertNote(
            NoteEntity(
                folderId = DEFAULT_FOLDER_ID,
                type = NoteTypes.TEXT,
                title = "Missing version guard",
                textContent = "Body",
                drawingData = null,
                createdAt = 2L,
                updatedAt = 2L,
            ),
        )

        try {
            repository.importBackupJson("""{"folders":[],"notes":[]}""")
            fail("Expected backup JSON without a version to be rejected.")
        } catch (_: IllegalArgumentException) {
        }

        assertEquals("Missing version guard", dao.getNote(noteId)?.title)
    }

    @Test
    fun backupJsonPreviewSummarizesBackupContents() = runTest {
        dao.ensureDefaultFolder(now = 1L)
        val folderId = dao.insertFolder(
            FolderEntity(
                name = "Projects",
                createdAt = 2L,
                updatedAt = 2L,
            ),
        )
        dao.insertNote(
            NoteEntity(
                folderId = folderId,
                type = NoteTypes.TEXT,
                title = "Active",
                textContent = "Body",
                drawingData = null,
                createdAt = 3L,
                updatedAt = 3L,
            ),
        )
        dao.insertNote(
            NoteEntity(
                folderId = folderId,
                type = NoteTypes.TEXT,
                title = "Deleted",
                textContent = "Trash",
                drawingData = null,
                createdAt = 4L,
                updatedAt = 5L,
                isDeleted = true,
                deletedAt = 5L,
            ),
        )

        val decodedBackup = BackupJson.decodeWithPreview(
            BackupJson.encode(
                folders = dao.getAllFolders(),
                notes = dao.getAllNotes(),
            ),
        )

        assertEquals(2, decodedBackup.preview.folderCount)
        assertEquals(2, decodedBackup.preview.noteCount)
        assertEquals(1, decodedBackup.preview.activeNoteCount)
        assertEquals(1, decodedBackup.preview.deletedNoteCount)
        assertTrue(decodedBackup.preview.exportedAt != null)
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

    @Test
    fun ocrUseCaseCreatesSearchableTextNoteFromRecognizedText() = runTest {
        val repository = NotepadRepository(dao)
        val useCase = OcrNoteUseCase(
            recognizer = FakeOcrTextRecognizer("Receipt Total\nMilk 80"),
            repository = repository,
            now = { 1_000L },
        )

        val result = useCase.createTextNoteFromImage(
            uri = Uri.parse("content://test/receipt"),
            fallbackTitlePrefix = "OCR Note",
        )

        val noteId = (result as OcrNoteResult.Created).noteId
        val note = dao.getNote(noteId)
        assertEquals(DEFAULT_FOLDER_ID, note?.folderId)
        assertEquals(NoteTypes.TEXT, note?.type)
        assertEquals("Receipt Total", note?.title)
        assertEquals("Receipt Total\nMilk 80", note?.textContent)
    }

    @Test
    fun ocrUseCaseDoesNotCreateNoteWhenNoTextRecognized() = runTest {
        val repository = NotepadRepository(dao)
        val useCase = OcrNoteUseCase(
            recognizer = FakeOcrTextRecognizer("  \n  "),
            repository = repository,
        )

        val result = useCase.createTextNoteFromImage(
            uri = Uri.parse("content://test/blank"),
            fallbackTitlePrefix = "OCR Note",
        )

        assertEquals(OcrNoteResult.NoText, result)
        assertEquals(emptyList<NoteEntity>(), dao.getAllNotes())
    }
}

private class FakeOcrTextRecognizer(
    private val text: String,
) : OcrTextRecognizer {
    override suspend fun recognizeText(uri: Uri): String {
        return text
    }
}
