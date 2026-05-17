package com.example.notepad.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    @Test
    fun newerRemoteNoteWinsWhenThereIsNoConflictTie() {
        val local = snapshot(
            deviceId = "local",
            notes = listOf(note(syncId = "note-1", title = "Local", updatedAt = 10L)),
        )
        val remote = snapshot(
            deviceId = "remote",
            notes = listOf(note(syncId = "note-1", title = "Remote", updatedAt = 20L)),
        )

        val result = SyncMerge.mergeSnapshots(local = local, remote = remote, now = 30L)

        assertEquals("Remote", result.snapshot.notes.single().title)
        assertTrue(result.conflictCopies.isEmpty())
    }

    @Test
    fun newerTombstonePreventsDeletedNoteFromBeingRevived() {
        val local = snapshot(
            deviceId = "local",
            notes = listOf(note(syncId = "note-1", title = "Local edit", updatedAt = 20L)),
        )
        val remote = snapshot(
            deviceId = "remote",
            notes = listOf(
                note(
                    syncId = "note-1",
                    title = "Remote deleted",
                    updatedAt = 15L,
                    deletedAt = 25L,
                ),
            ),
        )

        val result = SyncMerge.mergeSnapshots(local = local, remote = remote, now = 30L)

        assertEquals(25L, result.snapshot.notes.single().deletedAt)
        assertEquals("Remote deleted", result.snapshot.notes.single().title)
        assertTrue(result.conflictCopies.isEmpty())
    }

    @Test
    fun newerEditWinsOverOlderTombstone() {
        val local = snapshot(
            deviceId = "local",
            notes = listOf(note(syncId = "note-1", title = "Local edit", updatedAt = 30L)),
        )
        val remote = snapshot(
            deviceId = "remote",
            notes = listOf(
                note(
                    syncId = "note-1",
                    title = "Remote deleted",
                    updatedAt = 10L,
                    deletedAt = 20L,
                ),
            ),
        )

        val result = SyncMerge.mergeSnapshots(local = local, remote = remote, now = 40L)

        assertNull(result.snapshot.notes.single().deletedAt)
        assertEquals("Local edit", result.snapshot.notes.single().title)
    }

    @Test
    fun equalTimestampContentConflictKeepsWinnerAndCreatesCopy() {
        val local = snapshot(
            deviceId = "local",
            notes = listOf(note(syncId = "note-1", title = "Local", updatedAt = 20L)),
        )
        val remote = snapshot(
            deviceId = "remote",
            notes = listOf(note(syncId = "note-1", title = "Remote", updatedAt = 20L)),
        )

        val result = SyncMerge.mergeSnapshots(
            local = local,
            remote = remote,
            now = 30L,
            conflictSyncIdFactory = { "conflict-$it" },
        )

        assertEquals(2, result.snapshot.notes.size)
        assertEquals("Local", result.snapshot.notes.first { it.syncId == "note-1" }.title)
        val conflict = result.conflictCopies.single()
        assertEquals("conflict-note-1", conflict.syncId)
        assertEquals("Remote (conflict copy)", conflict.title)
        assertEquals(30L, conflict.updatedAt)
    }

    private fun snapshot(
        deviceId: String,
        notes: List<RemoteNote>,
    ): RemoteSyncSnapshot {
        val device = SyncDevice(deviceId = deviceId, deviceName = deviceId)
        return RemoteSyncSnapshot(
            exportedAt = 1L,
            sourceDevice = device,
            folders = listOf(
                RemoteFolder(
                    syncId = DEFAULT_FOLDER_SYNC_ID,
                    name = DEFAULT_FOLDER_NAME,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
            notes = notes,
        )
    }

    private fun note(
        syncId: String,
        title: String,
        updatedAt: Long,
        deletedAt: Long? = null,
    ): RemoteNote {
        return RemoteNote(
            syncId = syncId,
            folderSyncId = DEFAULT_FOLDER_SYNC_ID,
            type = NoteTypes.TEXT,
            title = title,
            textContent = title,
            drawingData = null,
            createdAt = 1L,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
        )
    }
}
