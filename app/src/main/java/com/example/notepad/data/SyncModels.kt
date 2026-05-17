package com.example.notepad.data

import java.util.UUID
import kotlin.math.abs

const val REMOTE_SYNC_SNAPSHOT_VERSION = 1

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
        conflictSyncIdFactory: (String) -> String = SyncIds::newConflictNoteSyncId,
    ): SyncMergeResult {
        if (remote == null) {
            return SyncMergeResult(
                snapshot = local.copy(
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
            conflictSyncIdFactory = conflictSyncIdFactory,
        )

        val devices = (local.devices + remote.devices + local.sourceDevice.copy(lastSyncAt = now))
            .associateBy { it.deviceId }
            .values
            .sortedBy { it.deviceName.lowercase() }

        return SyncMergeResult(
            snapshot = local.copy(
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
        conflictSyncIdFactory: (String) -> String,
    ): MergedNote {
        val deletionWinner = newerDeletionOrNull(
            localUpdatedAt = local.updatedAt,
            localDeletedAt = local.deletedAt,
            remoteUpdatedAt = remote.updatedAt,
            remoteDeletedAt = remote.deletedAt,
        )
        if (deletionWinner == DeletionWinner.Local) return MergedNote(local, null)
        if (deletionWinner == DeletionWinner.Remote) return MergedNote(remote, null)

        if (!local.hasSameUserContent(remote) && abs(local.updatedAt - remote.updatedAt) <= conflictWindowMillis) {
            val winner = if (local.updatedAt >= remote.updatedAt) local else remote
            val loser = if (winner == local) remote else local
            return MergedNote(
                note = winner,
                conflictCopy = loser.copy(
                    syncId = conflictSyncIdFactory(loser.syncId),
                    title = loser.conflictCopyTitle(),
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                ),
            )
        }

        return MergedNote(
            note = if (local.updatedAt >= remote.updatedAt) local else remote,
            conflictCopy = null,
        )
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
            drawingData == other.drawingData &&
            isPinned == other.isPinned &&
            reminderAt == other.reminderAt &&
            deletedAt == other.deletedAt
    }

    private fun RemoteNote.conflictCopyTitle(): String {
        val baseTitle = title.ifBlank { "Untitled note" }
        return "$baseTitle (conflict copy)"
    }
}
