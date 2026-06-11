package com.example.notepad.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.notepad.ocr.OcrNoteResult
import com.example.notepad.ocr.OcrNoteUseCase
import com.example.notepad.ocr.OcrTextRecognizer
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
    fun discardNewDrawingDraftIfBlankDeletesEmptyDrawingWithoutTombstone() = runTest {
        val repository = NotepadRepository(dao)
        val noteId = repository.createDrawingNote(folderId = null)

        assertTrue(
            repository.discardNewDrawingDraftIfBlank(
                noteId = noteId,
                isNewDraft = true,
                title = "   ",
                drawingData = "[]",
            ),
        )

        assertNull(dao.getNote(noteId))
        assertTrue(dao.getNoteTombstones().isEmpty())
    }

    @Test
    fun staleDrawingSaveDoesNotCommitAfterNewerEditArrives() = runTest {
        val repository = NotepadRepository(dao)
        val noteId = repository.createDrawingNote(folderId = null)
        val initialUpdatedAt = dao.getNote(noteId)?.updatedAt ?: throw AssertionError("Drawing note was not created")
        val saveEditGate = DrawingSaveEditGate(initialEditVersion = 1L)
        val delayedEditVersion = saveEditGate.currentEditVersion()
        val oldDrawingData = DrawingJson.encode(
            listOf(
                DrawingStroke(
                    points = listOf(DrawingPoint(1f, 1f), DrawingPoint(2f, 2f)),
                    tool = DrawingTools.PEN,
                ),
            ),
        )
        val newDrawingData = DrawingJson.encode(
            listOf(
                DrawingStroke(
                    points = listOf(DrawingPoint(10f, 10f), DrawingPoint(20f, 20f)),
                    tool = DrawingTools.PEN,
                ),
            ),
        )
        val delayedOldSave = async {
            delay(100L)
            repository.saveDrawingNoteIfCurrent(
                noteId = noteId,
                title = "A",
                drawingData = oldDrawingData,
                expectedUpdatedAt = initialUpdatedAt,
                expectedTitle = "",
                expectedDrawingData = "[]",
                saveEditGate = saveEditGate,
                isCurrentBeforeWrite = { saveEditGate.isCurrent(delayedEditVersion) },
            )
        }

        saveEditGate.markEdited()
        assertNull(delayedOldSave.await())
        assertEquals("", dao.getNote(noteId)?.title)
        assertEquals("[]", dao.getNote(noteId)?.drawingData)

        val currentEditVersion = saveEditGate.currentEditVersion()
        assertTrue(
            repository.saveDrawingNoteIfCurrent(
                noteId = noteId,
                title = "B",
                drawingData = newDrawingData,
                expectedUpdatedAt = initialUpdatedAt,
                expectedTitle = "",
                expectedDrawingData = "[]",
                saveEditGate = saveEditGate,
                isCurrentBeforeWrite = { saveEditGate.isCurrent(currentEditVersion) },
            ) != null,
        )

        val saved = dao.getNote(noteId)
        assertEquals("B", saved?.title)
        assertEquals(newDrawingData, saved?.drawingData)
    }

    @Test
    fun drawingSaveGateBlocksEditMutationDuringFinalDaoHandoff() = runTest {
        val repository = NotepadRepository(dao)
        val noteId = repository.createDrawingNote(folderId = null)
        val initialUpdatedAt = dao.getNote(noteId)?.updatedAt ?: throw AssertionError("Drawing note was not created")
        val saveEditGate = DrawingSaveEditGate(initialEditVersion = 1L)
        val expectedEditVersion = saveEditGate.currentEditVersion()
        val gateChecks = AtomicInteger(0)
        val editAttemptStarted = CountDownLatch(1)
        val editFinished = CountDownLatch(1)
        var editThread: Thread? = null
        val drawingData = DrawingJson.encode(
            listOf(
                DrawingStroke(
                    points = listOf(DrawingPoint(3f, 3f), DrawingPoint(6f, 6f)),
                    tool = DrawingTools.PEN,
                ),
            ),
        )

        val savedAt = repository.saveDrawingNoteIfCurrent(
            noteId = noteId,
            title = "Handoff",
            drawingData = drawingData,
            expectedUpdatedAt = initialUpdatedAt,
            expectedTitle = "",
            expectedDrawingData = "[]",
            saveEditGate = saveEditGate,
            isCurrentBeforeWrite = {
                val isCurrent = saveEditGate.isCurrent(expectedEditVersion)
                if (isCurrent && gateChecks.incrementAndGet() == 2) {
                    editThread = Thread {
                        editAttemptStarted.countDown()
                        saveEditGate.markEdited()
                        editFinished.countDown()
                    }.also { it.start() }
                    assertTrue(editAttemptStarted.await(5, TimeUnit.SECONDS))
                    assertFalse(editFinished.await(100, TimeUnit.MILLISECONDS))
                }
                isCurrent
            },
        )

        assertTrue(savedAt != null)
        editThread?.join(5_000)
        assertFalse(editThread?.isAlive ?: false)
        assertEquals(expectedEditVersion + 1, saveEditGate.currentEditVersion())
        val saved = dao.getNote(noteId)
        assertEquals("Handoff", saved?.title)
        assertEquals(drawingData, saved?.drawingData)
    }

    @Test
    fun drawingNoOpSaveSucceedsAfterMetadataOnlyUpdate() = runTest {
        val repository = NotepadRepository(dao)
        val noteId = repository.createDrawingNote(folderId = null)
        val initial = dao.getNote(noteId) ?: throw AssertionError("Drawing note was not created")
        val reminderAt = System.currentTimeMillis() + 60_000L
        val saveEditGate = DrawingSaveEditGate(initialEditVersion = 1L)
        val expectedEditVersion = saveEditGate.currentEditVersion()

        repository.setNoteReminder(noteId, reminderAt, ReminderRepeat.None.code)
        val metadataUpdated = dao.getNote(noteId) ?: throw AssertionError("Drawing note was not updated")

        val savedAt = repository.saveDrawingNoteIfCurrent(
            noteId = noteId,
            title = initial.title,
            drawingData = initial.drawingData.orEmpty(),
            expectedUpdatedAt = initial.updatedAt,
            expectedTitle = initial.title,
            expectedDrawingData = initial.drawingData.orEmpty(),
            saveEditGate = saveEditGate,
            isCurrentBeforeWrite = { saveEditGate.isCurrent(expectedEditVersion) },
        )

        assertEquals(metadataUpdated.updatedAt, savedAt)
        val saved = dao.getNote(noteId)
        assertEquals(initial.title, saved?.title)
        assertEquals(initial.drawingData, saved?.drawingData)
        assertEquals(reminderAt, saved?.reminderAt)
    }

    @Test
    fun drawingContentSaveSucceedsAfterMetadataOnlyUpdateWhenBaselineContentUnchanged() = runTest {
        val repository = NotepadRepository(dao)
        val noteId = repository.createDrawingNote(folderId = null)
        val initial = dao.getNote(noteId) ?: throw AssertionError("Drawing note was not created")
        val reminderAt = System.currentTimeMillis() + 60_000L
        val saveEditGate = DrawingSaveEditGate(initialEditVersion = 1L)
        val expectedEditVersion = saveEditGate.currentEditVersion()
        val localDrawingData = DrawingJson.encode(
            listOf(
                DrawingStroke(
                    points = listOf(DrawingPoint(6f, 8f), DrawingPoint(12f, 16f)),
                    tool = DrawingTools.PEN,
                ),
            ),
        )

        repository.setNoteReminder(noteId, reminderAt, ReminderRepeat.None.code)

        assertTrue(
            repository.saveDrawingNoteIfCurrent(
                noteId = noteId,
                title = "Local content",
                drawingData = localDrawingData,
                expectedUpdatedAt = initial.updatedAt,
                expectedTitle = initial.title,
                expectedDrawingData = initial.drawingData.orEmpty(),
                saveEditGate = saveEditGate,
                isCurrentBeforeWrite = { saveEditGate.isCurrent(expectedEditVersion) },
            ) != null,
        )
        val saved = dao.getNote(noteId)
        assertEquals("Local content", saved?.title)
        assertEquals(localDrawingData, saved?.drawingData)
        assertEquals(reminderAt, saved?.reminderAt)
    }

    @Test
    fun conditionalDrawingSaveKeepsUpdatedAtMonotonicAfterMetadataRace() = runTest {
        dao.ensureDefaultFolder(now = 1L)
        val noteId = dao.insertNote(
            NoteEntity(
                folderId = DEFAULT_FOLDER_ID,
                type = NoteTypes.DRAWING,
                title = "",
                textContent = null,
                drawingData = "[]",
                createdAt = 2L,
                updatedAt = 1_000L,
            ),
        )
        val localDrawingData = DrawingJson.encode(
            listOf(
                DrawingStroke(
                    points = listOf(DrawingPoint(4f, 4f), DrawingPoint(8f, 8f)),
                    tool = DrawingTools.PEN,
                ),
            ),
        )

        assertEquals(
            1,
            dao.updateDrawingNoteContentIfUnchanged(
                noteId = noteId,
                title = "Local content",
                drawingData = localDrawingData,
                updatedAt = 900L,
                expectedUpdatedAt = 2L,
                expectedTitle = "",
                expectedDrawingData = "[]",
            ),
        )

        val saved = dao.getNote(noteId)
        assertEquals("Local content", saved?.title)
        assertEquals(localDrawingData, saved?.drawingData)
        assertEquals(1_001L, saved?.updatedAt)
    }

    @Test
    fun discardDrawingDraftHelperRequiresNewDraftAuthorization() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val noteId = dao.insertNote(
            NoteEntity(
                folderId = DEFAULT_FOLDER_ID,
                type = NoteTypes.DRAWING,
                title = "",
                textContent = null,
                drawingData = "[]",
                createdAt = 2L,
                updatedAt = 2L,
            ),
        )

        assertEquals(0, dao.deleteBlankLocalDrawingDraftNote(noteId, isNewDraft = false))
        assertEquals(
            false,
            repository.discardNewDrawingDraftIfBlank(
                noteId = noteId,
                isNewDraft = false,
                title = "",
                drawingData = "[]",
            ),
        )

        val existingBlankDrawing = dao.getNote(noteId)
        assertEquals(NoteTypes.DRAWING, existingBlankDrawing?.type)
        assertEquals("", existingBlankDrawing?.title)
        assertEquals("[]", existingBlankDrawing?.drawingData)
        assertTrue(dao.getNoteTombstones().isEmpty())
    }

    @Test
    fun discardNewDrawingDraftIfBlankDeletesClearedPreviouslySavedDraft() = runTest {
        val repository = NotepadRepository(dao)
        val noteId = repository.createDrawingNote(folderId = null)
        val savedDrawingData = DrawingJson.encode(
            listOf(
                DrawingStroke(
                    points = listOf(DrawingPoint(10f, 20f), DrawingPoint(30f, 40f)),
                    tool = DrawingTools.PEN,
                ),
            ),
        )
        repository.saveDrawingNote(noteId, title = "Temporary sketch", drawingData = savedDrawingData)

        assertTrue(
            repository.discardNewDrawingDraftIfBlank(
                noteId = noteId,
                isNewDraft = true,
                title = "",
                drawingData = "[]",
            ),
        )

        assertNull(dao.getNote(noteId))
        assertTrue(dao.getNoteTombstones().isEmpty())
    }

    @Test
    fun discardNewDrawingDraftIfBlankKeepsNonblankTitle() = runTest {
        val repository = NotepadRepository(dao)
        val noteId = repository.createDrawingNote(folderId = null)

        assertEquals(
            false,
            repository.discardNewDrawingDraftIfBlank(
                noteId = noteId,
                isNewDraft = true,
                title = "Sketch",
                drawingData = "[]",
            ),
        )

        assertTrue(dao.getNote(noteId) != null)
        repository.saveDrawingNote(noteId, "Sketch", "[]")
        assertEquals("Sketch", dao.getNote(noteId)?.title)
    }

    @Test
    fun discardNewDrawingDraftIfBlankKeepsEraserOnlyStroke() = runTest {
        val repository = NotepadRepository(dao)
        val noteId = repository.createDrawingNote(folderId = null)
        val eraserOnlyDrawing = DrawingJson.encode(
            listOf(
                DrawingStroke(
                    points = listOf(DrawingPoint(10f, 20f), DrawingPoint(30f, 40f)),
                    widthPx = 18f,
                    tool = DrawingTools.ERASER,
                ),
            ),
        )

        assertEquals(
            false,
            repository.discardNewDrawingDraftIfBlank(
                noteId = noteId,
                isNewDraft = true,
                title = "",
                drawingData = eraserOnlyDrawing,
            ),
        )

        assertTrue(dao.getNote(noteId) != null)
    }

    @Test
    fun blankDrawingDraftGuardKeepsReminderMetadata() = runTest {
        val repository = NotepadRepository(dao)
        val noteId = repository.createDrawingNote(folderId = null)
        repository.setNoteReminder(noteId, reminderAt = 10_000L)

        assertEquals(0, dao.deleteBlankLocalDrawingDraftNote(noteId, isNewDraft = true))

        assertEquals(10_000L, dao.getNote(noteId)?.reminderAt)
    }

    @Test
    fun saveDrawingNotePreservesFolderAndReminderMetadata() = runTest {
        val repository = NotepadRepository(dao)
        val folderId = repository.createFolder("Sketches")
        val noteId = repository.createDrawingNote(folderId = null)
        repository.moveNote(noteId, folderId)
        repository.setNoteReminder(noteId, reminderAt = 20_000L, reminderRepeat = ReminderRepeat.Daily.code)
        val drawingData = DrawingJson.encode(
            listOf(
                DrawingStroke(
                    points = listOf(DrawingPoint(1f, 2f), DrawingPoint(3f, 4f)),
                    tool = DrawingTools.PEN,
                ),
            ),
        )

        repository.saveDrawingNote(noteId, title = "Updated", drawingData = drawingData)

        val note = dao.getNote(noteId)
        assertEquals(folderId, note?.folderId)
        assertEquals(20_000L, note?.reminderAt)
        assertEquals(ReminderRepeat.Daily.code, note?.reminderRepeat)
        assertEquals("Updated", note?.title)
        assertEquals(drawingData, note?.drawingData)
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
                textFormattingJson = TextFormattingJson.encode(
                    listOf(TextFormatRange(start = 0, end = 5, type = TextFormatType.Bold)),
                ),
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
        assertEquals(
            listOf(TextFormatRange(start = 0, end = 5, type = TextFormatType.Bold)),
            TextFormattingJson.decode(dao.getAllNotes().single().textFormattingJson, textLength = 5),
        )
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
    fun restoreRollbackStorePersistsCheckpointAcrossInstances() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        dao.insertNote(
            NoteEntity(
                folderId = DEFAULT_FOLDER_ID,
                type = NoteTypes.TEXT,
                title = "Durable checkpoint",
                textContent = "Saved to app-private storage.",
                drawingData = null,
                createdAt = 2L,
                updatedAt = 2L,
            ),
        )
        val checkpointFile = File(
            ApplicationProvider.getApplicationContext<Context>().cacheDir,
            "restore-rollback-${System.currentTimeMillis()}.json",
        )

        try {
            RestoreRollbackStore(checkpointFile).save(repository.exportBackupJson())

            val reloaded = RestoreRollbackStore(checkpointFile).load()

            assertEquals("Durable checkpoint", reloaded?.data?.notes?.single()?.title)
            assertEquals("Saved to app-private storage.", reloaded?.data?.notes?.single()?.textContent)

            RestoreRollbackStore(checkpointFile).clear()

            assertNull(RestoreRollbackStore(checkpointFile).load())
        } finally {
            checkpointFile.delete()
        }
    }

    @Test
    fun restoreRollbackStoreClearsCorruptCheckpoint() {
        val checkpointFile = File(
            ApplicationProvider.getApplicationContext<Context>().cacheDir,
            "restore-rollback-corrupt-${System.currentTimeMillis()}.json",
        )
        checkpointFile.writeText("not a backup")

        try {
            val restored = RestoreRollbackStore(checkpointFile).load()

            assertNull(restored)
            assertEquals(false, checkpointFile.exists())
        } finally {
            checkpointFile.delete()
        }
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
    fun permanentlyDeleteBlankTextDraftAcceptsWhitespaceOnlyValues() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val noteId = repository.createTextNote(DEFAULT_FOLDER_ID)
        repository.saveTextNote(
            noteId = noteId,
            title = " \n\t ",
            content = " \n  ",
            textFormattingJson = "\n\t",
        )

        assertTrue(repository.permanentlyDeleteBlankTextDraft(noteId))

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

        repository.setNoteReminder(noteId, 10_000L, ReminderRepeat.Daily.code)

        assertEquals(10_000L, dao.getNote(noteId)?.reminderAt)
        assertEquals(ReminderRepeat.Daily.code, dao.getNote(noteId)?.reminderRepeat)

        repository.setNoteReminder(noteId, null)

        assertNull(dao.getNote(noteId)?.reminderAt)
        assertEquals(ReminderRepeat.None.code, dao.getNote(noteId)?.reminderRepeat)
    }

    @Test
    fun settingReminderClearsTransientSnoozeAndNotificationToken() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val noteId = repository.createTextNote(DEFAULT_FOLDER_ID)
        repository.setNoteReminder(noteId, 10_000L, ReminderRepeat.Daily.code)
        dao.setReminderSnoozeUntil(noteId, 20_000L)
        dao.setActiveReminderFiredAt(noteId, 10_000L)

        repository.setNoteReminder(noteId, 30_000L, ReminderRepeat.Weekly.code)

        val note = dao.getNote(noteId)
        assertEquals(30_000L, note?.reminderAt)
        assertEquals(ReminderRepeat.Weekly.code, note?.reminderRepeat)
        assertNull(note?.reminderSnoozeUntil)
        assertNull(note?.activeReminderFiredAt)
    }

    @Test
    fun transientReminderStateChangesSyncFingerprint() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val noteId = repository.createTextNote(DEFAULT_FOLDER_ID)
        repository.setNoteReminder(noteId, 10_000L, ReminderRepeat.Daily.code)
        val before = repository.syncFingerprint()

        dao.setActiveReminderFiredAt(noteId, 10_000L)

        assertNotEquals(before, repository.syncFingerprint())
    }

    @Test
    fun reminderOccurrenceUpdateRequiresCurrentReminderSchedule() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val noteId = repository.createTextNote(DEFAULT_FOLDER_ID)
        repository.setNoteReminder(noteId, 10_000L, ReminderRepeat.Daily.code)
        repository.setNoteReminder(noteId, 20_000L, ReminderRepeat.Daily.code)

        val updated = dao.updateReminderOccurrenceIfCurrent(
            noteId = noteId,
            expectedReminderAt = 10_000L,
            expectedReminderRepeat = ReminderRepeat.Daily.code,
            expectedActiveReminderFiredAt = 10_000L,
            reminderAt = 30_000L,
            reminderRepeat = ReminderRepeat.Daily.code,
            updatedAt = 40_000L,
        )

        assertEquals(0, updated)
        assertEquals(20_000L, dao.getNote(noteId)?.reminderAt)
    }

    @Test
    fun reminderOccurrenceUpdateRequiresCurrentNotificationToken() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val noteId = repository.createTextNote(DEFAULT_FOLDER_ID)
        repository.setNoteReminder(noteId, 10_000L, ReminderRepeat.Daily.code)
        dao.setActiveReminderFiredAt(noteId, null)

        val updated = dao.updateReminderOccurrenceIfCurrent(
            noteId = noteId,
            expectedReminderAt = 10_000L,
            expectedReminderRepeat = ReminderRepeat.Daily.code,
            expectedActiveReminderFiredAt = 10_000L,
            reminderAt = 20_000L,
            reminderRepeat = ReminderRepeat.Daily.code,
            updatedAt = 30_000L,
        )

        assertEquals(0, updated)
        assertEquals(10_000L, dao.getNote(noteId)?.reminderAt)
        assertNull(dao.getNote(noteId)?.activeReminderFiredAt)
    }

    @Test
    fun deletingAndRestoringNoteClearsStaleReminderNotificationToken() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val noteId = repository.createTextNote(DEFAULT_FOLDER_ID)
        repository.setNoteReminder(noteId, 10_000L, ReminderRepeat.Daily.code)
        dao.setReminderSnoozeUntil(noteId, 20_000L)
        dao.setActiveReminderFiredAt(noteId, 10_000L)

        repository.deleteNote(noteId)

        assertNull(dao.getNote(noteId)?.reminderSnoozeUntil)
        assertNull(dao.getNote(noteId)?.activeReminderFiredAt)

        dao.setReminderSnoozeUntil(noteId, 20_000L)
        dao.setActiveReminderFiredAt(noteId, 10_000L)
        repository.restoreNote(noteId)

        assertNull(dao.getNote(noteId)?.reminderSnoozeUntil)
        assertNull(dao.getNote(noteId)?.activeReminderFiredAt)
    }

    @Test
    fun syncReplacePreservesLocalReminderSnoozeAndNotificationTokenForSameSchedule() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val noteId = repository.createTextNote(DEFAULT_FOLDER_ID)
        repository.saveTextNote(noteId, "Daily", "Check")
        repository.setNoteReminder(noteId, 10_000L, ReminderRepeat.Daily.code)
        dao.setReminderSnoozeUntil(noteId, 20_000L)
        dao.setActiveReminderFiredAt(noteId, 10_000L)
        val export = repository.exportRemoteSyncSnapshotWithFingerprint(
            sourceDevice = SyncDevice(deviceId = "device-1", deviceName = "Test device"),
            accountEmail = null,
            now = 30_000L,
        )

        assertTrue(repository.replaceWithRemoteSyncSnapshot(export.snapshot, export.fingerprint))

        val note = dao.getNote(noteId)
        assertEquals(20_000L, note?.reminderSnoozeUntil)
        assertEquals(10_000L, note?.activeReminderFiredAt)
    }

    @Test
    fun syncReplacePreservesLocalReminderTransientForEquivalentRecurringSchedule() = runTest {
        val repository = NotepadRepository(dao)
        dao.ensureDefaultFolder(now = 1L)
        val noteId = repository.createTextNote(DEFAULT_FOLDER_ID)
        val now = System.currentTimeMillis()
        val localReminderAt = now + 86_400_000L
        val staleRemoteReminderAt = localReminderAt - 86_400_000L
        repository.saveTextNote(noteId, "Daily", "Check")
        repository.setNoteReminder(noteId, localReminderAt, ReminderRepeat.Daily.code)
        dao.setReminderSnoozeUntil(noteId, now + 600_000L)
        dao.setActiveReminderFiredAt(noteId, staleRemoteReminderAt)
        val export = repository.exportRemoteSyncSnapshotWithFingerprint(
            sourceDevice = SyncDevice(deviceId = "device-1", deviceName = "Test device"),
            accountEmail = null,
            now = now,
        )
        val snapshot = export.snapshot.copy(
            notes = export.snapshot.notes.map { note ->
                if (note.syncId == dao.getNote(noteId)?.syncId) {
                    note.copy(reminderAt = staleRemoteReminderAt)
                } else {
                    note
                }
            },
        )

        assertTrue(repository.replaceWithRemoteSyncSnapshot(snapshot, export.fingerprint))

        val note = dao.getNote(noteId)
        assertEquals(now + 600_000L, note?.reminderSnoozeUntil)
        assertEquals(staleRemoteReminderAt, note?.activeReminderFiredAt)
    }

    @Test
    fun backupPreservesRecurringReminder() = runTest {
        val repository = NotepadRepository(dao)
        val noteId = repository.createTextNote(DEFAULT_FOLDER_ID)
        repository.saveTextNote(noteId, "Standup", "Daily check")
        repository.setNoteReminder(noteId, 10_000L, ReminderRepeat.Daily.code)

        val backupJson = repository.exportBackupJson()
        val decoded = BackupJson.decode(backupJson).notes.single()

        assertTrue(backupJson.contains("\"version\":6"))
        assertEquals(10_000L, decoded.reminderAt)
        assertEquals(ReminderRepeat.Daily.code, decoded.reminderRepeat)
    }

    @Test
    fun checklistNotePreservesStructuredItemsThroughBackup() = runTest {
        val repository = NotepadRepository(dao)
        val noteId = repository.createChecklistNote(DEFAULT_FOLDER_ID)
        val checklistItems = listOf(
            ChecklistItem(text = "Milk", checked = true),
            ChecklistItem(text = "Eggs", checked = false),
        )

        repository.saveChecklistNote(noteId, "Groceries", ChecklistJson.encode(checklistItems))
        val backupJson = repository.exportBackupJson()
        val decodedBackup = BackupJson.decode(backupJson)

        val note = decodedBackup.notes.single()
        assertTrue(backupJson.contains("\"version\":5"))
        assertEquals(NoteTypes.CHECKLIST, note.type)
        assertEquals("Groceries", note.title)
        assertEquals(checklistItems.map { it.text }, ChecklistJson.decode(note.textContent).map { it.text })
        assertEquals(listOf(true, false), ChecklistJson.decode(note.textContent).map { it.checked })
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
