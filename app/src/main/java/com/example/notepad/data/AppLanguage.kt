package com.example.notepad.data

import java.util.Locale

enum class AppLanguage {
    English,
    TraditionalChinese;

    companion object {
        fun fromLocale(locale: Locale): AppLanguage {
            return if (locale.language.equals(Locale.CHINESE.language, ignoreCase = true)) {
                TraditionalChinese
            } else {
                English
            }
        }
    }
}
