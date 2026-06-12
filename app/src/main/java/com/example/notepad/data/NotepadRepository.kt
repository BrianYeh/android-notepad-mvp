package com.example.notepad.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar

class NotepadRepository(
    private val dao: NotepadDao,
) {
    val folders: Flow<List<FolderEntity>> = dao.observeFolders()
    val allNotes: Flow<List<NoteEntity>> = dao.observeAllNotes()

    fun notes(folderId: Long?): Flow<List<NoteEntity>> {
        return if (folderId == null) {
            dao.observeAllNotes()
        } else {
            dao.observeNotesByFolder(folderId)
        }
    }

    fun observeNote(noteId: Long): Flow<NoteEntity?> = dao.observeNote(noteId)

    suspend fun getNote(noteId: Long): NoteEntity? = dao.getNote(noteId)

    suspend fun ensureDefaultFolder() {
        dao.ensureSyncMetadata()
    }

    suspend fun createFolder(name: String): Long {
        dao.ensureDefaultFolder()
        val now = System.currentTimeMillis()
        return dao.insertFolder(
            FolderEntity(
                name = name.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun renameFolder(folderId: Long, name: String) {
        if (folderId == DEFAULT_FOLDER_ID) return
        dao.renameFolder(folderId, name.trim(), System.currentTimeMillis())
    }

    suspend fun deleteFolder(folderId: Long) {
        dao.deleteFolderAndMoveNotes(folderId)
    }

    suspend fun createTextNote(folderId: Long?): Long {
        dao.ensureDefaultFolder()
        val now = System.currentTimeMillis()
        return dao.insertNote(
            NoteEntity(
                folderId = folderId ?: DEFAULT_FOLDER_ID,
                type = NoteTypes.TEXT,
                title = "",
                textContent = "",
                drawingData = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun createSharedTextNote(title: String, content: String): Long {
        dao.ensureDefaultFolder()
        val now = System.currentTimeMillis()
        return dao.insertNote(
            NoteEntity(
                folderId = DEFAULT_FOLDER_ID,
                type = NoteTypes.TEXT,
                title = title,
                textContent = content,
                drawingData = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun createDrawingNote(folderId: Long?): Long {
        dao.ensureDefaultFolder()
        val now = System.currentTimeMillis()
        return dao.insertNote(
            NoteEntity(
                folderId = folderId ?: DEFAULT_FOLDER_ID,
                type = NoteTypes.DRAWING,
                title = "",
                textContent = null,
                drawingData = "[]",
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun createChecklistNote(folderId: Long?): Long {
        dao.ensureDefaultFolder()
        val now = System.currentTimeMillis()
        return dao.insertNote(
            NoteEntity(
                folderId = folderId ?: DEFAULT_FOLDER_ID,
                type = NoteTypes.CHECKLIST,
                title = "",
                textContent = ChecklistJson.encode(ChecklistJson.emptyItems()),
                drawingData = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun saveTextNote(
        noteId: Long,
        title: String,
        content: String,
        textFormattingJson: String? = null,
    ): Long? {
        val current = dao.getNote(noteId) ?: return null
        val nextFormatting = if (textFormattingJson == null) {
            current.textFormattingJson
        } else {
            textFormattingJson.takeIf { it.isNotBlank() }
        }
        if (
            current.title == title &&
            current.textContent == content &&
            current.textFormattingJson == nextFormatting
        ) {
            return current.updatedAt
        }
        val now = System.currentTimeMillis()

        dao.updateNote(
            current.copy(
                title = title,
                textContent = content,
                drawingData = null,
                textFormattingJson = nextFormatting,
                updatedAt = now,
            ),
        )
        return now
    }

    suspend fun saveDrawingNote(noteId: Long, title: String, drawingData: String): Long? {
        return saveDrawingNote(
            noteId = noteId,
            title = title,
            drawingData = drawingData,
            expectedUpdatedAt = null,
            expectedTitle = null,
            expectedDrawingData = null,
            saveEditGate = null,
            isCurrentBeforeWrite = { true },
        )
    }

    suspend fun saveDrawingNoteIfCurrent(
        noteId: Long,
        title: String,
        drawingData: String,
        expectedUpdatedAt: Long,
        expectedTitle: String,
        expectedDrawingData: String,
        saveEditGate: DrawingSaveEditGate,
        isCurrentBeforeWrite: () -> Boolean,
    ): Long? {
        return saveDrawingNote(
            noteId = noteId,
            title = title,
            drawingData = drawingData,
            expectedUpdatedAt = expectedUpdatedAt,
            expectedTitle = expectedTitle,
            expectedDrawingData = expectedDrawingData,
            saveEditGate = saveEditGate,
            isCurrentBeforeWrite = isCurrentBeforeWrite,
        )
    }

    private suspend fun saveDrawingNote(
        noteId: Long,
        title: String,
        drawingData: String,
        expectedUpdatedAt: Long?,
        expectedTitle: String?,
        expectedDrawingData: String?,
        saveEditGate: DrawingSaveEditGate?,
        isCurrentBeforeWrite: () -> Boolean,
    ): Long? {
        val current = dao.getNote(noteId) ?: return null
        if (current.type != NoteTypes.DRAWING || current.isDeleted) return null
        if (!isCurrentBeforeWrite()) return null
        if (current.title == title && current.drawingData == drawingData) return current.updatedAt
        if (
            expectedUpdatedAt != null &&
            current.updatedAt != expectedUpdatedAt &&
            (expectedTitle == null || current.title != expectedTitle || current.drawingData != expectedDrawingData)
        ) {
            return null
        }
        val now = maxOf(System.currentTimeMillis(), current.updatedAt + 1)

        val updatedRows = if (expectedUpdatedAt == null) {
            dao.updateDrawingNoteContent(
                noteId = noteId,
                title = title,
                drawingData = drawingData,
                updatedAt = now,
            )
        } else if (saveEditGate != null) {
            withContext(Dispatchers.IO) {
                saveEditGate.withSaveCommitSection {
                    if (!isCurrentBeforeWrite()) {
                        0
                    } else {
                        dao.updateDrawingNoteContentIfUnchangedBlocking(
                            noteId = noteId,
                            title = title,
                            drawingData = drawingData,
                            updatedAt = now,
                            expectedUpdatedAt = expectedUpdatedAt,
                            expectedTitle = expectedTitle.orEmpty(),
                            expectedDrawingData = expectedDrawingData.orEmpty(),
                        )
                    }
                }
            }
        } else {
            if (!isCurrentBeforeWrite()) return null
            dao.updateDrawingNoteContentIfUnchanged(
                noteId = noteId,
                title = title,
                drawingData = drawingData,
                updatedAt = now,
                expectedUpdatedAt = expectedUpdatedAt,
                expectedTitle = expectedTitle.orEmpty(),
                expectedDrawingData = expectedDrawingData.orEmpty(),
            )
        }
        return if (updatedRows > 0) {
            dao.getNote(noteId)?.updatedAt ?: now
        } else {
            null
        }
    }

    suspend fun saveChecklistNote(noteId: Long, title: String, checklistJson: String): Long? {
        val current = dao.getNote(noteId) ?: return null
        if (current.title == title && current.textContent == checklistJson) return current.updatedAt
        val now = System.currentTimeMillis()

        dao.updateNote(
            current.copy(
                title = title,
                type = NoteTypes.CHECKLIST,
                textContent = checklistJson,
                drawingData = null,
                updatedAt = now,
            ),
        )
        return now
    }

    suspend fun moveNote(noteId: Long, folderId: Long) {
        dao.ensureDefaultFolder()
        if (dao.getFolder(folderId) == null) return
        dao.moveNote(noteId, folderId, System.currentTimeMillis())
    }

    suspend fun deleteNote(noteId: Long) {
        dao.softDeleteNote(noteId, System.currentTimeMillis())
    }

    suspend fun restoreNote(noteId: Long): NoteEntity? {
        dao.restoreNote(noteId, System.currentTimeMillis())
        return dao.getNote(noteId)
    }

    suspend fun permanentlyDeleteNote(noteId: Long) {
        dao.permanentlyDeleteNote(noteId)
    }

    suspend fun permanentlyDeleteBlankTextDraft(noteId: Long): Boolean {
        val current = dao.getNote(noteId) ?: return true
        val isBlankTextDraft = current.type == NoteTypes.TEXT &&
            !current.isDeleted &&
            current.title.isBlank() &&
            current.textContent.orEmpty().isBlank() &&
            current.textFormattingJson.isNullOrBlank()
        if (!isBlankTextDraft) return false
        return dao.deleteBlankLocalTextDraftNote(noteId) > 0
    }

    suspend fun discardNewTextDraftIfBlank(
        noteId: Long,
        title: String,
        content: String,
        textFormattingJson: String?,
    ): Boolean {
        if (title.isNotBlank() || content.isNotBlank() || !textFormattingJson.isNullOrBlank()) return false
        val current = dao.getNote(noteId) ?: return true
        if (current.type != NoteTypes.TEXT || current.isDeleted) return false
        dao.deleteNote(noteId)
        return true
    }

    suspend fun discardNewDrawingDraftIfBlank(
        noteId: Long,
        isNewDraft: Boolean,
        title: String,
        drawingData: String,
        saveEditGate: DrawingSaveEditGate? = null,
        isCurrentBeforeDelete: () -> Boolean = { true },
        expectedUpdatedAt: Long? = null,
        expectedTitle: String? = null,
        expectedDrawingData: String? = null,
    ): Boolean {
        if (!isNewDraft) return false
        if (title.isNotBlank() || DrawingJson.decode(drawingData).isNotEmpty()) return false
        return if (saveEditGate != null) {
            withContext(Dispatchers.IO) {
                saveEditGate.withSaveCommitSection {
                    if (!isCurrentBeforeDelete()) {
                        false
                    } else {
                        val current = dao.getNoteBlocking(noteId) ?: return@withSaveCommitSection true
                        if (current.type != NoteTypes.DRAWING || current.isDeleted) {
                            false
                        } else if (current.reminderAt != null || current.isPinned) {
                            false
                        } else {
                            val currentDrawingData = current.drawingData.orEmpty()
                            val isPersistedBlankDraft = current.title.isBlank() &&
                                DrawingJson.decode(currentDrawingData).isEmpty()
                            if (!isPersistedBlankDraft) {
                                if (
                                    expectedUpdatedAt == null ||
                                    expectedTitle == null ||
                                    expectedDrawingData == null
                                ) {
                                    return@withSaveCommitSection false
                                }
                                val deletedRows = dao.blankAndDeleteLocalDrawingDraftNoteIfUnchangedBlocking(
                                    noteId = noteId,
                                    isNewDraft = true,
                                    title = title,
                                    drawingData = drawingData,
                                    updatedAt = maxOf(System.currentTimeMillis(), current.updatedAt + 1),
                                    expectedUpdatedAt = expectedUpdatedAt,
                                    expectedTitle = expectedTitle,
                                    expectedDrawingData = expectedDrawingData,
                                )
                                return@withSaveCommitSection deletedRows > 0
                            }
                            dao.deleteBlankLocalDrawingDraftNoteBlocking(noteId, isNewDraft = true) > 0
                        }
                    }
                }
            }
        } else {
            if (!isCurrentBeforeDelete()) return false
            val current = dao.getNote(noteId) ?: return true
            if (current.type != NoteTypes.DRAWING || current.isDeleted) return false
            dao.deleteBlankLocalDrawingDraftNote(noteId, isNewDraft = true) > 0
        }
    }

    suspend fun setNotePinned(noteId: Long, isPinned: Boolean) {
        dao.setNotePinned(noteId, isPinned, System.currentTimeMillis())
    }

    suspend fun setNoteReminder(
        noteId: Long,
        reminderAt: Long?,
        reminderRepeat: String = ReminderRepeat.None.code,
    ): NoteEntity? {
        dao.setNoteReminder(
            noteId = noteId,
            reminderAt = reminderAt,
            reminderRepeat = if (reminderAt == null) {
                ReminderRepeat.None.code
            } else {
                normalizedReminderRepeat(reminderRepeat)
            },
            updatedAt = System.currentTimeMillis(),
        )
        return dao.getNote(noteId)
    }

    suspend fun getFutureReminderNotes(): List<NoteEntity> {
        return dao.getFutureReminderNotes(System.currentTimeMillis())
    }

    suspend fun getReminderNotes(): List<NoteEntity> {
        return dao.getReminderNotes()
    }

    suspend fun exportBackupJson(): String {
        dao.ensureSyncMetadata()
        return BackupJson.encode(
            folders = dao.getAllFolders(),
            notes = dao.getAllNotes(),
        )
    }

    suspend fun exportBatchZip(): ByteArray {
        dao.ensureSyncMetadata()
        return BatchPortability.exportZip(
            folders = dao.getAllFolders(),
            notes = dao.getAllNotes(),
        )
    }

    suspend fun importTextFiles(files: List<TextImportFile>): Int {
        dao.ensureDefaultFolder()
        var imported = 0
        files.forEach { file ->
            val content = file.content.trimEnd()
            if (content.isBlank()) return@forEach
            val now = System.currentTimeMillis()
            dao.insertNote(
                NoteEntity(
                    folderId = DEFAULT_FOLDER_ID,
                    type = NoteTypes.TEXT,
                    title = BatchPortability.titleFromFileName(file.name),
                    textContent = content,
                    drawingData = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            imported += 1
        }
        return imported
    }

    suspend fun currentBackupData(): BackupData {
        dao.ensureSyncMetadata()
        return BackupData(
            folders = dao.getAllFolders(),
            notes = dao.getAllNotes(),
        )
    }

    fun decodeBackupJson(json: String): DecodedBackup {
        return BackupJson.decodeWithPreview(json)
    }

    suspend fun importBackupJson(json: String) {
        importBackupData(BackupJson.decode(json))
    }

    suspend fun importBackupJsonWithRollbackCheckpoint(json: String): BackupData {
        return importBackupDataWithRollbackCheckpoint(BackupJson.decode(json))
    }

    suspend fun importBackupDataWithRollbackCheckpoint(backupData: BackupData): BackupData {
        val rollbackCheckpoint = currentBackupData()
        importBackupData(backupData)
        return rollbackCheckpoint
    }

    suspend fun importBackupData(backupData: BackupData) {
        dao.replaceAllData(backupData)
    }

    suspend fun syncFingerprint(): Int {
        val syncData = dao.getSyncData()
        return SyncFingerprint.calculate(syncData.folders, syncData.notes, dao.getNoteTombstones())
    }

    suspend fun exportRemoteSyncSnapshotWithFingerprint(
        sourceDevice: SyncDevice,
        accountEmail: String?,
        now: Long = System.currentTimeMillis(),
    ): LocalSyncExport {
        val syncData = dao.getSyncData(now)
        val folders = syncData.folders
        val notes = syncData.notes
        val tombstones = dao.getNoteTombstones()
        val folderSyncIdsById = folders.associate { it.id to it.syncId }
        val remoteNotes = notes.map { note ->
            RemoteNote(
                syncId = note.syncId,
                folderSyncId = folderSyncIdsById[note.folderId] ?: DEFAULT_FOLDER_SYNC_ID,
                type = note.type,
                title = note.title,
                textContent = note.textContent,
                drawingData = note.drawingData,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt,
                deletedAt = note.deletedAt,
                isPinned = note.isPinned,
                reminderAt = note.reminderAt,
                reminderRepeat = normalizedReminderRepeat(note.reminderRepeat),
                textFormattingJson = note.textFormattingJson,
            )
        } + tombstones.map { tombstone ->
            RemoteNote(
                syncId = tombstone.syncId,
                folderSyncId = DEFAULT_FOLDER_SYNC_ID,
                type = NoteTypes.TEXT,
                title = "",
                textContent = "",
                drawingData = null,
                createdAt = tombstone.deletedAt,
                updatedAt = tombstone.deletedAt,
                deletedAt = tombstone.deletedAt,
                reminderRepeat = ReminderRepeat.None.code,
                purged = true,
            )
        }
        return LocalSyncExport(
            snapshot = RemoteSyncSnapshot(
                formatVersion = remoteSyncSnapshotVersionFor(remoteNotes),
                exportedAt = now,
                sourceDevice = sourceDevice.copy(lastSyncAt = now),
                devices = listOf(sourceDevice.copy(lastSyncAt = now)),
                folders = folders.map { folder ->
                    RemoteFolder(
                        syncId = folder.syncId,
                        name = folder.name,
                        createdAt = folder.createdAt,
                        updatedAt = folder.updatedAt,
                        deletedAt = folder.deletedAt,
                    )
                },
                notes = remoteNotes,
            ),
            fingerprint = SyncFingerprint.calculate(folders, notes, tombstones),
        )
    }

    suspend fun replaceWithRemoteSyncSnapshot(
        snapshot: RemoteSyncSnapshot,
        expectedFingerprint: Int,
    ): Boolean {
        dao.ensureSyncMetadata()
        val existingFolders = dao.getAllFolders()
        val existingNotes = dao.getAllNotes()
        val existingFolderIdsBySyncId = existingFolders.associate { it.syncId to it.id }
        val existingNoteIdsBySyncId = existingNotes.associate { it.syncId to it.id }
        val existingNotesBySyncId = existingNotes.associateBy { it.syncId }
        var nextFolderId = (existingFolders.maxOfOrNull { it.id } ?: DEFAULT_FOLDER_ID) + 1L
        var nextNoteId = (existingNotes.maxOfOrNull { it.id } ?: 0L) + 1L

        val remoteFolders = (snapshot.folders + defaultRemoteFolderIfMissing(snapshot))
            .distinctBy { it.syncId }
        val folderIdsBySyncId = mutableMapOf<String, Long>()
        val deletedFolderSyncIds = remoteFolders
            .filter { it.deletedAt != null && it.syncId != DEFAULT_FOLDER_SYNC_ID }
            .map { it.syncId }
            .toSet()
        val folders = remoteFolders.map { remoteFolder ->
            val folderId = when (remoteFolder.syncId) {
                DEFAULT_FOLDER_SYNC_ID -> DEFAULT_FOLDER_ID
                else -> existingFolderIdsBySyncId[remoteFolder.syncId] ?: nextFolderId++
            }
            folderIdsBySyncId[remoteFolder.syncId] = folderId
            FolderEntity(
                id = folderId,
                syncId = remoteFolder.syncId,
                name = if (remoteFolder.syncId == DEFAULT_FOLDER_SYNC_ID) {
                    DEFAULT_FOLDER_NAME
                } else {
                    remoteFolder.name.ifBlank { "Folder $folderId" }
                },
                createdAt = remoteFolder.createdAt,
                updatedAt = remoteFolder.updatedAt,
                isDeleted = remoteFolder.deletedAt != null && remoteFolder.syncId != DEFAULT_FOLDER_SYNC_ID,
                deletedAt = remoteFolder.deletedAt.takeIf { remoteFolder.syncId != DEFAULT_FOLDER_SYNC_ID },
            )
        }

        val noteTombstones = snapshot.notes
            .filter { it.purged && it.deletedAt != null }
            .map { remoteNote ->
                NoteTombstoneEntity(
                    syncId = remoteNote.syncId,
                    deletedAt = remoteNote.deletedAt ?: remoteNote.updatedAt,
                )
            }
        val now = System.currentTimeMillis()
        val notes = snapshot.notes.filterNot { it.purged }.map { remoteNote ->
            val noteId = existingNoteIdsBySyncId[remoteNote.syncId] ?: nextNoteId++
            val type = when (remoteNote.type) {
                NoteTypes.DRAWING -> NoteTypes.DRAWING
                NoteTypes.CHECKLIST -> NoteTypes.CHECKLIST
                else -> NoteTypes.TEXT
            }
            val reminderRepeat = normalizedReminderRepeat(remoteNote.reminderRepeat)
            val existingNote = existingNotesBySyncId[remoteNote.syncId]
            val preserveLocalReminderTransient = existingNote != null &&
                normalizedReminderRepeat(existingNote.reminderRepeat) == reminderRepeat &&
                sameReminderScheduleForLocalTransient(
                    localReminderAt = existingNote.reminderAt,
                    remoteReminderAt = remoteNote.reminderAt,
                    reminderRepeat = reminderRepeat,
                    now = now,
                )
            NoteEntity(
                id = noteId,
                syncId = remoteNote.syncId,
                folderId = if (remoteNote.deletedAt == null && remoteNote.folderSyncId in deletedFolderSyncIds) {
                    DEFAULT_FOLDER_ID
                } else {
                    folderIdsBySyncId[remoteNote.folderSyncId] ?: DEFAULT_FOLDER_ID
                },
                type = type,
                title = remoteNote.title,
                textContent = if (type == NoteTypes.TEXT || type == NoteTypes.CHECKLIST) remoteNote.textContent.orEmpty() else null,
                drawingData = if (type == NoteTypes.DRAWING) remoteNote.drawingData ?: "[]" else null,
                createdAt = remoteNote.createdAt,
                updatedAt = remoteNote.updatedAt,
                isDeleted = remoteNote.deletedAt != null,
                deletedAt = remoteNote.deletedAt,
                isPinned = remoteNote.isPinned,
                reminderAt = remoteNote.reminderAt,
                reminderRepeat = reminderRepeat,
                textFormattingJson = if (type == NoteTypes.TEXT) remoteNote.textFormattingJson else null,
                reminderSnoozeUntil = existingNote?.reminderSnoozeUntil?.takeIf {
                    preserveLocalReminderTransient
                },
                activeReminderFiredAt = existingNote?.activeReminderFiredAt?.takeIf {
                    preserveLocalReminderTransient
                },
            )
        }

        return dao.replaceAllDataIfFingerprintMatches(
            backupData = BackupData(folders = folders, notes = notes),
            noteTombstones = noteTombstones,
            expectedFingerprint = expectedFingerprint,
        )
    }

    private fun defaultRemoteFolderIfMissing(snapshot: RemoteSyncSnapshot): List<RemoteFolder> {
        if (snapshot.folders.any { it.syncId == DEFAULT_FOLDER_SYNC_ID }) return emptyList()
        val now = System.currentTimeMillis()
        return listOf(
            RemoteFolder(
                syncId = DEFAULT_FOLDER_SYNC_ID,
                name = DEFAULT_FOLDER_NAME,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}

private fun sameReminderScheduleForLocalTransient(
    localReminderAt: Long?,
    remoteReminderAt: Long?,
    reminderRepeat: String,
    now: Long,
): Boolean {
    if (localReminderAt == remoteReminderAt) return true
    val repeat = normalizedReminderRepeat(reminderRepeat)
    if (repeat == ReminderRepeat.None.code) return false
    return nextReminderOccurrence(localReminderAt, repeat, now) ==
        nextReminderOccurrence(remoteReminderAt, repeat, now)
}

private fun nextReminderOccurrence(reminderAt: Long?, reminderRepeat: String, now: Long): Long? {
    reminderAt ?: return null
    if (reminderAt > now) return reminderAt
    val field = when (normalizedReminderRepeat(reminderRepeat)) {
        ReminderRepeat.Daily.code -> Calendar.DAY_OF_YEAR
        ReminderRepeat.Weekly.code -> Calendar.WEEK_OF_YEAR
        ReminderRepeat.Monthly.code -> Calendar.MONTH
        else -> return null
    }
    return Calendar.getInstance().apply {
        timeInMillis = reminderAt
        while (timeInMillis <= now) {
            add(field, 1)
        }
    }.timeInMillis
}

data class LocalSyncExport(
    val snapshot: RemoteSyncSnapshot,
    val fingerprint: Int,
)

object SyncFingerprint {
    fun calculate(
        folders: List<FolderEntity>,
        notes: List<NoteEntity>,
        noteTombstones: List<NoteTombstoneEntity> = emptyList(),
    ): Int {
        val folderPart = folders
            .sortedBy { it.syncId }
            .joinToString("|") { folder ->
                listOf(
                    folder.syncId,
                    folder.name,
                    folder.updatedAt,
                    folder.deletedAt,
                    folder.isDeleted,
                ).joinToString(":")
            }
        val notePart = notes
            .sortedBy { it.syncId }
            .joinToString("|") { note ->
                listOf(
                    note.syncId,
                    note.folderId,
                    note.type,
                    note.title,
                    note.textContent,
                    note.textFormattingJson,
                    note.drawingData,
                    note.updatedAt,
                    note.deletedAt,
                    note.isDeleted,
                    note.isPinned,
                    note.reminderAt,
                    normalizedReminderRepeat(note.reminderRepeat),
                    note.reminderSnoozeUntil,
                    note.activeReminderFiredAt,
                ).joinToString(":")
            }
        val tombstonePart = noteTombstones
            .sortedBy { it.syncId }
            .joinToString("|") { tombstone ->
                listOf(tombstone.syncId, tombstone.deletedAt).joinToString(":")
            }
        return "$folderPart\n$notePart\n$tombstonePart".hashCode()
    }
}
