package com.example.notepad.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val DEFAULT_FOLDER_ID = 1L
const val DEFAULT_FOLDER_NAME = "Uncategorized"
const val DEFAULT_FOLDER_SYNC_ID = "folder:default"
const val ALL_NOTES_FILTER_NAME = "All Notes"

object NoteTypes {
    const val TEXT = "TEXT"
    const val DRAWING = "DRAWING"
    const val CHECKLIST = "CHECKLIST"
}

enum class NoteListMode {
    Active,
    Trash,
}

enum class NoteSortOption {
    UpdatedAt,
    CreatedAt,
    Title,
}

enum class NoteTypeFilter {
    All,
    Text,
    Drawing,
    Checklist,
}

enum class NoteQuickFilter {
    All,
    Text,
    Drawing,
    Checklist,
    HasReminder,
    Pinned,
}

enum class ReminderFilter {
    All,
    WithReminder,
    Overdue,
    Upcoming,
}

enum class ReminderRepeat(val code: String) {
    None("NONE"),
    Daily("DAILY"),
    Weekly("WEEKLY"),
    Monthly("MONTHLY"),
}

fun normalizedReminderRepeat(code: String?): String {
    return ReminderRepeat.entries.firstOrNull { it.code == code }?.code ?: ReminderRepeat.None.code
}

@Entity(
    tableName = "folders",
    indices = [Index("syncId")],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val syncId: String = SyncIds.newFolderSyncId(),
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("folderId"), Index("syncId")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val syncId: String = SyncIds.newNoteSyncId(),
    val folderId: Long,
    val type: String,
    val title: String,
    val textContent: String?,
    val drawingData: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val isPinned: Boolean = false,
    val reminderAt: Long? = null,
    val reminderRepeat: String = ReminderRepeat.None.code,
    val reminderSnoozeUntil: Long? = null,
    val activeReminderFiredAt: Long? = null,
    val textFormattingJson: String? = null,
)

@Entity(tableName = "note_tombstones")
data class NoteTombstoneEntity(
    @PrimaryKey
    val syncId: String,
    val deletedAt: Long,
)
