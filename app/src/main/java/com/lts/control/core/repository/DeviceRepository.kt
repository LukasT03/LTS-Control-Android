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
import kotlin.math.roundToInt

class DeviceRepository private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    val bleManager = BleManager(context)
    val preferencesRepo = AppPreferencesRepository(context)
    private val notificationManager = LocalNotificationManager(context)

    private val settingSync = SettingSync(scope)

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
            observePersistedSettings()

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
                        _status.update { it.withoutTelemetry() }
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

        val s = _status.value
        var updated = s.copy(
            deviceState = newState,
            progress = payload.progressPercent ?: s.progress,
            remainingTime = payload.remainingSeconds ?: s.remainingTime,
            hasFilament = payload.hasFilament ?: s.hasFilament,
            chipTemperature = smoothedTemp,
            wifiSsid = payload.wifiSsid ?: s.wifiSsid,
            wifiConnected = effectiveWifiOk,
            isFanOn = payload.fanOn ?: s.isFanOn,
            usbVoltageMv = payload.usbVoltageMv,
            powerGood = payload.powerGood,
            firmwareVersion = fw
        )
            .remote("speedPercent", payload.speedPercent) { copy(speedPercent = it) }
            .remote("jingleStyle", payload.jingleStyle) { copy(jingleStyle = it) }
            .remote("ledBrightness", payload.ledBrightness) { copy(ledBrightness = it) }
            .remote("fanSpeed", payload.fanSpeed) { copy(fanSpeed = it) }
            .remote("fanAlways", payload.fanAlwaysOn) { copy(fanAlways = it) }
            .remote("useFilamentSensor", payload.useFilamentSensor) { copy(useFilamentSensor = it) }
            .remote("directionReversed", payload.motorDirection?.let { it == 1 }) { copy(directionReversed = it) }
            .remote("motorStrength", payload.motorStrength) { copy(motorStrength = it) }
            .remote("torqueLimit", payload.torqueLimit) { copy(torqueLimit = it) }
            .remote("highSpeed", payload.highSpeed) { copy(highSpeed = it) }
            .remote("durationAt80", payload.durationAt80) { copy(durationAt80 = it) }
            .remote("targetWeight", payload.targetWeight) { copy(targetWeight = it) }
            .remote("servoAngleR", payload.servoAngleR) { copy(servoAngleR = it) }
            .remote("servoAngleL", payload.servoAngleL) { copy(servoAngleL = it) }
            .remote("servoStepMm", payload.servoStepMm) { copy(servoStepMm = it) }
            .remote("servoHome", payload.servoHome?.uppercase()?.takeIf { it == "L" || it == "R" }) { copy(servoHome = it) }
            // Status packets are full snapshots, so a missing key means the firmware has no 2A mode
        updated = if (payload.highCurrent == null) updated.copy(highCurrent = null)
        else updated.remote("highCurrent", payload.highCurrent) { copy(highCurrent = it) }

        updated = when {
            isOldFw -> updated.copy(boardVariant = BoardVariant.UNKNOWN, didReceiveBoardVariant = false)
            else -> updated.remote("boardVariant", payload.boardVariant) { copy(boardVariant = it, didReceiveBoardVariant = true) }
        }

        _status.value = updated

        if (newState != currentState) syncForegroundService()
    }

    /** Applies a device-reported setting unless a local edit of [key] is still awaiting confirmation. */
    private fun <T : Any> DeviceStatus.remote(key: String, value: T?, apply: DeviceStatus.(T) -> DeviceStatus): DeviceStatus =
        if (value != null && settingSync.acceptRemote(key, value)) apply(value) else this

    private fun syncForegroundService() {
        val s = _status.value
        if (s.ongoingNotificationEnabled && s.deviceState != DeviceState.IDLE && _isConnected.value) {
            val intent = Intent(context, ConnectedDeviceService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    /** Writes settings to disk only when a persisted field actually changes, not on every status packet. */
    @OptIn(FlowPreview::class)
    private fun observePersistedSettings() {
        scope.launch {
            _status.map { it.withoutTelemetry() }
                .distinctUntilChanged()
                .debounce(500)
                .collect { preferencesRepo.saveDeviceSettings(it) }
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

    /**
     * Applies a setting locally right away; the device write is debounced and only the final value is sent
     * (skipped until the first status packet arrives).
     */
    private fun pushSetting(key: String, value: Any, command: ByteArray, apply: DeviceStatus.() -> DeviceStatus) {
        _status.update { it.apply() }
        settingSync.submit(key, value) {
            if (didInitialSync) bleManager.sendBytes(command)
        }
    }

    fun setSpeedPercent(p: Int) = p.coerceIn(50, 100).let {
        pushSetting("speedPercent", it, Commands.setSpeedPercent(it)) { copy(speedPercent = it) }
    }

    fun setJingleStyle(style: Int) = style.coerceIn(0, 3).let {
        pushSetting("jingleStyle", it, Commands.setJingleStyle(it)) { copy(jingleStyle = it) }
    }

    fun setLedBrightness(brightness: Int) = brightness.coerceIn(0, 100).let {
        pushSetting("ledBrightness", it, Commands.setLed(it)) { copy(ledBrightness = it) }
    }

    fun setFanSpeed(speed: Int) = speed.coerceIn(10, 100).let {
        pushSetting("fanSpeed", it, Commands.setFanSpeed(it)) { copy(fanSpeed = it) }
    }

    fun setFanAlwaysOn(always: Boolean) =
        pushSetting("fanAlways", always, Commands.setFanAlwaysOn(always)) { copy(fanAlways = always) }

    fun setUseFilamentSensor(use: Boolean) =
        pushSetting("useFilamentSensor", use, Commands.setUseFilamentSensor(use)) { copy(useFilamentSensor = use) }

    fun setDirectionReversed(rev: Boolean) =
        pushSetting("directionReversed", rev, Commands.setDirection(if (rev) 1 else 0)) { copy(directionReversed = rev) }

    fun setMotorStrength(str: Int) = str.coerceIn(80, 120).let {
        pushSetting("motorStrength", it, Commands.setMotorStrength(it)) { copy(motorStrength = it) }
    }

    fun setTorqueLimit(trq: Int) = trq.coerceIn(0, 3).let {
        pushSetting("torqueLimit", it, Commands.setTorqueLimit(it)) { copy(torqueLimit = it) }
    }

    fun setHighSpeed(hs: Boolean) =
        pushSetting("highSpeed", hs, Commands.setHighSpeedMode(hs)) { copy(highSpeed = hs) }

    fun setHighCurrent(on: Boolean) =
        pushSetting("highCurrent", on, Commands.setHighCurrentMode(on)) { copy(highCurrent = on) }

    // Firmware accepts 10..20000 s
    fun setDurationAt80(sec: Int) = sec.coerceIn(10, 20000).let {
        pushSetting("durationAt80", it, Commands.setDurationAt80(it)) { copy(durationAt80 = it) }
    }

    fun setTargetWeight(wgt: Int) = wgt.coerceIn(0, 3).let {
        pushSetting("targetWeight", it, Commands.setTargetWeight(it)) { copy(targetWeight = it) }
    }

    fun setBoardVariant(variant: BoardVariant) =
        pushSetting("boardVariant", variant, Commands.setBoardVariant(variant)) {
            copy(boardVariant = variant, didReceiveBoardVariant = true)
        }
    // Servo
    fun setServoAngleR(angle: Int) = angle.coerceIn(0, 180).let {
        pushSetting("servoAngleR", it, Commands.setServoAngleR(it)) { copy(servoAngleR = it) }
    }

    fun setServoAngleL(angle: Int) = angle.coerceIn(0, 180).let {
        pushSetting("servoAngleL", it, Commands.setServoAngleL(it)) { copy(servoAngleL = it) }
    }

    // Rounded so repeated ±0.01 steps don't accumulate float error and can match the device's echo
    fun setServoStepMm(mm: Double) = (mm.coerceIn(0.05, 20.0) * 100).roundToInt().div(100.0).let {
        pushSetting("servoStepMm", it, Commands.setServoStepMm(it)) { copy(servoStepMm = it) }
    }
    fun setServoHome(side: String) {
        val s = side.uppercase()
        if (s != "L" && s != "R") return
        pushSetting("servoHome", s, Commands.setServoHome(s)) { copy(servoHome = s) }
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
        syncForegroundService()
    }
}
