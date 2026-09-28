package com.lts.control

import com.lts.control.core.state.EchoGuard
import com.lts.control.core.state.SpeedEchoGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class EchoGuardTest {

    @Test
    fun testEchoGuardSuppressionWindow() {
        var virtualTime = 1000L
        val guard = EchoGuard(window = 500.milliseconds, timeProvider = { virtualTime })

        // Initial acceptance without local mark
        assertTrue(guard.shouldAcceptRemote("ledBrightness"))

        // Local change marked at t=1000
        guard.markLocal("ledBrightness")

        // t=1200 (200ms elapsed <= 500ms window) -> suppressed
        virtualTime = 1200L
        assertFalse(guard.shouldAcceptRemote("ledBrightness"))

        // Other keys are not suppressed
        assertTrue(guard.shouldAcceptRemote("fanSpeed"))

        // t=1499 (499ms elapsed <= 500ms window) -> still suppressed
        virtualTime = 1499L
        assertFalse(guard.shouldAcceptRemote("ledBrightness"))

        // t=1501 (501ms elapsed > 500ms window) -> accepted
        virtualTime = 1501L
        assertTrue(guard.shouldAcceptRemote("ledBrightness"))
    }

    @Test
    fun testSpeedEchoGuardMatchesExpectedValue() {
        var virtualTime = 1000L
        val guard = SpeedEchoGuard(
            echoWindow = 1200.milliseconds,
            debounceInterval = 120.milliseconds,
            debounceRounds = 2,
            timeProvider = { virtualTime }
        )

        // Local change to 85% at t=1000
        guard.markLocal(85)

        // Stale remote echo of 70% arrives at t=1300 within echo window (300ms <= 1200ms) -> suppressed
        virtualTime = 1300L
        assertFalse(guard.shouldAcceptRemote(70))

        // Remote echo matching expected 85% arrives at t=1500 -> accepted
        virtualTime = 1500L
        assertTrue(guard.shouldAcceptRemote(85))
    }

    @Test
    fun testSpeedEchoGuardDebounceOutsideEchoWindow() {
        var virtualTime = 3000L
        val guard = SpeedEchoGuard(
            echoWindow = 1200.milliseconds,
            debounceInterval = 120.milliseconds,
            debounceRounds = 2,
            timeProvider = { virtualTime }
        )

        // Remote speed 60 arrives at t=3000 (round 1) -> not accepted yet (debounce round 1)
        assertFalse(guard.shouldAcceptRemote(60))

        // Remote speed 60 arrives too fast at t=3050 (< 120ms interval) -> ignored/debounced
        virtualTime = 3050L
        assertFalse(guard.shouldAcceptRemote(60))

        // Remote speed 60 arrives at t=3200 (>= 120ms interval, round 2) -> accepted!
        virtualTime = 3200L
        assertTrue(guard.shouldAcceptRemote(60))
    }
}
