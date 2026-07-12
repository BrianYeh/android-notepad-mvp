package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.auth.OfficialGoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.billing.BillingVerificationOrchestrator
import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.PurchaseOwnershipValidator
import com.brianyeh.justnotes.backend.routes.justNotesRoutes
import com.brianyeh.justnotes.backend.rtdn.RtdnProcessor
import com.brianyeh.justnotes.backend.security.HmacSha256EmailHashDeriver
import com.brianyeh.justnotes.backend.security.SecretValueProvider
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(
        Netty,
        port = port,
        module = Application::justNotesBackendModule,
    ).start(wait = true)
}

fun Application.justNotesBackendModule(
    config: BackendConfig = BackendConfig.fromEnvironment(),
    dependencies: ProductionBackendDependencies = ProductionBackendDependencies.create(config),
) {
    val rtdnErrors = config.validateForRtdn()
    require(rtdnErrors.isEmpty()) { rtdnErrors.joinToString(separator = " ") }
    environment.monitor.subscribe(ApplicationStopped) {
        dependencies.close()
    }
    val purchaseOwnershipValidator = PurchaseOwnershipValidator(dependencies.obfuscatedAccountIdDeriver)
    val billingVerificationOrchestrator = BillingVerificationOrchestrator(
        config = config,
        entitlementRepository = dependencies.entitlementRepository,
        playSubscriptionVerifier = dependencies.playSubscriptionVerifier,
        playSubscriptionAcknowledger = dependencies.playSubscriptionAcknowledger,
        ownershipValidator = purchaseOwnershipValidator,
        purchaseTokenCipher = dependencies.purchaseTokenCipher,
    )
    val rtdnProcessor = if (config.rtdnEnabled) {
        RtdnProcessor(
            config = config,
            eventRepository = dependencies.rtdnEventRepository,
            entitlementRepository = dependencies.entitlementRepository,
            tokenHasher = dependencies.purchaseTokenHasher,
            tokenCipher = dependencies.purchaseTokenCipher,
            playVerifier = dependencies.playSubscriptionVerifier,
            processingLeaseMillis = config.rtdnProcessingLeaseSeconds * 1_000L,
        )
    } else {
        null
    }
    justNotesRoutes(
        config = config,
        idTokenVerifier = productionGoogleIdTokenVerifier(
            config = config,
            emailHashSecretProvider = dependencies.emailHashSecretProvider,
        ),
        entitlementRepository = dependencies.entitlementRepository,
        accountDeletionRepository = dependencies.accountDeletionRepository,
        billingVerificationOrchestrator = billingVerificationOrchestrator,
        obfuscatedAccountIdDeriver = dependencies.obfuscatedAccountIdDeriver,
        reviewerGrantPolicy = dependencies.reviewerGrantPolicy,
        rtdnProcessor = rtdnProcessor,
    )
}

private fun productionGoogleIdTokenVerifier(
    config: BackendConfig,
    emailHashSecretProvider: SecretValueProvider,
): GoogleIdTokenVerifier {
    val validationError = config.validateForIdTokenVerification()
    require(validationError == null) { validationError ?: "Google ID token configuration is invalid." }
    return OfficialGoogleIdTokenVerifier(
        config = config,
        emailHashDeriver = HmacSha256EmailHashDeriver(emailHashSecretProvider),
    )
}
