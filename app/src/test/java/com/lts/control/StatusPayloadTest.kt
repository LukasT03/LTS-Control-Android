package com.lts.control

import com.lts.control.core.ble.model.BoardVariant
import com.lts.control.core.ble.model.DeviceState
import com.lts.control.core.ble.model.IncomingMessage
import com.lts.control.core.ble.model.StatusPayload
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StatusPayloadTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testVerbatimCapturedStatusPacket() {
        // Real verbatim packet captured from board notify:
        val raw = """
            {
                "STAT":"R",
                "HAS_FIL":true,
                "USE_FIL":true,
                "PROG":42.5,
                "REM":320,
                "TEMP":48,
                "WIFI_SSID":"MyHomeWiFi",
                "WIFI_OK":true,
                "OTA_OK":null,
                "FW":"1.2.1",
                "VAR":"PRO",
                "SPD":85,
                "JIN":1,
                "LED":70,
                "DIR":0,
                "POW":100,
                "TRQ":2,
                "WGT":1,
                "DUR":895,
                "HS":false,
                "FAN_SPD":60,
                "FAN_ON":true,
                "FAN_ALW":false,
                "SV_R":5,
                "SV_L":175,
                "SV_STP":1.75,
                "SV_HOME":"R"
            }
        """.trimIndent()

        val payload = json.decodeFromString<StatusPayload>(raw)

        assertEquals(DeviceState.RUNNING, payload.deviceState)
        assertEquals(true, payload.hasFilament)
        assertEquals(true, payload.useFilamentSensor)
        assertEquals(42.5f, payload.progressPercent ?: 0f, 0.01f)
        assertEquals(320, payload.remainingSeconds)
        assertEquals(48, payload.chipTemperatureC)
        assertEquals("MyHomeWiFi", payload.wifiSsid)
        assertEquals(true, payload.wifiConnected)
        assertEquals("1.2.1", payload.firmwareVersion)
        assertEquals(BoardVariant.PRO, payload.boardVariant)
        assertEquals(85, payload.speedPercent)
        assertEquals(1, payload.jingleStyle)
        assertEquals(70, payload.ledBrightness)
        assertEquals(0, payload.motorDirection)
        assertEquals(100, payload.motorStrength)
        assertEquals(2, payload.torqueLimit)
        assertEquals(1, payload.targetWeight)
        assertEquals(895, payload.durationAt80)
        assertEquals(false, payload.highSpeed)
        assertEquals(60, payload.fanSpeed)
        assertEquals(true, payload.fanOn)
        assertEquals(false, payload.fanAlwaysOn)
        assertEquals(5, payload.servoAngleR)
        assertEquals(175, payload.servoAngleL)
        assertEquals(1.75, payload.servoStepMm ?: 0.0, 0.001)
        assertEquals("R", payload.servoHome)
    }

    @Test
    fun testWifiSsidNullWhenDisconnected() {
        val raw = """
            {
                "STAT":"I",
                "WIFI_SSID":null,
                "WIFI_OK":false,
                "OTA_OK":null,
                "FW":"1.2.0"
            }
        """.trimIndent()

        val payload = json.decodeFromString<StatusPayload>(raw)
        assertEquals(DeviceState.IDLE, payload.deviceState)
        assertNull(payload.wifiSsid)
        assertEquals(false, payload.wifiConnected)
    }

    @Test
    fun testBoardVariantVariantsAndIntegers() {
        // String wire values
        val stdJson = """{"VAR":"STD"}"""
        assertEquals(BoardVariant.STANDARD, json.decodeFromString<StatusPayload>(stdJson).boardVariant)

        val proJson = """{"VAR":"PRO"}"""
        assertEquals(BoardVariant.PRO, json.decodeFromString<StatusPayload>(proJson).boardVariant)

        val unkJson = """{"VAR":"UNK"}"""
        assertEquals(BoardVariant.UNKNOWN, json.decodeFromString<StatusPayload>(unkJson).boardVariant)

        // Integer wire values (0 = UNK, 1 = STD, 2 = PRO)
        val int0Json = """{"VAR":0}"""
        assertEquals(BoardVariant.UNKNOWN, json.decodeFromString<StatusPayload>(int0Json).boardVariant)

        val int1Json = """{"VAR":1}"""
        assertEquals(BoardVariant.STANDARD, json.decodeFromString<StatusPayload>(int1Json).boardVariant)

        val int2Json = """{"VAR":2}"""
        assertEquals(BoardVariant.PRO, json.decodeFromString<StatusPayload>(int2Json).boardVariant)
    }

    @Test
    fun testHighCurrentAndPowerFields() {
        val payload = json.decodeFromString<StatusPayload>(
            """{"STAT":"I","HI_CUR":true,"VBUS_MV":12080,"PG":true}"""
        )
        assertEquals(true, payload.highCurrent)
        assertEquals(12080, payload.usbVoltageMv)
        assertEquals(true, payload.powerGood)

        // Older firmware omits the keys entirely
        val old = json.decodeFromString<StatusPayload>("""{"STAT":"I","FW":"1.2.1"}""")
        assertNull(old.highCurrent)
        assertNull(old.usbVoltageMv)
        assertNull(old.powerGood)
    }

    @Test
    fun testForwardCompatibilityIgnoresUnknownKeys() {
        val raw = """
            {
                "STAT":"P",
                "FUTURE_SENSOR_VAL": 12345,
                "FUTURE_FLAG": true
            }
        """.trimIndent()

        val payload = json.decodeFromString<StatusPayload>(raw)
        assertEquals(DeviceState.PAUSED, payload.deviceState)
    }

    @Test
    fun testEventDiscrimination() {
        // 1. Single-key OTA_OK true
        val otaTrue = """{"OTA_OK":true}"""
        val msg1 = IncomingMessage.parse(otaTrue)
        assertTrue("Expected OtaResult", msg1 is IncomingMessage.OtaResult)
        assertTrue((msg1 as IncomingMessage.OtaResult).ok)

        // 2. Single-key OTA_OK false
        val otaFalse = """{"OTA_OK":false}"""
        val msg2 = IncomingMessage.parse(otaFalse)
        assertTrue("Expected OtaResult", msg2 is IncomingMessage.OtaResult)
        assertFalse((msg2 as IncomingMessage.OtaResult).ok)

        // 3. Status packet containing OTA_OK: null alongside other fields MUST NOT be parsed as OtaResult
        val statusWithOtaNull = """{"STAT":"I","OTA_OK":null,"FW":"1.2.0"}"""
        val msg3 = IncomingMessage.parse(statusWithOtaNull)
        assertTrue("Expected Status, not OtaResult", msg3 is IncomingMessage.Status)
        assertEquals(DeviceState.IDLE, (msg3 as IncomingMessage.Status).payload.deviceState)

        // 4. Standalone WIFI_CONN_RESULT
        val wifiConn = """{"WIFI_CONN_RESULT":true}"""
        val msg4 = IncomingMessage.parse(wifiConn)
        assertTrue("Expected WifiConnectResult", msg4 is IncomingMessage.WifiConnectResult)
        assertTrue((msg4 as IncomingMessage.WifiConnectResult).ok)

        // 5. Standalone SSID_LIST
        val ssids = """{"SSID_LIST":["NetworkA","NetworkB"]}"""
        val msg5 = IncomingMessage.parse(ssids)
        assertTrue("Expected WifiScan", msg5 is IncomingMessage.WifiScan)
        assertEquals(listOf("NetworkA", "NetworkB"), (msg5 as IncomingMessage.WifiScan).ssids)
    }
}
