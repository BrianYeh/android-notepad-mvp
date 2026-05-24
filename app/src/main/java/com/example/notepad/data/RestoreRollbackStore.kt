package com.example.notepad.data

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

class RestoreRollbackStore(
    private val checkpointFile: File,
) {
    fun load(): DecodedBackup? {
        val atomicFile = AtomicFile(checkpointFile)
        return try {
            val json = atomicFile.openRead().bufferedReader().use { it.readText() }
            BackupJson.decodeWithPreview(json)
        } catch (_: FileNotFoundException) {
            null
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun save(json: String): DecodedBackup {
        val decodedBackup = BackupJson.decodeWithPreview(json)
        val parent = checkpointFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val atomicFile = AtomicFile(checkpointFile)
        var output = atomicFile.startWrite()
        try {
            output.write(json.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
            output = null
        } finally {
            output?.let(atomicFile::failWrite)
        }
        return decodedBackup
    }

    fun clear() {
        AtomicFile(checkpointFile).delete()
    }
}
