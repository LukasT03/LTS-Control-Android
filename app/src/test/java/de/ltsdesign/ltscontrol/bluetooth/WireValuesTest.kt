package de.ltsdesign.ltscontrol.bluetooth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WireValuesTest {
    private val packet = Json.parseToJsonElement(
        """{"STAT":"R","HAS_FIL":true,"DIR":1,"PROG":26.5,"REM":616,"SV_STP":2,"WIFI_SSID":null,"VAR":2}""",
    ).jsonObject

    @Test
    fun readsBooleansFromBoolOrNumber() {
        assertEquals(true, packet["HAS_FIL"].bool)
        assertEquals(true, packet["DIR"].bool)
        assertNull(packet["STAT"].bool)
    }

    @Test
    fun readsNumbersLeniently() {
        assertEquals(26.5, packet["PROG"].double!!, 0.0)
        assertEquals(616, packet["REM"].int)
        assertNull(packet["PROG"].int)
        assertEquals(2.0, packet["SV_STP"].double!!, 0.0)
    }

    @Test
    fun treatsNullAsMissing() {
        assertNull(packet["WIFI_SSID"].string)
        assertNull(packet["WIFI_SSID"].int)
        assertNull(packet["MISSING"].bool)
    }

    @Test
    fun readsStateAndVariant() {
        assertEquals(DeviceState.Running, DeviceState.fromWireCode(packet["STAT"].string))
        assertEquals(BoardVariant.Pro, BoardVariant.fromWire(packet["VAR"]))
        assertEquals(BoardVariant.Standard, BoardVariant.fromWire(Json.parseToJsonElement("\"std\"")))
        assertEquals(BoardVariant.Unknown, BoardVariant.fromWire(null))
    }
}
