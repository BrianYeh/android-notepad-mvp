package com.brianyeh.justnotes.backend.security

class StaticSecretValueProvider(
    private val secret: VersionedSecret?,
) : SecretValueProvider {
    override fun currentSecret(): VersionedSecret? = secret
}
