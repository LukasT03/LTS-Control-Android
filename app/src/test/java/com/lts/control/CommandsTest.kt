package com.lts.control

import com.lts.control.core.ble.model.BoardVariant
import com.lts.control.core.ble.model.Commands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CommandsTest {

    private fun utf8(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)

    @Test
    fun testCoreCommands() {
        assertEquals("""{"CMD":"START"}""", utf8(Commands.start()))
        assertEquals("""{"CMD":"STOP"}""", utf8(Commands.stop()))
        assertEquals("""{"CMD":"PAUSE"}""", utf8(Commands.pause()))
        assertEquals("""{"CMD":"OTA"}""", utf8(Commands.ota()))
        assertEquals("""{"CMD":"WIFI_SCAN"}""", utf8(Commands.wifiScan()))
        assertEquals("""{"CMD":"WIFI_CONNECT"}""", utf8(Commands.wifiConnect()))
    }

    @Test
    fun testWifiHandshakeSeparationBugFix() {
        // Section 2.1: WIFI_CONNECT must be emitted separately from WIFI_SSID/WIFI_PASS.
        val credsPacket = utf8(Commands.setWifiCredentials("TestNetwork", "TestPassword"))
        val connectCmd = utf8(Commands.wifiConnect())

        assertEquals("""{"SET":{"WIFI_SSID":"TestNetwork","WIFI_PASS":"TestPassword"}}""", credsPacket)
        assertEquals("""{"CMD":"WIFI_CONNECT"}""", connectCmd)

        // Neither packet should contain both CMD and SET
        assertFalse(credsPacket.contains("CMD"))
        assertFalse(connectCmd.contains("SET"))
    }

    @Test
    fun testSettingCommands() {
        assertEquals("""{"SET":{"SPD":85}}""", utf8(Commands.setSpeedPercent(85)))
        assertEquals("""{"SET":{"JIN":2}}""", utf8(Commands.setJingleStyle(2)))
        assertEquals("""{"SET":{"LED":50}}""", utf8(Commands.setLedBrightness(50)))
        assertEquals("""{"SET":{"USE_FIL":1}}""", utf8(Commands.setUseFilamentSensor(true)))
        assertEquals("""{"SET":{"USE_FIL":0}}""", utf8(Commands.setUseFilamentSensor(false)))
        assertEquals("""{"SET":{"DIR":1}}""", utf8(Commands.setDirectionReversed(true)))
        assertEquals("""{"SET":{"DIR":0}}""", utf8(Commands.setDirectionReversed(false)))
        assertEquals("""{"SET":{"POW":105}}""", utf8(Commands.setMotorStrength(105)))
        assertEquals("""{"SET":{"TRQ":3}}""", utf8(Commands.setTorqueLimit(3)))
        assertEquals("""{"SET":{"WGT":1}}""", utf8(Commands.setTargetWeight(1)))
        assertEquals("""{"SET":{"DUR":900}}""", utf8(Commands.setDurationAt80(900)))
        assertEquals("""{"SET":{"HS":1}}""", utf8(Commands.setHighSpeed(true)))
        assertEquals("""{"SET":{"HS":0}}""", utf8(Commands.setHighSpeed(false)))
        assertEquals("""{"SET":{"FAN_ALW":1}}""", utf8(Commands.setFanAlwaysOn(true)))
        assertEquals("""{"SET":{"FAN_ALW":0}}""", utf8(Commands.setFanAlwaysOn(false)))
    }

    @Test
    fun testFanSpeedFloorGuarded() {
        // Section 5.4: Fan speed floor must guard >= 10 in command layer
        assertEquals("""{"SET":{"FAN_SPD":10}}""", utf8(Commands.setFanSpeed(5)))
        assertEquals("""{"SET":{"FAN_SPD":70}}""", utf8(Commands.setFanSpeed(70)))
    }

    @Test
    fun testBoardVariantCommands() {
        assertEquals("""{"SET":{"VAR":"STD"}}""", utf8(Commands.setBoardVariant(BoardVariant.STANDARD)))
        assertEquals("""{"SET":{"VAR":"PRO"}}""", utf8(Commands.setBoardVariant(BoardVariant.PRO)))
        assertEquals("""{"SET":{"VAR":"UNK"}}""", utf8(Commands.setBoardVariant(BoardVariant.UNKNOWN)))
    }

    @Test
    fun testServoCommands() {
        assertEquals("""{"SET":{"SV_R":15}}""", utf8(Commands.setServoAngleR(15)))
        assertEquals("""{"SET":{"SV_L":165}}""", utf8(Commands.setServoAngleL(165)))
        assertEquals("""{"SET":{"SV_STP":1.75}}""", utf8(Commands.setServoStepMm(1.75)))
        assertEquals("""{"SET":{"SV_HOME":"R"}}""", utf8(Commands.setServoHome("R")))
        assertEquals("""{"SET":{"SV_GOTO":"L"}}""", utf8(Commands.servoGoto("L")))
        assertEquals("""{"SET":{"SV_GOTO":"HOME"}}""", utf8(Commands.servoGoto("HOME")))
    }
}
