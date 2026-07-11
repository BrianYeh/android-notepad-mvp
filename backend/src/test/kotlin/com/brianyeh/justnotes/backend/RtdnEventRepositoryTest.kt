package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.rtdn.FirestoreRtdnEventRepository
import com.brianyeh.justnotes.backend.rtdn.RtdnClaimResult
import com.brianyeh.justnotes.backend.rtdn.RtdnEventDocumentStore
import com.brianyeh.justnotes.backend.rtdn.RtdnEventMutation
import com.google.cloud.Timestamp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RtdnEventRepositoryTest {
    @Test
    fun claimsOnceAndCompletedDeliveryIsIdempotent() = runBlocking {
        val store = RecordingEventStore()
        val repository = FirestoreRtdnEventRepository(store, ttlDays = 30)

        val first = assertIs<RtdnClaimResult.Claimed>(
            repository.claim(MESSAGE_ID_HASH, NOW, NOW + LEASE_MILLIS),
        )
        assertEquals(1L, first.generation)
        assertIs<RtdnClaimResult.InFlight>(
            repository.claim(MESSAGE_ID_HASH, NOW + 1, NOW + LEASE_MILLIS + 1),
        )
        assertTrue(repository.complete(MESSAGE_ID_HASH, first.generation, NOW + 2, "PLAY_REQUERIED"))
        assertIs<RtdnClaimResult.AlreadyCompleted>(
            repository.claim(MESSAGE_ID_HASH, NOW + 3, NOW + LEASE_MILLIS + 3),
        )
        assertFalse(repository.complete(MESSAGE_ID_HASH, first.generation, NOW + 4, "DUPLICATE"))

        val fields = requireNotNull(store.documents[MESSAGE_ID_HASH])
        assertEquals(1, fields["attemptCount"])
        assertEquals("PLAY_REQUERIED", fields["outcome"])
        assertEquals(NOW + 2, fields["completedAt"])
        assertNull(fields["leaseUntil"])
        assertTrue(fields["expiresAt"] is Timestamp)
        setOf(
            "messageId", "data", "body", "purchaseToken", "purchaseTokenHash",
            "ownerGoogleSub", "email", "authorization", "tokenCiphertext",
        ).forEach { forbidden -> assertFalse(fields.containsKey(forbidden), forbidden) }
    }

    @Test
    fun expiredLeaseCanBeReclaimedWithHigherGeneration() = runBlocking {
        val repository = FirestoreRtdnEventRepository(RecordingEventStore(), ttlDays = 30)
        val first = assertIs<RtdnClaimResult.Claimed>(
            repository.claim(MESSAGE_ID_HASH, NOW, NOW + LEASE_MILLIS),
        )

        val second = assertIs<RtdnClaimResult.Claimed>(
            repository.claim(MESSAGE_ID_HASH, NOW + LEASE_MILLIS, NOW + LEASE_MILLIS * 2),
        )

        assertEquals(first.generation + 1, second.generation)
        assertFalse(repository.complete(MESSAGE_ID_HASH, first.generation, NOW + LEASE_MILLIS + 1, "STALE"))
        assertTrue(repository.complete(MESSAGE_ID_HASH, second.generation, NOW + LEASE_MILLIS + 1, "RECOVERED"))
    }

    @Test
    fun retryReleaseDoesNotCompleteAndRespectsRetryAt() = runBlocking {
        val store = RecordingEventStore()
        val repository = FirestoreRtdnEventRepository(store, ttlDays = 30)
        val first = assertIs<RtdnClaimResult.Claimed>(
            repository.claim(MESSAGE_ID_HASH, NOW, NOW + LEASE_MILLIS),
        )

        assertTrue(
            repository.release(
                messageIdHash = MESSAGE_ID_HASH,
                generation = first.generation,
                retryAt = NOW + RETRY_MILLIS,
                errorCode = "PLAY_UNAVAILABLE",
            ),
        )
        assertIs<RtdnClaimResult.InFlight>(
            repository.claim(MESSAGE_ID_HASH, NOW + RETRY_MILLIS - 1, NOW + RETRY_MILLIS + LEASE_MILLIS),
        )
        val retry = assertIs<RtdnClaimResult.Claimed>(
            repository.claim(MESSAGE_ID_HASH, NOW + RETRY_MILLIS, NOW + RETRY_MILLIS + LEASE_MILLIS),
        )

        assertEquals(first.generation + 1, retry.generation)
        val fields = requireNotNull(store.documents[MESSAGE_ID_HASH])
        assertEquals(2, fields["attemptCount"])
        assertNull(fields["completedAt"])
        assertNull(fields["errorCode"])
    }

    @Test
    fun concurrentDuplicateClaimsHaveExactlyOneWinner() = runBlocking {
        val repository = FirestoreRtdnEventRepository(RecordingEventStore(), ttlDays = 30)

        val results = coroutineScope {
            (1..20).map {
                async { repository.claim(MESSAGE_ID_HASH, NOW, NOW + LEASE_MILLIS) }
            }.awaitAll()
        }

        assertEquals(1, results.count { it is RtdnClaimResult.Claimed })
        assertEquals(19, results.count { it is RtdnClaimResult.InFlight })
    }

    @Test
    fun rejectsUnsafeDocumentIdsAndInvalidHorizons() = runBlocking {
        val repository = FirestoreRtdnEventRepository(RecordingEventStore(), ttlDays = 30)

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            repository.claim("raw/message/id", NOW, NOW + LEASE_MILLIS)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            repository.claim(MESSAGE_ID_HASH, NOW, NOW)
        }
        Unit
    }

    private class RecordingEventStore : RtdnEventDocumentStore {
        val documents = linkedMapOf<String, Map<String, Any?>>()
        private val mutex = Mutex()

        override suspend fun <T> transact(
            documentId: String,
            operation: (Map<String, Any?>?) -> RtdnEventMutation<T>,
        ): T = mutex.withLock {
            val mutation = operation(documents[documentId])
            mutation.fields?.let { fields -> documents[documentId] = fields.toMap() }
            mutation.result
        }
    }

    companion object {
        private const val NOW = 1_783_814_400_000L
        private const val LEASE_MILLIS = 60_000L
        private const val RETRY_MILLIS = 10_000L
        private const val MESSAGE_ID_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
