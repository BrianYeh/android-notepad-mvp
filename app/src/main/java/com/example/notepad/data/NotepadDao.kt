package com.example.notepad.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NotepadDao {
    @Query("SELECT * FROM folders ORDER BY CASE WHEN id = :defaultFolderId THEN 0 ELSE 1 END, name COLLATE NOCASE ASC")
    abstract fun observeFolders(defaultFolderId: Long = DEFAULT_FOLDER_ID): Flow<List<FolderEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    abstract fun observeAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY updatedAt DESC")
    abstract fun observeNotesByFolder(folderId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    abstract fun observeNote(noteId: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    abstract suspend fun getNote(noteId: Long): NoteEntity?

    @Query("SELECT * FROM folders ORDER BY id ASC")
    abstract suspend fun getAllFolders(): List<FolderEntity>

    @Query("SELECT * FROM notes ORDER BY id ASC")
    abstract suspend fun getAllNotes(): List<NoteEntity>

    @Query("SELECT * FROM folders WHERE id = :folderId")
    abstract suspend fun getFolder(folderId: Long): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertFolder(folder: FolderEntity): Long

    @Insert
    abstract suspend fun insertNote(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreFolders(folders: List<FolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreNotes(notes: List<NoteEntity>)

    @Update
    abstract suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE folders SET name = :name, updatedAt = :updatedAt WHERE id = :folderId")
    abstract suspend fun renameFolder(folderId: Long, name: String, updatedAt: Long)

    @Query("DELETE FROM folders WHERE id = :folderId")
    abstract suspend fun deleteFolderById(folderId: Long)

    @Query("UPDATE notes SET folderId = :targetFolderId, updatedAt = :updatedAt WHERE folderId = :sourceFolderId")
    abstract suspend fun moveNotesToFolder(sourceFolderId: Long, targetFolderId: Long, updatedAt: Long)

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :noteId")
    abstract suspend fun moveNote(noteId: Long, folderId: Long, updatedAt: Long)

    @Query("DELETE FROM notes WHERE id = :noteId")
    abstract suspend fun deleteNote(noteId: Long)

    @Query("DELETE FROM notes")
    abstract suspend fun deleteAllNotes()

    @Query("DELETE FROM folders")
    abstract suspend fun deleteAllFolders()

    @Transaction
    open suspend fun ensureDefaultFolder(now: Long = System.currentTimeMillis()) {
        insertFolder(
            FolderEntity(
                id = DEFAULT_FOLDER_ID,
                name = DEFAULT_FOLDER_NAME,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @Transaction
    open suspend fun deleteFolderAndMoveNotes(folderId: Long, now: Long = System.currentTimeMillis()) {
        if (folderId == DEFAULT_FOLDER_ID) return
        ensureDefaultFolder(now)
        moveNotesToFolder(folderId, DEFAULT_FOLDER_ID, now)
        deleteFolderById(folderId)
    }

    @Transaction
    open suspend fun replaceAllData(backupData: BackupData) {
        deleteAllNotes()
        deleteAllFolders()
        restoreFolders(backupData.folders)
        restoreNotes(backupData.notes)
        ensureDefaultFolder()
    }
}
