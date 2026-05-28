package com.example.notepad.data

import org.json.JSONArray
import org.json.JSONObject

object SyncSnapshotJson {
    fun encode(snapshot: RemoteSyncSnapshot): String {
        return JSONObject()
            .put("formatVersion", remoteSyncSnapshotVersionFor(snapshot.notes))
            .put("snapshotId", snapshot.snapshotId)
            .put("exportedAt", snapshot.exportedAt)
            .put("sourceDevice", snapshot.sourceDevice.toJson())
            .put("devices", JSONArray().apply {
                snapshot.devices.forEach { put(it.toJson()) }
            })
            .put("folders", JSONArray().apply {
                snapshot.folders.forEach { folder ->
                    put(
                        JSONObject()
                            .put("syncId", folder.syncId)
                            .put("name", folder.name)
                            .put("createdAt", folder.createdAt)
                            .put("updatedAt", folder.updatedAt)
                            .putNullableLong("deletedAt", folder.deletedAt),
                    )
                }
            })
            .put("notes", JSONArray().apply {
                snapshot.notes.forEach { note ->
                    put(
                        JSONObject()
                            .put("syncId", note.syncId)
                            .put("folderSyncId", note.folderSyncId)
                            .put("type", note.type)
                            .put("title", note.title)
                            .putNullable("textContent", note.textContent)
                            .putNullable("textFormattingJson", note.textFormattingJson)
                            .putNullable("drawingData", note.drawingData)
                            .put("createdAt", note.createdAt)
                            .put("updatedAt", note.updatedAt)
                            .putNullableLong("deletedAt", note.deletedAt)
                            .put("isPinned", note.isPinned)
                            .putNullableLong("reminderAt", note.reminderAt)
                            .put("reminderRepeat", normalizedReminderRepeat(note.reminderRepeat))
                            .put("purged", note.purged),
                    )
                }
            })
            .toString()
    }

    fun decode(json: String): RemoteSyncSnapshot {
        val root = JSONObject(json)
        val formatVersion = root.optInt("formatVersion", 0)
        require(formatVersion in 1..REMOTE_SYNC_SNAPSHOT_VERSION) {
            "Unsupported sync snapshot version: $formatVersion"
        }

        val sourceDevice = root.getJSONObject("sourceDevice").toSyncDevice()
        return RemoteSyncSnapshot(
            formatVersion = formatVersion,
            snapshotId = root.optString("snapshotId").ifBlank { java.util.UUID.randomUUID().toString() },
            exportedAt = root.optLong("exportedAt", 0L),
            sourceDevice = sourceDevice,
            devices = root.optJSONArray("devices")
                ?.objects()
                ?.map { it.toSyncDevice() }
                ?.ifEmpty { listOf(sourceDevice) }
                ?: listOf(sourceDevice),
            folders = root.optJSONArray("folders")
                ?.objects()
                ?.mapNotNull { it.toRemoteFolderOrNull() }
                ?: emptyList(),
            notes = root.optJSONArray("notes")
                ?.objects()
                ?.mapNotNull { it.toRemoteNoteOrNull() }
                ?: emptyList(),
        )
    }

    private fun SyncDevice.toJson(): JSONObject {
        return JSONObject()
            .put("deviceId", deviceId)
            .put("deviceName", deviceName)
            .putNullableLong("lastSyncAt", lastSyncAt)
    }

    private fun JSONObject.toSyncDevice(): SyncDevice {
        return SyncDevice(
            deviceId = optionalString("deviceId") ?: "unknown-device",
            deviceName = optionalString("deviceName") ?: "Unknown device",
            lastSyncAt = optionalLong("lastSyncAt"),
        )
    }

    private fun JSONObject.toRemoteFolderOrNull(): RemoteFolder? {
        val syncId = optionalString("syncId") ?: return null
        return RemoteFolder(
            syncId = syncId,
            name = optionalString("name").orEmpty(),
            createdAt = optLong("createdAt", 0L),
            updatedAt = optLong("updatedAt", 0L),
            deletedAt = optionalLong("deletedAt"),
        )
    }

    private fun JSONObject.toRemoteNoteOrNull(): RemoteNote? {
        val syncId = optionalString("syncId") ?: return null
        return RemoteNote(
            syncId = syncId,
            folderSyncId = optionalString("folderSyncId") ?: DEFAULT_FOLDER_SYNC_ID,
            type = when (optionalString("type")) {
                NoteTypes.DRAWING -> NoteTypes.DRAWING
                NoteTypes.CHECKLIST -> NoteTypes.CHECKLIST
                else -> NoteTypes.TEXT
            },
            title = optionalString("title").orEmpty(),
            textContent = optionalString("textContent"),
            textFormattingJson = optionalString("textFormattingJson"),
            drawingData = optionalString("drawingData"),
            createdAt = optLong("createdAt", 0L),
            updatedAt = optLong("updatedAt", 0L),
            deletedAt = optionalLong("deletedAt"),
            isPinned = optBoolean("isPinned", false),
            reminderAt = optionalLong("reminderAt"),
            reminderRepeat = normalizedReminderRepeat(optionalString("reminderRepeat")),
            purged = optBoolean("purged", false),
        )
    }
}

private fun JSONArray.objects(): List<JSONObject> {
    return buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(::add)
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
