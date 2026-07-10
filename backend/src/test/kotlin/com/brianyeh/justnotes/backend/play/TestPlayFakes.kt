package com.brianyeh.justnotes.backend.play

object NoopPlaySubscriptionVerifier : PlaySubscriptionVerifier {
    override suspend fun verify(
        packageName: String,
        purchaseToken: String,
    ): PlaySubscriptionVerificationResult {
        return PlaySubscriptionVerificationResult.Failure("Test verifier is intentionally disabled.")
    }
}
