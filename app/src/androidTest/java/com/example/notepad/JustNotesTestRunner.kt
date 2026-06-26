package com.example.notepad

import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.runner.AndroidJUnitRunner
import com.example.notepad.debug.DebugPremiumAccess

class JustNotesTestRunner : AndroidJUnitRunner() {
    private val animationScaleSettings = listOf(
        "window_animation_scale",
        "transition_animation_scale",
        "animator_duration_scale",
    )
    private var originalAnimationScales: Map<String, String?> = emptyMap()

    override fun onCreate(arguments: Bundle) {
        DebugPremiumAccess.suppressBillingConnectionForTests(true)
        super.onCreate(arguments)
    }

    override fun onStart() {
        originalAnimationScales = animationScaleSettings.associateWith { setting ->
            shell("settings get global $setting")
                ?.trim()
                ?.takeUnless { value -> value.isBlank() || value == "null" }
        }
        animationScaleSettings.forEach { setting ->
            shell("settings put global $setting 0")
        }
        super.onStart()
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        restoreAnimationScales()
        DebugPremiumAccess.suppressBillingConnectionForTests(false)
        super.finish(resultCode, results)
    }

    private fun restoreAnimationScales() {
        animationScaleSettings.forEach { setting ->
            val originalValue = originalAnimationScales[setting]
            if (originalValue == null) {
                shell("settings delete global $setting")
            } else {
                shell("settings put global $setting $originalValue")
            }
        }
    }

    private fun shell(command: String): String? {
        return runCatching {
            val descriptor = uiAutomation.executeShellCommand(command)
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { reader ->
                reader.readText()
            }
        }.getOrNull()
    }
}
