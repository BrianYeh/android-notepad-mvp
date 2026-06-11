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
    @Query("SELECT * FROM folders WHERE isDeleted = 0 ORDER BY CASE WHEN id = :defaultFolderId THEN 0 ELSE 1 END, name COLLATE NOCASE ASC")
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

    @Query("SELECT * FROM note_tombstones ORDER BY syncId ASC")
    abstract suspend fun getNoteTombstones(): List<NoteTombstoneEntity>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND reminderAt IS NOT NULL AND reminderAt > :now ORDER BY reminderAt ASC")
    abstract suspend fun getFutureReminderNotes(now: Long): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND (reminderAt IS NOT NULL OR reminderSnoozeUntil IS NOT NULL) ORDER BY reminderAt ASC")
    abstract suspend fun getReminderNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC LIMIT :limit")
    abstract suspend fun getWidgetNotes(limit: Int = 8): List<NoteEntity>

    @Query("SELECT * FROM folders WHERE id = :folderId AND isDeleted = 0")
    abstract suspend fun getFolder(folderId: Long): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertFolder(folder: FolderEntity): Long

    @Insert
    abstract suspend fun insertNote(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreFolders(folders: List<FolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreNotes(notes: List<NoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertNoteTombstones(tombstones: List<NoteTombstoneEntity>)

    @Update
    abstract suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE folders SET name = :name, updatedAt = :updatedAt WHERE id = :folderId")
    abstract suspend fun renameFolder(folderId: Long, name: String, updatedAt: Long)

    @Query("UPDATE folders SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :folderId")
    abstract suspend fun softDeleteFolder(folderId: Long, deletedAt: Long)

    @Query("UPDATE notes SET folderId = :targetFolderId, updatedAt = :updatedAt WHERE folderId = :sourceFolderId")
    abstract suspend fun moveNotesToFolder(sourceFolderId: Long, targetFolderId: Long, updatedAt: Long)

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :noteId")
    abstract suspend fun moveNote(noteId: Long, folderId: Long, updatedAt: Long)

    @Query("UPDATE notes SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt, reminderSnoozeUntil = NULL, activeReminderFiredAt = NULL WHERE id = :noteId")
    abstract suspend fun softDeleteNote(noteId: Long, deletedAt: Long)

    @Query("UPDATE notes SET isDeleted = 0, deletedAt = NULL, updatedAt = :updatedAt, reminderSnoozeUntil = NULL, activeReminderFiredAt = NULL WHERE id = :noteId")
    abstract suspend fun restoreNote(noteId: Long, updatedAt: Long)

    @Query("UPDATE notes SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :noteId")
    abstract suspend fun setNotePinned(noteId: Long, isPinned: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET reminderAt = :reminderAt, reminderRepeat = :reminderRepeat, reminderSnoozeUntil = NULL, activeReminderFiredAt = NULL, updatedAt = :updatedAt WHERE id = :noteId")
    abstract suspend fun setNoteReminder(noteId: Long, reminderAt: Long?, reminderRepeat: String, updatedAt: Long)

    @Query("UPDATE notes SET reminderAt = :reminderAt, reminderRepeat = :reminderRepeat, updatedAt = :updatedAt WHERE id = :noteId")
    abstract suspend fun updateReminderOccurrence(noteId: Long, reminderAt: Long, reminderRepeat: String, updatedAt: Long)

    @Query("UPDATE notes SET reminderAt = :reminderAt, reminderRepeat = :reminderRepeat, updatedAt = :updatedAt WHERE id = :noteId AND reminderAt = :expectedReminderAt AND reminderRepeat = :expectedReminderRepeat AND activeReminderFiredAt = :expectedActiveReminderFiredAt AND isDeleted = 0")
    abstract suspend fun updateReminderOccurrenceIfCurrent(
        noteId: Long,
        expectedReminderAt: Long,
        expectedReminderRepeat: String,
        expectedActiveReminderFiredAt: Long,
        reminderAt: Long,
        reminderRepeat: String,
        updatedAt: Long,
    ): Int

    @Query("UPDATE notes SET reminderSnoozeUntil = :snoozeUntil WHERE id = :noteId")
    abstract suspend fun setReminderSnoozeUntil(noteId: Long, snoozeUntil: Long?)

    @Query("UPDATE notes SET activeReminderFiredAt = :firedAt WHERE id = :noteId")
    abstract suspend fun setActiveReminderFiredAt(noteId: Long, firedAt: Long?)

    @Query("UPDATE notes SET activeReminderFiredAt = :firedAt WHERE id = :noteId AND reminderAt = :expectedReminderAt AND reminderRepeat = :expectedReminderRepeat AND isDeleted = 0")
    abstract suspend fun setActiveReminderFiredAtIfCurrent(
        noteId: Long,
        expectedReminderAt: Long,
        expectedReminderRepeat: String,
        firedAt: Long?,
    ): Int

    @Query("UPDATE notes SET title = :title, textContent = NULL, drawingData = :drawingData, updatedAt = max(:updatedAt, updatedAt + 1) WHERE id = :noteId AND type = :drawingType AND isDeleted = 0")
    abstract suspend fun updateDrawingNoteContent(
        noteId: Long,
        title: String,
        drawingData: String,
        updatedAt: Long,
        drawingType: String = NoteTypes.DRAWING,
    ): Int

    @Query("UPDATE notes SET title = :title, textContent = NULL, drawingData = :drawingData, updatedAt = max(:updatedAt, updatedAt + 1) WHERE id = :noteId AND type = :drawingType AND isDeleted = 0 AND (updatedAt = :expectedUpdatedAt OR (title = :expectedTitle AND drawingData = :expectedDrawingData))")
    abstract suspend fun updateDrawingNoteContentIfUnchanged(
        noteId: Long,
        title: String,
        drawingData: String,
        updatedAt: Long,
        expectedUpdatedAt: Long,
        expectedTitle: String,
        expectedDrawingData: String,
        drawingType: String = NoteTypes.DRAWING,
    ): Int

    @Query("UPDATE notes SET title = :title, textContent = NULL, drawingData = :drawingData, updatedAt = max(:updatedAt, updatedAt + 1) WHERE id = :noteId AND type = :drawingType AND isDeleted = 0 AND (updatedAt = :expectedUpdatedAt OR (title = :expectedTitle AND drawingData = :expectedDrawingData))")
    abstract fun updateDrawingNoteContentIfUnchangedBlocking(
        noteId: Long,
        title: String,
        drawingData: String,
        updatedAt: Long,
        expectedUpdatedAt: Long,
        expectedTitle: String,
        expectedDrawingData: String,
        drawingType: String = NoteTypes.DRAWING,
    ): Int

    @Query("UPDATE notes SET activeReminderFiredAt = :firedAt WHERE id = :noteId AND reminderSnoozeUntil = :expectedSnoozeUntil AND isDeleted = 0")
    abstract suspend fun setActiveSnoozedReminderFiredAtIfCurrent(
        noteId: Long,
        expectedSnoozeUntil: Long,
        firedAt: Long?,
    ): Int

    @Query("DELETE FROM notes WHERE id = :noteId")
    abstract suspend fun deleteNote(noteId: Long)

    @Transaction
    open suspend fun deleteBlankLocalTextDraftNote(noteId: Long): Int {
        val current = getNote(noteId) ?: return 0
        val isBlankTextDraft = current.type == NoteTypes.TEXT &&
            !current.isDeleted &&
            current.title.isBlank() &&
            current.textContent.orEmpty().isBlank() &&
            current.textFormattingJson.isNullOrBlank()
        if (!isBlankTextDraft) return 0
        deleteNote(noteId)
        return 1
    }

    @Transaction
    open suspend fun deleteBlankLocalDrawingDraftNote(noteId: Long, isNewDraft: Boolean): Int {
        if (!isNewDraft) return 0
        val current = getNote(noteId) ?: return 0
        val isBlankDrawingDraft = current.type == NoteTypes.DRAWING &&
            !current.isDeleted &&
            current.reminderAt == null &&
            !current.isPinned
        if (!isBlankDrawingDraft) return 0
        deleteNote(noteId)
        return 1
    }

    @Query("DELETE FROM notes")
    abstract suspend fun deleteAllNotes()

    @Query("DELETE FROM note_tombstones")
    abstract suspend fun deleteAllNoteTombstones()

    @Query("DELETE FROM folders")
    abstract suspend fun deleteAllFolders()

    @Query("SELECT id FROM folders WHERE syncId = ''")
    abstract suspend fun getFolderIdsMissingSyncIds(): List<Long>

    @Query("SELECT id FROM notes WHERE syncId = ''")
    abstract suspend fun getNoteIdsMissingSyncIds(): List<Long>

    @Query("UPDATE folders SET syncId = :syncId WHERE id = :folderId")
    abstract suspend fun updateFolderSyncId(folderId: Long, syncId: String)

    @Query("UPDATE notes SET syncId = :syncId WHERE id = :noteId")
    abstract suspend fun updateNoteSyncId(noteId: Long, syncId: String)

    @Transaction
    open suspend fun ensureDefaultFolder(now: Long = System.currentTimeMillis()) {
        insertFolder(
            FolderEntity(
                id = DEFAULT_FOLDER_ID,
                syncId = DEFAULT_FOLDER_SYNC_ID,
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
        softDeleteFolder(folderId, now)
    }

    @Transaction
    open suspend fun ensureSyncMetadata(now: Long = System.currentTimeMillis()) {
        ensureDefaultFolder(now)
        getFolderIdsMissingSyncIds().forEach { folderId ->
            updateFolderSyncId(
                folderId = folderId,
                syncId = if (folderId == DEFAULT_FOLDER_ID) {
                    DEFAULT_FOLDER_SYNC_ID
                } else {
                    SyncIds.newFolderSyncId()
                },
            )
        }
        getNoteIdsMissingSyncIds().forEach { noteId ->
            updateNoteSyncId(noteId, SyncIds.newNoteSyncId())
        }
    }

    @Transaction
    open suspend fun replaceAllData(backupData: BackupData) {
        deleteAllNotes()
        deleteAllFolders()
        deleteAllNoteTombstones()
        restoreFolders(backupData.folders)
        restoreNotes(backupData.notes)
        ensureSyncMetadata()
    }

    @Transaction
    open suspend fun permanentlyDeleteNote(noteId: Long, now: Long = System.currentTimeMillis()) {
        ensureSyncMetadata(now)
        val note = getNote(noteId) ?: return
        upsertNoteTombstones(listOf(NoteTombstoneEntity(syncId = note.syncId, deletedAt = now)))
        deleteNote(noteId)
    }

    @Transaction
    open suspend fun getSyncData(now: Long = System.currentTimeMillis()): BackupData {
        ensureSyncMetadata(now)
        return BackupData(
            folders = getAllFolders(),
            notes = getAllNotes(),
        )
    }

    @Transaction
    open suspend fun replaceAllDataIfFingerprintMatches(
        backupData: BackupData,
        noteTombstones: List<NoteTombstoneEntity>,
        expectedFingerprint: Int,
    ): Boolean {
        val current = getSyncData()
        if (SyncFingerprint.calculate(current.folders, current.notes, getNoteTombstones()) != expectedFingerprint) {
            return false
        }
        deleteAllNotes()
        deleteAllFolders()
        deleteAllNoteTombstones()
        restoreFolders(backupData.folders)
        restoreNotes(backupData.notes)
        upsertNoteTombstones(noteTombstones)
        ensureSyncMetadata()
        return true
    }
}
