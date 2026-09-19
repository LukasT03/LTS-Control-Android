package com.lts.control.core.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Decouples local setting edits from the device round trip.
 *
 * Edits are applied locally by the caller and only the last value within [debounce] is sent. Remote
 * reports for a key are ignored until the device reports the sent value back, so stale status packets
 * that were generated before the write landed can't snap the UI back. If the device never confirms
 * (rejected or clamped the value), the remote value wins after [confirmTimeout].
 *
 * Not thread safe: call from a single thread (the main thread).
 */
class SettingSync(
    private val scope: CoroutineScope,
    private val debounce: Duration = 400.milliseconds,
    private val confirmTimeout: Duration = 3.seconds,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private class Pending(val value: Any?) {
        var job: Job? = null
        var sentAt: Long? = null
    }

    private val pending = mutableMapOf<String, Pending>()

    fun submit(key: String, value: Any?, send: suspend () -> Unit) {
        // A write already in flight is left alone; cancelling mid GATT op would desync the op queue
        pending[key]?.takeIf { it.sentAt == null }?.job?.cancel()
        val entry = Pending(value)
        pending[key] = entry
        entry.job = scope.launch {
            delay(debounce)
            entry.sentAt = timeProvider()
            send()
        }
    }

    fun acceptRemote(key: String, remote: Any?): Boolean {
        val entry = pending[key] ?: return true
        val sentAt = entry.sentAt ?: return false
        val settled = remote == entry.value || timeProvider() - sentAt > confirmTimeout.inWholeMilliseconds
        if (settled) pending.remove(key)
        return settled
    }
}
