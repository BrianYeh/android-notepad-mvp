package com.example.notepad.debug

object DebugSaveFailure {
    private val textNoteFailures = mutableSetOf<Long>()

    fun failNextTextSave(noteId: Long) {
        synchronized(textNoteFailures) {
            textNoteFailures += noteId
        }
    }

    fun consumeTextSaveFailure(noteId: Long): Boolean {
        return synchronized(textNoteFailures) {
            textNoteFailures.remove(noteId)
        }
    }

    fun clear() {
        synchronized(textNoteFailures) {
            textNoteFailures.clear()
        }
    }
}
