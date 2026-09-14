package com.lts.control.ui.screens.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lts.control.BuildConfig
import com.lts.control.R
import com.lts.control.core.ble.BleViewModel
import com.lts.control.core.ble.model.BoardVariant
import com.lts.control.core.ble.model.DeviceState
import com.lts.control.core.repository.DeviceRepository
import com.lts.control.core.util.FirmwareVersionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    vm: BleViewModel,
    onNavigateToInstructions: () -> Unit,
    onNavigateToChangelog: () -> Unit,
    onNavigateToUpgradeCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status by vm.status.collectAsState()
    val isConnected by vm.isConnected.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var latestFirmwareVersion by remember { mutableStateOf("–") }
    var holdUpdateAvailableUntil by remember { mutableStateOf(0L) }
    var hasAppliedFirstUpdateAvailableDelay by remember { mutableStateOf(false) }
    var updateButtonDisabledUntil by remember { mutableStateOf(0L) }

    val effectiveBoardFW = remember(isConnected, status.firmwareVersion) {
        if (!status.firmwareVersion.isNullOrEmpty()) status.firmwareVersion!! else "–"
    }

    val isV3Board = remember(status.boardVersion) {
        status.boardVersion?.contains("CtrBoard V3") == true
    }
    val v3Blocked = isV3Board && effectiveBoardFW == "1.0.0"

    // Polling latest firmware version when screen is started
    var isPollingActive by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        isPollingActive = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        isPollingActive = false
    }

    // Load cached latest board firmware at launch
    LaunchedEffect(Unit) {
        val prefs = DeviceRepository.getInstance(context).preferencesRepo
        // Observe cached version if present
        // and fetch fresh version
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            updateButtonDisabledUntil = System.currentTimeMillis() + 1500L
        } else {
            updateButtonDisabledUntil = 0L
        }
    }

    LaunchedEffect(isPollingActive) {
        if (!isPollingActive) return@LaunchedEffect
        while (isActive && isPollingActive) {
            withContext(Dispatchers.IO) {
                try {
                    val bucket = System.currentTimeMillis() / 15000L
                    val url = URL("https://download.lts-design.com/Firmware/latest_board_firmware.txt?t=$bucket")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 8000
                        readTimeout = 8000
                        setRequestProperty("Cache-Control", "no-cache")
                        setRequestProperty("Pragma", "no-cache")
                    }
                    if (conn.responseCode in 200..299) {
                        val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText().trim() }
                        if (body.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                latestFirmwareVersion = body
                                DeviceRepository.getInstance(context).preferencesRepo.setLatestBoardFirmwareVersion(body)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            delay(5000)
        }
    }

    // Delay update available display by 500ms on first discovery
    val isUpdateAvailable = remember(latestFirmwareVersion, effectiveBoardFW) {
        latestFirmwareVersion != "–" && effectiveBoardFW != "–" &&
                FirmwareVersionUtils.isNewerVersion(latestFirmwareVersion, effectiveBoardFW)
    }

    LaunchedEffect(isUpdateAvailable) {
        if (isUpdateAvailable && !hasAppliedFirstUpdateAvailableDelay) {
            hasAppliedFirstUpdateAvailableDelay = true
            holdUpdateAvailableUntil = System.currentTimeMillis() + 500L
        }
    }

    val now = System.currentTimeMillis()
    val isUpdateAvailableDisplayed = isUpdateAvailable && (now >= holdUpdateAvailableUntil)
    val isWiFiConnected = isConnected && (status.wifiConnected == true)
    val delayActive = now < updateButtonDisabledUntil
    val isUpdateButtonEnabled = isUpdateAvailableDisplayed && isWiFiConnected && !delayActive

    val boardType = remember(status.boardVersion, status.boardVariant) {
        getBoardTypeLabel(status.boardVersion, status.boardVariant)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_more)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Icon
            Image(
                painter = painterResource(id = R.drawable.app_icon_square),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(32.dp))
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Text(
                text = "LTS Control",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Board Type Label
            if (!boardType.isNullOrEmpty()) {
                Text(
                    text = boardType,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // App Version
            Text(
                text = stringResource(R.string.about_app_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            // Board Version
            if (effectiveBoardFW != "–") {
                Text(
                    text = stringResource(R.string.about_board_version, effectiveBoardFW),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Capsule: Notes / Hinweise
            Surface(
                shape = RoundedCornerShape(25.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clickable(onClick = onNavigateToInstructions)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.about_instructions),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // OTA Update Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Help Icon (top right)
                    IconButton(
                        onClick = {
                            if (v3Blocked) {
                                onNavigateToUpgradeCode()
                            } else {
                                onNavigateToChangelog()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                            contentDescription = "Help",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.about_board_update),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val subtitleText = when {
                            v3Blocked -> stringResource(R.string.about_updates_not_supported)
                            !isConnected -> stringResource(R.string.about_board_not_connected)
                            status.deviceState == DeviceState.UPDATING -> stringResource(R.string.about_firmware_updating)
                            status.otaSuccess == true -> stringResource(R.string.about_firmware_up_to_date)
                            status.otaSuccess == false -> stringResource(R.string.about_update_failed)
                            isUpdateAvailableDisplayed -> stringResource(
                                R.string.about_update_available,
                                effectiveBoardFW,
                                latestFirmwareVersion
                            )
                            else -> stringResource(R.string.about_firmware_up_to_date)
                        }

                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Action Button
                        if (v3Blocked || status.deviceState == DeviceState.UPDATING || isUpdateAvailableDisplayed) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    if (!v3Blocked) {
                                        vm.ota()
                                    }
                                },
                                enabled = isUpdateButtonEnabled,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                when {
                                    v3Blocked -> {
                                        Text(stringResource(R.string.about_btn_not_supported))
                                    }
                                    status.deviceState == DeviceState.UPDATING -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.5.dp
                                        )
                                    }
                                    isUpdateAvailableDisplayed && !isWiFiConnected -> {
                                        Text(stringResource(R.string.about_btn_no_wifi))
                                    }
                                    else -> {
                                        Text(stringResource(R.string.about_btn_update))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Power input: only when the firmware reports it
            if (isConnected && (status.usbVoltageMv != null || status.powerGood != null)) {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.about_power_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // USB-C VBUS only sees power from the USB-C port; near zero means the DC jack is supplying the board
                        val onDcJack = status.usbVoltageMv?.let { it < DC_JACK_VBUS_THRESHOLD_MV } == true
                        if (onDcJack) {
                            PowerInfoRow(
                                label = stringResource(R.string.about_power_source),
                                value = stringResource(R.string.about_power_dc_jack)
                            )
                        } else {
                            status.usbVoltageMv?.let { mv ->
                                PowerInfoRow(
                                    label = stringResource(R.string.about_usb_voltage),
                                    value = stringResource(R.string.about_voltage_value, mv / 1000f)
                                )
                            }
                            status.powerGood?.let { good ->
                                PowerInfoRow(
                                    label = stringResource(R.string.about_pd_status),
                                    value = stringResource(if (good) R.string.about_pd_ok else R.string.about_pd_not_ok)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(if (onDcJack) R.string.about_power_hint_dc else R.string.about_power_hint_usb),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Legal Footer
            Text(
                text = stringResource(R.string.about_legal_footer),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // OTA Result Alert
    if (status.otaSuccess != null) {
        val otaSuccess = status.otaSuccess == true
        AlertDialog(
            onDismissRequest = { vm.clearOtaSuccess() },
            title = {
                Text(
                    if (otaSuccess) stringResource(R.string.ota_alert_success_title)
                    else stringResource(R.string.ota_alert_failure_title)
                )
            },
            text = {
                Text(
                    if (otaSuccess) stringResource(R.string.ota_alert_success_message)
                    else stringResource(R.string.ota_alert_failure_message)
                )
            },
            confirmButton = {
                Row {
                    if (otaSuccess) {
                        TextButton(
                            onClick = {
                                vm.clearOtaSuccess()
                                onNavigateToChangelog()
                            }
                        ) {
                            Text(stringResource(R.string.ota_alert_show_changes))
                        }
                    }
                    TextButton(onClick = { vm.clearOtaSuccess() }) {
                        Text(stringResource(R.string.common_ok))
                    }
                }
            }
        )
    }
}

// Real USB-C supplies are >= ~4.5 V at the board; below this the reading is diode leakage / ADC noise
private const val DC_JACK_VBUS_THRESHOLD_MV = 3000

@Composable
private fun PowerInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getBoardTypeLabel(raw: String?, variant: BoardVariant): String? {
    if (raw == null) return null
    val isPro = (variant == BoardVariant.PRO)
    if (raw.contains("CtrBoard V3")) return "Control Board V3"
    if (raw.contains("CtrBoard V4") || raw.contains("LTS CB")) {
        return if (isPro) "Control Board (Pro)" else "Control Board"
    }
    if (raw.contains("LTS DB")) {
        return if (isPro) "Driver Board (Pro)" else "Driver Board"
    }
    if (raw.contains("esp32 PCB", ignoreCase = true)) {
        return "Driver Board"
    }
    return null
}
