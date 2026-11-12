package de.ltsdesign.ltscontrol.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import de.ltsdesign.ltscontrol.BuildConfig
import de.ltsdesign.ltscontrol.data.FirmwareVersion
import java.util.UUID
import kotlin.reflect.KMutableProperty0
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private const val TAG = "Bluetooth"

/**
 * Connection to the Respooler board. The board notifies its status as JSON,
 * the app sends `{"CMD": …}` for commands and `{"SET": {…}}` for settings.
 *
 * All state is changed on the main thread, GATT callbacks are posted there.
 */
@SuppressLint("MissingPermission") // every GATT call is behind hasConnectPermission()
class BleManager(context: Context) {
    companion object {
        val serviceUuid: UUID = UUID.fromString("9E05D06D-68A7-4E1F-A503-AE26713AC101")
        private val characteristicUuid = UUID.fromString("7CB2F1B4-7E3F-43D2-8C92-DF58C9A7B1A8")
        private val clientConfigUuid = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        /** The board sets 512, the status packet doesn't fit into the default MTU of 23. */
        private const val REQUESTED_MTU = 517
        private const val LAST_DEVICE_KEY = "lastDeviceAddress"
    }

    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("bluetooth", Context.MODE_PRIVATE)
    private val adapter: BluetoothAdapter? = this.context.getSystemService(BluetoothManager::class.java)?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    var isConnected by mutableStateOf(false)
        private set
    var deviceState by mutableStateOf(DeviceState.Idle)
        private set
    val status = DeviceStatus(this.context)
    var availableSsids by mutableStateOf<List<String>?>(null)
        private set
    var isScanningForSsids by mutableStateOf(false)
        private set
    var didInitialSync by mutableStateOf(false)
        private set
    var showOtaAlert by mutableStateOf(false)
        private set

    val needsBoardVariantSelection: Boolean
        get() = status.didReceiveBoardVariant && status.boardVariant == BoardVariant.Unknown

    /** The paired board, kept while it is out of range so the connection is restored. */
    var savedDeviceAddress by mutableStateOf(preferences.getString(LAST_DEVICE_KEY, null))
        private set

    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var isDemo = false

    /** Android allows one GATT operation at a time, so writes wait for the previous one. */
    private val writeQueue = ArrayDeque<ByteArray>()
    private var isWriting = false

    private val temperatureHistory = ArrayDeque<Int>()

    /**
     * Time of the last local change per wire key. Board values inside the echo window are ignored
     * so a control doesn't jump back while the board hasn't applied the change yet.
     */
    private val lastLocalChange = mutableMapOf<String, Long>()
    private var expectedSpeedEcho: Int? = null
    private var remoteSpeedCandidate: Pair<Int, Long>? = null

    /** Wi‑Fi is shown as disconnected for 2 s after connecting; the board's value is applied afterwards. */
    private var isHoldingWifiStatus = false
    private var pendingWifiConnected: Boolean? = null

