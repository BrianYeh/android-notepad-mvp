package com.example.notepad.data

enum class AppLanguage(
    val code: String,
    val displayName: String,
) {
    English("en", "English"),
    TraditionalChinese("zh-TW", "繁體中文");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.firstOrNull { it.code == code } ?: English
        }
    }
}
