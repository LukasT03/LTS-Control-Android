package com.lts.control.core.state

import com.lts.control.core.ble.model.BoardVariant
import com.lts.control.core.ble.model.DeviceState

data class DeviceStatus(
    // Persisted settings (with defaults matching iOS DeviceStatus.init)
    val speedPercent: Int = 85,
    val jingleStyle: Int = 0,
    val ledBrightness: Int = 50,
    val fanSpeed: Int = 60,
    val fanAlways: Boolean = false,
    val useFilamentSensor: Boolean = true,
    val directionReversed: Boolean = false,
    val motorStrength: Int = 100,
    val torqueLimit: Int = 0,
    val highSpeed: Boolean = false,
    val durationAt80: Int = 895,
    val targetWeight: Int = 0,
    val servoAngleR: Int = 5,
    val servoAngleL: Int = 175,
    val servoStepMm: Double = 1.75,
    val servoHome: String = "R",

    // Live telemetry (non-persisted, cleared on disconnect)
    val hasFilament: Boolean = false,
    val progress: Float = 0f,
    val remainingTime: Int? = null,
    val chipTemperature: Int? = null,
    val wifiSsid: String? = null,
    val wifiConnected: Boolean? = null,
    val wifiConnectionResult: Boolean? = null,
    val otaSuccess: Boolean? = null,
    val isFanOn: Boolean = false,
    val deviceState: DeviceState = DeviceState.IDLE,

    // Persisted identity
    val firmwareVersion: String? = null,
    val boardVersion: String? = null,
    val boardVariant: BoardVariant = BoardVariant.UNKNOWN,
    val didReceiveBoardVariant: Boolean = false,

    // App Preferences
    val temperatureInFahrenheit: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val ongoingNotificationEnabled: Boolean = true
) {
    val isProBoard: Boolean
        get() = boardVariant == BoardVariant.PRO

    fun toBoardTypeLabel(): String? {
        val raw = boardVersion ?: return null
        val isPro = isProBoard
        return when {
            raw.contains("CtrBoard V3") -> "Control Board V3"
            raw.contains("CtrBoard V4") || raw.contains("LTS CB") -> if (isPro) "Control Board (Pro)" else "Control Board"
            raw.contains("LTS DB") -> if (isPro) "Driver Board (Pro)" else "Driver Board"
            raw.contains("esp32 PCB", ignoreCase = true) -> "Driver Board"
            else -> null
        }
    }
}
