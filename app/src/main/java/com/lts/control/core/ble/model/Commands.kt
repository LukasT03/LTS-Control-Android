package com.lts.control.core.ble.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
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

    fun wifiConnect(): ByteArray = json.encodeToString(
        buildJsonObject { put("CMD", "WIFI_CONNECT") }
    ).encodeToByteArray()

    fun ota(): ByteArray = json.encodeToString(
        buildJsonObject { put("CMD", "OTA") }
    ).encodeToByteArray()

    // -------- Wi-Fi credentials (sent before WIFI_CONNECT) ----------
    fun setWifiCredentials(ssid: String, pass: String): ByteArray = set {
        put("WIFI_SSID", ssid)
        put("WIFI_PASS", pass)
    }

    // -------- Settings SET(...) ----------
    fun setDirection(dir: Int) = set { put("DIR", dir) }
    fun setLed(brightness0to100: Int) = set { put("LED", brightness0to100.coerceIn(0, 100)) }
    fun setUseFilamentSensor(enabled: Boolean) = set { put("USE_FIL", if (enabled) 1 else 0) }
    fun setMotorStrength(pct80to120: Int) = set { put("POW", pct80to120.coerceIn(80, 120)) }
    fun setTorqueLimit(level0to3: Int) = set { put("TRQ", level0to3.coerceIn(0, 3)) }
    fun setJingleStyle(style0to3: Int) = set { put("JIN", style0to3.coerceIn(0, 3)) }
    fun setDurationAt80(seconds: Int) = set { put("DUR", seconds) }
    fun setTargetWeight(mode0to3: Int) = set { put("WGT", mode0to3.coerceIn(0, 3)) }
    fun setSpeedPercent(p50to100: Int) = set { put("SPD", p50to100.coerceIn(50, 100)) }
    fun setHighSpeedMode(on: Boolean) = set { put("HS", if (on) 1 else 0) }
    fun setHighCurrentMode(on: Boolean) = set { put("HI_CUR", if (on) 1 else 0) }
    fun setFanSpeed(p10to100: Int) = set { put("FAN_SPD", p10to100.coerceIn(10, 100)) }
    fun setFanAlwaysOn(on: Boolean) = set { put("FAN_ALW", if (on) 1 else 0) }

    // Board Variant
    fun setBoardVariant(variant: BoardVariant) = set { put("VAR", variant.wireValue) }

    // Servo (Pro only)
    fun setServoAngleR(angle: Int) = set { put("SV_R", angle.coerceIn(0, 180)) }
    fun setServoAngleL(angle: Int) = set { put("SV_L", angle.coerceIn(0, 180)) }
    fun setServoStepMm(mm: Double) = set { put("SV_STP", mm.coerceIn(0.05, 20.0)) }
    fun setServoHome(side: String) = set {
        val s = side.uppercase()
        if (s == "L" || s == "R") put("SV_HOME", s)
    }
    fun servoGoto(target: String) = set {
        val t = target.uppercase()
        if (t == "L" || t == "R" || t == "HOME") put("SV_GOTO", t)
    }

    private fun set(builder: JsonObjectBuilder.() -> Unit): ByteArray {
        val body = buildJsonObject {
            putJsonObject("SET", builder)
        }
        return json.encodeToString(body).encodeToByteArray()
    }
}
