@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package com.lts.control.core.ble.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * JSON vom ESP32 (Notification auf NOTIFY-Characteristic).
 * Feldnamen gemäß Firmware: STAT, HAS_FIL, USE_FIL, PROG, REM, TEMP, WIFI_SSID, WIFI_OK, FW, OTA_OK, ...
 * Quelle: ESP32 Sketch sendStatus() u.a. Helfer.
 */
@Serializable
data class StatusPayload(
    // Primäre Felder
    @SerialName("STAT") val stat: String? = null,      // z.B. "I","R","P","A","U","D"
    @SerialName("HAS_FIL") val hasFilament: Boolean? = null,
    @SerialName("USE_FIL") val useFilamentSensor: Boolean? = null,
    @SerialName("PROG") val progressPercent: Float? = null, // 0..100
    @SerialName("REM") val remainingSeconds: Int? = null,
    @SerialName("TEMP") val chipTemperatureC: Int? = null,
    @SerialName("WIFI_SSID") val wifiSsid: String? = null,
    @SerialName("WIFI_OK") val wifiConnected: Boolean? = null,
    @SerialName("FW") val firmwareVersion: String? = null,
    @SerialName("OTA_OK") val otaOk: Boolean? = null,

    // Settings Echo
    @SerialName("SPD") val speedPercent: Int? = null,
    @SerialName("JIN") val jingleStyle: Int? = null,
    @SerialName("LED") val ledBrightness: Int? = null,
    @SerialName("DIR") val motorDirection: Int? = null,
    @SerialName("POW") val motorStrength: Int? = null,
    @SerialName("TRQ") val torqueLimit: Int? = null,   // 0..3
    @SerialName("WGT") val targetWeight: Int? = null,  // 0..3
    @SerialName("DUR") val durationAt80: Int? = null, // Sekunden
    @SerialName("HS")  val highSpeed: Boolean? = null,

    // Fan
    @SerialName("FAN_SPD") val fanSpeed: Int? = null,
    @SerialName("FAN_ON") val fanOn: Boolean? = null,
    @SerialName("FAN_ALW") val fanAlwaysOn: Boolean? = null,

    // Event-spezifische Felder (WiFi/OTA result etc.)
    @SerialName("SSID_LIST") val ssidList: List<String>? = null,
    @SerialName("WIFI_CONN_RESULT") val wifiConnectResult: Boolean? = null
) {
    val deviceState: DeviceState
        get() = when {
            stat?.length == 1 -> DeviceState.fromFirmwareChar(stat.first())
            else -> DeviceState.fromLooseString(stat)
        }
}