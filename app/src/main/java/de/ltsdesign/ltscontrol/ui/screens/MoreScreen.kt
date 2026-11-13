package de.ltsdesign.ltscontrol.ui.screens

import android.content.Intent
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowCircleDown
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import de.ltsdesign.ltscontrol.BuildConfig
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.bluetooth.BleManager
import de.ltsdesign.ltscontrol.bluetooth.BoardVariant
import de.ltsdesign.ltscontrol.bluetooth.DevicePairing
import de.ltsdesign.ltscontrol.bluetooth.DeviceState
import de.ltsdesign.ltscontrol.data.FirmwareUpdateRepository
import de.ltsdesign.ltscontrol.ui.settings.ActionCards
import de.ltsdesign.ltscontrol.ui.settings.CardAction
import de.ltsdesign.ltscontrol.ui.settings.SettingsCard
import de.ltsdesign.ltscontrol.ui.settings.SettingsItem
import de.ltsdesign.ltscontrol.ui.settings.SettingsItemColor
import de.ltsdesign.ltscontrol.ui.settings.SettingsPage
import de.ltsdesign.ltscontrol.ui.settings.settingsGroup
import de.ltsdesign.ltscontrol.ui.theme.greenTonalButtonColors
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
private sealed interface MoreDestination : NavKey

@Serializable
private data object MoreHome : MoreDestination

@Serializable
private data object InfoPage : MoreDestination

@Serializable
private data object ChangelogPage : MoreDestination

@Composable
fun MoreScreen(bleManager: BleManager, pairing: DevicePairing, firmwareUpdates: FirmwareUpdateRepository) {
    val backStack = rememberNavBackStack(MoreHome)
    val back: () -> Unit = { backStack.removeLastOrNull() }

    NavDisplay(
        backStack = backStack,
        onBack = back,
        // The slide from the Navigation 3 docs with Compose's default spring, like the settings.
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) togetherWith slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith slideOutHorizontally(targetOffsetX = { it })
        },
        entryProvider = entryProvider {
            entry<MoreHome> { AboutPage(bleManager, firmwareUpdates, onNavigate = { backStack.add(it) }) }
            entry<InfoPage> { InfoPage(bleManager, pairing, back) }
            entry<ChangelogPage> { FirmwareChangelogPage(back) }
        },
    )
}

/** Board name as reported over Bluetooth, shown as the board type. */
@Composable
private fun boardTypeLabel(boardVersion: String?, variant: BoardVariant): String? {
    val raw = boardVersion ?: return null
    val isPro = variant == BoardVariant.Pro
    return when {
        "CtrBoard V3" in raw -> stringResource(R.string.control_board_v3)
        "CtrBoard V4" in raw || "LTS CB" in raw ->
            stringResource(if (isPro) R.string.control_board_pro else R.string.control_board)
        "LTS DB" in raw -> stringResource(if (isPro) R.string.driver_board_pro else R.string.driver_board)
        raw.contains("esp32 PCB", ignoreCase = true) -> stringResource(R.string.driver_board)
        else -> null
    }
}

