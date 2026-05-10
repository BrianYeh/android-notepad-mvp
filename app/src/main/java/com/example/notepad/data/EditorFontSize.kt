package com.example.notepad.data

enum class EditorFontSize(
    val code: String,
    val fontSizeSp: Int,
) {
    Small("small", 16),
    Medium("medium", 18),
    Large("large", 22),
    ;

    companion object {
        fun fromCode(code: String?): EditorFontSize {
            return entries.firstOrNull { it.code == code } ?: Medium
        }
    }
}
