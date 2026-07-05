package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.auth.FailClosedGoogleIdTokenVerifier
import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.NoopEntitlementRepository
import com.brianyeh.justnotes.backend.play.NoopPlaySubscriptionVerifier
import com.brianyeh.justnotes.backend.routes.justNotesRoutes
import io.ktor.server.application.Application
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
) {
    justNotesRoutes(
        config = config,
        idTokenVerifier = FailClosedGoogleIdTokenVerifier(config),
        entitlementRepository = NoopEntitlementRepository,
        playSubscriptionVerifier = NoopPlaySubscriptionVerifier,
    )
}