@Composable
private fun AboutPage(
    bleManager: BleManager,
    firmwareUpdates: FirmwareUpdateRepository,
    onNavigate: (MoreDestination) -> Unit,
) {
    val status = bleManager.status
    val isConnected = bleManager.isConnected
    val boardFirmware = status.firmwareVersion?.takeIf { it.isNotEmpty() }

    // Checks for new firmware on appear, on every (dis)connect and when the app returns to the foreground.
    LaunchedEffect(isConnected) { firmwareUpdates.refresh() }
    var resumeCount by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeCount++
        onPauseOrDispose { }
    }
    LaunchedEffect(resumeCount) { if (resumeCount > 1) firmwareUpdates.refresh() }

    // Right after connecting the board hasn't reported Wi‑Fi and firmware yet, so the update button waits a moment.
    // Counted from the connection, not from opening the tab, which recreates this page every time.
    fun settleRemainingMs() =
        if (isConnected) bleManager.connectedAt + 1_500 - SystemClock.elapsedRealtime() else 0L
    var isSettlingAfterConnect by remember { mutableStateOf(settleRemainingMs() > 0) }
    LaunchedEffect(isConnected) {
        val remaining = settleRemainingMs()
        isSettlingAfterConnect = remaining > 0
        if (remaining > 0) {
            delay(remaining)
            isSettlingAfterConnect = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        AboutHeader(boardTypeLabel(status.boardVersion, status.boardVariant), boardFirmware)
        Spacer(Modifier.weight(1f))
        ActionCards(
            listOf(
                CardAction(R.string.info, Icons.Rounded.Info) { onNavigate(InfoPage) },
                CardAction(R.string.version_history, Icons.Rounded.History) { onNavigate(ChangelogPage) },
            ),
        )
        Spacer(Modifier.height(16.dp))
        UpdateCard(bleManager, firmwareUpdates, boardFirmware, isSettlingAfterConnect)
    }

    OtaResultDialog(bleManager, onShowChanges = { onNavigate(ChangelogPage) })
}

@Composable
private fun AboutHeader(boardType: String?, boardFirmware: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            // The app icon, cropped to a circle like on iOS; the night resource is the dark icon.
            painter = painterResource(R.drawable.app_icon),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(180.dp).clip(CircleShape),
        )
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
        val secondary = MaterialTheme.colorScheme.onSurfaceVariant
        if (boardType != null) {
            Text(boardType, style = MaterialTheme.typography.bodyMedium, color = secondary)
            Spacer(Modifier.height(4.dp))
        }
        Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = secondary)
        if (boardFirmware != null) {
            Text(stringResource(R.string.board_version, boardFirmware), style = MaterialTheme.typography.bodyMedium, color = secondary)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateCard(
    bleManager: BleManager,
    firmwareUpdates: FirmwareUpdateRepository,
    boardFirmware: String?,
    isSettlingAfterConnect: Boolean,
) {
    val status = bleManager.status
    val isConnected = bleManager.isConnected
    val isWifiConnected = isConnected && status.wifiConnected == true
    val isUpdateAvailable = firmwareUpdates.isUpdateAvailable(boardFirmware)
    val isUpdating = bleManager.deviceState == DeviceState.Updating
    val canUpdate = isUpdateAvailable && isWifiConnected && !isSettlingAfterConnect
    // The V3 Control Board is stuck on firmware 1.0.0 and gets no more updates.
    val isV3Blocked = status.boardVersion?.contains("CtrBoard V3") == true && boardFirmware == "1.0.0"
    val latest = firmwareUpdates.latestVersion

    val statusText = when {
        isV3Blocked -> stringResource(R.string.updates_not_supported)
        !isConnected -> stringResource(R.string.board_not_connected)
        isUpdating -> stringResource(R.string.updating_firmware)
        status.otaSuccess == true -> stringResource(R.string.firmware_up_to_date)
        status.otaSuccess == false -> stringResource(R.string.update_failed)
        isUpdateAvailable && boardFirmware != null && latest != null ->
            stringResource(R.string.update_available, boardFirmware, latest)
        else -> stringResource(R.string.firmware_up_to_date)
    }

    SettingsCard {
        Column(Modifier.padding(bottom = 16.dp)) {
            SettingsItem(stringResource(R.string.board_update), summary = statusText)
            // Medium size like the full-width buttons of the system Settings.
            val buttonHeight = ButtonDefaults.MediumContainerHeight
            Button(
                onClick = { if (!isV3Blocked) bleManager.triggerOtaUpdate() },
                enabled = !isV3Blocked && canUpdate && !isUpdating,
                colors = greenTonalButtonColors(SettingsItemColor),
                shapes = ButtonDefaults.shapesFor(buttonHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = buttonHeight),
            ) {
                when {
                    isV3Blocked -> Text(stringResource(R.string.not_supported), style = ButtonDefaults.textStyleFor(buttonHeight))
                    isUpdating -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = LocalContentColor.current)
                    isUpdateAvailable && !isWifiConnected && isConnected ->
                        Text(stringResource(R.string.no_wifi_connection), style = ButtonDefaults.textStyleFor(buttonHeight))
                    else -> Text(stringResource(R.string.update_firmware), style = ButtonDefaults.textStyleFor(buttonHeight))
                }
            }
        }
    }
}

