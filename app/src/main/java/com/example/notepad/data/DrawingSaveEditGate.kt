package com.example.notepad.data

class DrawingSaveEditGate(initialEditVersion: Long = 0L) {
    private val lock = Any()
    private var editVersion = initialEditVersion

    fun currentEditVersion(): Long = synchronized(lock) {
        editVersion
    }

    fun markEdited(): Long = synchronized(lock) {
        editVersion += 1
        editVersion
    }

    fun isCurrent(expectedEditVersion: Long): Boolean = synchronized(lock) {
        editVersion == expectedEditVersion
    }

    fun <T> withSaveCommitSection(block: () -> T): T = synchronized(lock) {
        block()
    }
}
