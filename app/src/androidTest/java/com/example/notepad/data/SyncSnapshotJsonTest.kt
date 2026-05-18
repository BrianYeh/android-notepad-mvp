package com.example.notepad.data

import org.junit.Assert.assertEquals
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
    fun unsupportedVersionFailsFast() {
        try {
            SyncSnapshotJson.decode("""{"formatVersion":999}""")
            fail("Expected unsupported snapshot version to fail.")
        } catch (_: IllegalArgumentException) {
        }
    }
}
