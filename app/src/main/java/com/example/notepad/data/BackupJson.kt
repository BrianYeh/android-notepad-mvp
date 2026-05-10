package com.example.notepad.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val folders: List<FolderEntity>,
    val notes: List<NoteEntity>,
)

object BackupJson {
    private const val VERSION = 1

    fun encode(folders: List<FolderEntity>, notes: List<NoteEntity>): String {
        return JSONObject()
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("folders", JSONArray().apply {
                folders.forEach { folder ->
                    put(
                        JSONObject()
                            .put("id", folder.id)
                            .put("name", folder.name)
                            .put("createdAt", folder.createdAt)
                            .put("updatedAt", folder.updatedAt),
                    )
                }
            })
            .put("notes", JSONArray().apply {
                notes.forEach { note ->
                    put(
                        JSONObject()
                            .put("id", note.id)
                            .put("folderId", note.folderId)
                            .put("type", note.type)
                            .put("title", note.title)
                            .putNullable("textContent", note.textContent)
                            .putNullable("drawingData", note.drawingData)
                            .put("createdAt", note.createdAt)
                            .put("updatedAt", note.updatedAt),
                    )
                }
            })
            .toString()
    }

    fun decode(json: String, now: Long = System.currentTimeMillis()): BackupData {
        val root = JSONObject(json)
        val folders = LinkedHashMap<Long, FolderEntity>()
        val folderArray = root.optJSONArray("folders") ?: JSONArray()

        for (index in 0 until folderArray.length()) {
            val folderJson = folderArray.optJSONObject(index) ?: continue
            val id = folderJson.optLong("id", 0L)
            if (id <= 0L) continue

            folders[id] = FolderEntity(
                id = id,
                name = folderJson.optString("name").ifBlank {
                    if (id == DEFAULT_FOLDER_ID) DEFAULT_FOLDER_NAME else "Folder $id"
                },
                createdAt = folderJson.optLong("createdAt", now),
                updatedAt = folderJson.optLong("updatedAt", now),
            )
        }

        folders[DEFAULT_FOLDER_ID] = folders[DEFAULT_FOLDER_ID]
            ?.copy(name = DEFAULT_FOLDER_NAME)
            ?: FolderEntity(
                id = DEFAULT_FOLDER_ID,
                name = DEFAULT_FOLDER_NAME,
                createdAt = now,
                updatedAt = now,
            )

        val notes = LinkedHashMap<Long, NoteEntity>()
        val noteArray = root.optJSONArray("notes") ?: JSONArray()

        for (index in 0 until noteArray.length()) {
            val noteJson = noteArray.optJSONObject(index) ?: continue
            val id = noteJson.optLong("id", 0L)
            if (id <= 0L) continue

            val type = when (noteJson.optString("type")) {
                NoteTypes.DRAWING -> NoteTypes.DRAWING
                else -> NoteTypes.TEXT
            }
            val folderId = noteJson.optLong("folderId", DEFAULT_FOLDER_ID)
                .takeIf { folders.containsKey(it) }
                ?: DEFAULT_FOLDER_ID

            notes[id] = NoteEntity(
                id = id,
                folderId = folderId,
                type = type,
                title = noteJson.optString("title"),
                textContent = if (type == NoteTypes.TEXT) {
                    noteJson.optionalString("textContent").orEmpty()
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
            )
        }

        return BackupData(
            folders = folders.values.sortedWith(
                compareBy<FolderEntity> { if (it.id == DEFAULT_FOLDER_ID) 0 else 1 }
                    .thenBy { it.id },
            ),
            notes = notes.values.sortedBy { it.id },
        )
    }
}

private fun JSONObject.putNullable(name: String, value: String?): JSONObject {
    return put(name, value ?: JSONObject.NULL)
}

private fun JSONObject.optionalString(name: String): String? {
    return if (has(name) && !isNull(name)) getString(name) else null
}
