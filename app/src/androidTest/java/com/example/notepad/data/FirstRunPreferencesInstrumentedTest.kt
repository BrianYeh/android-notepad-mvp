package com.example.notepad.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstRunPreferencesInstrumentedTest {
    @Test
    fun completionPersistsAndIsSeparateFromNoteStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(FirstRunPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        assertFalse(FirstRunPreferences.hasCompleted(context))

        FirstRunPreferences.markCompleted(context)

        assertTrue(FirstRunPreferences.hasCompleted(context))
    }
}
