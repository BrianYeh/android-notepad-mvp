package com.example.notepad.data

import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleDriveSyncErrorTest {
    @Test
    fun unknownHostMapsToSafeNetworkUnavailableError() {
        val rawMessage =
            "Unable to resolve host \"www.googleapis.com\": No address associated with hostname"

        val error = UnknownHostException(rawMessage).toDriveSyncError()

        assertEquals(SyncErrorCode.NetworkUnavailable, error.code)
        assertEquals("Network unavailable.", error.message)
        assertEquals(false, error.message.contains("googleapis.com"))
        assertEquals(false, error.message.contains(rawMessage))
    }

    @Test
    fun cancelledSyncRestoresAConnectedAccountToRetryableIdleState() {
        val networkError = SyncError(
            code = SyncErrorCode.NetworkUnavailable,
            message = "raw network detail",
        )
        val syncing = SyncMetadata(
            deviceId = "device",
            deviceName = "Pixel",
            accountEmail = "person@example.com",
            status = SyncStatus.Syncing,
            lastError = networkError,
        )

        val recovered = syncing.afterSyncCancellation()

        assertEquals(SyncStatus.Idle, recovered.status)
        assertEquals(null, recovered.lastError)
        assertEquals("person@example.com", recovered.accountEmail)
    }
}
