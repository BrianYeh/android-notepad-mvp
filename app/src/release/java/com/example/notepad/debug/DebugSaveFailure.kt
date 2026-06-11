package com.example.notepad.debug

object DebugSaveFailure {
    fun failNextTextSave(noteId: Long) = Unit

    fun failNextDrawingSave(noteId: Long) = Unit

    fun delayNextDrawingSave(noteId: Long, delayMillis: Long) = Unit

    fun consumeTextSaveFailure(noteId: Long): Boolean = false

    fun consumeDrawingSaveFailure(noteId: Long): Boolean = false

    suspend fun delayDrawingSaveIfRequested(noteId: Long) = Unit

    fun clear() = Unit
}
