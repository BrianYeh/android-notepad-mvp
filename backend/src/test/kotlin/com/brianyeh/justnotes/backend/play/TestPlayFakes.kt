package com.brianyeh.justnotes.backend.play

object NoopPlaySubscriptionVerifier : PlaySubscriptionVerifier {
    override suspend fun verify(
        packageName: String,
        purchaseToken: String,
    ): PlaySubscriptionVerificationResult {
        return PlaySubscriptionVerificationResult.Failure(
            reason = "Test verifier is intentionally disabled.",
            retryable = false,
            code = PlayVerificationFailureCode.INVALID_INPUT,
        )
    }
}
