package com.lts.control.core.ble

import java.util.UUID

object BleConstants {
    // Aus ESP32-Sketch:
    // SERVICE_UUID              "9E05D06D-68A7-4E1F-A503-AE26713AC101"
    // CHARACTERISTIC_UUID       "7CB2F1B4-7E3F-43D2-8C92-DF58C9A7B1A8"
    val SERVICE_UUID: UUID = UUID.fromString("9E05D06D-68A7-4E1F-A503-AE26713AC101")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("7CB2F1B4-7E3F-43D2-8C92-DF58C9A7B1A8")

    const val ADVERTISED_NAME = "esp32 PCB"

    // BLE timeouts & intervals
    const val SCAN_MS: Long = 12_000
    const val RECONNECT_DELAY_MS: Long = 2_000
    const val WRITE_TIMEOUT_MS: Long = 3_000
    const val REQUESTED_MTU: Int = 512

    // GATT status
    const val GATT_SUCCESS = 0
}