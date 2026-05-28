package com.example.notepad.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val folders: List<FolderEntity>,
    val notes: List<NoteEntity>,
)

data class DecodedBackup(
    val data: BackupData,
    val preview: BackupPreview,
)

data class BackupPreview(
    val exportedAt: Long?,
    val folderCount: Int,
    val noteCount: Int,
    val activeNoteCount: Int,
    val deletedNoteCount: Int,
) {
    companion object {
        fun from(
            folders: List<FolderEntity>,
            notes: List<NoteEntity>,
            exportedAt: Long? = null,
        ): BackupPreview {
            return BackupPreview(
                exportedAt = exportedAt,
                folderCount = folders.count { !it.isDeleted },
                noteCount = notes.size,
                activeNoteCount = notes.count { !it.isDeleted },
                deletedNoteCount = notes.count { it.isDeleted },
            )
        }
    }
}

object BackupJson {
    private const val VERSION = 7

    fun encode(folders: List<FolderEntity>, notes: List<NoteEntity>): String {
        return JSONObject()
            .put("version", backupVersionFor(notes))
            .put("exportedAt", System.currentTimeMillis())
            .put("folders", JSONArray().apply {
                folders.forEach { folder ->
                    put(
                        JSONObject()
                            .put("id", folder.id)
                            .put("syncId", folder.syncId)
                            .put("name", folder.name)
                            .put("createdAt", folder.createdAt)
                            .put("updatedAt", folder.updatedAt)
                            .put("isDeleted", folder.isDeleted)
                            .putNullableLong("deletedAt", folder.deletedAt),
                    )
                }
            })
            .put("notes", JSONArray().apply {
                notes.forEach { note ->
                    put(
                        JSONObject()
                            .put("id", note.id)
                            .put("syncId", note.syncId)
                            .put("folderId", note.folderId)
                            .put("type", note.type)
                            .put("title", note.title)
                            .putNullable("textContent", note.textContent)
                            .putNullable("textFormattingJson", note.textFormattingJson)
                            .putNullable("drawingData", note.drawingData)
                            .put("createdAt", note.createdAt)
                            .put("updatedAt", note.updatedAt)
                            .put("isDeleted", note.isDeleted)
                            .putNullableLong("deletedAt", note.deletedAt)
                            .put("isPinned", note.isPinned)
                            .putNullableLong("reminderAt", note.reminderAt)
                            .put("reminderRepeat", normalizedReminderRepeat(note.reminderRepeat)),
                    )
                }
            })
            .toString()
    }

    fun decode(json: String, now: Long = System.currentTimeMillis()): BackupData {
        return decodeWithPreview(json, now).data
    }

