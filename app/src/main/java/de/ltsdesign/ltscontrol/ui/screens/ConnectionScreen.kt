package de.ltsdesign.ltscontrol.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.bluetooth.BleManager
import de.ltsdesign.ltscontrol.bluetooth.DevicePairing
import de.ltsdesign.ltscontrol.bluetooth.DeviceState
import de.ltsdesign.ltscontrol.ui.settings.Banner
import de.ltsdesign.ltscontrol.ui.settings.ChoiceItem
import de.ltsdesign.ltscontrol.ui.settings.SettingsCustomItem
import de.ltsdesign.ltscontrol.ui.settings.SettingsItem
import de.ltsdesign.ltscontrol.ui.settings.SettingsItemColor
import de.ltsdesign.ltscontrol.ui.settings.SettingsPage
import de.ltsdesign.ltscontrol.ui.settings.settingsBanner
import de.ltsdesign.ltscontrol.ui.settings.settingsGroup
import de.ltsdesign.ltscontrol.ui.theme.blueTonalButtonColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectionScreen(bleManager: BleManager, pairing: DevicePairing) {
    val connectToBoard = rememberConnectAction(bleManager, pairing)
    val focusManager = LocalFocusManager.current
    val status = bleManager.status
    val isConnected = bleManager.isConnected
    val isWifiConnected = isConnected && status.wifiConnected == true

    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var isConnectingToWifi by remember { mutableStateOf(false) }
    var showWifiError by rememberSaveable { mutableStateOf(false) }

    val canScan = isConnected && bleManager.deviceState != DeviceState.Updating
    val canSend = canScan && !isConnectingToWifi && ssid.isNotEmpty() && password.isNotEmpty()
    val ssids = bleManager.availableSsids.orEmpty().map { it.trim() }.distinct()

    LaunchedEffect(status.wifiConnectionResult) {
        val result = status.wifiConnectionResult ?: return@LaunchedEffect
        isConnectingToWifi = false
        if (!result) {
            showWifiError = true
            delay(3_500)
            showWifiError = false
        }
    }
    LaunchedEffect(isWifiConnected) { if (isWifiConnected) isConnectingToWifi = false }
    LaunchedEffect(isConnected) {
        if (!isConnected) {
            // The board's result can't arrive anymore.
            isConnectingToWifi = false
            ssid = ""
        }
    }
    LaunchedEffect(isConnectingToWifi) {
        if (!isConnectingToWifi) return@LaunchedEffect
        // The board takes the password first, then the network, then connects.
        bleManager.sendWifiPassword(password)
        password = ""
        delay(300)
        bleManager.sendWifiSsid(ssid)
        delay(300)
        bleManager.triggerWifiConnect()
    }
    val sendCredentials = {
        focusManager.clearFocus()
        // The board reports the result only once, so a second failure in a row must still count as a change.
        status.wifiConnectionResult = null
        isConnectingToWifi = true
    }

    val bluetoothSummary = stringResource(if (isConnected) R.string.connected else R.string.disconnected)
    val wifiSummary = when {
        showWifiError -> stringResource(R.string.error)
        isWifiConnected -> stringResource(R.string.connected)
        else -> stringResource(R.string.disconnected)
    }
    val networkPlaceholder = stringResource(R.string.select_network, ssids.size)
    val errorColor = MaterialTheme.colorScheme.error

    SettingsPage(title = stringResource(R.string.title_connection)) {
        settingsBanner(
            Banner(
                R.string.bluetooth,
                bluetoothSummary,
                if (isConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.BluetoothDisabled,
            ),
            Banner(
                R.string.wifi,
                wifiSummary,
                if (isWifiConnected) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                iconTint = if (showWifiError) errorColor else null,
            ),
        )

        settingsGroup(header = R.string.wifi, footer = R.string.wifi_note) {
            item {
                ChoiceItem(
                    title = stringResource(R.string.network),
                    options = ssids.map { it to it },
                    selected = ssid,
                    enabled = !bleManager.isScanningForSsids,
                    placeholder = networkPlaceholder,
                    emptyText = stringResource(R.string.no_networks),
                    trailing = {
                        // The spinner takes the button's place at the right edge, so the row doesn't change width.
                        Box(contentAlignment = Alignment.CenterEnd) {
                            val isScanning = bleManager.isScanningForSsids
                            FilledTonalButton(
                                onClick = bleManager::triggerWifiScan,
                                enabled = canScan && !isScanning,
                                modifier = Modifier.alpha(if (isScanning) 0f else 1f),
                            ) {
                                Text(stringResource(R.string.scan))
                            }
                            if (isScanning) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        }
                    },
                ) { ssid = it }
            }
            item {
                // The outlined field already reserves 8 dp above itself for its floating label.
                SettingsCustomItem(topPadding = 8.dp) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.password)) },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (canSend) sendCredentials() }),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // 16 dp to the field instead of the item's usual 12 dp.
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                        // Medium size like the full-width buttons of the system Settings.
                        val buttonHeight = ButtonDefaults.MediumContainerHeight
                        Button(
                            onClick = sendCredentials,
                            enabled = canSend,
                            colors = blueTonalButtonColors(SettingsItemColor),
                            shapes = ButtonDefaults.shapesFor(buttonHeight),
                            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                            modifier = Modifier.fillMaxWidth().heightIn(min = buttonHeight),
                        ) {
                            if (isConnectingToWifi) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = LocalContentColor.current)
                            } else {
                                Text(stringResource(R.string.send_credentials), style = ButtonDefaults.textStyleFor(buttonHeight))
                            }
                        }
                    }
                }
            }
        }

        settingsGroup(header = R.string.bluetooth, footer = R.string.bluetooth_note) {
            item {
                SettingsItem(
                    title = stringResource(R.string.connect),
                    icon = Icons.Rounded.AddLink,
                    enabled = bleManager.savedDeviceAddress == null,
                    onClick = connectToBoard,
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.remove_saved_device),
                    icon = Icons.Rounded.Delete,
                    onClick = {
                        bleManager.savedDeviceAddress?.let(pairing::forget)
                        bleManager.forgetBoard()
                    },
                )
            }
        }
    }
}

/**
 * Asks for the Bluetooth permission if needed, then opens the system dialog to choose the board
 * and connects to it.
 */
@Composable
internal fun rememberConnectAction(bleManager: BleManager, pairing: DevicePairing): () -> Unit {
    val context = LocalContext.current
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        pairing.addressFromResult(result.data)?.let { bleManager.connectToDevice(it) }
    }
    val findDevice = {
        pairing.findDevice(
            onChooser = { chooser.launch(IntentSenderRequest.Builder(it).build()) },
            onFailure = { error -> error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } },
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) findDevice()
    }
    return {
        if (bleManager.hasConnectPermission()) {
            findDevice()
        } else {
            permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
}
