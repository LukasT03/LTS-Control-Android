package com.lts.control.core.ble.model

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