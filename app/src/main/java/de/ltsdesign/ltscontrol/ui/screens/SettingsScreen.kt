package de.ltsdesign.ltscontrol.ui.screens

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.bluetooth.BleManager
import de.ltsdesign.ltscontrol.bluetooth.BoardVariant
import de.ltsdesign.ltscontrol.bluetooth.DeviceState
import de.ltsdesign.ltscontrol.data.AppPreferences

import de.ltsdesign.ltscontrol.ui.settings.Banner
import de.ltsdesign.ltscontrol.ui.settings.ChoiceItem
import de.ltsdesign.ltscontrol.ui.settings.RadioItem
import de.ltsdesign.ltscontrol.ui.settings.SegmentedItem
import de.ltsdesign.ltscontrol.ui.settings.SettingsItem
import de.ltsdesign.ltscontrol.ui.settings.SettingsCustomItem
import de.ltsdesign.ltscontrol.ui.settings.SettingsPage
import de.ltsdesign.ltscontrol.ui.settings.SliderItem
import de.ltsdesign.ltscontrol.ui.settings.StepperItem
import de.ltsdesign.ltscontrol.ui.settings.SwitchItem
import de.ltsdesign.ltscontrol.ui.settings.settingsBanner
import de.ltsdesign.ltscontrol.ui.settings.settingsFooter
import de.ltsdesign.ltscontrol.ui.settings.settingsGroup
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
private sealed interface SettingsDestination : NavKey

@Serializable
private data object SettingsHome : SettingsDestination

@Serializable
private data object AmountSettings : SettingsDestination

@Serializable
private data object VariantSettings : SettingsDestination

@Serializable
private data object CalibrationSettings : SettingsDestination

@Composable
fun SettingsScreen(bleManager: BleManager, preferences: AppPreferences) {
    val backStack = rememberNavBackStack(SettingsHome)
    val back: () -> Unit = { backStack.removeLastOrNull() }

    NavDisplay(
        backStack = backStack,
        onBack = back,
        // The slide from the Navigation 3 docs with Compose's default spring.
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        entryProvider = entryProvider {
            entry<SettingsHome> { SettingsMain(bleManager, preferences, onNavigate = { backStack.add(it) }) }
            entry<AmountSettings> { AmountPage(bleManager, back) }
            entry<VariantSettings> { VariantPage(bleManager, back) }
            entry<CalibrationSettings> { CalibrationPage(bleManager, back) }
        },
    )
}

@Composable
private fun amountLabel(weight: Int): String = when (weight) {
    1 -> stringResource(R.string.amount_1kg)
    2 -> stringResource(R.string.amount_500g)
    3 -> stringResource(R.string.amount_250g)
    else -> stringResource(R.string.whole_spool)
}

@Composable
private fun variantLabel(variant: BoardVariant): String =
    stringResource(if (variant == BoardVariant.Pro) R.string.respooler_pro else R.string.respooler_v4)

private fun stateIcon(bleManager: BleManager): ImageVector {
    if (!bleManager.isConnected) return Icons.Rounded.BluetoothDisabled
    return when (bleManager.deviceState) {
        DeviceState.Running -> Icons.Rounded.PlayCircle
        DeviceState.Paused -> Icons.Rounded.PauseCircle
        DeviceState.Updating -> Icons.Rounded.Sync
        DeviceState.Done -> Icons.Rounded.CheckCircle
        DeviceState.AutoStop -> Icons.Rounded.Error
        DeviceState.Idle -> Icons.Rounded.PowerSettingsNew
    }
}

