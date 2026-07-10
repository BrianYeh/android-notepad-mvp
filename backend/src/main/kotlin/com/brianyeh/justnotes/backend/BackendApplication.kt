package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.auth.OfficialGoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.billing.BillingVerificationOrchestrator
import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.PurchaseOwnershipValidator
import com.brianyeh.justnotes.backend.routes.justNotesRoutes
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
    justNotesRoutes(
        config = config,
        idTokenVerifier = productionGoogleIdTokenVerifier(config),
        entitlementRepository = dependencies.entitlementRepository,
        billingVerificationOrchestrator = billingVerificationOrchestrator,
        obfuscatedAccountIdDeriver = dependencies.obfuscatedAccountIdDeriver,
    )
}

private fun productionGoogleIdTokenVerifier(config: BackendConfig): GoogleIdTokenVerifier {
    val validationError = config.validateForIdTokenVerification()
    require(validationError == null) { validationError ?: "Google ID token configuration is invalid." }
    return OfficialGoogleIdTokenVerifier(config)
}
