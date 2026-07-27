package com.example.notepad.ui

import com.example.notepad.data.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V109TextTest {
    @Test
    fun attractionCopyIsCompleteInEnglishAndTraditionalChinese() {
        AppLanguage.entries.forEach { language ->
            val values = V109Text::class.java.declaredFields
                .filter { field -> field.type == String::class.java }
                .map { field ->
                    field.isAccessible = true
                    field.get(v109Text(language)) as String
                }

            assertTrue(values.all(String::isNotBlank))
        }
        assertEquals(
            "Capture it now. Find it when it matters",
            v109Text(AppLanguage.English).welcomeTitle,
        )
        assertEquals(
            "現在記下，重要時找得到",
            v109Text(AppLanguage.TraditionalChinese).welcomeTitle,
        )
    }
}
