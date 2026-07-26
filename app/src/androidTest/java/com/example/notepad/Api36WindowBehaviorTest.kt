package com.example.notepad

import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Api36WindowBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Suppress("DEPRECATION")
    @Test
    fun activityUsesTransparentEdgeToEdgeSystemBars() {
        composeRule.activityRule.scenario.onActivity { activity ->
            assertEquals(android.graphics.Color.TRANSPARENT, activity.window.statusBarColor)
            assertEquals(android.graphics.Color.TRANSPARENT, activity.window.navigationBarColor)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                assertFalse(activity.window.isNavigationBarContrastEnforced)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                assertEquals(
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
                    activity.window.attributes.layoutInDisplayCutoutMode,
                )
            }
        }
    }

    @Test
    fun homeHasNoEnabledBackCallbackSoSystemCanFinishActivity() {
        composeRule.activityRule.scenario.onActivity { activity ->
            assertFalse(activity.onBackPressedDispatcher.hasEnabledCallbacks())
        }
    }
}
