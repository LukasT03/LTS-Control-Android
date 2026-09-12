@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package com.lts.control.core.ble.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * JSON from ESP32 (Notification on NOTIFY-Characteristic).
 */
@Serializable
data class StatusPayload(
    // Primary fields
    @SerialName("STAT") val stat: String? = null,
    @SerialName("HAS_FIL") val hasFilament: Boolean? = null,
    @SerialName("USE_FIL") val useFilamentSensor: Boolean? = null,
    @SerialName("PROG") val progressPercent: Float? = null,
    @SerialName("REM") val remainingSeconds: Int? = null,
    @SerialName("TEMP") val chipTemperatureC: Int? = null,
    @SerialName("WIFI_SSID") val wifiSsid: String? = null,
    @SerialName("WIFI_OK") val wifiConnected: Boolean? = null,
    @SerialName("FW") val firmwareVersion: String? = null,
    @SerialName("OTA_OK") val otaOk: Boolean? = null,
    @SerialName("VAR") val boardVariant: BoardVariant? = null,

    // Settings Echo
    @SerialName("SPD") val speedPercent: Int? = null,
    @SerialName("JIN") val jingleStyle: Int? = null,
    @SerialName("LED") val ledBrightness: Int? = null,
    @SerialName("DIR") val motorDirection: Int? = null,
    @SerialName("POW") val motorStrength: Int? = null,
    @SerialName("TRQ") val torqueLimit: Int? = null,
    @SerialName("WGT") val targetWeight: Int? = null,
    @SerialName("DUR") val durationAt80: Int? = null,
    @SerialName("HS") val highSpeed: Boolean? = null,

    // Fan
    @SerialName("FAN_SPD") val fanSpeed: Int? = null,
    @SerialName("FAN_ON") val fanOn: Boolean? = null,
    @SerialName("FAN_ALW") val fanAlwaysOn: Boolean? = null,

    // Servo (Pro only)
    @SerialName("SV_R") val servoAngleR: Int? = null,
    @SerialName("SV_L") val servoAngleL: Int? = null,
    @SerialName("SV_STP") val servoStepMm: Double? = null,
    @SerialName("SV_HOME") val servoHome: String? = null,

    // Event-specific fields
    @SerialName("SSID_LIST") val ssidList: List<String>? = null,
    @SerialName("WIFI_CONN_RESULT") val wifiConnectResult: Boolean? = null
) {
    val deviceState: DeviceState
        get() = when {
            stat?.length == 1 -> DeviceState.fromFirmwareChar(stat.first())
            else -> DeviceState.fromLooseString(stat)
        }
}
