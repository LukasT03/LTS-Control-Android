package com.lts.control.core.ble

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lts.control.core.ble.model.BoardVariant
import com.lts.control.core.ble.model.DiscoveredDevice
import com.lts.control.core.repository.DeviceRepository
import com.lts.control.core.state.DeviceStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class BleViewModel(app: Application) : AndroidViewModel(app) {
    val repo = DeviceRepository.getInstance(app)

    val status: StateFlow<DeviceStatus> = repo.status
    val isConnected: StateFlow<Boolean> = repo.isConnected
    val connectionState: StateFlow<BleManager.ConnectionState> = repo.bleManager.connectionState
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = repo.bleManager.discoveredDevices

    val availableSsids: StateFlow<List<String>> = repo.availableSsids
    val isScanningSsids: StateFlow<Boolean> = repo.isScanningSsids
    val isConnectingWifi: StateFlow<Boolean> = repo.isConnectingWifi
    val wifiConnectSuccess = repo.wifiConnectSuccess

    val hasCompletedSetup: StateFlow<Boolean> = repo.preferencesRepo.hasCompletedSetupFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val lastDeviceAddress: StateFlow<String?> = repo.preferencesRepo.lastDeviceAddressFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Motor commands
    fun start() = repo.start()
    fun stop() = repo.stop()
    fun pause() = repo.pause()
    fun ota() = repo.ota()
    fun clearOtaSuccess() = repo.clearOtaSuccess()

    // Scan & Connect
    fun startScan() = repo.bleManager.startScan()
    fun stopScan() = repo.bleManager.stopScan()
    fun connectDevice(d: DiscoveredDevice) = repo.connectDevice(d)
    fun disconnect() = repo.disconnect()
    fun removeSavedDevice() = repo.removeSavedDevice()

    // Wi-Fi
    fun triggerWifiScan() = repo.triggerWifiScan()
    fun sendWifiCredentialsAndConnect(ssid: String, pass: String) = repo.sendWifiCredentialsAndConnect(ssid, pass)

    // Settings
    fun setSpeed(p: Int) = repo.setSpeedPercent(p)
    fun setJingle(style: Int) = repo.setJingleStyle(style)
    fun setLed(brightness: Int) = repo.setLedBrightness(brightness)
    fun setFanSpeed(speed: Int) = repo.setFanSpeed(speed)
    fun setFanAlways(always: Boolean) = repo.setFanAlwaysOn(always)
    fun setFilamentSensor(use: Boolean) = repo.setUseFilamentSensor(use)
    fun setDirectionReversed(reversed: Boolean) = repo.setDirectionReversed(reversed)
    fun setMotorStrength(strength: Int) = repo.setMotorStrength(strength)
    fun setTorque(level: Int) = repo.setTorqueLimit(level)
    fun setHighSpeed(enabled: Boolean) = repo.setHighSpeed(enabled)
    fun setHighCurrent(enabled: Boolean) = repo.setHighCurrent(enabled)
    fun setDurationAt80(seconds: Int) = repo.setDurationAt80(seconds)
    fun setTargetWeight(weight: Int) = repo.setTargetWeight(weight)

    // Board Variant
    fun setBoardVariant(variant: BoardVariant) = repo.setBoardVariant(variant)

    // Servo (Pro only)
    fun setServoAngleR(angle: Int) = repo.setServoAngleR(angle)
    fun setServoAngleL(angle: Int) = repo.setServoAngleL(angle)
    fun setServoStepMm(step: Double) = repo.setServoStepMm(step)
    fun setServoHome(side: String) = repo.setServoHome(side)
    fun servoGoto(target: String) = repo.servoGoto(target)

    // App Preferences
    fun setTemperatureInFahrenheit(isFahrenheit: Boolean) = repo.setTemperatureInFahrenheit(isFahrenheit)
    fun setNotificationsEnabled(enabled: Boolean) = repo.setNotificationsEnabled(enabled)
    fun setOngoingNotificationEnabled(enabled: Boolean) = repo.setOngoingNotificationEnabled(enabled)
}
