package com.example.notepad.billing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BackendPurchaseRepositoryTest {
    @Test
    fun fetchContextReturnsGuardedBackendResult() = runBlocking {
        val expected = BackendBillingContextFetchResult.Success(BackendBillingContext("a".repeat(43)))
        val repository = repository(contextResult = expected)

        assertSame(expected, repository.fetchBillingContext())
    }

    @Test
    fun verifiedAndPendingResponsesAreApplied() = runBlocking {
        val applied = mutableListOf<PremiumBackendEntitlementResponse>()
        val verifiedResponse = response(PremiumSubscriptionStatus.Active)
        val pendingResponse = response(PremiumSubscriptionStatus.VerificationPending, retryAfterSeconds = 30)
        val results = ArrayDeque<BackendPurchaseVerificationResult>().apply {
            add(BackendPurchaseVerificationResult.Verified(verifiedResponse))
            add(BackendPurchaseVerificationResult.Pending(pendingResponse))
        }
        val repository = repository(
            verify = { results.removeFirst() },
            apply = { applied += it; true },
        )

        repository.verify(candidate("token-1"))
        repository.verify(candidate("token-2"))

        assertEquals(listOf(verifiedResponse, pendingResponse), applied)
    }

    @Test
    fun simultaneousDuplicateTokenCollapsesToOnePost() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var posts = 0
        val repository = repository(
            verify = {
                posts += 1
                started.complete(Unit)
                release.await()
                BackendPurchaseVerificationResult.Verified(response(PremiumSubscriptionStatus.Active))
            },
        )

        val first = async { repository.verify(candidate("same-token")) }
        started.await()
        val duplicate = repository.verify(candidate("same-token"))
        release.complete(Unit)

        assertEquals(BackendPurchaseSubmissionResult.DuplicateInFlight, duplicate)
        assertEquals(1, posts)
        assertEquals(
            true,
            first.await() is BackendPurchaseSubmissionResult.Submitted,
        )
    }

    @Test
    fun laterRetryIsAllowedAfterFailure() = runBlocking {
        var posts = 0
        val repository = repository(
            verify = {
                posts += 1
                BackendPurchaseVerificationResult.Failure("safe failure")
            },
        )

        repository.verify(candidate("retry-token"))
        repository.verify(candidate("retry-token"))

        assertEquals(2, posts)
    }

    @Test
    fun rejectedUnavailableAndStaleResultsFailClosed() = runBlocking {
        val rejectedResponse = response(PremiumSubscriptionStatus.Error)
        val unavailableResponse = response(PremiumSubscriptionStatus.VerificationPending)
        val results = ArrayDeque<BackendPurchaseVerificationResult>().apply {
            add(BackendPurchaseVerificationResult.Rejected(409, rejectedResponse))
            add(BackendPurchaseVerificationResult.Unavailable(unavailableResponse))
            add(BackendPurchaseVerificationResult.Stale)
        }
        val applied = mutableListOf<PremiumBackendEntitlementResponse>()
        val repository = repository(
            verify = { results.removeFirst() },
            apply = { applied += it; true },
        )

        repository.verify(candidate("one"))
        repository.verify(candidate("two"))
        repository.verify(candidate("three"))

        assertEquals(listOf(rejectedResponse, unavailableResponse), applied)
    }

    private fun repository(
        contextResult: BackendBillingContextFetchResult = BackendBillingContextFetchResult.NotSignedIn,
        verify: suspend (BackendPurchaseCandidate) -> BackendPurchaseVerificationResult = {
            BackendPurchaseVerificationResult.Verified(response(PremiumSubscriptionStatus.Active))
        },
        apply: (PremiumBackendEntitlementResponse) -> Boolean = { true },
    ) = BackendPurchaseRepository(
        fetchContext = { contextResult },
        verifyPurchase = verify,
        applyBackendEntitlement = apply,
    )

    private fun candidate(token: String) = BackendPurchaseCandidate(
        purchaseToken = token,
        packageName = "com.brianyeh.justnotes",
        productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
        basePlanId = "monthly",
        offerId = PremiumCatalog.TRIAL_OFFER_ID,
        appVersion = "1.0.7",
        versionCode = 5,
        deviceLocale = "zh-TW",
    )

    private fun response(
        status: PremiumSubscriptionStatus,
        retryAfterSeconds: Long? = null,
    ) = PremiumBackendEntitlementResponse(
        hasPremium = status == PremiumSubscriptionStatus.Active,
        status = status,
        source = PremiumEntitlementSource.BackendVerified,
        packageName = "com.brianyeh.justnotes",
        productId = PremiumCatalog.PREFERRED_PRODUCT_ID,
        acknowledgementState = if (status == PremiumSubscriptionStatus.Active) {
            PremiumBackendAcknowledgementState.Acknowledged
        } else {
            PremiumBackendAcknowledgementState.Pending
        },
        expiryTime = if (status == PremiumSubscriptionStatus.Active) Long.MAX_VALUE else null,
        retryable = retryAfterSeconds != null,
        retryAfterSeconds = retryAfterSeconds,
    )
}
