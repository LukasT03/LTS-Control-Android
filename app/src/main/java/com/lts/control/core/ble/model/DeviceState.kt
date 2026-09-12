package com.lts.control.core.ble.model

import androidx.annotation.StringRes
import com.lts.control.R

enum class DeviceState {
    IDLE,        // 'I'
    RUNNING,     // 'R'
    PAUSED,      // 'P'
    AUTO_STOP,   // 'A' (Torque Stop)
    UPDATING,    // 'U'
    DONE,        // 'D'
    ERROR;       // Fallback

    companion object {
        fun fromFirmwareChar(c: Char?): DeviceState = when (c) {
            'I' -> IDLE
            'R' -> RUNNING
            'P' -> PAUSED
            'A' -> AUTO_STOP
            'U' -> UPDATING
            'D' -> DONE
            else -> ERROR
        }

        // Tolerant gegen String-States (falls iOS/Alt-Frontend irgendwo Text liefert)
        fun fromLooseString(s: String?): DeviceState = when (s?.lowercase()) {
            "idle" -> IDLE
            "running" -> RUNNING
            "paused" -> PAUSED
            "autostop", "auto_stop", "auto-stop", "autostop ", "auto stop" -> AUTO_STOP
            "updating" -> UPDATING
            "done", "finished" -> DONE
            "error", "err" -> ERROR
            else -> IDLE
        }
    }
}

/** Settings/status capsule wording. */
@StringRes
fun DeviceState.labelRes(): Int = when (this) {
    DeviceState.IDLE      -> R.string.state_idle
    DeviceState.RUNNING   -> R.string.state_running
    DeviceState.PAUSED    -> R.string.state_paused
    DeviceState.AUTO_STOP -> R.string.state_auto_stop
    DeviceState.UPDATING  -> R.string.state_updating
    DeviceState.DONE      -> R.string.state_done
    DeviceState.ERROR     -> R.string.state_error
}

/** Home screen wording (intentionally different from the settings capsule). */
@StringRes
fun DeviceState.homeLabelRes(): Int = when (this) {
    DeviceState.IDLE      -> R.string.home_state_idle
    DeviceState.RUNNING   -> R.string.home_state_running
    DeviceState.PAUSED    -> R.string.home_state_paused
    DeviceState.AUTO_STOP -> R.string.home_state_auto_stop
    DeviceState.UPDATING  -> R.string.home_state_updating
    DeviceState.DONE      -> R.string.home_state_done
    DeviceState.ERROR     -> R.string.home_state_error
}