package com.example.notepad.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DrawingSaveEditGateTest {
    @Test
    fun saveCommitSectionBlocksEditVersionMutationUntilCommitCompletes() {
        val gate = DrawingSaveEditGate()
        val expectedVersion = gate.currentEditVersion()
        val commitStarted = CountDownLatch(1)
        val releaseCommit = CountDownLatch(1)
        val editAttemptStarted = CountDownLatch(1)
        val editFinished = CountDownLatch(1)

        val saveThread = Thread {
            gate.withSaveCommitSection {
                assertTrue(gate.isCurrent(expectedVersion))
                commitStarted.countDown()
                assertTrue(releaseCommit.await(5, TimeUnit.SECONDS))
                assertTrue(gate.isCurrent(expectedVersion))
            }
        }
        saveThread.start()
        assertTrue(commitStarted.await(5, TimeUnit.SECONDS))

        val editThread = Thread {
            editAttemptStarted.countDown()
            gate.markEdited()
            editFinished.countDown()
        }
        editThread.start()
        assertTrue(editAttemptStarted.await(5, TimeUnit.SECONDS))
        assertFalse(editFinished.await(100, TimeUnit.MILLISECONDS))

        releaseCommit.countDown()
        saveThread.join(5_000)
        editThread.join(5_000)

        assertFalse(saveThread.isAlive)
        assertFalse(editThread.isAlive)
        assertEquals(expectedVersion + 1, gate.currentEditVersion())
    }
}
