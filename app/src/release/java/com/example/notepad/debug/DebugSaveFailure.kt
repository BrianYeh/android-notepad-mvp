package com.example.notepad.debug

object DebugSaveFailure {
    fun failNextTextSave(noteId: Long) = Unit

    fun consumeTextSaveFailure(noteId: Long): Boolean = false

    fun clear() = Unit
}
