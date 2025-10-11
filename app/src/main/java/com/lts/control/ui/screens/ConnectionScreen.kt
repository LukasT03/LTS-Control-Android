@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lts.control.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lts.control.core.ble.BleManager
import com.lts.control.core.ble.BleViewModel
import com.lts.control.core.ble.model.DeviceState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ConnectionScreen(
    vm: BleViewModel,
    onBack: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val status by vm.status.collectAsState()
    val deviceState by vm.deviceState.collectAsState()
    val connection by vm.connection.collectAsState()

    // iOS-States
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var showWifiError by remember { mutableStateOf(false) }
    var wiggleWifi by remember { mutableStateOf(false) }
    var bounceWifi by remember { mutableStateOf(false) }

    // SSIDs aus Firmware-Events
    var ssids by remember { mutableStateOf(listOf<String>()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanSessionId by remember { mutableStateOf(0) } // nur für Recompose-Key analog iOS

    // Firmware-Messages konsumieren (SSID_LIST, WIFI_CONN_RESULT)
    LaunchedEffect(Unit) {
        vm.messages.collectLatest { msg ->
            when (msg) {
                is com.lts.control.core.ble.model.IncomingMessage.WifiScan -> {
                    ssids = msg.ssids.distinct().map { it.trim() }.filter { it.isNotEmpty() }
                    isScanning = false
                    scanSessionId++ // Picker refresh
                }
                is com.lts.control.core.ble.model.IncomingMessage.WifiConnectResult -> {
                    isConnecting = false
                    if (msg.ok) {
                        bounceWifi = !bounceWifi
                        // Passwort wie in iOS leeren
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
                else -> Unit
            }
        }
    }

    // Wenn WiFi connected meldet, Verbindungsvorgang beenden
    val isBleConnected = status != null
    val isWifiConnected = isBleConnected && (status?.wifiConnected == true)
    LaunchedEffect(isWifiConnected) {
        if (isWifiConnected) isConnecting = false
    }
    // Bei BLE-Disconnect SSID zurücksetzen (analog Swift)
    LaunchedEffect(isBleConnected) {
        if (!isBleConnected) {
            wifiSsid = ""
            scanSessionId++
        }
    }

    val canSend = !isConnecting &&
            isBleConnected &&
            deviceState != DeviceState.UPDATING &&
            wifiSsid.isNotBlank() &&
            wifiPassword.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verbindung") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                },
                actions = {
                    IconButton(
                        enabled = isBleConnected && deviceState != DeviceState.UPDATING,
                        onClick = {
                            isScanning = true
                            vm.wifiScan()
                        }
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Netzwerk-Scan")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = 8.dp)
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
                    icon = { Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = if (isBleConnected) Color.Cyan else LocalContentColor.current) },
                    title = "Bluetooth",
                    subtitle = if (isBleConnected) "Verbunden" else "Getrennt"
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
                    modifier = Modifier.weight(1f).scale(wifiScale),
                    icon = {
                        val tint = when {
                            isWifiConnected -> Color(0xFF2E7D32)
                            showWifiError -> Color(0xFFD32F2F)
                            else -> LocalContentColor.current
                        }
                        Icon(Icons.Filled.Wifi, contentDescription = null, tint = tint)
                    },
                    title = "WLAN",
                    subtitle = when {
                        showWifiError -> "Fehler!"
                        isBleConnected && isWifiConnected -> "Verbunden"
                        else -> "Getrennt"
                    }
                )
            }

            // ----------------------------------- WLAN -----------------------------------
            SettingsGroupHeader("WLAN")
            Text(
                "Für die WLAN-Verbindung hier nach verfügbaren Netzwerken suchen und das Passwort eingeben. Funktioniert nur mit 2,4 GHz Netzwerken.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            Card(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp)) {
                    // SSID Picker / Loader
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Netzwerk", Modifier.weight(1f))
                        if (isScanning) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            var expanded by remember(scanSessionId) { mutableStateOf(false) }
                            val items = ssids
                            Text(
                                if (wifiSsid.isBlank()) "Auswählen (${items.size})" else wifiSsid,
                                color = Color.Gray,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = items.isNotEmpty()) { expanded = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Auswählen (${items.size})") },
                                    onClick = {
                                        wifiSsid = ""
                                        expanded = false
                                    }
                                )
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

                    // Passwort
                    OutlinedTextField(
                        value = wifiPassword,
                        onValueChange = { wifiPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Passwort") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Button(
                            enabled = canSend,
                            onClick = {
                                isConnecting = true
                                // Analog iOS: erst Passwort, dann SSID, dann Connect-Trigger
                                vm.wifiConnect(ssid = wifiSsid, pass = wifiPassword)
                                // Passwort lokal löschen wie in Swift
                                wifiPassword = ""
                            }
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Verbinden…")
                            } else {
                                Text("Zugangsdaten senden")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // -------------------------------- Bluetooth ---------------------------------
            SettingsGroupHeader("Bluetooth")
            Text(
                "Bei Verbindungsproblemen das gespeicherte Gerät entfernen und die Verbindung neu aufbauen.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            Card(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Button(
                        onClick = { vm.startScan() },
                        enabled = connection !is BleManager.ConnectionState.Ready
                    ) { Text("Verbindung herstellen") }

                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = { vm.disconnect() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F))
                    ) { Text("Gespeichertes Gerät entfernen") }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/* --------------------------------- UI Bits -------------------------------- */

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
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(25))
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.BottomCenter) { icon() }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable private fun SettingsGroupHeader(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
    )
}
