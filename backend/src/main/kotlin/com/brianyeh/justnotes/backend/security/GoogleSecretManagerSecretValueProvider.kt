package com.brianyeh.justnotes.backend.security

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient

data class SecretManagerAccessResult(
    val value: String,
    val version: String,
) {
    override fun toString(): String = "SecretManagerAccessResult(value=[REDACTED], version=$version)"
}

fun interface SecretManagerGateway {
    fun accessSecretVersion(resourceName: String): SecretManagerAccessResult
}

class GoogleCloudSecretManagerGateway(
    private val client: SecretManagerServiceClient,
) : SecretManagerGateway {
    override fun accessSecretVersion(resourceName: String): SecretManagerAccessResult {
        val response = client.accessSecretVersion(resourceName)
        return SecretManagerAccessResult(
            value = response.payload.data.toStringUtf8(),
            version = response.name.substringAfterLast('/'),
        )
    }
}

class CachingSecretManagerSecretValueProvider(
    private val resourceName: String,
    private val gateway: SecretManagerGateway,
    private val cacheTtlMillis: Long = DEFAULT_CACHE_TTL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) : SecretValueProvider {
    @Volatile
    private var cached: CachedSecret? = null

    init {
        require(SECRET_VERSION_RESOURCE_PATTERN.matches(resourceName)) {
            "Secret Manager resource name is malformed."
        }
        require(cacheTtlMillis > 0L) { "Secret cache TTL must be positive." }
    }

    override fun currentSecret(): VersionedSecret {
        val now = clock()
        cached?.takeIf { now < it.expiresAt }?.let { return it.secret }
        return synchronized(this) {
            cached?.takeIf { now < it.expiresAt }?.secret ?: load(now)
        }
    }

    private fun load(now: Long): VersionedSecret {
        val accessed = gateway.accessSecretVersion(resourceName)
        check(accessed.value.isNotEmpty()) { "Secret Manager returned an empty secret payload." }
        check(accessed.version.matches(SECRET_VERSION_PATTERN)) {
            "Secret Manager returned an invalid secret version."
        }
        return VersionedSecret(
            value = accessed.value,
            version = accessed.version,
        ).also { secret ->
            cached = CachedSecret(secret, expiresAt = now + cacheTtlMillis)
        }
    }

    private data class CachedSecret(
        val secret: VersionedSecret,
        val expiresAt: Long,
    )

    private companion object {
        const val DEFAULT_CACHE_TTL_MILLIS = 5L * 60L * 1_000L
        val SECRET_VERSION_RESOURCE_PATTERN =
            Regex("^projects/[^/]+/secrets/[A-Za-z0-9_-]+/versions/(latest|[1-9][0-9]*)$")
        val SECRET_VERSION_PATTERN = Regex("^[1-9][0-9]*$")
    }
}
