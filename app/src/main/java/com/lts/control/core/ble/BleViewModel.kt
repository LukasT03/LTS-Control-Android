package com.lts.control.core.ble

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lts.control.core.ble.model.DeviceState
import com.lts.control.core.ble.model.StatusPayload
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.lts.control.core.ble.model.IncomingMessage

class BleViewModel(app: Application) : AndroidViewModel(app) {
    private val mgr = BleManager(app)

    val connection: StateFlow<BleManager.ConnectionState> = mgr.connectionState
    val status: StateFlow<StatusPayload?> = mgr.status
    val messages: SharedFlow<IncomingMessage> = mgr.messages
    val progressBarValue: StateFlow<Float> = mgr.status
        .map { s ->
            val p = s?.progressPercent ?: 0f
            val state = s?.deviceState ?: DeviceState.IDLE
            // UX-Regel aus deiner iOS-Logik: bei IDLE immer 0 %
            if (state == DeviceState.IDLE) 0f else p.coerceIn(0f, 100f)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    val remainingSeconds: StateFlow<Int> = mgr.status
        .map { it?.remainingSeconds ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val deviceState: StateFlow<DeviceState> = mgr.status
        .map { it?.deviceState ?: DeviceState.IDLE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DeviceState.IDLE)

    val highSpeed: StateFlow<Boolean> = mgr.status
        .map { it?.highSpeed ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // API
    fun startScan() = mgr.startScan()
    fun stopScan() = mgr.stopScan()
    fun disconnect() = mgr.disconnect()

    fun start() = mgr.start()
    fun stop() = mgr.stop()
    fun pause() = mgr.pause()
    fun ota() = mgr.ota()
    fun wifiScan() = mgr.wifiScan()
    fun wifiConnect(ssid: String, pass: String) = mgr.wifiConnect(ssid, pass)

    fun setSpeed(p: Int) = mgr.setSpeedPercent(p)
    fun setTargetWeight(m: Int) = mgr.setTargetWeight(m)
    fun setTorque(l: Int) = mgr.setTorque(l)
    fun setDirection(d: Int) = mgr.setDirection(d)
    fun setLed(b: Int) = mgr.setLed(b)
    fun setFilamentSensor(on: Boolean) = mgr.setFilamentSensor(on)
    fun setMotorStrength(p: Int) = mgr.setMotorStrength(p)
    fun setJingle(j: Int) = mgr.setJingle(j)
    fun setDurationAt80(sec: Int) = mgr.setDurationAt80(sec)
    fun setHighSpeed(on: Boolean) = mgr.setHighSpeed(on)
    fun setFanSpeed(p: Int) = mgr.setFanSpeed(p)
    fun setFanAlways(on: Boolean) = mgr.setFanAlwaysOn(on)

    override fun onCleared() {
        super.onCleared()
        mgr.clear()
    }
}