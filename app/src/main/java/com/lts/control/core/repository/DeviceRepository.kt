package com.lts.control.core.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.lts.control.core.ble.BleManager
import com.lts.control.core.ble.model.*
import com.lts.control.core.notifications.LocalNotificationManager
import com.lts.control.core.permissions.BlePermissions
import com.lts.control.core.service.ConnectedDeviceService
import com.lts.control.core.state.*
import com.lts.control.core.util.FirmwareVersionUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DeviceRepository private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    val bleManager = BleManager(context)
    val preferencesRepo = AppPreferencesRepository(context)
    private val notificationManager = LocalNotificationManager(context)

    // Guards
    val echoGuard = EchoGuard()
    val speedEchoGuard = SpeedEchoGuard()

    // State
    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    var didInitialSync: Boolean = false
        private set

    private val temperatureHistory = mutableListOf<Int>()
    private var wifiStatusHoldUntil: Long = 0L
    private var pendingWiFiConnected: Boolean? = null
    private var previousState: DeviceState = DeviceState.IDLE

    // Wi-Fi scan & connect states
    private val _availableSsids = MutableStateFlow<List<String>>(emptyList())
    val availableSsids: StateFlow<List<String>> = _availableSsids.asStateFlow()

    private val _isScanningSsids = MutableStateFlow(false)
    val isScanningSsids: StateFlow<Boolean> = _isScanningSsids.asStateFlow()

    private val _isConnectingWifi = MutableStateFlow(false)
    val isConnectingWifi: StateFlow<Boolean> = _isConnectingWifi.asStateFlow()

    private val _wifiConnectSuccess = MutableSharedFlow<Boolean>()
    val wifiConnectSuccess: SharedFlow<Boolean> = _wifiConnectSuccess.asSharedFlow()

    private var wifiScanTimeoutJob: Job? = null
    private var wifiConnectTimeoutJob: Job? = null

    companion object {
        @Volatile
        private var INSTANCE: DeviceRepository? = null

        fun getInstance(context: Context): DeviceRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DeviceRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun initialize() {
        // Collect initial preferences to populate defaults and remembered device
        scope.launch {
            preferencesRepo.initialDeviceStatusFlow.first().let { initial ->
                _status.update { current ->
                    current.copy(
                        speedPercent = initial.speedPercent,
                        jingleStyle = initial.jingleStyle,
                        ledBrightness = initial.ledBrightness,
                        fanSpeed = initial.fanSpeed,
                        fanAlways = initial.fanAlways,
                        useFilamentSensor = initial.useFilamentSensor,
                        directionReversed = initial.directionReversed,
                        motorStrength = initial.motorStrength,
                        torqueLimit = initial.torqueLimit,
                        highSpeed = initial.highSpeed,
                        durationAt80 = initial.durationAt80,
                        targetWeight = initial.targetWeight,
                        servoAngleR = initial.servoAngleR,
                        servoAngleL = initial.servoAngleL,
                        servoStepMm = initial.servoStepMm,
                        servoHome = initial.servoHome,
                        firmwareVersion = initial.firmwareVersion,
                        boardVersion = initial.boardVersion,
                        boardVariant = initial.boardVariant,
                        temperatureInFahrenheit = initial.temperatureInFahrenheit,
                        notificationsEnabled = initial.notificationsEnabled,
                        ongoingNotificationEnabled = initial.ongoingNotificationEnabled
                    )
                }
            }

            // Auto-reconnect if device is saved and permissions are present
            val savedAddress = preferencesRepo.lastDeviceAddressFlow.first()
            if (!savedAddress.isNullOrEmpty() &&
                BlePermissions.hasPermissions(context) &&
                BlePermissions.isBluetoothEnabled(context)
            ) {
                bleManager.connectByAddress(savedAddress)
            }
        }

        // Observe connection state
        scope.launch {
            bleManager.connectionState.collect { state ->
                when (state) {
                    is BleManager.ConnectionState.Ready -> {
                        _isConnected.value = true
                        wifiStatusHoldUntil = System.currentTimeMillis() + 2000L
                        _status.update { it.copy(wifiConnected = false) }

                        // Schedule release of Wi-Fi hold after 2 seconds
                        scope.launch {
                            delay(2000)
                            wifiStatusHoldUntil = 0L
                            if (pendingWiFiConnected != null) {
                                _status.update { it.copy(wifiConnected = pendingWiFiConnected) }
                                pendingWiFiConnected = null
                            }
                        }
                    }
                    is BleManager.ConnectionState.Disconnected,
                    is BleManager.ConnectionState.Error -> {
                        _isConnected.value = false
                        didInitialSync = false
                        previousState = DeviceState.IDLE
                        temperatureHistory.clear()
                        _availableSsids.value = emptyList()
                        _isScanningSsids.value = false
                        _isConnectingWifi.value = false
                        _status.update {
                            it.copy(
                                hasFilament = false,
                                progress = 0f,
                                remainingTime = null,
                                chipTemperature = null,
                                wifiSsid = null,
                                wifiConnected = null,
                                wifiConnectionResult = null,
                                isFanOn = false,
                                deviceState = DeviceState.IDLE
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        // Observe board name
        scope.launch {
            bleManager.boardName.collect { name ->
                if (!name.isNullOrEmpty()) {
                    _status.update { it.copy(boardVersion = name) }
                    persistCurrentSettings()
                }
            }
        }

        // Observe incoming messages
        scope.launch {
            bleManager.messages.collect { msg ->
                when (msg) {
                    is IncomingMessage.Status -> handleStatusPayload(msg.payload)
                    is IncomingMessage.WifiScan -> {
                        wifiScanTimeoutJob?.cancel()
                        _isScanningSsids.value = false
                        _availableSsids.value = msg.ssids
                    }
                    is IncomingMessage.WifiConnectResult -> {
                        wifiConnectTimeoutJob?.cancel()
                        _isConnectingWifi.value = false
                        _status.update { it.copy(wifiConnectionResult = msg.ok) }
                        _wifiConnectSuccess.emit(msg.ok)
                    }
                    is IncomingMessage.OtaResult -> {
                        _status.update { it.copy(otaSuccess = msg.ok) }
                    }
                    is IncomingMessage.Raw -> {}
                }
            }
        }
    }

    private fun handleStatusPayload(payload: StatusPayload) {
        if (!didInitialSync) {
            didInitialSync = true
        }

        val currentState = _status.value.deviceState
        val newState = payload.deviceState

        // Handle notifications
        if (newState != currentState) {
            if (newState == DeviceState.DONE) {
                notificationManager.notifyDone(_status.value.notificationsEnabled)
            } else if (newState == DeviceState.AUTO_STOP && previousState == DeviceState.RUNNING) {
                notificationManager.notifyAutoStop(_status.value.notificationsEnabled)
            }
            previousState = currentState
        }

        // Handle temperature smoothing (rolling mean of 10)
        val smoothedTemp: Int? = payload.chipTemperatureC?.let { temp ->
            temperatureHistory.add(temp)
            if (temperatureHistory.size > 10) temperatureHistory.removeAt(0)
            (temperatureHistory.sum().toDouble() / temperatureHistory.size).toInt()
        } ?: _status.value.chipTemperature

        // Handle Wi-Fi hold
        val now = System.currentTimeMillis()
        val incomingWifiOk = payload.wifiConnected
        val effectiveWifiOk: Boolean? = if (now < wifiStatusHoldUntil) {
            pendingWiFiConnected = incomingWifiOk
            false
        } else {
            incomingWifiOk ?: _status.value.wifiConnected
        }

        // Check firmware <= 1.1.2 rule
        val fw = payload.firmwareVersion ?: _status.value.firmwareVersion
        val isOldFw = fw != null && FirmwareVersionUtils.isFirmwareVersionAtMost(fw, 1, 1, 2)

        _status.update { s ->
            var updated = s.copy(
                deviceState = newState,
                progress = payload.progressPercent ?: s.progress,
                remainingTime = payload.remainingSeconds ?: s.remainingTime,
                hasFilament = payload.hasFilament ?: s.hasFilament,
                chipTemperature = smoothedTemp,
                wifiSsid = payload.wifiSsid ?: s.wifiSsid,
                wifiConnected = effectiveWifiOk,
                isFanOn = payload.fanOn ?: s.isFanOn,
                firmwareVersion = fw
            )

            // Echo suppression & Settings updates
            payload.speedPercent?.let { spd ->
                if (speedEchoGuard.shouldAcceptRemote(spd)) {
                    updated = updated.copy(speedPercent = spd)
                }
            }
            payload.jingleStyle?.let { jin ->
                if (echoGuard.shouldAcceptRemote("jingleStyle")) {
                    updated = updated.copy(jingleStyle = jin)
                }
            }
            payload.ledBrightness?.let { led ->
                if (echoGuard.shouldAcceptRemote("ledBrightness")) {
                    updated = updated.copy(ledBrightness = led)
                }
            }
            payload.fanSpeed?.let { fan ->
                if (echoGuard.shouldAcceptRemote("fanSpeed")) {
                    updated = updated.copy(fanSpeed = fan)
                }
            }
            payload.fanAlwaysOn?.let { alw ->
                if (echoGuard.shouldAcceptRemote("fanAlways")) {
                    updated = updated.copy(fanAlways = alw)
                }
            }
            payload.useFilamentSensor?.let { use ->
                if (echoGuard.shouldAcceptRemote("useFilamentSensor")) {
                    updated = updated.copy(useFilamentSensor = use)
                }
            }
            payload.motorDirection?.let { dir ->
                if (echoGuard.shouldAcceptRemote("directionReversed")) {
                    updated = updated.copy(directionReversed = dir == 1)
                }
            }
            payload.motorStrength?.let { pow ->
                if (echoGuard.shouldAcceptRemote("motorStrength")) {
                    updated = updated.copy(motorStrength = pow)
                }
            }
            payload.torqueLimit?.let { trq ->
                if (echoGuard.shouldAcceptRemote("torqueLimit")) {
                    updated = updated.copy(torqueLimit = trq)
                }
            }
            payload.highSpeed?.let { hs ->
                if (echoGuard.shouldAcceptRemote("highSpeed")) {
                    updated = updated.copy(highSpeed = hs)
                }
            }
            payload.durationAt80?.let { dur ->
                if (echoGuard.shouldAcceptRemote("durationAt80")) {
                    updated = updated.copy(durationAt80 = dur)
                }
            }
            payload.targetWeight?.let { wgt ->
                if (echoGuard.shouldAcceptRemote("targetWeight")) {
                    updated = updated.copy(targetWeight = wgt)
                }
            }

            // Servo
            payload.servoAngleR?.let { r ->
                if (echoGuard.shouldAcceptRemote("servoAngleR")) {
                    updated = updated.copy(servoAngleR = r)
                }
            }
            payload.servoAngleL?.let { l ->
                if (echoGuard.shouldAcceptRemote("servoAngleL")) {
                    updated = updated.copy(servoAngleL = l)
                }
            }
            payload.servoStepMm?.let { stp ->
                if (echoGuard.shouldAcceptRemote("servoStepMm")) {
                    updated = updated.copy(servoStepMm = stp)
                }
            }
            payload.servoHome?.let { home ->
                val h = home.uppercase()
                if ((h == "L" || h == "R") && echoGuard.shouldAcceptRemote("servoHome")) {
                    updated = updated.copy(servoHome = h)
                }
            }

            // Board Variant
            if (isOldFw) {
                updated = updated.copy(
                    boardVariant = BoardVariant.UNKNOWN,
                    didReceiveBoardVariant = false
                )
            } else if (payload.boardVariant != null) {
                updated = updated.copy(
                    boardVariant = payload.boardVariant,
                    didReceiveBoardVariant = true
                )
            }

            updated
        }

        // Sync Foreground Service
        syncForegroundService(newState)
        persistCurrentSettings()
    }

    private fun syncForegroundService(state: DeviceState) {
        if (_status.value.ongoingNotificationEnabled && state != DeviceState.IDLE && _isConnected.value) {
            val intent = Intent(context, ConnectedDeviceService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun persistCurrentSettings() {
        scope.launch {
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    // --- Actions ---
    fun start() = scope.launch { bleManager.sendBytes(Commands.start()) }
    fun stop() = scope.launch { bleManager.sendBytes(Commands.stop()) }
    fun pause() = scope.launch { bleManager.sendBytes(Commands.pause()) }
    fun ota() = scope.launch { bleManager.sendBytes(Commands.ota()) }

    fun clearOtaSuccess() {
        _status.update { it.copy(otaSuccess = null) }
    }

    // Wi-Fi Flow (§2.1)
    fun triggerWifiScan() {
        _isScanningSsids.value = true
        _availableSsids.value = emptyList()
        scope.launch {
            bleManager.sendBytes(Commands.wifiScan())
            wifiScanTimeoutJob?.cancel()
            wifiScanTimeoutJob = launch {
                delay(15_000)
                if (_isScanningSsids.value) {
                    _isScanningSsids.value = false
                }
            }
        }
    }

    fun sendWifiCredentialsAndConnect(ssid: String, pass: String) {
        _isConnectingWifi.value = true
        scope.launch {
            // Packet 1: Credentials
            bleManager.sendBytes(Commands.setWifiCredentials(ssid, pass))
            delay(300)
            // Packet 2: Command
            bleManager.sendBytes(Commands.wifiConnect())

            wifiConnectTimeoutJob?.cancel()
            // Board timeout is 10s. Wait 12s on client before falling back.
            wifiConnectTimeoutJob = launch {
                delay(12_000)
                if (_isConnectingWifi.value) {
                    _isConnectingWifi.value = false
                    _wifiConnectSuccess.emit(false)
                }
            }
        }
    }

    // Connect / Disconnect
    fun connectDevice(discovered: DiscoveredDevice) {
        scope.launch {
            preferencesRepo.setLastDeviceAddress(discovered.address)
            preferencesRepo.setHasCompletedSetup(true)
            if (discovered.inferredVariant != null) {
                _status.update { it.copy(boardVariant = discovered.inferredVariant, didReceiveBoardVariant = true) }
            }
            bleManager.connect(discovered.device)
        }
    }

    fun removeSavedDevice() {
        scope.launch {
            preferencesRepo.setLastDeviceAddress(null)
            bleManager.disconnect()
        }
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    // Settings Setters (all guarded by didInitialSync)
    fun setSpeedPercent(p: Int) {
        val clamped = p.coerceIn(50, 100)
        speedEchoGuard.markLocal(clamped)
        _status.update { it.copy(speedPercent = clamped) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setSpeedPercent(clamped))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    fun setJingleStyle(style: Int) {
        val clamped = style.coerceIn(0, 3)
        echoGuard.markLocal("jingleStyle")
        _status.update { it.copy(jingleStyle = clamped) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setJingleStyle(clamped))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    fun setLedBrightness(brightness: Int) {
        val clamped = brightness.coerceIn(0, 100)
        echoGuard.markLocal("ledBrightness")
        _status.update { it.copy(ledBrightness = clamped) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setLed(clamped))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    fun setFanSpeed(speed: Int) {
        val clamped = speed.coerceIn(10, 100)
        echoGuard.markLocal("fanSpeed")
        _status.update { it.copy(fanSpeed = clamped) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setFanSpeed(clamped))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    fun setFanAlwaysOn(always: Boolean) {
        echoGuard.markLocal("fanAlways")
        _status.update { it.copy(fanAlways = always) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setFanAlwaysOn(always))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    fun setUseFilamentSensor(use: Boolean) {
        echoGuard.markLocal("useFilamentSensor")
        _status.update { it.copy(useFilamentSensor = use) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setUseFilamentSensor(use))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    fun setDirectionReversed(rev: Boolean) {
        echoGuard.markLocal("directionReversed")
        _status.update { it.copy(directionReversed = rev) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setDirection(if (rev) 1 else 0))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    fun setMotorStrength(str: Int) {
        val clamped = str.coerceIn(80, 120)
        echoGuard.markLocal("motorStrength")
        _status.update { it.copy(motorStrength = clamped) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setMotorStrength(clamped))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    fun setTorqueLimit(trq: Int) {
        val clamped = trq.coerceIn(0, 3)
        echoGuard.markLocal("torqueLimit")
        _status.update { it.copy(torqueLimit = clamped) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setTorqueLimit(clamped))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    fun setHighSpeed(hs: Boolean) {
        echoGuard.markLocal("highSpeed")
        _status.update { it.copy(highSpeed = hs) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setHighSpeedMode(hs))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    fun setDurationAt80(sec: Int) {
        echoGuard.markLocal("durationAt80")
        _status.update { it.copy(durationAt80 = sec) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setDurationAt80(sec))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    fun setTargetWeight(wgt: Int) {
        val clamped = wgt.coerceIn(0, 3)
        echoGuard.markLocal("targetWeight")
        _status.update { it.copy(targetWeight = clamped) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setTargetWeight(clamped))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    // Board Variant
    fun setBoardVariant(variant: BoardVariant) {
        echoGuard.markLocal("boardVariant")
        _status.update { it.copy(boardVariant = variant, didReceiveBoardVariant = true) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setBoardVariant(variant))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    // Servo
    fun setServoAngleR(angle: Int) {
        val clamped = angle.coerceIn(0, 180)
        echoGuard.markLocal("servoAngleR")
        _status.update { it.copy(servoAngleR = clamped) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setServoAngleR(clamped))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    fun setServoAngleL(angle: Int) {
        val clamped = angle.coerceIn(0, 180)
        echoGuard.markLocal("servoAngleL")
        _status.update { it.copy(servoAngleL = clamped) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setServoAngleL(clamped))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    fun setServoStepMm(mm: Double) {
        val clamped = mm.coerceIn(0.05, 20.0)
        echoGuard.markLocal("servoStepMm")
        _status.update { it.copy(servoStepMm = clamped) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setServoStepMm(clamped))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    fun setServoHome(side: String) {
        val s = side.uppercase()
        if (s != "L" && s != "R") return
        echoGuard.markLocal("servoHome")
        _status.update { it.copy(servoHome = s) }
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.setServoHome(s))
            preferencesRepo.saveDeviceSettings(_status.value)
        }
    }

    fun servoGoto(target: String) {
        scope.launch {
            if (!didInitialSync) return@launch
            bleManager.sendBytes(Commands.servoGoto(target))
        }
    }

    // App Preferences
    fun setTemperatureInFahrenheit(isFahrenheit: Boolean) {
        _status.update { it.copy(temperatureInFahrenheit = isFahrenheit) }
        scope.launch { preferencesRepo.setTemperatureUnit(isFahrenheit) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _status.update { it.copy(notificationsEnabled = enabled) }
        scope.launch { preferencesRepo.setNotificationsEnabled(enabled) }
    }

    fun setOngoingNotificationEnabled(enabled: Boolean) {
        _status.update { it.copy(ongoingNotificationEnabled = enabled) }
        scope.launch { preferencesRepo.setOngoingNotificationEnabled(enabled) }
    }
}
