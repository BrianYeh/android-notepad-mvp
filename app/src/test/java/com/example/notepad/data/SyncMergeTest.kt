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
    fun purgedTombstonePreventsDeletedNoteFromBeingRevived() {
        val local = snapshot(
            deviceId = "local",
            notes = listOf(
                note(
                    syncId = "note-1",
                    title = "",
                    updatedAt = 100L,
                    deletedAt = 100L,
                    purged = true,
                ),
            ),
        )
        val remote = snapshot(
            deviceId = "remote",
            notes = listOf(note(syncId = "note-1", title = "Remote active", updatedAt = 50L)),
        )

        val result = SyncMerge.mergeSnapshots(local = local, remote = remote, now = 120L)

        val merged = result.snapshot.notes.single()
        assertEquals(100L, merged.deletedAt)
        assertTrue(merged.purged)
    }

    @Test
    fun purgedTombstoneWinsOverNewerConcurrentEdit() {
        val local = snapshot(
            deviceId = "local",
            notes = listOf(
                note(
                    syncId = "note-1",
                    title = "",
                    updatedAt = 80L,
                    deletedAt = 80L,
                    purged = true,
                ),
            ),
        )
        val remote = snapshot(
            deviceId = "remote",
            notes = listOf(note(syncId = "note-1", title = "Remote offline edit", updatedAt = 100L)),
        )

        val result = SyncMerge.mergeSnapshots(
            local = local,
            remote = remote,
            now = 120L,
            conflictModifiedAfterMillis = 40L,
            conflictSyncIdFactory = { "conflict-$it" },
        )

        val merged = result.snapshot.notes.single()
        assertEquals(80L, merged.deletedAt)
        assertTrue(merged.purged)
        assertTrue(result.conflictCopies.isEmpty())
    }

    @Test
    fun malformedPurgedTombstoneKeepsDeleteMarker() {
        val local = snapshot(
            deviceId = "local",
            notes = listOf(
                note(
                    syncId = "note-1",
                    title = "",
                    updatedAt = 80L,
                    deletedAt = null,
                    purged = true,
                ),
            ),
        )
        val remote = snapshot(
            deviceId = "remote",
            notes = listOf(note(syncId = "note-1", title = "Remote offline edit", updatedAt = 100L)),
        )

        val result = SyncMerge.mergeSnapshots(
            local = local,
            remote = remote,
            now = 120L,
            conflictModifiedAfterMillis = 40L,
        )

        val merged = result.snapshot.notes.single()
        assertEquals(80L, merged.deletedAt)
        assertTrue(merged.purged)
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

    @Test
    fun notesChangedOnBothSidesSinceLastSyncKeepBothVersions() {
        val local = snapshot(
            deviceId = "local",
            notes = listOf(note(syncId = "note-1", title = "Local offline edit", updatedAt = 100L)),
        )
        val remote = snapshot(
            deviceId = "remote",
            notes = listOf(note(syncId = "note-1", title = "Remote offline edit", updatedAt = 50L)),
        )

        val result = SyncMerge.mergeSnapshots(
            local = local,
            remote = remote,
            now = 120L,
            conflictModifiedAfterMillis = 40L,
            conflictSyncIdFactory = { "conflict-$it" },
        )

        assertEquals(2, result.snapshot.notes.size)
        assertEquals("Local offline edit", result.snapshot.notes.first { it.syncId == "note-1" }.title)
        assertEquals("Remote offline edit (conflict copy)", result.conflictCopies.single().title)
    }

    @Test
    fun oneSidedEditAfterLastSyncDoesNotCreateConflictCopy() {
        val local = snapshot(
            deviceId = "local",
            notes = listOf(note(syncId = "note-1", title = "Local edit", updatedAt = 100L)),
        )
        val remote = snapshot(
            deviceId = "remote",
            notes = listOf(note(syncId = "note-1", title = "Remote old", updatedAt = 30L)),
        )

        val result = SyncMerge.mergeSnapshots(
            local = local,
            remote = remote,
            now = 120L,
            conflictModifiedAfterMillis = 40L,
            conflictSyncIdFactory = { "conflict-$it" },
        )

        assertEquals(1, result.snapshot.notes.size)
        assertEquals("Local edit", result.snapshot.notes.single().title)
        assertTrue(result.conflictCopies.isEmpty())
    }

    @Test
    fun firstSyncWithUnknownBaselineKeepsDivergentVersions() {
        val local = snapshot(
            deviceId = "local",
            notes = listOf(note(syncId = "note-1", title = "Local first sync edit", updatedAt = 100L)),
        )
        val remote = snapshot(
            deviceId = "remote",
            notes = listOf(note(syncId = "note-1", title = "Remote first sync edit", updatedAt = 50L)),
        )

        val result = SyncMerge.mergeSnapshots(
            local = local,
            remote = remote,
            now = 120L,
            conflictModifiedAfterMillis = Long.MIN_VALUE,
            conflictSyncIdFactory = { "conflict-$it" },
        )

        assertEquals(2, result.snapshot.notes.size)
        assertEquals("Local first sync edit", result.snapshot.notes.first { it.syncId == "note-1" }.title)
        assertEquals("Remote first sync edit (conflict copy)", result.conflictCopies.single().title)
    }

    @Test
    fun remoteSnapshotConsolidationKeepsConcurrentVersions() {
        val deviceA = SyncDevice(deviceId = "device-a", deviceName = "A", lastSyncAt = 110L)
        val deviceB = SyncDevice(deviceId = "device-b", deviceName = "B", lastSyncAt = 120L)
        val snapshotA = snapshot(
            device = deviceA,
            exportedAt = 110L,
            notes = listOf(note(syncId = "note-1", title = "A edit", updatedAt = 100L)),
        )
        val snapshotB = snapshot(
            device = deviceB,
            exportedAt = 120L,
            notes = listOf(note(syncId = "note-1", title = "B edit", updatedAt = 90L)),
        )

        val result = RemoteSnapshotConsolidator.consolidate(
            snapshots = listOf(snapshotA, snapshotB),
            now = 130L,
            conflictSyncIdFactory = { "conflict-$it" },
        )

        val notes = requireNotNull(result).notes
        assertEquals(2, notes.size)
        assertEquals("A edit", notes.first { it.syncId == "note-1" }.title)
        assertEquals("B edit (conflict copy)", notes.first { it.syncId == "conflict-note-1" }.title)
    }

    @Test
    fun remoteSnapshotConsolidationSkipsConflictWhenNewerSnapshotSawOlder() {
        val deviceA = SyncDevice(deviceId = "device-a", deviceName = "A", lastSyncAt = 110L)
        val deviceB = SyncDevice(deviceId = "device-b", deviceName = "B", lastSyncAt = 140L)
        val snapshotA = snapshot(
            device = deviceA,
            exportedAt = 110L,
            notes = listOf(note(syncId = "note-1", title = "A edit", updatedAt = 100L)),
        )
        val snapshotB = snapshot(
            device = deviceB,
            exportedAt = 140L,
            devices = listOf(deviceA, deviceB),
            notes = listOf(note(syncId = "note-1", title = "B edit", updatedAt = 130L)),
        )

        val result = RemoteSnapshotConsolidator.consolidate(
            snapshots = listOf(snapshotA, snapshotB),
            now = 150L,
            conflictSyncIdFactory = { "conflict-$it" },
        )

        val notes = requireNotNull(result).notes
        assertEquals(1, notes.size)
        assertEquals("B edit", notes.single().title)
    }

    private fun snapshot(
        deviceId: String,
        notes: List<RemoteNote>,
    ): RemoteSyncSnapshot {
        val device = SyncDevice(deviceId = deviceId, deviceName = deviceId)
        return snapshot(device = device, exportedAt = 1L, notes = notes)
    }

    private fun snapshot(
        device: SyncDevice,
        exportedAt: Long,
        devices: List<SyncDevice> = listOf(device),
        notes: List<RemoteNote>,
    ): RemoteSyncSnapshot {
        return RemoteSyncSnapshot(
            exportedAt = exportedAt,
            sourceDevice = device,
            devices = devices,
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
        purged: Boolean = false,
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
            purged = purged,
        )
    }
}
