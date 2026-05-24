package com.example.notepad.data

import kotlinx.coroutines.flow.Flow

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

    suspend fun saveTextNote(noteId: Long, title: String, content: String): Long? {
        val current = dao.getNote(noteId) ?: return null
        if (current.title == title && current.textContent == content) return current.updatedAt
        val now = System.currentTimeMillis()

        dao.updateNote(
            current.copy(
                title = title,
                textContent = content,
                drawingData = null,
                updatedAt = now,
            ),
        )
        return now
    }

    suspend fun saveDrawingNote(noteId: Long, title: String, drawingData: String): Long? {
        val current = dao.getNote(noteId) ?: return null
        if (current.title == title && current.drawingData == drawingData) return current.updatedAt
        val now = System.currentTimeMillis()

        dao.updateNote(
            current.copy(
                title = title,
                textContent = null,
                drawingData = drawingData,
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

    suspend fun setNotePinned(noteId: Long, isPinned: Boolean) {
        dao.setNotePinned(noteId, isPinned, System.currentTimeMillis())
    }

    suspend fun setNoteReminder(noteId: Long, reminderAt: Long?): NoteEntity? {
        dao.setNoteReminder(noteId, reminderAt, System.currentTimeMillis())
        return dao.getNote(noteId)
    }

    suspend fun getFutureReminderNotes(): List<NoteEntity> {
        return dao.getFutureReminderNotes(System.currentTimeMillis())
    }

    suspend fun exportBackupJson(): String {
        dao.ensureSyncMetadata()
        return BackupJson.encode(
            folders = dao.getAllFolders(),
            notes = dao.getAllNotes(),
        )
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
        return LocalSyncExport(
            snapshot = RemoteSyncSnapshot(
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
                notes = notes.map { note ->
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
                        purged = true,
                    )
                },
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
        val notes = snapshot.notes.filterNot { it.purged }.map { remoteNote ->
            val noteId = existingNoteIdsBySyncId[remoteNote.syncId] ?: nextNoteId++
            val type = when (remoteNote.type) {
                NoteTypes.DRAWING -> NoteTypes.DRAWING
                else -> NoteTypes.TEXT
            }
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
                textContent = if (type == NoteTypes.TEXT) remoteNote.textContent.orEmpty() else null,
                drawingData = if (type == NoteTypes.DRAWING) remoteNote.drawingData ?: "[]" else null,
                createdAt = remoteNote.createdAt,
                updatedAt = remoteNote.updatedAt,
                isDeleted = remoteNote.deletedAt != null,
                deletedAt = remoteNote.deletedAt,
                isPinned = remoteNote.isPinned,
                reminderAt = remoteNote.reminderAt,
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
                    note.drawingData,
                    note.updatedAt,
                    note.deletedAt,
                    note.isDeleted,
                    note.isPinned,
                    note.reminderAt,
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
