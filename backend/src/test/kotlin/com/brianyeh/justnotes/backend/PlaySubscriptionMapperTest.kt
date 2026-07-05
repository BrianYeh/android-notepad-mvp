package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.entitlement.BackendSubscriptionStatus
import com.brianyeh.justnotes.backend.play.PlayAcknowledgementState
import com.brianyeh.justnotes.backend.play.PlaySubscriptionLineItem
import com.brianyeh.justnotes.backend.play.PlaySubscriptionMapper
import com.brianyeh.justnotes.backend.play.PlaySubscriptionState
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerification
import com.brianyeh.justnotes.backend.play.PlaySubscriptionsV2Snapshot
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
    fun activeAfterExpiryDoesNotGrantPremium() {
        val record = mapped(BackendSubscriptionStatus.Active, expiryTime = NOW - 1L)

        assertFalse(record.hasPremium)
        assertEquals(BackendSubscriptionStatus.Expired, record.status)
    }

    @Test
    fun onHoldPausedExpiredRevokedPendingAndUnknownDoNotGrantPremium() {
        assertFalse(mapped(BackendSubscriptionStatus.OnHold).hasPremium)
        assertFalse(mapped(BackendSubscriptionStatus.Paused).hasPremium)
        assertFalse(mapped(BackendSubscriptionStatus.Expired).hasPremium)
        assertFalse(mapped(BackendSubscriptionStatus.Revoked).hasPremium)
        assertFalse(mapped(BackendSubscriptionStatus.VerificationPending).hasPremium)
        assertFalse(mapped(BackendSubscriptionStatus.Unknown).hasPremium)
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
    fun grantableStateDoesNotGrantWithoutAcknowledgement() {
        val record = mapped(
            status = BackendSubscriptionStatus.Active,
            acknowledgementState = "PENDING",
        )

        assertFalse(record.hasPremium)
        assertEquals(BackendSubscriptionStatus.VerificationPending, record.status)
    }

    @Test
    fun grantableStateDoesNotGrantWithoutOwnership() {
        val record = mapped(
            status = BackendSubscriptionStatus.Active,
            ownershipVerified = false,
        )

        assertFalse(record.hasPremium)
    }

    @Test
    fun mapperDefaultsToNoGrantWithoutExplicitOwnershipProof() {
        val record = PlaySubscriptionMapper.toEntitlement(
            googleSub = "google-sub",
            verification = PlaySubscriptionVerification(
                packageName = "com.brianyeh.justnotes",
                purchaseTokenHash = "token-hash",
                subscriptionState = BackendSubscriptionStatus.Active,
                lineItems = listOf(
                    PlaySubscriptionLineItem(
                        productId = "just_notes_premium",
                        basePlanId = "monthly",
                        offerId = null,
                        expiryTime = NOW + 1_000L,
                    ),
                ),
                acknowledgementState = "ACKNOWLEDGED",
                autoRenewing = true,
                linkedPurchaseTokenHash = null,
            ),
            now = NOW,
        )

        assertFalse(record.hasPremium)
        assertEquals(BackendSubscriptionStatus.VerificationPending, record.status)
    }

    @Test
    fun monthlyTrialAndAnnualBasePlanMetadataArePreserved() {
        val monthly = mapped(BackendSubscriptionStatus.Active, basePlanId = "monthly", offerId = "trial10d")
        val annual = mapped(BackendSubscriptionStatus.Active, basePlanId = "annual", offerId = null)

        assertEquals("trial10d", monthly.offerId)
        assertEquals("annual", annual.basePlanId)
        assertEquals(null, annual.offerId)
    }

    @Test
    fun annualTrialAndUnknownOfferAreRejectedByMapperAllowlist() {
        assertFalse(mapped(BackendSubscriptionStatus.Active, basePlanId = "annual", offerId = "trial10d").hasPremium)
        assertFalse(mapped(BackendSubscriptionStatus.Active, basePlanId = "monthly", offerId = "unknown").hasPremium)
    }

    @Test
    fun subscriptionsV2StatesMapToBackendStatuses() {
        assertEquals(
            BackendSubscriptionStatus.Active,
            mappedFromV2(PlaySubscriptionState.SUBSCRIPTION_STATE_ACTIVE).status,
        )
        assertEquals(
            BackendSubscriptionStatus.GracePeriod,
            mappedFromV2(PlaySubscriptionState.SUBSCRIPTION_STATE_IN_GRACE_PERIOD).status,
        )
        assertEquals(
            BackendSubscriptionStatus.Active,
            mappedFromV2(PlaySubscriptionState.SUBSCRIPTION_STATE_CANCELED).status,
        )
        assertEquals(
            BackendSubscriptionStatus.OnHold,
            mappedFromV2(PlaySubscriptionState.SUBSCRIPTION_STATE_ON_HOLD).status,
        )
        assertEquals(
            BackendSubscriptionStatus.Paused,
            mappedFromV2(PlaySubscriptionState.SUBSCRIPTION_STATE_PAUSED).status,
        )
        assertEquals(
            BackendSubscriptionStatus.Expired,
            mappedFromV2(PlaySubscriptionState.SUBSCRIPTION_STATE_EXPIRED).status,
        )
        assertEquals(
            BackendSubscriptionStatus.Revoked,
            mappedFromV2(PlaySubscriptionState.REVOKED).status,
        )
        assertEquals(
            BackendSubscriptionStatus.VerificationPending,
            mappedFromV2(PlaySubscriptionState.SUBSCRIPTION_STATE_PENDING).status,
        )
        assertEquals(
            BackendSubscriptionStatus.Unknown,
            mappedFromV2(PlaySubscriptionState.UNKNOWN).status,
        )
    }

    private fun mapped(
        status: BackendSubscriptionStatus,
        basePlanId: String = "monthly",
        offerId: String? = null,
        canceledButActiveUntilExpiry: Boolean = false,
        expiryTime: Long = NOW + 30L * 24L * 60L * 60L * 1_000L,
        acknowledgementState: String = "ACKNOWLEDGED",
        ownershipVerified: Boolean = true,
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
            acknowledgementState = acknowledgementState,
            autoRenewing = true,
            linkedPurchaseTokenHash = null,
            canceledButActiveUntilExpiry = canceledButActiveUntilExpiry,
        ),
        now = NOW,
        ownershipVerified = ownershipVerified,
    )

    private fun mappedFromV2(state: PlaySubscriptionState) = PlaySubscriptionMapper.toEntitlement(
        googleSub = "google-sub",
        verification = PlaySubscriptionMapper.fromSubscriptionsV2(
            PlaySubscriptionsV2Snapshot(
                packageName = "com.brianyeh.justnotes",
                purchaseTokenHash = "token-hash",
                subscriptionState = state,
                acknowledgementState = PlayAcknowledgementState.ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED,
                lineItems = listOf(
                    PlaySubscriptionLineItem(
                        productId = "just_notes_premium",
                        basePlanId = "monthly",
                        offerId = null,
                        expiryTime = NOW + 1_000L,
                    ),
                ),
                autoRenewing = true,
                linkedPurchaseTokenHash = null,
                externalAccountIdentifiers = null,
            ),
        ),
        now = NOW,
        ownershipVerified = true,
    )

    private companion object {
        const val NOW = 1_762_000_000_000L
    }
}