@Composable
private fun SettingsMain(
    bleManager: BleManager,
    preferences: AppPreferences,
    onNavigate: (SettingsDestination) -> Unit,
) {
    val status = bleManager.status
    val off = stringResource(R.string.off)
    val low = stringResource(R.string.low)
    val medium = stringResource(R.string.medium)
    val high = stringResource(R.string.high)

    val completionSounds = listOf(
        0 to off,
        1 to stringResource(R.string.simple),
        2 to stringResource(R.string.glissando),
        3 to "Star Wars",
    )
    val autoStopLevels = listOf(0 to off, 1 to low, 2 to medium, 3 to high)
    val sensorLevels = listOf(400 to low, 200 to medium, 100 to high)
    val sides = listOf("L" to stringResource(R.string.left), "R" to stringResource(R.string.right))
    val units = listOf(false to stringResource(R.string.celsius), true to stringResource(R.string.fahrenheit))
    val stateText = stringResource(stateLabel(bleManager))
    val powerLossNote = stringResource(
        if (status.boardVariant == BoardVariant.Pro) R.string.power_loss_note_pro else R.string.power_loss_note,
    )
    val amount = amountLabel(status.targetWeight)
    val variant = variantLabel(status.boardVariant)
    val referenceTime = stringResource(R.string.minutes_seconds, status.durationAt85 / 60, status.durationAt85 % 60)

    SettingsPage(title = stringResource(R.string.title_settings)) {
        settingsBanner(Banner(R.string.status, stateText, stateIcon(bleManager)))

        settingsGroup(header = R.string.general) {
            item { SettingsItem(stringResource(R.string.respool_amount), summary = amount, onClick = { onNavigate(AmountSettings) }) }
            item {
                ChoiceItem(stringResource(R.string.completion_sound), completionSounds, status.jingleStyle) {
                    bleManager.setJingleStyle(it)
                }
            }
            item {
                SliderItem(stringResource(R.string.led_brightness), status.ledBrightness, 0..100, step = 10) {
                    bleManager.setLed(it)
                }
            }
            item {
                SwitchItem(
                    title = stringResource(R.string.use_filament_sensor),
                    checked = status.useFilamentSensor,
                    summary = stringResource(R.string.use_filament_sensor_note),
                    onCheckedChange = bleManager::setUseFilamentSensor,
                )
            }
        }

        settingsGroup(header = R.string.motor) {
            item {
                SwitchItem(stringResource(R.string.reverse_direction), status.directionReversed) {
                    bleManager.setDirectionReversed(it)
                }
            }
            item {
                SliderItem(stringResource(R.string.strength), status.motorStrength, 80..120, step = 10) {
                    bleManager.setMotorStrength(it)
                }
            }
            item {
                ChoiceItem(
                    stringResource(R.string.auto_stop_sensitivity),
                    autoStopLevels,
                    status.torqueLimit,
                    enabled = !status.highSpeed,
                ) { bleManager.setTorqueLimit(it) }
            }
            item {
                SwitchItem(
                    title = stringResource(R.string.high_speed),
                    checked = status.highSpeed,
                    summary = stringResource(R.string.high_speed_note),
                    onCheckedChange = bleManager::setHighSpeed,
                )
            }
        }

        if (status.boardVariant == BoardVariant.Pro) {
            settingsGroup(header = R.string.servo) {
                item {
                    SettingsItem(stringResource(R.string.end_positions), onClick = { onNavigate(CalibrationSettings) })
                }
                item {
                    StepperItem(
                        title = stringResource(R.string.step_distance),
                        valueText = String.format(Locale.getDefault(), "%.2f mm", status.servoStepMm),
                        canDecrease = status.servoStepMm > 0.5,
                        canIncrease = status.servoStepMm < 4.0,
                        onDecrease = { bleManager.setServoStepMm(Math.round((status.servoStepMm - 0.01) * 100) / 100.0) },
                        onIncrease = { bleManager.setServoStepMm(Math.round((status.servoStepMm + 0.01) * 100) / 100.0) },
                    )
                }
                item {
                    ChoiceItem(stringResource(R.string.start_side), sides, status.servoHome) {
                        bleManager.setServoHome(it)
                    }
                }
            }
        }

        settingsGroup(header = R.string.cooling_fan) {
            item {
                SliderItem(stringResource(R.string.fan_speed), status.fanSpeed, 10..100, step = 10) {
                    bleManager.setFanSpeed(it)
                }
            }
            item {
                SwitchItem(
                    title = stringResource(R.string.fan_always_on),
                    checked = status.fanAlways,
                    summary = stringResource(R.string.fan_note),
                    onCheckedChange = bleManager::setFanAlways,
                )
            }
        }

        settingsGroup(header = R.string.advanced, footer = R.string.calibration_duration_note) {
            item {
                StepperItem(
                    title = stringResource(R.string.reference_time),
                    valueText = referenceTime,
                    canDecrease = status.durationAt85 > 5,
                    canIncrease = status.durationAt85 < 2000,
                    onDecrease = { bleManager.setDurationAt85((status.durationAt85 - 5).coerceAtLeast(5)) },
                    onIncrease = { bleManager.setDurationAt85((status.durationAt85 + 5).coerceAtMost(2000)) },
                )
            }
            if (status.supportsFirmware133Settings) {
                item {
                    ChoiceItem(
                        stringResource(R.string.sensor_sensitivity),
                        sensorLevels,
                        status.filamentLossMs,
                        enabled = status.useFilamentSensor,
                    ) { bleManager.setFilamentLossMs(it) }
                }
                item {
                    SwitchItem(
                        title = stringResource(R.string.power_loss_recovery),
                        checked = status.resumeAfterPowerLoss,
                        summary = powerLossNote,
                        onCheckedChange = bleManager::setResumeAfterPowerLoss,
                    )
                }
            }
        }

        settingsGroup(header = R.string.app_section) {
            item {
                ChoiceItem(stringResource(R.string.temperature_unit), units, preferences.temperatureInFahrenheit) {
                    preferences.temperatureInFahrenheit = it
                }
            }
            if (status.boardVariant != BoardVariant.Unknown) {
                item { SettingsItem(stringResource(R.string.variant), summary = variant, onClick = { onNavigate(VariantSettings) }) }
            }
        }
    }
}

