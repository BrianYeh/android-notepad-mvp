package com.example.notepad.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotepadMigrationTest {
    @Test
    fun migration3To7PreservesRowsBackfillsSyncMetadataAndCreatesTombstones() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-3-7-${System.currentTimeMillis()}.db"
        context.deleteDatabase(dbName)
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).apply {
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
            version = 3
            close()
        }

        val database = Room.databaseBuilder(context, NotepadDatabase::class.java, dbName)
            .addMigrations(
                NotepadDatabase.MIGRATION_3_4,
                NotepadDatabase.MIGRATION_4_5,
                NotepadDatabase.MIGRATION_5_6,
                NotepadDatabase.MIGRATION_6_7,
            )
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
            assertEquals(ReminderRepeat.None.code, note?.reminderRepeat)
            assertNull(note?.reminderSnoozeUntil)
            assertNull(note?.activeReminderFiredAt)
            assertNull(note?.textFormattingJson)

            dao.deleteFolderAndMoveNotes(folderId = 2L, now = 50L)

            val tombstone = dao.getAllFolders().first { it.id == 2L }
            assertNull(dao.getFolder(2L))
            assertEquals(true, tombstone.isDeleted)
            assertEquals(50L, tombstone.deletedAt)
            assertEquals(DEFAULT_FOLDER_ID, dao.getNote(10L)?.folderId)
        } finally {
            database.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migration6To7AddsNullableTextFormattingColumn() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-6-7-${System.currentTimeMillis()}.db"
        context.deleteDatabase(dbName)
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS folders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    syncId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    deletedAt INTEGER
                )
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    syncId TEXT NOT NULL,
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
                    reminderRepeat TEXT NOT NULL DEFAULT 'NONE',
                    reminderSnoozeUntil INTEGER,
                    activeReminderFiredAt INTEGER,
                    FOREIGN KEY(folderId) REFERENCES folders(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            execSQL("CREATE INDEX IF NOT EXISTS index_folders_syncId ON folders(syncId)")
            execSQL("CREATE INDEX IF NOT EXISTS index_notes_folderId ON notes(folderId)")
            execSQL("CREATE INDEX IF NOT EXISTS index_notes_syncId ON notes(syncId)")
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS note_tombstones (
                    syncId TEXT NOT NULL PRIMARY KEY,
                    deletedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO folders (id, syncId, name, createdAt, updatedAt, isDeleted, deletedAt)
                VALUES (1, '$DEFAULT_FOLDER_SYNC_ID', '$DEFAULT_FOLDER_NAME', 1, 1, 0, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO notes (
                    id, syncId, folderId, type, title, textContent, drawingData,
                    createdAt, updatedAt, isDeleted, deletedAt, isPinned, reminderAt,
                    reminderRepeat, reminderSnoozeUntil, activeReminderFiredAt
                ) VALUES (10, 'note:1', 1, 'TEXT', 'Title', 'Body', NULL, 2, 2, 0, NULL, 0, NULL, 'NONE', NULL, NULL)
                """.trimIndent(),
            )
            version = 6
            close()
        }

        val database = Room.databaseBuilder(context, NotepadDatabase::class.java, dbName)
            .addMigrations(NotepadDatabase.MIGRATION_6_7)
            .build()

        try {
            val note = database.notepadDao().getNote(10L)

            assertEquals("Title", note?.title)
            assertNull(note?.textFormattingJson)
        } finally {
            database.close()
            context.deleteDatabase(dbName)
        }
    }
}
