package com.example.notepad.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumBillingStateTest {
    @Test
    fun hasPremiumAccessDefaultsToFalse() {
        assertFalse(PremiumBillingState().hasPremiumAccess)
    }

    @Test
    fun hasPremiumAccessUsesRealSubscription() {
        assertTrue(PremiumBillingState(isPremium = true).hasPremiumAccess)
    }

    @Test
    fun hasPremiumAccessUsesDebugOverrideWithoutChangingSubscription() {
        val state = PremiumBillingState(debugPremiumOverride = true)

        assertFalse(state.isPremium)
        assertTrue(state.hasPremiumAccess)
    }
}
