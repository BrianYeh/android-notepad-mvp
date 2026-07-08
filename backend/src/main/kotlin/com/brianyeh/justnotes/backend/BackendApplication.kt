package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.auth.FailClosedGoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.auth.GoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.auth.OfficialGoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.InMemoryEntitlementRepository
import com.brianyeh.justnotes.backend.play.NoopPlaySubscriptionVerifier
import com.brianyeh.justnotes.backend.routes.justNotesRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

private val productionEntitlementRepository = InMemoryEntitlementRepository()

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
) {
    justNotesRoutes(
        config = config,
        idTokenVerifier = productionGoogleIdTokenVerifier(config),
        entitlementRepository = productionEntitlementRepository,
        playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
    )
}

private fun productionGoogleIdTokenVerifier(config: BackendConfig): GoogleIdTokenVerifier {
    if (config.validateForIdTokenVerification() != null) return FailClosedGoogleIdTokenVerifier(config)
    return runCatching { OfficialGoogleIdTokenVerifier(config) }
        .getOrElse { FailClosedGoogleIdTokenVerifier(config) }
}
