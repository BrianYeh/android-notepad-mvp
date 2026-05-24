package com.example.notepad.data

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class BatchPortabilityInstrumentedTest {
    @Test
    fun exportZipIncludesTextChecklistDrawingAndManifest() {
        val zipBytes = BatchPortability.exportZip(
            folders = listOf(
                FolderEntity(
                    id = DEFAULT_FOLDER_ID,
                    syncId = DEFAULT_FOLDER_SYNC_ID,
                    name = DEFAULT_FOLDER_NAME,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
            notes = listOf(
                NoteEntity(
                    id = 1L,
                    folderId = DEFAULT_FOLDER_ID,
                    type = NoteTypes.TEXT,
                    title = "Alpha note",
                    textContent = "hello",
                    drawingData = null,
                    createdAt = 1L,
                    updatedAt = 2L,
                ),
                NoteEntity(
                    id = 2L,
                    folderId = DEFAULT_FOLDER_ID,
                    type = NoteTypes.CHECKLIST,
                    title = "List",
                    textContent = ChecklistJson.encode(
                        listOf(ChecklistItem(text = "Buy milk", checked = true)),
                    ),
                    drawingData = null,
                    createdAt = 1L,
                    updatedAt = 3L,
                ),
                NoteEntity(
                    id = 3L,
                    folderId = DEFAULT_FOLDER_ID,
                    type = NoteTypes.DRAWING,
                    title = "Sketch",
                    textContent = null,
                    drawingData = """[{"points":[]}]""",
                    createdAt = 1L,
                    updatedAt = 4L,
                ),
            ),
            exportedAt = 5L,
        )

        val entries = unzip(zipBytes)

        assertTrue(entries.keys.any { it.endsWith("alpha-note.txt") })
        assertTrue(entries.keys.any { it.endsWith("list.txt") })
        assertTrue(entries.keys.any { it.endsWith("sketch.drawing.json") })
        assertEquals("[x] Buy milk", entries.entries.first { it.key.endsWith("list.txt") }.value)
        assertTrue(entries.getValue("manifest.json").contains(""""noteCount": 3"""))
    }

    @Test
    fun titleFromFileNameDropsExtension() {
        assertEquals("meeting-notes", BatchPortability.titleFromFileName("meeting-notes.txt"))
    }

    @Test
    fun titleFromFileNameUsesExtensionlessBasename() {
        assertEquals("README", BatchPortability.titleFromFileName("primary:Download/README"))
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return entries
    }
}