    init {
        ContextCompat.registerReceiver(
            this.context,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    onAdapterStateChanged(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR))
                }
            },
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /** Reconnects to the last board, e.g. on launch or after the permission was granted. */
    fun restoreConnection() {
        savedDeviceAddress?.let { connectToDevice(it) }
    }

    fun connectToDevice(address: String, autoConnect: Boolean = false) {
        if (isDemo) return
        savedDeviceAddress = address
        preferences.edit { putString(LAST_DEVICE_KEY, address) }

        val adapter = adapter
        if (adapter == null || !adapter.isEnabled || !hasConnectPermission()) {
            Log.i(TAG, "Connect $address deferred, Bluetooth off or no permission")
            return
        }
        gatt?.let { current ->
            if (current.device.address == address) return
            current.close()
        }
        Log.i(TAG, "Connect $address (autoConnect $autoConnect)")
        // autoConnect waits without timeout until the board advertises again, like a CoreBluetooth
        // connection request. The first attempt connects directly, which is faster.
        gatt = adapter.getRemoteDevice(address)
            .connectGatt(context, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    /** Disconnects and stops reconnecting, e.g. after the board was removed. */
    fun forgetBoard() {
        Log.i(TAG, "Forget board")
        savedDeviceAddress = null
        preferences.edit { remove(LAST_DEVICE_KEY) }
        mainHandler.removeCallbacksAndMessages(null)
        closeGatt()
        resetLiveState()
        status.firmwareVersion = null
        status.boardVersion = null
        status.save()
    }

    /** Clears everything that is only valid while connected. */
    fun resetLiveState() {
        isConnected = false
        deviceState = DeviceState.Idle
        status.resetLiveValues()
        temperatureHistory.clear()
        pendingWifiConnected = null
        availableSsids = null
        isScanningForSsids = false
        showOtaAlert = status.otaSuccess != null
    }

    private fun closeGatt() {
        gatt?.close()
        gatt = null
        characteristic = null
        writeQueue.clear()
        isWriting = false
    }

    private fun onAdapterStateChanged(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_ON -> restoreConnection()
            BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                if (gatt != null) {
                    closeGatt()
                    resetLiveState()
                }
            }
        }
    }

    /** After a failed attempt it waits a moment, so a board that keeps refusing isn't retried in a tight loop. */
    private fun reconnectLater() {
        mainHandler.postDelayed({ savedDeviceAddress?.let { connectToDevice(it, autoConnect = true) } }, 3_000)
    }

    // region Commands

    fun startMotor() = sendPacket(cmd = "START")
    fun stopMotor() = sendPacket(cmd = "STOP")
    fun pauseMotor() = sendPacket(cmd = "PAUSE")
    fun triggerOtaUpdate() = sendPacket(cmd = "OTA")
    fun triggerWifiConnect() = sendPacket(cmd = "WIFI_CONNECT")
    fun sendWifiSsid(ssid: String) = sendPacket(settings = mapOf("WIFI_SSID" to ssid))
    fun sendWifiPassword(password: String) = sendPacket(settings = mapOf("WIFI_PASS" to password))

    fun triggerWifiScan() {
        isScanningForSsids = true
        sendPacket(cmd = "WIFI_SCAN")
    }

    fun clearOtaStatus() {
        status.otaSuccess = null
        showOtaAlert = false
    }

    private fun sendPacket(cmd: String? = null, settings: Map<String, Any>? = null) {
        val payload = buildJsonObject {
            cmd?.let { put("CMD", JsonPrimitive(it)) }
            settings?.let { values ->
                put("SET", buildJsonObject { values.forEach { (key, value) -> put(key, value.toJson()) } })
            }
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "Send $payload")
        if (characteristic == null) return
        writeQueue.addLast(payload.toString().encodeToByteArray())
        writeNext()
    }

    private fun writeNext() {
        val gatt = gatt ?: return
        val characteristic = characteristic ?: return
        if (isWriting) return
        val data = writeQueue.removeFirstOrNull() ?: return
        isWriting = true
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = data
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
        if (!started) {
            Log.e(TAG, "Write failed to start")
            isWriting = false
        }
    }

    // endregion

    // region Settings

    fun setLed(value: Int) = setSetting("LED", value, status::ledBrightness)
    fun setFanAlways(on: Boolean) = setSetting("FAN_ALW", on, status::fanAlways)
    fun setUseFilamentSensor(on: Boolean) = setSetting("USE_FIL", on, status::useFilamentSensor)
    fun setFilamentLossMs(ms: Int) = setSetting("FIL_T", ms, status::filamentLossMs)
    fun setResumeAfterPowerLoss(on: Boolean) = setSetting("RES", on, status::resumeAfterPowerLoss)
    fun setDirectionReversed(on: Boolean) = setSetting("DIR", on, status::directionReversed)
    fun setMotorStrength(value: Int) = setSetting("POW", value, status::motorStrength)
    fun setTorqueLimit(value: Int) = setSetting("TRQ", value, status::torqueLimit)
    fun setTargetWeight(value: Int) = setSetting("WGT", value, status::targetWeight)
    fun setHighSpeed(on: Boolean) = setSetting("HS", on, status::highSpeed)
    fun setDurationAt85(seconds: Int) = setSetting("DUR", seconds, status::durationAt85)
    fun setJingleStyle(style: Int) = setSetting("JIN", style, status::jingleStyle)
    fun setServoAngleR(angle: Int) = setSetting("SV_R", angle.coerceIn(0, 180), status::servoAngleR)
    fun setServoAngleL(angle: Int) = setSetting("SV_L", angle.coerceIn(0, 180), status::servoAngleL)
    fun setServoStepMm(mm: Double) = setSetting("SV_STP", mm.coerceIn(0.05, 20.0), status::servoStepMm)

    fun setFanSpeed(value: Int) {
        if (value < 10) return
        setSetting("FAN_SPD", value, status::fanSpeed)
    }

    fun setSpeedPercent(percent: Int) {
        if (!didInitialSync) return
        expectedSpeedEcho = percent
        setSetting("SPD", percent, status::speedPercent)
    }

    fun setServoHome(side: String) {
        val home = side.uppercase()
        if (home != "L" && home != "R") return
        setSetting("SV_HOME", home, status::servoHome)
    }

    fun setBoardVariant(variant: BoardVariant) {
        if (!didInitialSync) return
        status.didReceiveBoardVariant = true
        setSetting("VAR", variant, status::boardVariant, wireValue = variant.wireValue)
    }

    fun servoGoto(target: String) {
        val position = target.uppercase()
        if (!didInitialSync || position !in setOf("L", "R", "HOME")) return
        sendPacket(settings = mapOf("SV_GOTO" to position))
    }

    /**
     * The value is shown right away, like the iOS bindings do. It's only sent and cached once the
     * board has reported its status; until then the board's values overwrite it.
     */
    private fun <T : Any> setSetting(key: String, value: T, property: KMutableProperty0<T>, wireValue: Any = value) {
        property.set(value)
        if (!didInitialSync) return
        sendPacket(settings = mapOf(key to wireValue))
        lastLocalChange[key] = SystemClock.elapsedRealtime()
        status.save()
    }

    // endregion

    // region Incoming status

    private fun handleIncoming(data: ByteArray) {
        val dict = runCatching { Json.parseToJsonElement(data.decodeToString()) }.getOrNull() as? JsonObject ?: return
        val ssids = dict["SSID_LIST"] as? JsonArray
        if (ssids != null) {
            isScanningForSsids = false
            availableSsids = ssids.mapNotNull { it.string }
        } else {
            updateStatus(dict["STAT"] as? JsonObject ?: dict)
        }
    }

    /**
     * Most packets contain the full status, but some (e.g. `OTA_OK`, `WIFI_CONN_RESULT`)
     * only a single key, so values are only updated for keys that are present.
     */
    private fun updateStatus(dict: JsonObject) {
        val newState = DeviceState.fromWireCode(dict["STAT"].string)
        if (newState != null && newState != deviceState) {
            deviceState = newState
        }
        if (!isConnected) return
        didInitialSync = true

        dict["HAS_FIL"].bool?.let { status.hasFilament = it }
        dict["PROG"].double?.let { status.progress = it }
        dict["REM"].int?.let { status.remainingTime = it }
        dict["FAN_ON"].bool?.let { status.isFanOn = it }
        dict["WIFI_CONN_RESULT"].bool?.let { status.wifiConnectionResult = it }
        if ("WIFI_SSID" in dict) status.wifiSsid = dict["WIFI_SSID"].string
        dict["WIFI_OK"].bool?.let { ok ->
            if (isHoldingWifiStatus) pendingWifiConnected = ok else status.wifiConnected = ok
        }
        dict["TEMP"].int?.let { temp ->
            // Averaged over the last 10 values, the raw chip temperature jumps around.
            temperatureHistory.addLast(temp)
            if (temperatureHistory.size > 10) temperatureHistory.removeFirst()
            status.chipTemperature = temperatureHistory.sum() / temperatureHistory.size
        }
        dict["OTA_OK"].bool?.let { ok ->
            status.otaSuccess = ok
            showOtaAlert = true
        }
        dict["FW"].string?.let { fw ->
            status.firmwareVersion = fw
            clearVariantIfLegacyFirmware()
        }
        if ("VAR" in dict && !isInEchoWindow("VAR")) {
            status.didReceiveBoardVariant = true
            status.boardVariant = BoardVariant.fromWire(dict["VAR"])
        }
        dict["SPD"].int?.let(::applyRemoteSpeed)
        applySetting(dict, "USE_FIL", status::useFilamentSensor) { it.bool }
        applySetting(dict, "FIL_T", status::filamentLossMs) { it.int }
        applySetting(dict, "RES", status::resumeAfterPowerLoss) { it.bool }
        applySetting(dict, "JIN", status::jingleStyle) { it.int }
        applySetting(dict, "LED", status::ledBrightness) { it.int }
        applySetting(dict, "FAN_SPD", status::fanSpeed) { it.int }
        applySetting(dict, "FAN_ALW", status::fanAlways) { it.bool }
        applySetting(dict, "DIR", status::directionReversed) { it.bool }
        applySetting(dict, "POW", status::motorStrength) { it.int }
        applySetting(dict, "TRQ", status::torqueLimit) { it.int }
        applySetting(dict, "HS", status::highSpeed) { it.bool }
        applySetting(dict, "DUR", status::durationAt85) { it.int }
        applySetting(dict, "WGT", status::targetWeight) { it.int }
        applySetting(dict, "SV_R", status::servoAngleR) { it.int }
        applySetting(dict, "SV_L", status::servoAngleL) { it.int }
        applySetting(dict, "SV_STP", status::servoStepMm) { it.double }
        applySetting(dict, "SV_HOME", status::servoHome) { value ->
            value.string?.uppercase()?.takeIf { it == "L" || it == "R" }
        }

        status.save()
    }

    private fun <T> applySetting(
        dict: JsonObject,
        key: String,
        property: KMutableProperty0<T>,
        read: (JsonElement?) -> T?,
    ) {
        val value = read(dict[key]) ?: return
        if (isInEchoWindow(key) || property.get() == value) return
        property.set(value)
    }

    /**
     * After a local change only the echo of that value is accepted. Otherwise a new speed must be
     * reported twice at least 0.12 s apart, which filters short glitches while the motor ramps.
     */
    private fun applyRemoteSpeed(speed: Int) {
        val now = SystemClock.elapsedRealtime()
        val candidate = remoteSpeedCandidate
        if (isInEchoWindow("SPD", 1_200)) {
            if (speed != expectedSpeedEcho) return
            expectedSpeedEcho = null
            remoteSpeedCandidate = speed to now
            status.speedPercent = speed
        } else if (candidate != null && candidate.first == speed) {
            if (now - candidate.second >= 120 && status.speedPercent != speed) {
                expectedSpeedEcho = null
                status.speedPercent = speed
            }
        } else {
            remoteSpeedCandidate = speed to now
        }
    }

    private fun isInEchoWindow(key: String, windowMs: Long = 500): Boolean {
        val last = lastLocalChange[key] ?: return false
        return SystemClock.elapsedRealtime() - last <= windowMs
    }

    /** Firmware ≤ 1.1.2 never sends VAR, so a cached Pro/Standard variant must not stick. */
    private fun clearVariantIfLegacyFirmware() {
        val fw = status.firmwareVersion ?: return
        if (FirmwareVersion.compare(fw, "1.1.2") > 0) return
        if (status.boardVariant == BoardVariant.Unknown && !status.didReceiveBoardVariant) return
        status.boardVariant = BoardVariant.Unknown
        status.didReceiveBoardVariant = false
    }

    private fun holdWifiStatus() {
        isHoldingWifiStatus = true
        status.wifiConnected = false
        mainHandler.postDelayed({
            isHoldingWifiStatus = false
            pendingWifiConnected?.let {
                status.wifiConnected = it
                pendingWifiConnected = null
            }
        }, 2_000)
    }

    // endregion

    // region GATT

    private fun didConnect(gatt: BluetoothGatt) {
        val device = gatt.device
        Log.i(TAG, "Connected to ${device.address} (${device.name ?: "–"})")
        if (!gatt.requestMtu(REQUESTED_MTU)) gatt.discoverServices()
        device.name?.takeIf { it.isNotEmpty() }?.let { status.boardVersion = it }
        isConnected = true
        deviceState = DeviceState.Idle
        didInitialSync = false
        availableSsids = null
        clearVariantIfLegacyFirmware()
        status.save()
        holdWifiStatus()
    }

    private fun didDisconnect(gatt: BluetoothGatt, gattStatus: Int) {
        val wasConnected = isConnected
        Log.i(TAG, "Disconnected from ${gatt.device.address}, status $gattStatus")
        closeGatt()
        resetLiveState()
        val address = savedDeviceAddress ?: return
        if (wasConnected) {
            // A lost connection is requested again right away and goes through as soon as the
            // board advertises again, e.g. after it was switched off and on.
            connectToDevice(address, autoConnect = true)
        } else {
            reconnectLater()
        }
    }

    private fun didDiscoverServices(gatt: BluetoothGatt) {
        val c = gatt.getService(serviceUuid)?.getCharacteristic(characteristicUuid)
        if (c == null) {
            Log.e(TAG, "Status characteristic not found")
            return
        }
        Log.i(TAG, "Ready, subscribing to status")
        gatt.setCharacteristicNotification(c, true)
        val descriptor = c.getDescriptor(clientConfigUuid) ?: return
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    /** Callbacks arrive on a binder thread; only those of the current connection are handled. */
    private fun onMain(gatt: BluetoothGatt, block: () -> Unit) {
        mainHandler.post { if (gatt == this.gatt) block() }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) = onMain(gatt) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> didConnect(gatt)
                BluetoothProfile.STATE_DISCONNECTED -> didDisconnect(gatt, status)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) = onMain(gatt) {
            Log.i(TAG, "MTU $mtu")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) = onMain(gatt) {
            didDiscoverServices(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) =
            onMain(gatt) {
                characteristic = descriptor.characteristic
                writeNext()
            }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) = onMain(gatt) {
            isWriting = false
            writeNext()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) = onMain(gatt) { handleIncoming(value) }

        @Deprecated("Used below Android 13")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value?.copyOf() ?: return
            onMain(gatt) { handleIncoming(value) }
        }
    }

    // endregion

    // region Demo for screenshots

    /**
     * Launched with `--es demo running` or `--es demo paused` in a debug build, the app shows a
     * connected board mid-respool without Bluetooth, for screenshots in the emulator.
     */
    fun showDemo(state: DeviceState) {
        if (!BuildConfig.DEBUG) return
        isDemo = true
        isConnected = true
        didInitialSync = true
        deviceState = state
        status.hasFilament = true
        status.progress = 26.0
        status.remainingTime = 10 * 60 + 16
        status.chipTemperature = 38
        status.isFanOn = true
        status.fanSpeed = 60
        status.wifiConnected = true
        status.boardVersion = "LTS CB"
        status.boardVariant = BoardVariant.Standard
        status.firmwareVersion = "1.3.1" // one behind the published version, so the update card offers it
        status.speedPercent = 85
        status.torqueLimit = 2
        status.targetWeight = 0
    }

    // endregion
}

private fun Any.toJson(): JsonPrimitive = when (this) {
    is Boolean -> JsonPrimitive(if (this) 1 else 0) // the firmware expects booleans as 0/1
    is Number -> JsonPrimitive(this)
    else -> JsonPrimitive(toString())
}
