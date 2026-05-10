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
}
