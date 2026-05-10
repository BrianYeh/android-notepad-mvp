package com.example.notepad.data

private const val SHARED_NOTE_TITLE_MAX_LENGTH = 60

fun buildSharedNoteTitle(subject: String?, sharedText: String, defaultTitle: String): String {
    subject?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

    return sharedText
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?.take(SHARED_NOTE_TITLE_MAX_LENGTH)
        ?.trim()
        ?.ifBlank { null }
        ?: defaultTitle
}
