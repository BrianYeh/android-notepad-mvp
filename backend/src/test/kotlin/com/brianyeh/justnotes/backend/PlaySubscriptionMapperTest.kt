package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.play.PlaySubscriptionLineItem
import com.brianyeh.justnotes.backend.play.PlaySubscriptionMapper
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaySubscriptionMapperTest {
    @Test
    fun activeAndGracePeriodGrantPremium() {
        assertTrue(mapped(BackendSubscriptionStatus.Active).hasPremium)
        assertTrue(mapped(BackendSubscriptionStatus.GracePeriod).hasPremium)
    }

    @Test
    fun onHoldExpiredAndRevokedDoNotGrantPremium() {
        assertFalse(mapped(BackendSubscriptionStatus.OnHold).hasPremium)
        assertFalse(mapped(BackendSubscriptionStatus.Expired).hasPremium)
        assertFalse(mapped(BackendSubscriptionStatus.Revoked).hasPremium)
    }

    @Test
    fun canceledButStillActiveUntilExpiryGrantsUntilExpiry() {
        val record = mapped(
            status = BackendSubscriptionStatus.Expired,
            canceledButActiveUntilExpiry = true,
            expiryTime = NOW + 1_000L,
        )

        assertTrue(record.hasPremium)
        assertEquals(BackendSubscriptionStatus.Active, record.status)
    }

    @Test
    fun monthlyTrialAndAnnualBasePlanMetadataArePreserved() {
        val monthly = mapped(BackendSubscriptionStatus.Active, basePlanId = "monthly", offerId = "trial10d")
        val annual = mapped(BackendSubscriptionStatus.Active, basePlanId = "annual", offerId = null)

        assertEquals("trial10d", monthly.offerId)
        assertEquals("annual", annual.basePlanId)
        assertEquals(null, annual.offerId)
    }

    private fun mapped(
        status: BackendSubscriptionStatus,
        basePlanId: String = "monthly",
        offerId: String? = null,
        canceledButActiveUntilExpiry: Boolean = false,
        expiryTime: Long = NOW + 30L * 24L * 60L * 60L * 1_000L,
    ) = PlaySubscriptionMapper.toEntitlement(
        googleSub = "google-sub",
        verification = PlaySubscriptionVerification(
            packageName = "com.brianyeh.justnotes",
            purchaseTokenHash = "token-hash",
            subscriptionState = status,
            lineItems = listOf(
                PlaySubscriptionLineItem(
                    productId = "just_notes_premium",
                    basePlanId = basePlanId,
                    offerId = offerId,
                    expiryTime = expiryTime,
                ),
            ),
            acknowledgementState = "ACKNOWLEDGED",
            autoRenewing = true,
            linkedPurchaseTokenHash = null,
            canceledButActiveUntilExpiry = canceledButActiveUntilExpiry,
        ),
        now = NOW,
    )

    private companion object {
        const val NOW = 1_762_000_000_000L
    }
}
