package com.lts.control.core.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import com.lts.control.core.ble.model.*
import java.nio.charset.Charset
import java.util.UUID

/**
 * Android-nativer BLE-Manager:
 * - scan nach Service-UUID
 * - connect, discover services, requestMtu(512)
 * - enable notifications
 * - send/receive JSON auf einer Write+Notify-Characteristic
 * - Auto-Reconnect
 *
 * Hinweise:
 * - Erfordert BLUETOOTH_SCAN/BLUETOOTH_CONNECT (API 31+) und Location/BT auf älteren Geräten
 * - Auf echten Geräten testen (Emulator unterstützt kein BLE)
 */
@SuppressLint("MissingPermission")
class BleManager(
    private val context: Context
) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val adapter: BluetoothAdapter by lazy {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mgr.adapter
    }
    private val scanner: BluetoothLeScanner? get() = adapter.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var device: BluetoothDevice? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Public State
    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected as ConnectionState)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<IncomingMessage>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<IncomingMessage> = _messages.asSharedFlow()

    private val _status = MutableStateFlow<StatusPayload?>(null)
    val status: StateFlow<StatusPayload?> = _status.asStateFlow()

    // Scan/Connect
    fun startScan() {
        if (!adapter.isEnabled) return
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        _connectionState.value = ConnectionState.Scanning
        scanner?.startScan(listOf(filter), settings, scanCallback)
        scope.launch {
            delay(BleConstants.SCAN_MS)
            stopScan()
            if (_connectionState.value == ConnectionState.Scanning) {
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    fun stopScan() {
        try { scanner?.stopScan(scanCallback) } catch (_: Throwable) {}
    }

    fun disconnect() {
        stopScan()
        _connectionState.value = ConnectionState.Disconnecting
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeChar = null
        notifyChar = null
        device = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun connect(to: BluetoothDevice) {
        device = to
        _connectionState.value = ConnectionState.Connecting
        gatt = to.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device ?: return
            // Optional: zusätzlich am AdvertisedName matchen
            val name = result.scanRecord?.deviceName ?: d.name
            if (result.scanRecord?.serviceUuids?.any { it.uuid == BleConstants.SERVICE_UUID } == true ||
                name?.contains(BleConstants.ADVERTISED_NAME, ignoreCase = true) == true
            ) {
                stopScan()
                connect(d)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = ConnectionState.Error("Scan failed: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BleConstants.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = ConnectionState.Error("GATT error: $status")
                scope.launch {
                    delay(BleConstants.RECONNECT_DELAY_MS)
                    device?.let { connect(it) }
                }
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.ConnectedDiscovering
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Disconnected
                    scope.launch {
                        delay(BleConstants.RECONNECT_DELAY_MS)
                        device?.let { connect(it) }
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BleConstants.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Error("Service discovery failed: $status")
                return
            }
            val service = gatt.getService(BleConstants.SERVICE_UUID)
            val ch = service?.getCharacteristic(BleConstants.CHARACTERISTIC_UUID)
            if (service == null || ch == null) {
                _connectionState.value = ConnectionState.Error("Service/Characteristic not found")
                return
            }
            writeChar = ch
            notifyChar = ch

            // MTU hoch
            gatt.requestMtu(BleConstants.REQUESTED_MTU)

            // Notifications aktivieren
            val ccc = ch.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            gatt.setCharacteristicNotification(ch, true)
            if (ccc != null) {
                ccc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                gatt.writeDescriptor(ccc)
            }

            _connectionState.value = ConnectionState.Ready
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            // nice to have: log
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid != BleConstants.CHARACTERISTIC_UUID) return
            val bytes = characteristic.value ?: return
            val text = bytes.toString(Charset.forName("UTF-8"))
            handleIncoming(text)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status != BleConstants.GATT_SUCCESS) {
                // Optional: Retry/Fehlerlogik
            }
        }
    }

    private fun handleIncoming(jsonText: String) {
        // Einige Notifications sind reine Events: {"SSID_LIST":[...]} oder {"WIFI_CONN_RESULT":true} oder {"OTA_OK":true}
        try {
            val element = json.parseToJsonElement(jsonText)
            if (element is JsonObject) {
                when {
                    "SSID_LIST" in element -> {
                        val list = element["SSID_LIST"]!!.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
                        scope.launch { _messages.emit(IncomingMessage.WifiScan(list)) }
                    }
                    "WIFI_CONN_RESULT" in element -> {
                        val ok = element["WIFI_CONN_RESULT"]!!.jsonPrimitive.booleanOrNull == true
                        scope.launch { _messages.emit(IncomingMessage.WifiConnectResult(ok)) }
                    }
                    "OTA_OK" in element && element.size == 1 -> {
                        val ok = element["OTA_OK"]!!.jsonPrimitive.booleanOrNull == true
                        scope.launch { _messages.emit(IncomingMessage.OtaResult(ok)) }
                    }
                    else -> {
                        // Voller Status-Payload
                        val payload = json.decodeFromJsonElement(StatusPayload.serializer(), element)
                        _status.value = payload
                        scope.launch { _messages.emit(IncomingMessage.Status(payload)) }
                    }
                }
            } else {
                scope.launch { _messages.emit(IncomingMessage.Raw(jsonText)) }
            }
        } catch (_: Throwable) {
            scope.launch { _messages.emit(IncomingMessage.Raw(jsonText)) }
        }
    }

    // --- Public API: Commands ---
    fun send(bytes: ByteArray, onError: ((Throwable) -> Unit)? = null) {
        val g = gatt ?: return
        val ch = writeChar ?: return
        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        ch.value = bytes
        if (!g.writeCharacteristic(ch)) {
            onError?.invoke(IllegalStateException("writeCharacteristic returned false"))
        }
    }

    fun sendJson(obj: String) = send(obj.encodeToByteArray())

    fun start()  = send(Commands.start())
    fun stop()   = send(Commands.stop())
    fun pause()  = send(Commands.pause())
    fun ota()    = send(Commands.ota())
    fun wifiScan() = send(Commands.wifiScan())
    fun wifiConnect(ssid: String, pass: String) = send(Commands.wifiConnect(ssid, pass))

    // Settings
    fun setDirection(dir: Int) = send(Commands.setDirection(dir))
    fun setLed(brightness: Int) = send(Commands.setLed(brightness))
    fun setFilamentSensor(on: Boolean) = send(Commands.setUseFilamentSensor(on))
    fun setMotorStrength(pct80to120: Int) = send(Commands.setMotorStrength(pct80to120))
    fun setTorque(limit0to3: Int) = send(Commands.setTorqueLimit(limit0to3))
    fun setJingle(style0to3: Int) = send(Commands.setJingleStyle(style0to3))
    fun setDurationAt80(seconds: Int) = send(Commands.setDurationAt80(seconds))
    fun setTargetWeight(mode0to3: Int) = send(Commands.setTargetWeight(mode0to3))
    fun setSpeedPercent(p50to100: Int) = send(Commands.setSpeedPercent(p50to100))
    fun setHighSpeed(on: Boolean) = send(Commands.setHighSpeedMode(on))
    fun setFanSpeed(p0to100: Int) = send(Commands.setFanSpeed(p0to100))
    fun setFanAlwaysOn(on: Boolean) = send(Commands.setFanAlwaysOn(on))

    fun clear() {
        disconnect()
        scope.cancel()
    }

    // Verbindung modellieren
    sealed interface ConnectionState {
        data object Disconnected : ConnectionState
        data object Scanning : ConnectionState
        data object Connecting : ConnectionState
        data object ConnectedDiscovering : ConnectionState
        data object Ready : ConnectionState
        data object Disconnecting : ConnectionState
        data class Error(val message: String) : ConnectionState
    }
}