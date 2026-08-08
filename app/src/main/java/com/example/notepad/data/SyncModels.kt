package com.example.notepad.data

import java.util.UUID
import kotlin.math.abs

const val REMOTE_SYNC_SNAPSHOT_VERSION = 4

fun remoteSyncSnapshotVersionFor(notes: List<RemoteNote>): Int {
    return when {
        notes.any { !it.textFormattingJson.isNullOrBlank() } -> 4
        notes.any { normalizedReminderRepeat(it.reminderRepeat) != ReminderRepeat.None.code } -> 3
        notes.any { it.type == NoteTypes.CHECKLIST } -> 2
        else -> 1
    }
}

object SyncIds {
    fun newFolderSyncId(): String = "folder:${UUID.randomUUID()}"

    fun newNoteSyncId(): String = "note:${UUID.randomUUID()}"

    fun newConflictNoteSyncId(originalSyncId: String): String {
        return "note-conflict:$originalSyncId:${UUID.randomUUID()}"
    }
}

data class SyncMetadata(
    val deviceId: String,
    val deviceName: String,
    val accountEmail: String? = null,
    val lastSyncedAt: Long? = null,
    val status: SyncStatus = SyncStatus.SetupRequired,
    val lastError: SyncError? = null,
)

enum class SyncStatus {
    SetupRequired,
    SignedOut,
    Idle,
    Syncing,
    Succeeded,
    Failed,
    Conflict,
}

data class SyncError(
    val code: SyncErrorCode,
    val message: String,
)

internal fun SyncMetadata.afterSyncCancellation(): SyncMetadata {
    if (status != SyncStatus.Syncing) return this
    return copy(
        status = if (accountEmail == null) SyncStatus.SignedOut else SyncStatus.Idle,
        lastError = null,
    )
}

enum class SyncErrorCode {
    MissingGoogleConfiguration,
    NotSignedIn,
    NetworkUnavailable,
    PermissionRevoked,
    RemoteDataCorrupt,
    Conflict,
    Unknown,
}

data class SyncDevice(
    val deviceId: String,
    val deviceName: String,
    val lastSyncAt: Long? = null,
)

data class RemoteSyncSnapshot(
    val formatVersion: Int = REMOTE_SYNC_SNAPSHOT_VERSION,
    val snapshotId: String = UUID.randomUUID().toString(),
    val exportedAt: Long,
    val sourceDevice: SyncDevice,
    val devices: List<SyncDevice> = listOf(sourceDevice),
    val folders: List<RemoteFolder>,
    val notes: List<RemoteNote>,
)

