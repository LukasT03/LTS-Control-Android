package com.lts.control.core.state

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class EchoGuard(
    private val window: Duration = 500.milliseconds,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val lastLocalChanges = ConcurrentHashMap<String, Long>()

    fun markLocal(key: String) {
        lastLocalChanges[key] = timeProvider()
    }

    fun shouldAcceptRemote(key: String): Boolean {
        val lastChange = lastLocalChanges[key] ?: return true
        val ageMs = timeProvider() - lastChange
        return ageMs > window.inWholeMilliseconds
    }
}

class SpeedEchoGuard(
    private val echoWindow: Duration = 1200.milliseconds,
    private val debounceInterval: Duration = 120.milliseconds,
    private val debounceRounds: Int = 2,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private var lastLocalChange: Long = 0
    private var expectedSpeed: Int? = null
    private var lastRemoteSpeed: Int? = null
    private var lastRemoteTimestamp: Long = 0
    private var remoteRepeatCount: Int = 0

    fun markLocal(speed: Int) {
        lastLocalChange = timeProvider()
        expectedSpeed = speed
    }

    /**
     * Evaluates whether incoming remote speed should update the state.
     * Returns true if remote speed should be applied, false if suppressed.
     */
    fun shouldAcceptRemote(speed: Int): Boolean {
        val now = timeProvider()
        val ageMs = now - lastLocalChange

        if (ageMs <= echoWindow.inWholeMilliseconds) {
            return if (expectedSpeed == speed) {
                expectedSpeed = null
                lastRemoteSpeed = speed
                lastRemoteTimestamp = now
                remoteRepeatCount = 0
                true
            } else {
                false
            }
        } else {
            if (lastRemoteSpeed == speed) {
                if ((now - lastRemoteTimestamp) >= debounceInterval.inWholeMilliseconds) {
                    remoteRepeatCount++
                    lastRemoteTimestamp = now
                }
            } else {
                lastRemoteSpeed = speed
                lastRemoteTimestamp = now
                remoteRepeatCount = 1
            }

            return if (remoteRepeatCount >= debounceRounds) {
                expectedSpeed = null
                remoteRepeatCount = 0
                true
            } else {
                false
            }
        }
    }
}
