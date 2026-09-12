package com.lts.control.core.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.lts.control.BuildConfig
import com.lts.control.core.ble.model.*
import com.lts.control.core.permissions.BlePermissions
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.nio.charset.StandardCharsets

@SuppressLint("MissingPermission")
class BleManager(
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val adapter: BluetoothAdapter? by lazy {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        mgr?.adapter
    }
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var device: BluetoothDevice? = null

    private var intentionalDisconnect: Boolean = false
    private var reconnectDelayMs: Long = BleConstants.RECONNECT_DELAY_MS
    private var reconnectJob: Job? = null

    // Serialized GATT queue
    private val gattMutex = Mutex()
    private var pendingOpDeferred: CompletableDeferred<Boolean>? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Public State
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<IncomingMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<IncomingMessage> = _messages.asSharedFlow()

    private val _status = MutableStateFlow<StatusPayload?>(null)
    val status: StateFlow<StatusPayload?> = _status.asStateFlow()

    private val _boardName = MutableStateFlow<String?>(null)
    val boardName: StateFlow<String?> = _boardName.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    // --- GATT Operations Queue ---
    private suspend fun executeGattOp(block: (BluetoothGatt) -> Boolean): Boolean = gattMutex.withLock {
        val g = gatt ?: return false
        val deferred = CompletableDeferred<Boolean>()
        pendingOpDeferred = deferred
        val initiated = try {
            block(g)
        } catch (e: Exception) {
            Log.e("BleManager", "Error executing GATT op", e)
            pendingOpDeferred = null
            return false
        }
        if (!initiated) {
            pendingOpDeferred = null
            return false
        }
        return try {
            withTimeout(BleConstants.WRITE_TIMEOUT_MS) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            Log.w("BleManager", "GATT operation timed out after ${BleConstants.WRITE_TIMEOUT_MS}ms")
            false
        } finally {
            pendingOpDeferred = null
        }
    }

    // --- Scan API ---
    fun startScan() {
        if (!BlePermissions.hasPermissions(context) || !BlePermissions.isBluetoothEnabled(context)) {
            _connectionState.value = ConnectionState.Error("Missing permissions or Bluetooth disabled")
            return
        }
        // Android 8–11 silently returns no BLE results while Location services are off
        if (!BlePermissions.isLocationEnabledIfRequired(context)) {
            _connectionState.value = ConnectionState.Error("Location services must be enabled to scan")
            return
        }
        _discoveredDevices.value = emptyList()
        _connectionState.value = ConnectionState.Scanning

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(listOf(filter), settings, scanCallback)
        } catch (e: Exception) {
            Log.e("BleManager", "Scan failed to start", e)
            _connectionState.value = ConnectionState.Error("Scan failed: ${e.message}")
            return
        }

        scope.launch {
            delay(BleConstants.SCAN_MS)
            stopScan()
            if (_connectionState.value == ConnectionState.Scanning) {
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    fun stopScan() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Throwable) {}
        if (_connectionState.value == ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    // --- Connect / Disconnect Lifecycle ---
    fun connect(to: BluetoothDevice) {
        intentionalDisconnect = false
        reconnectJob?.cancel()
        device = to
        _connectionState.value = ConnectionState.Connecting
        gatt = to.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun connectByAddress(address: String) {
        val d = adapter?.getRemoteDevice(address) ?: return
        connect(d)
    }

    fun disconnect() {
        intentionalDisconnect = true
        reconnectJob?.cancel()
        stopScan()
        _connectionState.value = ConnectionState.Disconnecting
        val g = gatt
        if (g != null) {
            g.disconnect()
            // Do not close immediately; close in onConnectionStateChange(STATE_DISCONNECTED)
        } else {
            _connectionState.value = ConnectionState.Disconnected
            onDisconnected()
        }
    }

    private fun scheduleReconnect() {
        val target = device ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(reconnectDelayMs)
            reconnectDelayMs = minOf(30_000L, reconnectDelayMs * 2)
            Log.d("BleManager", "Reconnecting to ${target.address} (delay: ${reconnectDelayMs}ms)...")
            connect(target)
        }
    }

    private fun onDisconnected() {
        _status.value = null
        writeChar = null
        notifyChar = null
    }

    // --- Scan Callback ---
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device ?: return
            val record = result.scanRecord
            val name = record?.deviceName ?: try { d.name } catch (_: SecurityException) { null }

            val matchesUuid = record?.serviceUuids?.any { it.uuid == BleConstants.SERVICE_UUID } == true
            val matchesName = BleConstants.KNOWN_NAMES.any { known -> name?.contains(known, ignoreCase = true) == true }

            if (matchesUuid || matchesName) {
                val mfgData = record?.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID)
                val variant = when (mfgData?.firstOrNull()?.toInt()) {
                    0x01 -> BoardVariant.STANDARD
                    0x02 -> BoardVariant.PRO
                    else -> null
                }
                val discovered = DiscoveredDevice(
                    device = d,
                    name = name ?: d.address,
                    address = d.address,
                    rssi = result.rssi,
                    inferredVariant = variant
                )

                val current = _discoveredDevices.value.toMutableList()
                val existingIndex = current.indexOfFirst { it.address == d.address }
                if (existingIndex >= 0) {
                    current[existingIndex] = discovered
                } else {
                    current.add(discovered)
                }
                _discoveredDevices.value = current
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = ConnectionState.Error("Scan failed: $errorCode")
        }
    }

    // --- GATT Callback ---
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BleConstants.GATT_SUCCESS) {
                Log.w("BleManager", "onConnectionStateChange error status: $status, newState: $newState")
                try { gatt.close() } catch (_: Throwable) {}
                this@BleManager.gatt = null
                writeChar = null
                notifyChar = null
                _connectionState.value = ConnectionState.Error("GATT error: $status")
                onDisconnected()
                if (!intentionalDisconnect) {
                    scheduleReconnect()
                }
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    reconnectDelayMs = BleConstants.RECONNECT_DELAY_MS
                    _connectionState.value = ConnectionState.ConnectedDiscovering
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    try { gatt.close() } catch (_: Throwable) {}
                    this@BleManager.gatt = null
                    writeChar = null
                    notifyChar = null
                    _connectionState.value = ConnectionState.Disconnected
                    onDisconnected()
                    if (!intentionalDisconnect) {
                        scheduleReconnect()
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
                _connectionState.value = ConnectionState.Error("Service or Characteristic not found")
                return
            }

            writeChar = ch
            notifyChar = ch

            try {
                val dName = gatt.device.name
                if (!dName.isNullOrEmpty()) {
                    _boardName.value = dName
                }
            } catch (_: SecurityException) {}

            // Order: requestMtu -> CCCD descriptor write -> then mark Ready
            scope.launch {
                executeGattOp { it.requestMtu(BleConstants.REQUESTED_MTU) }

                gatt.setCharacteristicNotification(ch, true)
                val ccc = ch.getDescriptor(BleConstants.CCCD_UUID)
                if (ccc != null) {
                    val descriptorSuccess = executeGattOp {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(ccc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                        } else {
                            @Suppress("DEPRECATION")
                            ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(ccc)
                        }
                    }
                    if (!descriptorSuccess) {
                        Log.w("BleManager", "CCCD descriptor write failed")
                    }
                }

                reconnectDelayMs = BleConstants.RECONNECT_DELAY_MS
                _connectionState.value = ConnectionState.Ready
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            pendingOpDeferred?.complete(status == BleConstants.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            pendingOpDeferred?.complete(status == BleConstants.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            pendingOpDeferred?.complete(status == BleConstants.GATT_SUCCESS)
        }

        // API 33+
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid != BleConstants.CHARACTERISTIC_UUID) return
            val text = value.toString(StandardCharsets.UTF_8)
            handleIncoming(text)
        }

        // API < 33
        @Deprecated("Deprecated in Java")
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            if (characteristic.uuid != BleConstants.CHARACTERISTIC_UUID) return
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: return
            val text = bytes.toString(StandardCharsets.UTF_8)
            handleIncoming(text)
        }
    }

    // --- Incoming Message Handling ---
    private fun handleIncoming(jsonText: String) {
        val message = IncomingMessage.parse(jsonText)
        if (message is IncomingMessage.Status) {
            _status.value = message.payload
        } else if (message is IncomingMessage.Raw && BuildConfig.DEBUG) {
            Log.w("BleManager", "Unrecognized or raw BLE payload: $jsonText")
        }
        scope.launch { _messages.emit(message) }
    }

    // --- Outgoing Writes (Queued via Channel/executeGattOp) ---
    suspend fun sendBytes(bytes: ByteArray): Boolean {
        val ch = writeChar ?: return false
        return executeGattOp { g ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.value = bytes
                @Suppress("DEPRECATION")
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
        }
    }

    fun clear() {
        disconnect()
        scope.cancel()
    }

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
