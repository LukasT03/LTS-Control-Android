package de.ltsdesign.ltscontrol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.ModeFanOff
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.bluetooth.BleManager
import de.ltsdesign.ltscontrol.bluetooth.DeviceState
import de.ltsdesign.ltscontrol.data.AppPreferences
import de.ltsdesign.ltscontrol.ui.settings.SettingsBackground
import de.ltsdesign.ltscontrol.ui.settings.SettingsTitleStyle
import de.ltsdesign.ltscontrol.ui.InteractiveSpool
import de.ltsdesign.ltscontrol.ui.settings.Banner
import de.ltsdesign.ltscontrol.ui.settings.BannerGrid
import de.ltsdesign.ltscontrol.ui.theme.blueTonalButtonColors
import de.ltsdesign.ltscontrol.ui.theme.redTonalButtonColors

@Composable
fun ControlScreen(bleManager: BleManager, preferences: AppPreferences) {
    // Fades toward the control card halfway to the lowest surface, lighter in light and darker in dark mode,
    // so the card in the tab bar's color stands out.
    val background = Brush.verticalGradient(
        0.35f to SettingsBackground,
        0.8f to lerp(SettingsBackground, MaterialTheme.colorScheme.surfaceContainerLowest, 0.5f),
    )
    Column(Modifier.fillMaxSize().background(background)) {
        // Same height and inset as the top bar of the other tabs, and the cards 8 dp below it like there.
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.height(56.dp).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
                Text(stringResource(R.string.title_respooler), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal))
            }
            StatusPills(bleManager, preferences)
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
        ) {
            val spoolSize = minOf(maxWidth * 0.64f, maxHeight * 0.78f, 280.dp)

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                InteractiveSpool(
                    imageResource = R.drawable.spool,
                    contentDescription = stringResource(R.string.spool),
                    rotateClockwiseLabel = stringResource(R.string.rotate_spool_clockwise),
                    rotateCounterClockwiseLabel = stringResource(R.string.rotate_spool_counterclockwise),
                    modifier = Modifier.size(spoolSize),
                    alpha = 0.72f,
                )
                Spacer(Modifier.weight(1f))
            }
        }
        StatusProgress(bleManager, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        Controls(bleManager)
    }
}

/** Progress, state, remaining time and speed: a bit larger than the settings item titles. */
private val StatusTextStyle: TextStyle
    @Composable get() = SettingsTitleStyle.copy(fontSize = 18.sp, lineHeight = 24.sp)

