package com.example.notepad

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.notepad.billing.PremiumAcknowledgementStatus
import com.example.notepad.billing.PremiumBillingState
import com.example.notepad.billing.PremiumEntitlementSource
import com.example.notepad.billing.PremiumSubscriptionSnapshot
import com.example.notepad.billing.PremiumSubscriptionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PremiumPurchaseGateTest {
    @Test
    fun normalDebugBuildKeepsBackendPurchaseFlowDisabled() {
        val otherwiseReady = PremiumBillingState(
            billingAvailable = true,
            backendPurchaseReady = true,
            loading = false,
        )

        assertFalse(BuildConfig.ENABLE_BACKEND_PURCHASE_FLOW)
        assertFalse(otherwiseReady.canLaunchPurchase)
    }

    @Test
    fun enabledFlowCanLaunchOnlyAfterAccountBackendAndBillingAreReady() {
        val ready = PremiumBillingState(
            billingAvailable = true,
            backendPurchaseReady = true,
            loading = false,
        )

        assertTrue(ready.canLaunchPurchase(enableBackendPurchaseFlow = true))
        assertFalse(ready.copy(backendPurchaseReady = false).canLaunchPurchase(true))
        assertFalse(ready.copy(purchaseLaunching = true).canLaunchPurchase(true))
        assertFalse(ready.copy(purchaseVerificationInFlight = true).canLaunchPurchase(true))
    }

    @Test
    fun pendingAndClientObservedPurchasedNeverUnlockPremium() {
        val pending = PremiumBillingState(
            subscription = PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.PendingPurchase,
                source = PremiumEntitlementSource.ClientObserved,
            ),
        )
        val purchasedButUnverified = PremiumBillingState(
            subscription = PremiumSubscriptionSnapshot(
                status = PremiumSubscriptionStatus.VerificationPending,
                source = PremiumEntitlementSource.ClientObserved,
                acknowledgementStatus = PremiumAcknowledgementStatus.BackendRequired,
            ),
        )

        assertFalse(pending.hasPremiumAccess)
        assertFalse(purchasedButUnverified.hasPremiumAccess)
        assertFalse(pending.canLaunchPurchase(enableBackendPurchaseFlow = true))
        assertFalse(purchasedButUnverified.canLaunchPurchase(enableBackendPurchaseFlow = true))
    }
}
