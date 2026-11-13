package de.ltsdesign.ltscontrol.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.bluetooth.BleManager
import de.ltsdesign.ltscontrol.bluetooth.DeviceState
import de.ltsdesign.ltscontrol.data.AppPreferences
import de.ltsdesign.ltscontrol.ui.settings.SettingsBackground
import de.ltsdesign.ltscontrol.ui.settings.SettingsLargeTitleStyle
import de.ltsdesign.ltscontrol.ui.InteractiveSpool
import de.ltsdesign.ltscontrol.ui.settings.Banner
import de.ltsdesign.ltscontrol.ui.settings.BannerGrid
import de.ltsdesign.ltscontrol.ui.theme.StatusColors
import de.ltsdesign.ltscontrol.ui.theme.blueTonalButtonColors
import de.ltsdesign.ltscontrol.ui.theme.redTonalButtonColors

@Composable
fun ControlScreen(bleManager: BleManager, preferences: AppPreferences) {
    // Fades toward the control card part way to the lowest surface, lighter in light and darker in dark mode,
    // so the card in the tab bar's color stands out. Darkening shows more, so dark mode goes less far.
    val fade = if (isSystemInDarkTheme()) 0.15f else 0.5f
    val background = Brush.verticalGradient(
        0.35f to SettingsBackground,
        0.8f to lerp(SettingsBackground, MaterialTheme.colorScheme.surfaceContainerLowest, fade),
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
            val spoolSize = minOf(maxWidth * 0.7f, maxHeight * 0.84f, 300.dp)

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
                    isRunning = bleManager.isConnected && bleManager.deviceState == DeviceState.Running,
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
    @Composable get() = SettingsLargeTitleStyle

@Composable
private fun StatusPills(bleManager: BleManager, preferences: AppPreferences) {
    val status = bleManager.status
    val isConnected = bleManager.isConnected

    val chipTemperature = if (isConnected) status.chipTemperature else null
    val isTemperatureCritical = (chipTemperature ?: 0) >= 65
    val hasFilament = isConnected && status.hasFilament
    val fanOn = isConnected && status.isFanOn
    val fanUnsupported = status.boardVersion == "CtrBoard V3"

    // Colored like on iOS while the state is active; off, disconnected or missing they keep the text color.
    BannerGrid(
        listOf(
            Banner(
                R.string.status_connection,
                stringResource(if (isConnected) R.string.connected else R.string.disconnected),
                if (isConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.BluetoothDisabled,
                iconTint = if (isConnected) StatusColors.cyan else null,
            ),
            Banner(
                R.string.status_temperature,
                stringResource(R.string.chip_temperature, chipTemperature?.let(preferences::formatTemperature) ?: "–"),
                Icons.Rounded.DeviceThermostat,
                iconTint = when {
                    chipTemperature == null -> null
                    isTemperatureCritical -> StatusColors.red
                    else -> StatusColors.green
                },
            ),
            Banner(
                R.string.status_filament,
                stringResource(if (hasFilament) R.string.detected else R.string.not_detected),
                if (hasFilament) Icons.Rounded.CheckCircleOutline else Icons.Rounded.HighlightOff,
                iconTint = if (hasFilament) StatusColors.purple else null,
            ),
            Banner(
                R.string.status_fan,
                when {
                    fanUnsupported -> stringResource(R.string.not_supported)
                    fanOn -> stringResource(R.string.fan_on, status.fanSpeed)
                    else -> stringResource(R.string.off)
                },
                if (fanOn && !fanUnsupported) Icons.Rounded.Air else Icons.Rounded.ModeFanOff,
                iconTint = if (fanOn && !fanUnsupported) MaterialTheme.colorScheme.primary else null,
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
    val leftLabel = when {
        state == DeviceState.AutoStop -> LeftLabel.Error
        isActive -> LeftLabel.Progress
        else -> LeftLabel.None
    }
    // Tabular figures like monospacedDigit on iOS, so the numbers don't shift while they count.
    val numberStyle = StatusTextStyle.copy(fontFeatureSettings = "tnum")

    Column(modifier.padding(horizontal = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Fades in and out with the state; the counting progress itself just updates.
            AnimatedContent(
                targetState = leftLabel,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentAlignment = Alignment.CenterStart,
                label = "progress",
                modifier = Modifier.width(72.dp),
            ) { label ->
                when (label) {
                    LeftLabel.Error -> Text(stringResource(R.string.error), style = StatusTextStyle, color = StatusColors.red)
                    LeftLabel.Progress -> Text("${Math.round(status.progress ?: 0.0)} %", style = numberStyle)
                    // Keeps the row's height.
                    LeftLabel.None -> Text("", style = StatusTextStyle)
                }
            }
            Text(
                text = stringResource(stateLabel(bleManager)),
                style = StatusTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            // Fades like the progress; it keeps its width, so the state text stays centered.
            val timeAlpha by animateFloatAsState(if (isActive || state == DeviceState.AutoStop) 1f else 0f)
            val seconds = maxOf(0, status.remainingTime ?: 0)
            Text(
                "-%02d:%02d".format(seconds / 60, seconds % 60),
                style = numberStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.width(72.dp).alpha(timeAlpha),
            )
        }
        Spacer(Modifier.height(12.dp))
        // Colored like on iOS: red on an error once there is progress, green when done and orange
        // while paused; on an error and while paused the track takes the color too.
        val progress = ((status.progress ?: 0.0) / 100).toFloat().coerceIn(0f, 1f)
        // The board reports the progress in steps, so the bar glides between them.
        val animatedProgress by animateFloatAsState(progress, ProgressIndicatorDefaults.ProgressAnimationSpec)
        val isError = state == DeviceState.AutoStop && progress > 0f
        val red = StatusColors.red
        val orange = StatusColors.orange
        val color by animateColorAsState(
            when {
                isError -> red
                state == DeviceState.Done -> StatusColors.green
                state == DeviceState.Paused -> orange
                else -> MaterialTheme.colorScheme.primary
            },
            ColorTransition,
        )
        val trackColor by animateColorAsState(
            when {
                isError -> red.copy(alpha = 0.2f)
                state == DeviceState.Paused -> orange.copy(alpha = 0.2f)
                else -> ProgressIndicatorDefaults.linearTrackColor
            },
            ColorTransition,
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            color = color,
            trackColor = trackColor,
            modifier = Modifier.fillMaxWidth().height(12.dp),
        )
        Spacer(Modifier.height(4.dp))
    }
}

private enum class LeftLabel { None, Progress, Error }

/** Slow enough that the bar visibly fades to the new state's color. */
private val ColorTransition = tween<Color>(durationMillis = 600, easing = FastOutSlowInEasing)

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

    val haptics = LocalHapticFeedback.current
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
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = localSpeed,
                    onValueChange = {
                        // A tick for every percent while dragging, like on iOS.
                        if (it.toInt() != localSpeed.toInt()) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
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