@Composable
private fun AmountPage(bleManager: BleManager, onBack: () -> Unit) {
    val selected = bleManager.status.targetWeight
    val labels = (0..3).map { amountLabel(it) }
    SettingsPage(title = stringResource(R.string.respool_amount), onBack = onBack) {
        settingsGroup {
            item { RadioItem(labels[0], selected == 0, summary = stringResource(R.string.entire_spool_note)) { bleManager.setTargetWeight(0) } }
        }
        settingsGroup {
            for (weight in 1..3) {
                item { RadioItem(labels[weight], selected == weight) { bleManager.setTargetWeight(weight) } }
            }
        }
        settingsFooter(R.string.fixed_amount_note, R.string.amount_accuracy_note)
    }
}

@Composable
private fun VariantPage(bleManager: BleManager, onBack: () -> Unit) {
    val selected = bleManager.status.boardVariant
    SettingsPage(title = stringResource(R.string.variant), onBack = onBack) {
        settingsGroup {
            item {
                SettingsCustomItem {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for ((variant, image) in listOf(BoardVariant.Standard to R.drawable.respooler, BoardVariant.Pro to R.drawable.respooler_pro)) {
                            VariantOption(image, variantLabel(variant), selected == variant, Modifier.weight(1f)) {
                                bleManager.status.boardVariant = variant
                                bleManager.setBoardVariant(variant)
                            }
                        }
                    }
                }
            }
        }
        settingsFooter(R.string.variant_note)
    }
}

@Composable
private fun VariantOption(image: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(painterResource(image), label, Modifier.fillMaxWidth().height(120.dp), contentScale = ContentScale.Fit)
        Text(label, style = MaterialTheme.typography.titleMedium)
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun CalibrationPage(bleManager: BleManager, onBack: () -> Unit) {
    val status = bleManager.status
    var side by rememberSaveable { mutableStateOf(if (status.servoHome == "L") "L" else "R") }
    val angle = if (side == "L") status.servoAngleL else status.servoAngleR

    // The guide moves to the side being calibrated and back home when leaving.
    DisposableEffect(side) {
        bleManager.servoGoto(side)
        onDispose { }
    }
    DisposableEffect(Unit) { onDispose { bleManager.servoGoto("HOME") } }

    // The servo angle grows to the left, so the left arrow increases it.
    fun adjust(delta: Int) {
        val newAngle = (angle + delta).coerceIn(0, 180)
        if (side == "L") bleManager.setServoAngleL(newAngle) else bleManager.setServoAngleR(newAngle)
    }

    SettingsPage(title = stringResource(R.string.end_positions), onBack = onBack) {
        settingsGroup {
            item {
                SegmentedItem(
                    options = listOf("L" to stringResource(R.string.left_side), "R" to stringResource(R.string.right_side)),
                    selected = side,
                    onSelect = { side = it },
                )
            }
            item {
                SettingsCustomItem {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { adjust(1) }, enabled = angle < 180) {
                            Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.increase, stringResource(R.string.angle_title)))
                        }
                        Text(
                            stringResource(R.string.angle, angle),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalIconButton(onClick = { adjust(-1) }, enabled = angle > 0) {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.decrease, stringResource(R.string.angle_title)))
                        }
                    }
                }
            }
        }
        settingsFooter(R.string.calibration_note)
    }
}
