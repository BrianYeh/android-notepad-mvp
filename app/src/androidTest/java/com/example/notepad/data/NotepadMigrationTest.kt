package com.example.notepad.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotepadMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NotepadDatabase::class.java,
    )

    @Test
    fun migration3To4PreservesRowsAndBackfillsSyncMetadata() = runTest {
        val dbName = "migration-3-4-${System.currentTimeMillis()}.db"
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS folders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    folderId INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    textContent TEXT,
                    drawingData TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    deletedAt INTEGER,
                    isPinned INTEGER NOT NULL DEFAULT 0,
                    reminderAt INTEGER,
                    FOREIGN KEY(folderId) REFERENCES folders(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            execSQL("CREATE INDEX IF NOT EXISTS index_notes_folderId ON notes(folderId)")
            execSQL("INSERT INTO folders (id, name, createdAt, updatedAt) VALUES (1, 'Uncategorized', 1, 1)")
            execSQL("INSERT INTO folders (id, name, createdAt, updatedAt) VALUES (2, 'Work', 2, 2)")
            execSQL(
                """
                INSERT INTO notes (
                    id, folderId, type, title, textContent, drawingData,
                    createdAt, updatedAt, isDeleted, deletedAt, isPinned, reminderAt
                ) VALUES (10, 2, 'TEXT', 'Plan', 'Body', NULL, 3, 3, 0, NULL, 0, NULL)
                """.trimIndent(),
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.databaseBuilder(context, NotepadDatabase::class.java, dbName)
            .addMigrations(NotepadDatabase.MIGRATION_3_4)
            .build()

        try {
            database.openHelper.writableDatabase
            val dao = database.notepadDao()
            dao.ensureSyncMetadata(now = 4L)

            val folders = dao.getAllFolders()
            assertEquals(DEFAULT_FOLDER_SYNC_ID, folders.first { it.id == DEFAULT_FOLDER_ID }.syncId)
            assertTrue(folders.first { it.id == 2L }.syncId.startsWith("folder:"))
            assertEquals(false, folders.first { it.id == 2L }.isDeleted)

            val note = dao.getNote(10L)
            assertEquals("Plan", note?.title)
            assertTrue(note?.syncId?.startsWith("note:") == true)

            dao.deleteFolderAndMoveNotes(folderId = 2L, now = 50L)

            val tombstone = dao.getAllFolders().first { it.id == 2L }
            assertNull(dao.getFolder(2L))
            assertEquals(true, tombstone.isDeleted)
            assertEquals(50L, tombstone.deletedAt)
            assertEquals(DEFAULT_FOLDER_ID, dao.getNote(10L)?.folderId)
        } finally {
            database.close()
        }
    }
}
