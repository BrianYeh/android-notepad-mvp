package com.example.notepad.debug

import kotlinx.coroutines.delay

object DebugSaveFailure {
    private val textNoteFailures = mutableSetOf<Long>()
    private val drawingNoteFailures = mutableSetOf<Long>()
    private val drawingNoteDelays = mutableMapOf<Long, Long>()

    fun failNextTextSave(noteId: Long) {
        synchronized(textNoteFailures) {
            textNoteFailures += noteId
        }
    }

    fun failNextDrawingSave(noteId: Long) {
        synchronized(drawingNoteFailures) {
            drawingNoteFailures += noteId
        }
    }

    fun delayNextDrawingSave(noteId: Long, delayMillis: Long) {
        synchronized(drawingNoteDelays) {
            drawingNoteDelays[noteId] = delayMillis.coerceAtLeast(0L)
        }
    }

    fun consumeTextSaveFailure(noteId: Long): Boolean {
        return synchronized(textNoteFailures) {
            textNoteFailures.remove(noteId)
        }
    }

    fun consumeDrawingSaveFailure(noteId: Long): Boolean {
        return synchronized(drawingNoteFailures) {
            drawingNoteFailures.remove(noteId)
        }
    }

    suspend fun delayDrawingSaveIfRequested(noteId: Long) {
        val delayMillis = synchronized(drawingNoteDelays) {
            drawingNoteDelays.remove(noteId)
        } ?: return
        if (delayMillis > 0L) delay(delayMillis)
    }

    fun clear() {
        synchronized(textNoteFailures) {
            textNoteFailures.clear()
        }
        synchronized(drawingNoteFailures) {
            drawingNoteFailures.clear()
        }
        synchronized(drawingNoteDelays) {
            drawingNoteDelays.clear()
        }
    }
}
