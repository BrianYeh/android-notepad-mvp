package com.example.notepad.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun chineseSystemLocalesUseTraditionalChineseText() {
        assertEquals(AppLanguage.TraditionalChinese, AppLanguage.fromLocale(Locale.TRADITIONAL_CHINESE))
        assertEquals(AppLanguage.TraditionalChinese, AppLanguage.fromLocale(Locale.SIMPLIFIED_CHINESE))
    }

    @Test
    fun nonChineseSystemLocalesUseEnglishText() {
        assertEquals(AppLanguage.English, AppLanguage.fromLocale(Locale.ENGLISH))
        assertEquals(AppLanguage.English, AppLanguage.fromLocale(Locale.JAPANESE))
    }
}
