package com.example.notepad.ui

import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UiTextTest {
    @Test
    fun purchaseCopySeparatesTrialPendingAndBackendVerificationStates() {
        listOf(EnglishText, TraditionalChineseText).forEach { text ->
            assertNotEquals(text.premiumTrial, text.premiumRenewal)
            assertNotEquals(text.premiumSubscribePending, text.premiumRenewal)
            assertTrue(text.premiumTrial.contains("10"))
        }
    }

    @Test
    fun constructorStaysBelowDexInvokeArgumentLimit() {
        val maxConstructorParameters = UiText::class.java.declaredConstructors.maxOf { it.parameterCount }

        assertTrue(
            "UiText constructors must stay at 254 parameters or less so Android DEX invoke-range " +
                "does not overflow when locale text singletons are initialized.",
            maxConstructorParameters <= 254,
        )
    }
}
