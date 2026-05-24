package com.example.notepad.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncSnapshotJsonTest {
    @Test
    fun roundTripPreservesSyncSnapshot() {
        val device = SyncDevice(
            deviceId = "device-1",
            deviceName = "Pixel",
            lastSyncAt = 30L,
        )
        val snapshot = RemoteSyncSnapshot(
            exportedAt = 30L,
            sourceDevice = device,
            devices = listOf(device),
            folders = listOf(
                RemoteFolder(
                    syncId = DEFAULT_FOLDER_SYNC_ID,
                    name = DEFAULT_FOLDER_NAME,
                    createdAt = 1L,
                    updatedAt = 2L,
                ),
            ),
            notes = listOf(
                RemoteNote(
                    syncId = "note-1",
                    folderSyncId = DEFAULT_FOLDER_SYNC_ID,
                    type = NoteTypes.TEXT,
                    title = "Title",
                    textContent = "Body",
                    drawingData = null,
                    createdAt = 3L,
                    updatedAt = 4L,
                    isPinned = true,
                    reminderAt = 5L,
                    purged = true,
                ),
            ),
        )

        val decoded = SyncSnapshotJson.decode(SyncSnapshotJson.encode(snapshot))

        assertEquals(snapshot.sourceDevice, decoded.sourceDevice)
        assertEquals(snapshot.folders, decoded.folders)
        assertEquals(snapshot.notes, decoded.notes)
    }

    @Test
    fun encodedSnapshotUsesChecklistCompatibleVersion() {
        val device = SyncDevice(deviceId = "device-1", deviceName = "Pixel")
        val snapshot = RemoteSyncSnapshot(
            exportedAt = 30L,
            sourceDevice = device,
            folders = listOf(
                RemoteFolder(
                    syncId = DEFAULT_FOLDER_SYNC_ID,
                    name = DEFAULT_FOLDER_NAME,
                    createdAt = 1L,
                    updatedAt = 2L,
                ),
            ),
            notes = listOf(
                RemoteNote(
                    syncId = "note-checklist",
                    folderSyncId = DEFAULT_FOLDER_SYNC_ID,
                    type = NoteTypes.CHECKLIST,
                    title = "Groceries",
                    textContent = ChecklistJson.encode(
                        listOf(ChecklistItem(text = "Milk", checked = true)),
                    ),
                    drawingData = null,
                    createdAt = 3L,
                    updatedAt = 4L,
                ),
            ),
        )

        val encoded = SyncSnapshotJson.encode(snapshot)
        val decoded = SyncSnapshotJson.decode(encoded)

        assertTrue(encoded.contains("\"formatVersion\":2"))
        assertEquals(NoteTypes.CHECKLIST, decoded.notes.single().type)
    }

    @Test
    fun encodedTextOnlySnapshotKeepsLegacyCompatibleVersion() {
        val device = SyncDevice(deviceId = "device-1", deviceName = "Pixel")
        val snapshot = RemoteSyncSnapshot(
            exportedAt = 30L,
            sourceDevice = device,
            folders = listOf(
                RemoteFolder(
                    syncId = DEFAULT_FOLDER_SYNC_ID,
                    name = DEFAULT_FOLDER_NAME,
                    createdAt = 1L,
                    updatedAt = 2L,
                ),
            ),
            notes = listOf(
                RemoteNote(
                    syncId = "note-text",
                    folderSyncId = DEFAULT_FOLDER_SYNC_ID,
                    type = NoteTypes.TEXT,
                    title = "Title",
                    textContent = "Body",
                    drawingData = null,
                    createdAt = 3L,
                    updatedAt = 4L,
                ),
            ),
        )

        val encoded = SyncSnapshotJson.encode(snapshot)

        assertTrue(encoded.contains("\"formatVersion\":1"))
    }

    @Test
    fun encodedRecurringReminderSnapshotUsesRepeatCompatibleVersion() {
        val device = SyncDevice(deviceId = "device-1", deviceName = "Pixel")
        val snapshot = RemoteSyncSnapshot(
            exportedAt = 30L,
            sourceDevice = device,
            folders = listOf(
                RemoteFolder(
                    syncId = DEFAULT_FOLDER_SYNC_ID,
                    name = DEFAULT_FOLDER_NAME,
                    createdAt = 1L,
                    updatedAt = 2L,
                ),
            ),
            notes = listOf(
                RemoteNote(
                    syncId = "note-repeat",
                    folderSyncId = DEFAULT_FOLDER_SYNC_ID,
                    type = NoteTypes.TEXT,
                    title = "Standup",
                    textContent = "Daily check",
                    drawingData = null,
                    createdAt = 3L,
                    updatedAt = 4L,
                    reminderAt = 5L,
                    reminderRepeat = ReminderRepeat.Daily.code,
                ),
            ),
        )

        val encoded = SyncSnapshotJson.encode(snapshot)
        val decoded = SyncSnapshotJson.decode(encoded)

        assertTrue(encoded.contains("\"formatVersion\":3"))
        assertEquals(ReminderRepeat.Daily.code, decoded.notes.single().reminderRepeat)
    }

    @Test
    fun legacySnapshotVersionStillDecodes() {
        val decoded = SyncSnapshotJson.decode(
            """
            {
              "formatVersion":1,
              "snapshotId":"snapshot-1",
              "exportedAt":30,
              "sourceDevice":{"deviceId":"device-1","deviceName":"Pixel","lastSyncAt":30},
              "devices":[],
              "folders":[{"syncId":"$DEFAULT_FOLDER_SYNC_ID","name":"$DEFAULT_FOLDER_NAME","createdAt":1,"updatedAt":2}],
              "notes":[{"syncId":"note-1","folderSyncId":"$DEFAULT_FOLDER_SYNC_ID","type":"TEXT","title":"Title","textContent":"Body","drawingData":null,"createdAt":3,"updatedAt":4,"isPinned":false,"reminderAt":null,"purged":false}]
            }
            """.trimIndent(),
        )

        assertEquals(1, decoded.formatVersion)
        assertEquals(NoteTypes.TEXT, decoded.notes.single().type)
    }

    @Test
    fun unsupportedVersionFailsFast() {
        try {
            SyncSnapshotJson.decode("""{"formatVersion":999}""")
            fail("Expected unsupported snapshot version to fail.")
        } catch (_: IllegalArgumentException) {
        }
    }
}
