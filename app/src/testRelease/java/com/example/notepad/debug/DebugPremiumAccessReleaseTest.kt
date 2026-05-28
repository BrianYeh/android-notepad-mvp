package com.example.notepad.debug

import android.content.ContextWrapper
import org.junit.Assert.assertFalse
import org.junit.Test

class DebugPremiumAccessReleaseTest {
    @Test
    fun releaseBuildCannotEnableDebugPremiumOverride() {
        val context = ContextWrapper(null)

        assertFalse(DebugPremiumAccess.isAvailable)
        assertFalse(DebugPremiumAccess.read(context))

        DebugPremiumAccess.write(context, true)

        assertFalse(DebugPremiumAccess.read(context))
    }
}
