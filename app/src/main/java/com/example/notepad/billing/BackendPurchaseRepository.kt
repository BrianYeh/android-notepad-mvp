package com.example.notepad.billing

internal sealed class BackendPurchaseSubmissionResult {
    data class Submitted(val result: BackendPurchaseVerificationResult) : BackendPurchaseSubmissionResult()
    data object DuplicateInFlight : BackendPurchaseSubmissionResult()
}

internal class BackendPurchaseRepository(
    private val fetchContext: suspend () -> BackendBillingContextFetchResult,
    private val verifyPurchase: suspend (BackendPurchaseCandidate) -> BackendPurchaseVerificationResult,
    private val applyBackendEntitlement: (PremiumBackendEntitlementResponse) -> Boolean,
) {
    constructor(
        entitlementRepository: BackendEntitlementRepository,
        applyBackendEntitlement: (PremiumBackendEntitlementResponse) -> Boolean,
    ) : this(
        fetchContext = entitlementRepository::fetchBillingContext,
        verifyPurchase = entitlementRepository::verifyPurchase,
        applyBackendEntitlement = applyBackendEntitlement,
    )

    private val inFlightTokens = mutableSetOf<String>()

    suspend fun fetchBillingContext(): BackendBillingContextFetchResult = fetchContext()

    suspend fun verify(candidate: BackendPurchaseCandidate): BackendPurchaseSubmissionResult {
        val admitted = synchronized(inFlightTokens) { inFlightTokens.add(candidate.purchaseToken) }
        if (!admitted) return BackendPurchaseSubmissionResult.DuplicateInFlight
        return try {
            val result = verifyPurchase(candidate)
            result.responseToApply()?.let(applyBackendEntitlement)
            BackendPurchaseSubmissionResult.Submitted(result)
        } finally {
            synchronized(inFlightTokens) { inFlightTokens.remove(candidate.purchaseToken) }
        }
    }
}

private fun BackendPurchaseVerificationResult.responseToApply(): PremiumBackendEntitlementResponse? {
    return when (this) {
        is BackendPurchaseVerificationResult.Verified -> response
        is BackendPurchaseVerificationResult.Pending -> response
        is BackendPurchaseVerificationResult.Rejected -> response
        is BackendPurchaseVerificationResult.Unavailable -> response
        BackendPurchaseVerificationResult.Disabled,
        BackendPurchaseVerificationResult.NotSignedIn,
        BackendPurchaseVerificationResult.Unauthorized,
        BackendPurchaseVerificationResult.Stale,
        is BackendPurchaseVerificationResult.Failure,
        -> null
    }
}
