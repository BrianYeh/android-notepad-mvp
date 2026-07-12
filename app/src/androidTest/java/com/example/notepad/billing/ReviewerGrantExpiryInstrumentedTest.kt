package com.example.notepad.billing

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewerGrantExpiryInstrumentedTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun clearStoredEntitlement() {
        application.getSharedPreferences(PremiumEntitlementStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun clearStoredEntitlementAfterTest() = clearStoredEntitlement()

    @Test
    fun reviewerGrantEmitsExpiredStateAtDeadlineWithoutAnotherBillingEvent() {
        var now = System.currentTimeMillis()
        val scheduler = FakeReviewerGrantExpiryScheduler()
        val billing = PremiumBilling(
            application = application,
            clock = { now },
            connectToPlay = false,
            reviewerGrantExpiryScheduler = scheduler,
        )

        try {
            assertTrue(
                billing.applyBackendEntitlement(
                    PremiumBackendEntitlementResponse(
                        hasPremium = true,
                        status = PremiumSubscriptionStatus.Active,
                        source = PremiumEntitlementSource.ReviewerGrant,
                        expiryTime = now + 1_000L,
                        lastVerifiedAt = now,
                        acknowledgementState = PremiumBackendAcknowledgementState.NotRequired,
                    ),
                ),
            )
            assertTrue(billing.state.value.hasPremiumAccess)
            assertEquals(1_000L, scheduler.delayMillis)

            now += 1_000L
            scheduler.runDeadline()

            assertFalse(billing.state.value.hasPremiumAccess)
            assertEquals(PremiumSubscriptionStatus.Expired, billing.state.value.subscription.status)
            assertEquals(PremiumEntitlementSource.None, billing.state.value.subscription.source)
        } finally {
            billing.close()
        }
    }

    @Test
    fun staleDeadlineCannotExpireARefreshedReviewerGrant() {
        var now = System.currentTimeMillis()
        val scheduler = FakeReviewerGrantExpiryScheduler()
        val billing = PremiumBilling(
            application = application,
            clock = { now },
            connectToPlay = false,
            reviewerGrantExpiryScheduler = scheduler,
        )

        try {
            billing.applyBackendEntitlement(reviewerGrant(now, now + 1_000L))
            val staleDeadline = scheduler.tasks.single()
            billing.applyBackendEntitlement(reviewerGrant(now, now + 5_000L))

            now += 1_000L
            staleDeadline.invoke()

            assertTrue(billing.state.value.hasPremiumAccess)
            assertEquals(PremiumEntitlementSource.ReviewerGrant, billing.state.value.subscription.source)
            assertEquals(now + 4_000L, billing.state.value.subscription.expiryTime)
        } finally {
            billing.close()
        }
    }

    @Test
    fun expirySchedulerIgnoresReplacementAfterClose() {
        val scheduler = TimerReviewerGrantExpiryScheduler()
        scheduler.close()

        scheduler.replaceDeadline(0L) {
            throw AssertionError("Closed scheduler must not run a replacement task.")
        }
    }

    private fun reviewerGrant(now: Long, expiryTime: Long) = PremiumBackendEntitlementResponse(
        hasPremium = true,
        status = PremiumSubscriptionStatus.Active,
        source = PremiumEntitlementSource.ReviewerGrant,
        expiryTime = expiryTime,
        lastVerifiedAt = now,
        acknowledgementState = PremiumBackendAcknowledgementState.NotRequired,
    )

    private class FakeReviewerGrantExpiryScheduler : ReviewerGrantExpiryScheduler {
        var delayMillis: Long? = null
        private var task: (() -> Unit)? = null
        val tasks = mutableListOf<() -> Unit>()

        override fun replaceDeadline(delayMillis: Long?, task: () -> Unit) {
            this.delayMillis = delayMillis
            this.task = task.takeIf { delayMillis != null }
            if (delayMillis != null) tasks += task
        }

        override fun close() {
            delayMillis = null
            task = null
            tasks.clear()
        }

        fun runDeadline() {
            val current = task
            task = null
            current?.invoke()
        }
    }
}