    fun decodeWithPreview(json: String, now: Long = System.currentTimeMillis()): DecodedBackup {
        val root = JSONObject(json)
        val version = root.requiredInt("version")
        if (version !in 1..VERSION) {
            throw IllegalArgumentException("Unsupported backup version: $version")
        }
        val exportedAt = root.optionalLong("exportedAt")?.takeIf { it > 0L }
        val folders = LinkedHashMap<Long, FolderEntity>()
        val folderArray = root.requiredArray("folders")

        for (index in 0 until folderArray.length()) {
            val folderJson = folderArray.optJSONObject(index)
                ?: throw IllegalArgumentException("Backup folder entry must be an object.")
            val id = folderJson.optLong("id", 0L)
            if (id <= 0L) {
                throw IllegalArgumentException("Backup folder id must be positive.")
            }
            if (folders.containsKey(id)) {
                throw IllegalArgumentException("Duplicate backup folder id: $id")
            }

            folders[id] = FolderEntity(
                id = id,
                syncId = folderJson.optionalString("syncId")
                    ?: if (id == DEFAULT_FOLDER_ID) DEFAULT_FOLDER_SYNC_ID else SyncIds.newFolderSyncId(),
                name = folderJson.optString("name").ifBlank {
                    if (id == DEFAULT_FOLDER_ID) DEFAULT_FOLDER_NAME else "Folder $id"
                },
                createdAt = folderJson.optLong("createdAt", now),
                updatedAt = folderJson.optLong("updatedAt", now),
                isDeleted = folderJson.optBoolean("isDeleted", false),
                deletedAt = folderJson.optionalLong("deletedAt"),
            )
        }

        folders[DEFAULT_FOLDER_ID] = folders[DEFAULT_FOLDER_ID]
            ?.copy(
                syncId = DEFAULT_FOLDER_SYNC_ID,
                name = DEFAULT_FOLDER_NAME,
                isDeleted = false,
                deletedAt = null,
            )
            ?: FolderEntity(
                id = DEFAULT_FOLDER_ID,
                syncId = DEFAULT_FOLDER_SYNC_ID,
                name = DEFAULT_FOLDER_NAME,
                createdAt = now,
                updatedAt = now,
            )

        val notes = LinkedHashMap<Long, NoteEntity>()
        val noteArray = root.requiredArray("notes")

        for (index in 0 until noteArray.length()) {
            val noteJson = noteArray.optJSONObject(index)
                ?: throw IllegalArgumentException("Backup note entry must be an object.")
            val id = noteJson.optLong("id", 0L)
            if (id <= 0L) {
                throw IllegalArgumentException("Backup note id must be positive.")
            }
            if (notes.containsKey(id)) {
                throw IllegalArgumentException("Duplicate backup note id: $id")
            }

            val type = when (noteJson.optString("type")) {
                NoteTypes.DRAWING -> NoteTypes.DRAWING
                NoteTypes.CHECKLIST -> NoteTypes.CHECKLIST
                else -> NoteTypes.TEXT
            }
            val folderId = noteJson.optLong("folderId", DEFAULT_FOLDER_ID)
                .takeIf { folders.containsKey(it) }
                ?: DEFAULT_FOLDER_ID

            notes[id] = NoteEntity(
                id = id,
                syncId = noteJson.optionalString("syncId") ?: SyncIds.newNoteSyncId(),
                folderId = folderId,
                type = type,
                title = noteJson.optString("title"),
                textContent = if (type == NoteTypes.TEXT || type == NoteTypes.CHECKLIST) {
                    noteJson.optionalString("textContent").orEmpty()
                } else {
                    null
                },
                textFormattingJson = if (type == NoteTypes.TEXT) {
                    noteJson.optionalString("textFormattingJson")
                } else {
                    null
                },
                drawingData = if (type == NoteTypes.DRAWING) {
                    noteJson.optionalString("drawingData") ?: "[]"
                } else {
                    null
                },
                createdAt = noteJson.optLong("createdAt", now),
                updatedAt = noteJson.optLong("updatedAt", now),
                isDeleted = noteJson.optBoolean("isDeleted", false),
                deletedAt = noteJson.optionalLong("deletedAt"),
                isPinned = noteJson.optBoolean("isPinned", false),
                reminderAt = noteJson.optionalLong("reminderAt"),
                reminderRepeat = normalizedReminderRepeat(noteJson.optionalString("reminderRepeat")),
            )
        }

        val backupData = BackupData(
            folders = folders.values.sortedWith(
                compareBy<FolderEntity> { if (it.id == DEFAULT_FOLDER_ID) 0 else 1 }
                    .thenBy { it.id },
            ),
            notes = notes.values.sortedBy { it.id },
        )
        return DecodedBackup(
            data = backupData,
            preview = BackupPreview.from(
                folders = backupData.folders,
                notes = backupData.notes,
                exportedAt = exportedAt,
            ),
        )
    }

    private fun backupVersionFor(notes: List<NoteEntity>): Int {
        return when {
            notes.any { !it.textFormattingJson.isNullOrBlank() } -> 7
            notes.any { normalizedReminderRepeat(it.reminderRepeat) != ReminderRepeat.None.code } -> 6
            notes.any { it.type == NoteTypes.CHECKLIST } -> 5
            else -> 4
        }
    }
}

private fun JSONObject.putNullable(name: String, value: String?): JSONObject {
    return put(name, value ?: JSONObject.NULL)
}

private fun JSONObject.putNullableLong(name: String, value: Long?): JSONObject {
    return put(name, value ?: JSONObject.NULL)
}

private fun JSONObject.optionalString(name: String): String? {
    return if (has(name) && !isNull(name)) getString(name) else null
}

private fun JSONObject.optionalLong(name: String): Long? {
    return if (has(name) && !isNull(name)) getLong(name) else null
}

private fun JSONObject.requiredInt(name: String): Int {
    if (!has(name) || isNull(name)) {
        throw IllegalArgumentException("Backup is missing $name.")
    }
    return getInt(name)
}

private fun JSONObject.requiredArray(name: String): JSONArray {
    if (!has(name) || isNull(name)) {
        throw IllegalArgumentException("Backup is missing $name.")
    }
    return optJSONArray(name) ?: throw IllegalArgumentException("Backup $name must be an array.")
}
