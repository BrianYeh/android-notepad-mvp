package com.example.notepad.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val DEFAULT_FOLDER_ID = 1L
const val DEFAULT_FOLDER_NAME = "Uncategorized"
const val ALL_NOTES_FILTER_NAME = "All Notes"

object NoteTypes {
    const val TEXT = "TEXT"
    const val DRAWING = "DRAWING"
}

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
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
    indices = [Index("folderId")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folderId: Long,
    val type: String,
    val title: String,
    val textContent: String?,
    val drawingData: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