@Composable
private fun StatusPills(bleManager: BleManager, preferences: AppPreferences) {
    val status = bleManager.status
    val isConnected = bleManager.isConnected

    val chipTemperature = if (isConnected) status.chipTemperature else null
    val isTemperatureCritical = (chipTemperature ?: 0) >= 65
    val hasFilament = isConnected && status.hasFilament
    val fanOn = isConnected && status.isFanOn
    val fanUnsupported = status.boardVersion == "CtrBoard V3"

    BannerGrid(
        listOf(
            Banner(
                R.string.status_connection,
                stringResource(if (isConnected) R.string.connected else R.string.disconnected),
                if (isConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.BluetoothDisabled,
            ),
            Banner(
                R.string.status_temperature,
                stringResource(R.string.chip_temperature, chipTemperature?.let(preferences::formatTemperature) ?: "–"),
                Icons.Rounded.DeviceThermostat,
                iconTint = if (isTemperatureCritical) MaterialTheme.colorScheme.error else null,
            ),
            Banner(
                R.string.status_filament,
                stringResource(if (hasFilament) R.string.detected else R.string.not_detected),
                if (hasFilament) Icons.Rounded.CheckCircleOutline else Icons.Rounded.HighlightOff,
            ),
            Banner(
                R.string.status_fan,
                when {
                    fanUnsupported -> stringResource(R.string.not_supported)
                    fanOn -> stringResource(R.string.fan_on, status.fanSpeed)
                    else -> stringResource(R.string.off)
                },
                if (fanOn && !fanUnsupported) Icons.Rounded.Air else Icons.Rounded.ModeFanOff,
            ),
        ),
        columns = 2,
    )
}

@Composable
private fun StatusProgress(bleManager: BleManager, modifier: Modifier = Modifier) {
    val state = bleManager.deviceState
    val status = bleManager.status
    val isActive = state == DeviceState.Running || state == DeviceState.Paused

    Column(modifier.padding(horizontal = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    state == DeviceState.AutoStop -> stringResource(R.string.error)
                    isActive -> "${Math.round(status.progress ?: 0.0)} %"
                    else -> ""
                },
                style = StatusTextStyle,
                color = if (state == DeviceState.AutoStop) MaterialTheme.colorScheme.error else LocalContentColor.current,
                modifier = Modifier.width(72.dp),
            )
            Text(
                text = stringResource(stateLabel(bleManager)),
                style = StatusTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (isActive || state == DeviceState.AutoStop) {
                    val seconds = maxOf(0, status.remainingTime ?: 0)
                    "-%02d:%02d".format(seconds / 60, seconds % 60)
                } else {
                    ""
                },
                style = StatusTextStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.width(72.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { ((status.progress ?: 0.0) / 100).toFloat().coerceIn(0f, 1f) },
            color = when (state) {
                DeviceState.AutoStop -> MaterialTheme.colorScheme.error
                DeviceState.Paused -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.fillMaxWidth().height(12.dp),
        )
        Spacer(Modifier.height(4.dp))
    }
}

internal fun stateLabel(bleManager: BleManager): Int {
    if (!bleManager.isConnected) return R.string.not_connected
    return when (bleManager.deviceState) {
        DeviceState.Idle -> R.string.state_idle
        DeviceState.Running -> R.string.state_running
        DeviceState.Paused -> R.string.state_paused
        DeviceState.Updating -> R.string.state_updating
        DeviceState.AutoStop -> R.string.state_auto_stop
        DeviceState.Done -> R.string.state_done
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Controls(bleManager: BleManager) {
    // Medium size like "Send Credentials" and the full-width buttons of the system Settings.
    val buttonHeight = ButtonDefaults.MediumContainerHeight
    val status = bleManager.status
    val state = bleManager.deviceState
    val isConnected = bleManager.isConnected
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val primaryColors = blueTonalButtonColors(cardColor)
    val stopColors = redTonalButtonColors(cardColor)

    // Slider position; follows the board's speed unless the user is dragging.
    var localSpeed by remember { mutableFloatStateOf(status.speedPercent.toFloat()) }
    var isEditingSpeed by remember { mutableStateOf(false) }
    LaunchedEffect(status.speedPercent, isConnected) {
        if (!isEditingSpeed) localSpeed = status.speedPercent.toFloat()
    }

    // Runs edge to edge down to the tab bar in its color, so both read as one surface; only the top corners are rounded.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp),
        color = cardColor,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state == DeviceState.Running) {
                    Button(
                        onClick = bleManager::pauseMotor,
                        enabled = isConnected,
                        colors = primaryColors,
                        shapes = ButtonDefaults.shapesFor(buttonHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                        modifier = Modifier.weight(1f).heightIn(min = buttonHeight),
                    ) {
                        Text(stringResource(R.string.pause), style = ButtonDefaults.textStyleFor(buttonHeight))
                    }
                } else {
                    Button(
                        onClick = bleManager::startMotor,
                        enabled = isConnected && state != DeviceState.Updating &&
                            (!status.useFilamentSensor || status.hasFilament),
                        colors = primaryColors,
                        shapes = ButtonDefaults.shapesFor(buttonHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                        modifier = Modifier.weight(1f).heightIn(min = buttonHeight),
                    ) {
                        Text(stringResource(R.string.start), style = ButtonDefaults.textStyleFor(buttonHeight))
                    }
                }
                Button(
                    onClick = bleManager::stopMotor,
                    enabled = isConnected && state != DeviceState.Updating,
                    colors = stopColors,
                    shapes = ButtonDefaults.shapesFor(buttonHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                    modifier = Modifier.weight(1f).heightIn(min = buttonHeight),
                ) {
                    Text(stringResource(R.string.stop), style = ButtonDefaults.textStyleFor(buttonHeight))
                }
            }
            // Same space above and below, so the slider sits centered between the buttons and the card edge.
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = localSpeed,
                    onValueChange = {
                        isEditingSpeed = true
                        localSpeed = it
                    },
                    onValueChangeFinished = {
                        isEditingSpeed = false
                        if (isConnected) bleManager.setSpeedPercent(localSpeed.toInt())
                    },
                    valueRange = 50f..100f,
                    modifier = Modifier.weight(1f),
                )
                // An invisible "100 %" reserves the widest value at any text size, so the slider keeps its length.
                Box(Modifier.padding(start = 12.dp), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        "100 %",
                        style = StatusTextStyle,
                        modifier = Modifier.alpha(0f).clearAndSetSemantics { },
                    )
                    Text(
                        "${localSpeed.toInt()} %",
                        style = StatusTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
