package com.example.notepad.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class TextImportFile(
    val name: String,
    val content: String,
)

object BatchPortability {
    fun exportZip(
        folders: List<FolderEntity>,
        notes: List<NoteEntity>,
        exportedAt: Long = System.currentTimeMillis(),
    ): ByteArray {
        val activeFoldersById = folders
            .filter { !it.isDeleted }
            .associateBy { it.id }
        val activeNotes = notes
            .filter { !it.isDeleted }
            .sortedWith(compareBy<NoteEntity> { activeFoldersById[it.folderId]?.name.orEmpty().lowercase() }
                .thenBy { it.title.lowercase() }
                .thenBy { it.id })
        val usedNames = mutableSetOf<String>()
        val manifestNotes = JSONArray()
        val output = ByteArrayOutputStream()

        ZipOutputStream(output).use { zip ->
            activeNotes.forEachIndexed { index, note ->
                val folderName = activeFoldersById[note.folderId]?.name ?: DEFAULT_FOLDER_NAME
                val extension = if (note.type == NoteTypes.DRAWING) "drawing.json" else "txt"
                val entryName = uniqueEntryName(
                    folderName = folderName,
                    title = note.title.ifBlank { "Untitled" },
                    index = index + 1,
                    extension = extension,
                    usedNames = usedNames,
                )
                val body = when (note.type) {
                    NoteTypes.CHECKLIST -> ChecklistJson.plainText(note.textContent)
                    NoteTypes.DRAWING -> note.drawingData.orEmpty()
                    else -> note.textContent.orEmpty()
                }
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                manifestNotes.put(
                    JSONObject()
                        .put("id", note.id)
                        .put("syncId", note.syncId)
                        .put("type", note.type)
                        .put("title", note.title)
                        .put("folder", folderName)
                        .put("file", entryName)
                        .put("createdAt", note.createdAt)
                        .put("updatedAt", note.updatedAt)
                        .put("isPinned", note.isPinned)
                        .put("reminderAt", note.reminderAt ?: JSONObject.NULL)
                        .put("reminderRepeat", normalizedReminderRepeat(note.reminderRepeat)),
                )
            }

            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(
                JSONObject()
                    .put("format", "just-notes-batch-export")
                    .put("version", 1)
                    .put("exportedAt", exportedAt)
                    .put("noteCount", activeNotes.size)
                    .put("notes", manifestNotes)
                    .toString(2)
                    .toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()
        }

        return output.toByteArray()
    }

    fun titleFromFileName(fileName: String): String {
        val baseName = fileName
            .substringAfterLast('/')
            .substringAfterLast(':')
        return baseName
            .substringBeforeLast('.', baseName)
            .trim()
            .ifBlank { "Imported note" }
    }

    private fun uniqueEntryName(
        folderName: String,
        title: String,
        index: Int,
        extension: String,
        usedNames: MutableSet<String>,
    ): String {
        val folder = sanitizePathPart(folderName.ifBlank { DEFAULT_FOLDER_NAME })
        val base = sanitizePathPart(title)
        val prefix = index.toString().padStart(3, '0')
        var name = "notes/$folder/$prefix-$base.$extension"
        var duplicate = 2
        while (!usedNames.add(name)) {
            name = "notes/$folder/$prefix-$base-$duplicate.$extension"
            duplicate += 1
        }
        return name
    }

    private fun sanitizePathPart(value: String): String {
        return value
            .trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._ -]+"), "")
            .replace(Regex("\\s+"), "-")
            .trim('-', '.', '_')
            .take(60)
            .ifBlank { "note" }
    }
}

fun defaultBatchExportFileName(now: Long = System.currentTimeMillis()): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(now))
    return "just-notes-export-$stamp.zip"
}
