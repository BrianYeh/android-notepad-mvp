package com.example.notepad.debug

import android.content.ContextWrapper
import org.junit.Assert.assertFalse
import org.junit.Test

class DebugGoogleSyncAccessReleaseTest {
    @Test
    fun releaseBuildCannotEnableGoogleSyncDebugEntry() {
        val context = ContextWrapper(null)

        assertFalse(DebugGoogleSyncAccess.isAvailable)
        assertFalse(DebugGoogleSyncAccess.read(context))

        DebugGoogleSyncAccess.write(context, true)

        assertFalse(DebugGoogleSyncAccess.read(context))
    }
}
