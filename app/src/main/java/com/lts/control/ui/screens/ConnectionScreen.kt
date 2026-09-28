@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lts.control.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lts.control.R
import com.lts.control.core.ble.BleViewModel
import com.lts.control.core.ble.model.BoardVariant
import com.lts.control.core.ble.model.DeviceState
import com.lts.control.core.ble.model.DiscoveredDevice
import com.lts.control.core.permissions.BlePermissions
import com.lts.control.ui.theme.StateCriticalRed
import com.lts.control.ui.theme.StateNormalGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun ConnectionScreen(
    vm: BleViewModel,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val status by vm.status.collectAsState()
    val isBleConnected by vm.isConnected.collectAsState()
    val isScanningSsids by vm.isScanningSsids.collectAsState()
    val availableSsids by vm.availableSsids.collectAsState()
    val isConnectingWifi by vm.isConnectingWifi.collectAsState()
    val discoveredDevices by vm.discoveredDevices.collectAsState()
    val lastSavedAddress by vm.lastDeviceAddress.collectAsState()

    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var showWifiError by remember { mutableStateOf(false) }
    var wiggleWifi by remember { mutableStateOf(false) }
    var bounceWifi by remember { mutableStateOf(false) }

    var showDevicePicker by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(BlePermissions.hasPermissions(context)) }
    var isBtEnabled by remember { mutableStateOf(BlePermissions.isBluetoothEnabled(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            hasPermissions = BlePermissions.hasPermissions(context)
            isBtEnabled = BlePermissions.isBluetoothEnabled(context)
        }
    )

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            isBtEnabled = BlePermissions.isBluetoothEnabled(context)
        }
    )

    LaunchedEffect(Unit) {
        vm.wifiConnectSuccess.collectLatest { ok ->
            if (ok) {
                bounceWifi = !bounceWifi
                wifiPassword = ""
            } else {
                wiggleWifi = !wiggleWifi
                showWifiError = true
                scope.launch {
                    delay(3500)
                    showWifiError = false
                }
            }
        }
    }

    val isWifiConnected = isBleConnected && (status.wifiConnected == true)

    val canSendWifi = !isConnectingWifi &&
            isBleConnected &&
            status.deviceState != DeviceState.UPDATING &&
            wifiSsid.isNotBlank() &&
            wifiPassword.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conn_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    }
                },
                actions = {
                    IconButton(
                        enabled = isBleConnected && status.deviceState != DeviceState.UPDATING,
                        onClick = { vm.triggerWifiScan() }
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.conn_scan_networks))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // -------------------------- Status-Header (Capsules) --------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Capsule(
                    modifier = Modifier.weight(1f),
                    icon = {
                        Icon(
                            Icons.Filled.Bluetooth,
                            contentDescription = null,
                            tint = if (isBleConnected) Color.Cyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    title = stringResource(R.string.conn_bluetooth),
                    subtitle = if (isBleConnected) stringResource(R.string.common_connected) else stringResource(R.string.common_disconnected)
                )

                val wifiScale by animateFloatAsState(
                    targetValue = when {
                        showWifiError -> 1.08f
                        bounceWifi -> 1.10f
                        else -> 1f
                    },
                    animationSpec = tween(220, easing = if (bounceWifi) LinearOutSlowInEasing else FastOutLinearInEasing),
                    label = "wifiScale"
                )

                Capsule(
                    modifier = Modifier
                        .weight(1f)
                        .scale(wifiScale),
                    icon = {
                        val tint = when {
                            isWifiConnected -> StateNormalGreen
                            showWifiError -> StateCriticalRed
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(Icons.Filled.Wifi, contentDescription = null, tint = tint)
                    },
                    title = stringResource(R.string.conn_wlan),
                    subtitle = when {
                        showWifiError -> stringResource(R.string.conn_error_short)
                        isWifiConnected -> stringResource(R.string.common_connected)
                        else -> stringResource(R.string.common_disconnected)
                    }
                )
            }

            // ----------------------------------- WLAN -----------------------------------
            SettingsGroupHeader(stringResource(R.string.conn_wlan))
            Text(
                stringResource(R.string.conn_wlan_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            Card(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    // SSID Picker / Loader
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.conn_network), Modifier.weight(1f))
                        if (isScanningSsids) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            var expanded by remember { mutableStateOf(false) }
                            val items = availableSsids
                            val label = when {
                                wifiSsid.isNotBlank() -> wifiSsid
                                items.isEmpty() -> stringResource(R.string.conn_no_networks_found)
                                else -> stringResource(R.string.conn_select_count, items.size)
                            }
                            Text(
                                text = label,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = items.isNotEmpty()) { expanded = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                items.forEach { ssid ->
                                    DropdownMenuItem(
                                        text = { Text(ssid) },
                                        onClick = {
                                            wifiSsid = ssid
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Password field
                    OutlinedTextField(
                        value = wifiPassword,
                        onValueChange = { wifiPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.conn_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Button(
                            enabled = canSendWifi,
                            onClick = {
                                vm.sendWifiCredentialsAndConnect(wifiSsid, wifiPassword)
                            }
                        ) {
                            if (isConnectingWifi) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.conn_connecting))
                            } else {
                                Text(stringResource(R.string.conn_send_credentials))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // -------------------------------- Bluetooth ---------------------------------
            SettingsGroupHeader(stringResource(R.string.conn_bluetooth))
            Text(
                stringResource(R.string.conn_bluetooth_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            Card(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    val canConnect = hasPermissions && isBtEnabled

                    if (!hasPermissions) {
                        Text(
                            text = stringResource(R.string.conn_permission_rationale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { permissionLauncher.launch(BlePermissions.getRequiredPermissions()) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.conn_grant_permission))
                        }
                    } else if (!isBtEnabled) {
                        Text(
                            text = stringResource(R.string.conn_bluetooth_disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.conn_enable_bluetooth))
                        }
                    } else {
                        Button(
                            onClick = {
                                showDevicePicker = true
                                vm.startScan()
                            },
                            enabled = !isBleConnected,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.conn_connect))
                        }
                    }

                    if (!lastSavedAddress.isNullOrEmpty() || isBleConnected) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { vm.removeSavedDevice() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.conn_forget_device))
                        }
                    }
                }
            }
        }
    }

    // Device Picker Modal Bottom Sheet (§6.3)
    if (showDevicePicker) {
        ModalBottomSheet(
            onDismissRequest = {
                showDevicePicker = false
                vm.stopScan()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.conn_select_device),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(12.dp))

                if (discoveredDevices.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.conn_searching_devices), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(discoveredDevices, key = { it.address }) { device ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.connectDevice(device)
                                        showDevicePicker = false
                                        vm.stopScan()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val imageRes = if (device.inferredVariant == BoardVariant.PRO) {
                                        R.drawable.respooler_pro
                                    } else {
                                        R.drawable.respooler
                                    }
                                    Image(
                                        painter = painterResource(id = imageRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                                        val variantSubtitle = when (device.inferredVariant) {
                                            BoardVariant.PRO -> stringResource(R.string.variant_pro)
                                            BoardVariant.STANDARD -> stringResource(R.string.variant_v4)
                                            else -> device.address
                                        }
                                        Text(variantSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("${device.rssi} dBm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Capsule(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Row(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(25))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsGroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
    )
}
