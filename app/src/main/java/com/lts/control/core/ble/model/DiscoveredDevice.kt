package com.lts.control.core.ble.model

import android.bluetooth.BluetoothDevice

data class DiscoveredDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
    val inferredVariant: BoardVariant?
)
