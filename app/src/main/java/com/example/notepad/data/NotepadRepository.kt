package com.example.notepad.data

import kotlinx.coroutines.flow.Flow

class NotepadRepository(
    private val dao: NotepadDao,
) {
    val folders: Flow<List<FolderEntity>> = dao.observeFolders()

    fun notes(folderId: Long?): Flow<List<NoteEntity>> {
        return if (folderId == null) {
            dao.observeAllNotes()
        } else {
            dao.observeNotesByFolder(folderId)
        }
    }

    fun observeNote(noteId: Long): Flow<NoteEntity?> = dao.observeNote(noteId)

    suspend fun ensureDefaultFolder() {
        dao.ensureDefaultFolder()
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

    suspend fun saveTextNote(noteId: Long, title: String, content: String) {
        val current = dao.getNote(noteId) ?: return
        if (current.title == title && current.textContent == content) return

        dao.updateNote(
            current.copy(
                title = title,
                textContent = content,
                drawingData = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun saveDrawingNote(noteId: Long, title: String, drawingData: String) {
        val current = dao.getNote(noteId) ?: return
        if (current.title == title && current.drawingData == drawingData) return

        dao.updateNote(
            current.copy(
                title = title,
                textContent = null,
                drawingData = drawingData,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun moveNote(noteId: Long, folderId: Long) {
        dao.ensureDefaultFolder()
        if (dao.getFolder(folderId) == null) return
        dao.moveNote(noteId, folderId, System.currentTimeMillis())
    }

    suspend fun deleteNote(noteId: Long) {
        dao.deleteNote(noteId)
    }

    suspend fun exportBackupJson(): String {
        dao.ensureDefaultFolder()
        return BackupJson.encode(
            folders = dao.getAllFolders(),
            notes = dao.getAllNotes(),
        )
    }

    suspend fun importBackupJson(json: String) {
        dao.replaceAllData(BackupJson.decode(json))
    }
}
