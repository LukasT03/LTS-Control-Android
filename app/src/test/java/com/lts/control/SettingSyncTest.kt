package com.lts.control

import com.lts.control.core.state.SettingSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SettingSyncTest {

    private fun TestScope.newSync() = SettingSync(
        scope = backgroundScope,
        debounce = 400.milliseconds,
        confirmTimeout = 3.seconds,
        timeProvider = { testScheduler.currentTime }
    )

    @Test
    fun rapidEditsSendOnlyTheLastValue() = runTest {
        val sync = newSync()
        val sent = mutableListOf<Int>()
        (1..10).forEach { v ->
            sync.submit("dur", 700 - v * 5) { sent += 700 - v * 5 }
            advanceTimeBy(100)
        }
        advanceTimeBy(400)
        runCurrent()
        assertEquals(listOf(650), sent)
    }

    @Test
    fun staleRemoteValuesAreIgnoredUntilDeviceConfirms() = runTest {
        val sync = newSync()
        assertTrue(sync.acceptRemote("dur", 700))

        sync.submit("dur", 600) {}
        // Before the write goes out, even a long wait must not let the old device value win
        advanceTimeBy(300)
        assertFalse(sync.acceptRemote("dur", 700))

        advanceTimeBy(200)
        runCurrent()
        // Packet generated before the device applied the write
        assertFalse(sync.acceptRemote("dur", 700))
        assertTrue(sync.acceptRemote("dur", 600))
        // Once confirmed, later device changes are accepted again
        assertTrue(sync.acceptRemote("dur", 650))
    }

    @Test
    fun unconfirmedValueFallsBackToDeviceAfterTimeout() = runTest {
        val sync = newSync()
        sync.submit("dur", 5) {}
        advanceTimeBy(500)
        runCurrent()
        assertFalse(sync.acceptRemote("dur", 10))
        advanceTimeBy(3_100)
        assertTrue(sync.acceptRemote("dur", 10))
    }

    @Test
    fun keysAreIndependent() = runTest {
        val sync = newSync()
        sync.submit("led", 50) {}
        assertTrue(sync.acceptRemote("fan", 30))
        assertFalse(sync.acceptRemote("led", 40))
    }
}
