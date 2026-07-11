package com.brianyeh.justnotes.backend.rtdn

import com.google.cloud.Timestamp

sealed class RtdnClaimResult {
    data class Claimed(val generation: Long) : RtdnClaimResult()
    data object AlreadyCompleted : RtdnClaimResult()
    data object InFlight : RtdnClaimResult()
}

interface RtdnEventRepository {
    suspend fun claim(messageIdHash: String, now: Long, leaseUntil: Long): RtdnClaimResult
    suspend fun complete(
        messageIdHash: String,
        generation: Long,
        completedAt: Long,
        outcome: String,
    ): Boolean
    suspend fun release(
        messageIdHash: String,
        generation: Long,
        retryAt: Long,
        errorCode: String,
    ): Boolean
}

data class RtdnEventMutation<T>(
    val result: T,
    val fields: Map<String, Any?>? = null,
)

interface RtdnEventDocumentStore {
    suspend fun <T> transact(
        documentId: String,
        operation: (Map<String, Any?>?) -> RtdnEventMutation<T>,
    ): T
}

class FirestoreRtdnEventRepository(
    private val store: RtdnEventDocumentStore,
    ttlDays: Int,
) : RtdnEventRepository {
    private val ttlMillis: Long

    init {
        require(ttlDays in 1..365) { "RTDN event TTL days must be between 1 and 365." }
        ttlMillis = ttlDays * MILLIS_PER_DAY
    }

    override suspend fun claim(messageIdHash: String, now: Long, leaseUntil: Long): RtdnClaimResult {
        validateDocumentId(messageIdHash)
        require(now > 0L) { "RTDN claim time must be positive." }
        require(leaseUntil > now) { "RTDN processing lease must end after it starts." }
        return store.transact(messageIdHash) { existingValue ->
            val existing = existingValue.orEmpty()
            when {
                existing.long(COMPLETED_AT_FIELD) != null ->
                    RtdnEventMutation(RtdnClaimResult.AlreadyCompleted)
                existing.long(LEASE_UNTIL_FIELD)?.let { it > now } == true ->
                    RtdnEventMutation(RtdnClaimResult.InFlight)
                existing.long(RETRY_AT_FIELD)?.let { it > now } == true ->
                    RtdnEventMutation(RtdnClaimResult.InFlight)
                else -> {
                    val generation = existing.long(GENERATION_FIELD)?.plus(1L) ?: 1L
                    val attemptCount = existing.int(ATTEMPT_COUNT_FIELD)?.plus(1) ?: 1
                    RtdnEventMutation(
                        result = RtdnClaimResult.Claimed(generation),
                        fields = mapOf(
                            GENERATION_FIELD to generation,
                            LEASE_UNTIL_FIELD to leaseUntil,
                            RETRY_AT_FIELD to null,
                            COMPLETED_AT_FIELD to null,
                            OUTCOME_FIELD to null,
                            ERROR_CODE_FIELD to null,
                            ATTEMPT_COUNT_FIELD to attemptCount,
                            EXPIRES_AT_FIELD to timestamp(now + ttlMillis),
                        ),
                    )
                }
            }
        }
    }

    override suspend fun complete(
        messageIdHash: String,
        generation: Long,
        completedAt: Long,
        outcome: String,
    ): Boolean {
        validateDocumentId(messageIdHash)
        require(generation > 0L) { "RTDN generation must be positive." }
        require(completedAt > 0L) { "RTDN completion time must be positive." }
        require(STABLE_CODE_PATTERN.matches(outcome)) { "RTDN outcome must be a stable code." }
        return store.transact(messageIdHash) { existingValue ->
            val existing = existingValue.orEmpty()
            if (
                existing.long(GENERATION_FIELD) != generation ||
                existing.long(LEASE_UNTIL_FIELD) == null ||
                existing.long(COMPLETED_AT_FIELD) != null
            ) {
                RtdnEventMutation(false)
            } else {
                RtdnEventMutation(
                    result = true,
                    fields = existing + mapOf(
                        LEASE_UNTIL_FIELD to null,
                        RETRY_AT_FIELD to null,
                        COMPLETED_AT_FIELD to completedAt,
                        OUTCOME_FIELD to outcome,
                        ERROR_CODE_FIELD to null,
                        EXPIRES_AT_FIELD to timestamp(completedAt + ttlMillis),
                    ),
                )
            }
        }
    }

    override suspend fun release(
        messageIdHash: String,
        generation: Long,
        retryAt: Long,
        errorCode: String,
    ): Boolean {
        validateDocumentId(messageIdHash)
        require(generation > 0L) { "RTDN generation must be positive." }
        require(retryAt > 0L) { "RTDN retry time must be positive." }
        require(STABLE_CODE_PATTERN.matches(errorCode)) { "RTDN error must be a stable code." }
        return store.transact(messageIdHash) { existingValue ->
            val existing = existingValue.orEmpty()
            if (
                existing.long(GENERATION_FIELD) != generation ||
                existing.long(LEASE_UNTIL_FIELD) == null ||
                existing.long(COMPLETED_AT_FIELD) != null
            ) {
                RtdnEventMutation(false)
            } else {
                RtdnEventMutation(
                    result = true,
                    fields = existing + mapOf(
                        LEASE_UNTIL_FIELD to null,
                        RETRY_AT_FIELD to retryAt,
                        COMPLETED_AT_FIELD to null,
                        OUTCOME_FIELD to null,
                        ERROR_CODE_FIELD to errorCode,
                    ),
                )
            }
        }
    }

    private fun validateDocumentId(documentId: String) {
        require(SHA256_URL_SAFE_PATTERN.matches(documentId)) {
            "RTDN event document ID must be a SHA-256 Base64URL digest."
        }
    }

    private fun Map<String, Any?>.long(name: String): Long? =
        (this[name] as? Number)?.toLong()

    private fun Map<String, Any?>.int(name: String): Int? =
        (this[name] as? Number)?.toInt()

    private fun timestamp(epochMillis: Long): Timestamp {
        val seconds = Math.floorDiv(epochMillis, 1_000L)
        val nanos = Math.floorMod(epochMillis, 1_000L).toInt() * 1_000_000
        return Timestamp.ofTimeSecondsAndNanos(seconds, nanos)
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        const val GENERATION_FIELD = "generation"
        const val LEASE_UNTIL_FIELD = "leaseUntil"
        const val RETRY_AT_FIELD = "retryAt"
        const val COMPLETED_AT_FIELD = "completedAt"
        const val OUTCOME_FIELD = "outcome"
        const val ERROR_CODE_FIELD = "errorCode"
        const val ATTEMPT_COUNT_FIELD = "attemptCount"
        const val EXPIRES_AT_FIELD = "expiresAt"
        val SHA256_URL_SAFE_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")
        val STABLE_CODE_PATTERN = Regex("^[A-Z][A-Z0-9_]{0,63}$")
    }
}
