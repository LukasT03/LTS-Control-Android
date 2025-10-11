package com.lts.control.core.ble.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object Commands {
    private val json = Json { encodeDefaults = false }

    fun start(): ByteArray = json.encodeToString(
        buildJsonObject { put("CMD", "START") }
    ).encodeToByteArray()

    fun stop(): ByteArray = json.encodeToString(
        buildJsonObject { put("CMD", "STOP") }
    ).encodeToByteArray()

    fun pause(): ByteArray = json.encodeToString(
        buildJsonObject { put("CMD", "PAUSE") }
    ).encodeToByteArray()

    fun wifiScan(): ByteArray = json.encodeToString(
        buildJsonObject { put("CMD", "WIFI_SCAN") }
    ).encodeToByteArray()

    fun ota(): ByteArray = json.encodeToString(
        buildJsonObject { put("CMD", "OTA") }
    ).encodeToByteArray()

    fun wifiConnect(ssid: String, pass: String): ByteArray = json.encodeToString(
        buildJsonObject {
            put("CMD", "WIFI_CONNECT")
            putJsonObject("SET") {
                put("WIFI_SSID", ssid)
                put("WIFI_PASS", pass)
            }
        }
    ).encodeToByteArray()

    // -------- Settings SET(...) ----------
    fun setDirection(dir: Int) = set { put("DIR", dir) }
    fun setLed(brightness0to100: Int) = set { put("LED", brightness0to100) }
    fun setUseFilamentSensor(enabled: Boolean) = set { put("USE_FIL", if (enabled) 1 else 0) }
    fun setMotorStrength(pct80to120: Int) = set { put("POW", pct80to120) }
    fun setTorqueLimit(level0to3: Int) = set { put("TRQ", level0to3) }
    fun setJingleStyle(style0to3: Int) = set { put("JIN", style0to3) }
    fun setDurationAt80(seconds: Int) = set { put("DUR", seconds) }
    fun setTargetWeight(mode0to3: Int) = set { put("WGT", mode0to3) }
    fun setSpeedPercent(p50to100: Int) = set { put("SPD", p50to100) }
    fun setHighSpeedMode(on: Boolean) = set { put("HS", if (on) 1 else 0) }
    fun setFanSpeed(p0to100: Int) = set { put("FAN_SPD", p0to100) }
    fun setFanAlwaysOn(on: Boolean) = set { put("FAN_ALW", if (on) 1 else 0) }

    private fun set(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): ByteArray {
        val body = buildJsonObject {
            putJsonObject("SET", builder)
        }
        return json.encodeToString(body).encodeToByteArray()
    }
}