@Composable
private fun OtaResultDialog(bleManager: BleManager, onShowChanges: () -> Unit) {
    if (!bleManager.showOtaAlert) return
    val success = bleManager.status.otaSuccess == true
    AlertDialog(
        onDismissRequest = bleManager::clearOtaStatus,
        title = { Text(stringResource(if (success) R.string.update_successful else R.string.update_failed)) },
        text = { Text(stringResource(if (success) R.string.ota_success_message else R.string.ota_failed_message)) },
        confirmButton = {
            TextButton(onClick = bleManager::clearOtaStatus) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = if (success) {
            {
                TextButton(onClick = {
                    onShowChanges()
                    bleManager.clearOtaStatus()
                }) { Text(stringResource(R.string.show_changes)) }
            }
        } else {
            null
        },
    )
}

@Composable
private fun InfoPage(bleManager: BleManager, pairing: DevicePairing, onBack: () -> Unit) {
    val context = LocalContext.current
    val connect = rememberConnectAction(bleManager, pairing)
    val open: (String) -> Unit = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    val feedbackMail = stringResource(R.string.feedback_mail)

    SettingsPage(title = stringResource(R.string.info), onBack = onBack) {
        settingsGroup {
            item {
                SettingsItem(
                    stringResource(R.string.connect),
                    summary = withBold(R.string.instruction_connect, R.string.instruction_connect_action),
                    icon = Icons.Rounded.SettingsInputAntenna,
                    onClick = connect,
                )
            }
            item {
                SettingsItem(
                    stringResource(R.string.board_firmware),
                    summary = stringResource(R.string.instruction_firmware),
                    icon = Icons.Rounded.ArrowCircleDown,
                )
            }
            item {
                SettingsItem(
                    stringResource(R.string.motor_strength),
                    summary = stringResource(R.string.instruction_strength),
                    icon = Icons.Rounded.WarningAmber,
                )
            }
            item {
                SettingsItem(
                    stringResource(R.string.auto_stop),
                    summary = stringResource(R.string.instruction_auto_stop),
                    icon = Icons.Rounded.StopCircle,
                )
            }
        }
        settingsGroup {
            item {
                SettingsItem(
                    stringResource(R.string.github),
                    summary = withBold(R.string.instruction_github, R.string.github),
                    icon = Icons.Rounded.Code,
                    onClick = { open(GITHUB_URL) },
                )
            }
            item {
                SettingsItem(
                    stringResource(R.string.feedback),
                    summary = withBold(R.string.instruction_feedback, R.string.feedback_mail),
                    icon = Icons.Rounded.Feedback,
                    onClick = { open("mailto:$feedbackMail") },
                )
            }
            item {
                SettingsItem(
                    stringResource(R.string.legal_notice),
                    summary = stringResource(R.string.imprint),
                    icon = Icons.Rounded.Business,
                )
            }
        }
    }
}

/** The text with its tappable part in bold, so the item reads as a button. */
@Composable
private fun withBold(@StringRes text: Int, @StringRes boldPart: Int): AnnotatedString {
    val full = stringResource(text)
    val part = stringResource(boldPart)
    return buildAnnotatedString {
        append(full)
        val start = full.indexOf(part)
        if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + part.length)
    }
}

private const val GITHUB_URL = "https://github.com/LukasT03/LTS-Respooler"