data class RemoteFolder(
    val syncId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

data class RemoteNote(
    val syncId: String,
    val folderSyncId: String,
    val type: String,
    val title: String,
    val textContent: String?,
    val drawingData: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val isPinned: Boolean = false,
    val reminderAt: Long? = null,
    val reminderRepeat: String = ReminderRepeat.None.code,
    val purged: Boolean = false,
    val textFormattingJson: String? = null,
)

interface DriveSyncClient {
    val accountEmail: String?

    suspend fun readSnapshot(): DriveSyncResult<RemoteSyncSnapshot?>

    suspend fun writeSnapshot(snapshot: RemoteSyncSnapshot): DriveSyncResult<Unit>
}

sealed class DriveSyncResult<out T> {
    data class Success<T>(val value: T) : DriveSyncResult<T>()

    data class Failure(val error: SyncError) : DriveSyncResult<Nothing>()
}

data class SyncMergeResult(
    val snapshot: RemoteSyncSnapshot,
    val conflictCopies: List<RemoteNote>,
)

object SyncMerge {
    fun mergeSnapshots(
        local: RemoteSyncSnapshot,
        remote: RemoteSyncSnapshot?,
        now: Long,
        conflictWindowMillis: Long = 0L,
        conflictModifiedAfterMillis: Long? = null,
        conflictSyncIdFactory: (String) -> String = SyncIds::newConflictNoteSyncId,
    ): SyncMergeResult {
        if (remote == null) {
            return SyncMergeResult(
                snapshot = local.copy(
                    formatVersion = remoteSyncSnapshotVersionFor(local.notes),
                    exportedAt = now,
                    devices = listOf(local.sourceDevice.copy(lastSyncAt = now)),
                ),
                conflictCopies = emptyList(),
            )
        }

        val mergedFolders = mergeFolders(local.folders, remote.folders)
        val noteMerge = mergeNotes(
            local = local.notes,
            remote = remote.notes,
            now = now,
            conflictWindowMillis = conflictWindowMillis,
            conflictModifiedAfterMillis = conflictModifiedAfterMillis,
            conflictSyncIdFactory = conflictSyncIdFactory,
        )

        val devices = (local.devices + remote.devices + local.sourceDevice.copy(lastSyncAt = now))
            .associateBy { it.deviceId }
            .values
            .sortedBy { it.deviceName.lowercase() }

        return SyncMergeResult(
            snapshot = local.copy(
                formatVersion = remoteSyncSnapshotVersionFor(noteMerge.notes),
                snapshotId = UUID.randomUUID().toString(),
                exportedAt = now,
                devices = devices,
                folders = mergedFolders,
                notes = noteMerge.notes,
            ),
            conflictCopies = noteMerge.conflictCopies,
        )
    }

    private fun mergeFolders(
        local: List<RemoteFolder>,
        remote: List<RemoteFolder>,
    ): List<RemoteFolder> {
        val allSyncIds = (local.map { it.syncId } + remote.map { it.syncId }).toSortedSet()
        val localById = local.associateBy { it.syncId }
        val remoteById = remote.associateBy { it.syncId }

        return allSyncIds.mapNotNull { syncId ->
            val localFolder = localById[syncId]
            val remoteFolder = remoteById[syncId]
            when {
                localFolder == null -> remoteFolder
                remoteFolder == null -> localFolder
                else -> mergeFolder(localFolder, remoteFolder)
            }
        }
    }

    private fun mergeFolder(local: RemoteFolder, remote: RemoteFolder): RemoteFolder {
        val deletionWinner = newerDeletionOrNull(
            localUpdatedAt = local.updatedAt,
            localDeletedAt = local.deletedAt,
            remoteUpdatedAt = remote.updatedAt,
            remoteDeletedAt = remote.deletedAt,
        )
        if (deletionWinner == DeletionWinner.Local) return local
        if (deletionWinner == DeletionWinner.Remote) return remote

        return if (local.updatedAt >= remote.updatedAt) local else remote
    }

    private data class NoteMerge(
        val notes: List<RemoteNote>,
        val conflictCopies: List<RemoteNote>,
    )

    private fun mergeNotes(
        local: List<RemoteNote>,
        remote: List<RemoteNote>,
        now: Long,
        conflictWindowMillis: Long,
        conflictModifiedAfterMillis: Long?,
        conflictSyncIdFactory: (String) -> String,
    ): NoteMerge {
        val allSyncIds = (local.map { it.syncId } + remote.map { it.syncId }).toSortedSet()
        val localById = local.associateBy { it.syncId }
        val remoteById = remote.associateBy { it.syncId }
        val conflicts = mutableListOf<RemoteNote>()
        val merged = allSyncIds.mapNotNull { syncId ->
            val localNote = localById[syncId]
            val remoteNote = remoteById[syncId]
            when {
                localNote == null -> remoteNote
                remoteNote == null -> localNote
                else -> mergeNote(
                    local = localNote,
                    remote = remoteNote,
                    now = now,
                    conflictWindowMillis = conflictWindowMillis,
                    conflictModifiedAfterMillis = conflictModifiedAfterMillis,
                    conflictSyncIdFactory = conflictSyncIdFactory,
                ).also { mergedNote ->
                    mergedNote.conflictCopy?.let(conflicts::add)
                }.note
            }
        } + conflicts

        return NoteMerge(
            notes = merged.sortedWith(compareBy<RemoteNote> { it.deletedAt != null }.thenBy { it.syncId }),
            conflictCopies = conflicts,
        )
    }

    private data class MergedNote(
        val note: RemoteNote,
        val conflictCopy: RemoteNote?,
    )

    private fun mergeNote(
        local: RemoteNote,
        remote: RemoteNote,
        now: Long,
        conflictWindowMillis: Long,
        conflictModifiedAfterMillis: Long?,
        conflictSyncIdFactory: (String) -> String,
    ): MergedNote {
        purgedWinnerOrNull(local, remote)?.let { return MergedNote(it, null) }

        val deletionWinner = newerDeletionOrNull(
            localUpdatedAt = local.updatedAt,
            localDeletedAt = local.deletedAt,
            remoteUpdatedAt = remote.updatedAt,
            remoteDeletedAt = remote.deletedAt,
        )
        if (deletionWinner == DeletionWinner.Local) return MergedNote(local, null)
        if (deletionWinner == DeletionWinner.Remote) return MergedNote(remote, null)

        val bothChangedSinceLastSync = conflictModifiedAfterMillis != null &&
            local.updatedAt > conflictModifiedAfterMillis &&
            remote.updatedAt > conflictModifiedAfterMillis
        val changedInsideConflictWindow = abs(local.updatedAt - remote.updatedAt) <= conflictWindowMillis

        if (!local.hasSameUserContent(remote) && (bothChangedSinceLastSync || changedInsideConflictWindow)) {
            val winner = if (local.updatedAt >= remote.updatedAt) local else remote
            val loser = if (winner == local) remote else local
            if (loser.deletedAt != null || loser.purged) {
                return MergedNote(winner, null)
            }
            return MergedNote(
                note = winner,
                conflictCopy = loser.copy(
                    syncId = conflictSyncIdFactory(loser.syncId),
                    title = loser.conflictCopyTitle(),
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                    purged = false,
                ),
            )
        }

        return MergedNote(
            note = if (local.updatedAt >= remote.updatedAt) local else remote,
            conflictCopy = null,
        )
    }

    private fun purgedWinnerOrNull(local: RemoteNote, remote: RemoteNote): RemoteNote? {
        val localPurgedAt = local.purgedAt()
        val remotePurgedAt = remote.purgedAt()
        return if (localPurgedAt == null) {
            remotePurgedAt?.let { remote.asPersistablePurge(it) }
        } else if (remotePurgedAt == null || localPurgedAt >= remotePurgedAt) {
            local.asPersistablePurge(localPurgedAt)
        } else {
            remote.asPersistablePurge(remotePurgedAt)
        }
    }

    private fun RemoteNote.purgedAt(): Long? {
        return if (purged) deletedAt ?: updatedAt else null
    }

    private fun RemoteNote.asPersistablePurge(purgedAt: Long): RemoteNote {
        return if (deletedAt != null) this else copy(deletedAt = purgedAt)
    }

    private enum class DeletionWinner {
        Local,
        Remote,
    }

    private fun newerDeletionOrNull(
        localUpdatedAt: Long,
        localDeletedAt: Long?,
        remoteUpdatedAt: Long,
        remoteDeletedAt: Long?,
    ): DeletionWinner? {
        if (localDeletedAt != null && remoteDeletedAt != null) {
            return if (localDeletedAt >= remoteDeletedAt) DeletionWinner.Local else DeletionWinner.Remote
        }
        if (localDeletedAt != null && localDeletedAt >= remoteUpdatedAt) return DeletionWinner.Local
        if (remoteDeletedAt != null && remoteDeletedAt >= localUpdatedAt) return DeletionWinner.Remote
        return null
    }

    private fun RemoteNote.hasSameUserContent(other: RemoteNote): Boolean {
        return folderSyncId == other.folderSyncId &&
            type == other.type &&
            title == other.title &&
            textContent == other.textContent &&
            textFormattingJson == other.textFormattingJson &&
            drawingData == other.drawingData &&
            isPinned == other.isPinned &&
            reminderAt == other.reminderAt &&
            normalizedReminderRepeat(reminderRepeat) == normalizedReminderRepeat(other.reminderRepeat) &&
            deletedAt == other.deletedAt &&
            purged == other.purged
    }

    private fun RemoteNote.conflictCopyTitle(): String {
        val baseTitle = title.ifBlank { "Untitled note" }
        return "$baseTitle (conflict copy)"
    }
}

object RemoteSnapshotConsolidator {
    fun consolidate(
        snapshots: List<RemoteSyncSnapshot>,
        now: Long,
        conflictSyncIdFactory: (String) -> String = SyncIds::newConflictNoteSyncId,
    ): RemoteSyncSnapshot? {
        val orderedSnapshots = snapshots.sortedWith(
            compareBy<RemoteSyncSnapshot> { it.exportedAt }
                .thenBy { it.snapshotId },
        )
        val base = orderedSnapshots.reduceOrNull { merged, next ->
            SyncMerge.mergeSnapshots(
                local = merged,
                remote = next,
                now = now,
            ).snapshot
        } ?: return null
        val conflictCopies = concurrentConflictCopies(
            snapshots = orderedSnapshots,
            winners = base.notes.associateBy { it.syncId },
            now = now,
            conflictSyncIdFactory = conflictSyncIdFactory,
        )
        if (conflictCopies.isEmpty()) {
            return base.copy(formatVersion = remoteSyncSnapshotVersionFor(base.notes))
        }
        val notes = (base.notes + conflictCopies).sortedWith(
            compareBy<RemoteNote> { it.deletedAt != null }.thenBy { it.syncId },
        )
        return base.copy(
            formatVersion = remoteSyncSnapshotVersionFor(notes),
            notes = notes,
        )
    }

    private data class SnapshotNote(
        val snapshot: RemoteSyncSnapshot,
        val note: RemoteNote,
    )

    private fun concurrentConflictCopies(
        snapshots: List<RemoteSyncSnapshot>,
        winners: Map<String, RemoteNote>,
        now: Long,
        conflictSyncIdFactory: (String) -> String,
    ): List<RemoteNote> {
        val versionsBySyncId = snapshots
            .flatMap { snapshot -> snapshot.notes.map { note -> SnapshotNote(snapshot, note) } }
            .groupBy { it.note.syncId }

        return versionsBySyncId.flatMap { (syncId, versions) ->
            val winner = winners[syncId] ?: return@flatMap emptyList()
            val winnerVersion = versions
                .filter { it.note.hasSameUserContentAs(winner) }
                .maxWithOrNull(compareBy<SnapshotNote> { it.snapshot.exportedAt }.thenBy { it.snapshot.snapshotId })
                ?: return@flatMap emptyList()

            versions
                .filterNot { it.note.hasSameUserContentAs(winner) }
                .filterNot { winnerVersion.snapshot.hasSeen(it.snapshot) }
                .filter { it.note.deletedAt == null }
                .distinctBy { it.note.conflictContentKey() }
                .map { version ->
                    version.note.copy(
                        syncId = conflictSyncIdFactory(version.note.syncId),
                        title = version.note.conflictCopyTitle(),
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                    )
                }
        }
    }

    private fun RemoteSyncSnapshot.hasSeen(other: RemoteSyncSnapshot): Boolean {
        if (sourceDevice.deviceId == other.sourceDevice.deviceId && exportedAt >= other.exportedAt) {
            return true
        }
        return devices
            .firstOrNull { it.deviceId == other.sourceDevice.deviceId }
            ?.lastSyncAt
            ?.let { it >= other.exportedAt }
            ?: false
    }

    private fun RemoteNote.hasSameUserContentAs(other: RemoteNote): Boolean {
        return folderSyncId == other.folderSyncId &&
            type == other.type &&
            title == other.title &&
            textContent == other.textContent &&
            textFormattingJson == other.textFormattingJson &&
            drawingData == other.drawingData &&
            isPinned == other.isPinned &&
            reminderAt == other.reminderAt &&
            normalizedReminderRepeat(reminderRepeat) == normalizedReminderRepeat(other.reminderRepeat) &&
            deletedAt == other.deletedAt &&
            purged == other.purged
    }

    private fun RemoteNote.conflictCopyTitle(): String {
        val baseTitle = title.ifBlank { "Untitled note" }
        return "$baseTitle (conflict copy)"
    }

    private fun RemoteNote.conflictContentKey(): String {
        return listOf(
            folderSyncId,
            type,
            title,
            textContent,
            textFormattingJson,
            drawingData,
            isPinned,
            reminderAt,
            normalizedReminderRepeat(reminderRepeat),
            deletedAt,
            purged,
        ).joinToString("|")
    }
}
