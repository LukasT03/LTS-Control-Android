@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lts.control.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lts.control.R
import com.lts.control.core.ble.BleViewModel
import com.lts.control.core.ble.model.BoardVariant
import com.lts.control.core.ble.model.DeviceState
import com.lts.control.core.ble.model.labelRes
import com.lts.control.ui.theme.*
import java.util.Locale

@Composable
fun SettingsScreen(
    vm: BleViewModel,
    onOpenRespoolAmount: () -> Unit,
    onOpenBoardVariant: () -> Unit = {},
    onOpenServoCalibration: () -> Unit = {}
) {
    val status by vm.status.collectAsState()
    val isConnected by vm.isConnected.collectAsState()
    val deviceState = status.deviceState
    val context = LocalContext.current

    val requestNotifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            vm.setNotificationsEnabled(granted)
        }
    )

    fun onNotificationToggle(checked: Boolean) {
        if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        vm.setNotificationsEnabled(checked)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 24.dp)
    ) {
        /* ------------------------------ Status-Kapsel ------------------------------ */
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
        ) {
            val (icon, tint) = when {
                !isConnected -> Icons.Rounded.SignalCellularConnectedNoInternet0Bar to MaterialTheme.colorScheme.onSurfaceVariant
                else -> when (deviceState) {
                    DeviceState.RUNNING   -> Icons.Rounded.PlayCircle   to StateNormalGreen
                    DeviceState.PAUSED    -> Icons.Rounded.PauseCircle  to StatePausedOrange
                    DeviceState.UPDATING  -> Icons.Rounded.SystemUpdateAlt to MaterialTheme.colorScheme.primary
                    DeviceState.DONE      -> Icons.Filled.Check         to StateDoneGreen
                    DeviceState.AUTO_STOP -> Icons.Rounded.Warning      to MaterialTheme.colorScheme.error
                    DeviceState.IDLE,
                    DeviceState.ERROR     -> Icons.Rounded.PowerSettingsNew to MaterialTheme.colorScheme.primary
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(25))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .height(50.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(16.dp))
                Icon(icon, contentDescription = null, tint = tint)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.set_status), style = MaterialTheme.typography.titleSmall)
                    val subtitleText = if (isConnected) stringResource(deviceState.labelRes()) else stringResource(R.string.common_disconnected)
                    AnimatedContent(
                        targetState = subtitleText,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                        label = "stateText"
                    ) { text ->
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
            }
        }

        /* ------------------------------ Konfiguration ------------------------------ */
        SectionHeader(stringResource(R.string.set_section_configuration))
        SettingsCard {
            // Jingle Picker
            val jingleNames = listOf(
                stringResource(R.string.jingle_off),
                stringResource(R.string.jingle_simple),
                stringResource(R.string.jingle_glissando),
                stringResource(R.string.jingle_star_wars)
            )
            DropdownRow(
                title = stringResource(R.string.set_completion_sound),
                options = jingleNames,
                selectedIndex = status.jingleStyle.coerceIn(0, jingleNames.lastIndex),
                onSelected = { vm.setJingle(it) }
            )

            // LED Brightness: Stepper (0-100, step 10) per §5.4
            StepperRow(
                title = stringResource(R.string.set_led_brightness),
                currentLabel = "${status.ledBrightness} %",
                minusEnabled = status.ledBrightness > 0,
                plusEnabled = status.ledBrightness < 100,
                onMinus = { vm.setLed(status.ledBrightness - 10) },
                onPlus = { vm.setLed(status.ledBrightness + 10) }
            )

            // Respool Amount
            val respoolLabel = when (status.targetWeight) {
                1 -> stringResource(R.string.weight_1_0kg)
                2 -> stringResource(R.string.weight_0_5kg)
                3 -> stringResource(R.string.weight_0_25kg)
                else -> stringResource(R.string.respool_entire_spool)
            }
            NavigationRow(
                title = stringResource(R.string.set_respool_amount),
                subtitle = respoolLabel,
                onClick = onOpenRespoolAmount
            )

            // Board Variant row (only when STD or PRO per §5.2)
            if (status.boardVariant == BoardVariant.STANDARD || status.boardVariant == BoardVariant.PRO) {
                val variantLabel = if (status.boardVariant == BoardVariant.PRO) {
                    stringResource(R.string.variant_pro)
                } else {
                    stringResource(R.string.variant_v4)
                }
                NavigationRow(
                    title = stringResource(R.string.set_variant),
                    subtitle = variantLabel,
                    onClick = onOpenBoardVariant
                )
            }

            // Filament Sensor Toggle
            ToggleRow(
                title = stringResource(R.string.set_use_filament_sensor),
                checked = status.useFilamentSensor,
                onCheckedChange = { vm.setFilamentSensor(it) }
            )
        }
        SectionFooter(stringResource(R.string.set_footer_filament_sensor))

        /* ------------------------------ Motor ------------------------------ */
        SectionHeader(stringResource(R.string.set_section_motor))
        SettingsCard {
            ToggleRow(
                title = stringResource(R.string.set_reverse_direction),
                checked = status.directionReversed,
                onCheckedChange = { vm.setDirectionReversed(it) }
            )

            StepperRow(
                title = stringResource(R.string.set_strength),
                currentLabel = "${status.motorStrength} %",
                minusEnabled = status.motorStrength > 80,
                plusEnabled = status.motorStrength < 120,
                onMinus = { vm.setMotorStrength(status.motorStrength - 10) },
                onPlus = { vm.setMotorStrength(status.motorStrength + 10) }
            )

            // Auto-stop sensitivity: Dropdown per §5.4
            val sensitivityNames = listOf(
                stringResource(R.string.sensitivity_off),
                stringResource(R.string.sensitivity_low),
                stringResource(R.string.sensitivity_medium),
                stringResource(R.string.sensitivity_high)
            )
            DropdownRow(
                title = stringResource(R.string.set_auto_stop_sensitivity),
                options = sensitivityNames,
                selectedIndex = status.torqueLimit.coerceIn(0, sensitivityNames.lastIndex),
                onSelected = { vm.setTorque(it) },
                enabled = !status.highSpeed
            )
        }
        SectionFooter(stringResource(R.string.set_footer_auto_stop))

        SettingsCard {
            ToggleRow(
                title = stringResource(R.string.set_high_speed),
                checked = status.highSpeed,
                onCheckedChange = { vm.setHighSpeed(it) }
            )
        }
        SectionFooter(stringResource(R.string.set_footer_high_speed))

        /* ------------------------------ Servo (Pro only §5.3) ------------------------------ */
        if (status.isProBoard) {
            SectionHeader(stringResource(R.string.set_section_servo))
            SettingsCard {
                NavigationRow(
                    title = stringResource(R.string.set_calibrate_end_positions),
                    subtitle = "",
                    onClick = onOpenServoCalibration
                )

                // Stepper: 0.50..4.00 mm, step 0.01
                StepperRow(
                    title = stringResource(R.string.servo_step_width, status.servoStepMm),
                    currentLabel = String.format(Locale.US, "%.2f mm", status.servoStepMm),
                    minusEnabled = status.servoStepMm > 0.50,
                    plusEnabled = status.servoStepMm < 4.00,
                    onMinus = { vm.setServoStepMm(maxOf(0.50, (status.servoStepMm - 0.01))) },
                    onPlus = { vm.setServoStepMm(minOf(4.00, (status.servoStepMm + 0.01))) }
                )

                // Segmented Control: Home side Links / Rechts
                val sideOptions = listOf(
                    stringResource(R.string.servo_side_left),
                    stringResource(R.string.servo_side_right)
                )
                val selectedSideIdx = if (status.servoHome.uppercase() == "L") 0 else 1
                SegmentedRow(
                    title = stringResource(R.string.set_section_servo),
                    options = sideOptions,
                    selectedIndex = selectedSideIdx,
                    onSelected = { idx ->
                        vm.setServoHome(if (idx == 0) "L" else "R")
                    }
                )
            }
            SectionFooter(stringResource(R.string.servo_footer))
        }

        /* ------------------------------ Lüfter ------------------------------ */
        SectionHeader(stringResource(R.string.set_section_fan))
        SettingsCard {
            StepperRow(
                title = stringResource(R.string.set_speed),
                currentLabel = "${status.fanSpeed} %",
                minusEnabled = status.fanSpeed > 10,
                plusEnabled = status.fanSpeed < 100,
                onMinus = { vm.setFanSpeed(status.fanSpeed - 10) },
                onPlus = { vm.setFanSpeed(status.fanSpeed + 10) }
            )

            ToggleRow(
                title = stringResource(R.string.set_fan_always_on),
                checked = status.fanAlways,
                onCheckedChange = { vm.setFanAlways(it) }
            )

            // Temperature Unit
            val unitOptions = listOf(stringResource(R.string.unit_celsius), stringResource(R.string.unit_fahrenheit))
            SegmentedRow(
                title = stringResource(R.string.set_temperature_unit),
                options = unitOptions,
                selectedIndex = if (status.temperatureInFahrenheit) 1 else 0,
                onSelected = { idx -> vm.setTemperatureInFahrenheit(idx == 1) }
            )
        }
        SectionFooter(stringResource(R.string.set_footer_fan))

        /* ------------------------------ Kalibrierung ------------------------------ */
        SectionHeader(stringResource(R.string.set_section_calibration))
        SettingsCard {
            val min = status.durationAt80 / 60
            val sec = status.durationAt80 % 60
            StepperRow(
                title = stringResource(R.string.set_duration),
                currentLabel = stringResource(R.string.duration_value, min, sec),
                minusEnabled = status.durationAt80 > 5,
                plusEnabled = status.durationAt80 < 2000,
                onMinus = { vm.setDurationAt80(maxOf(5, status.durationAt80 - 5)) },
                onPlus = { vm.setDurationAt80(minOf(2000, status.durationAt80 + 5)) }
            )
        }
        SectionFooter(stringResource(R.string.set_footer_calibration))

        /* ------------------------------ App ------------------------------ */
        SectionHeader(stringResource(R.string.set_section_app))
        SettingsCard {
            ToggleRow(
                title = stringResource(R.string.set_notifications),
                checked = status.notificationsEnabled,
                onCheckedChange = { onNotificationToggle(it) }
            )

            ToggleRow(
                title = stringResource(R.string.set_ongoing_notification),
                checked = status.ongoingNotificationEnabled,
                onCheckedChange = { vm.setOngoingNotificationEnabled(it) }
            )

            LanguagePickerPreferenceRow()
        }
        SectionFooter(stringResource(R.string.set_footer_notifications))
    }
}

/* -------------------------------------------------------------------------- */
/*                        Respool Amount (Subscreen/Sheet)                    */
/* -------------------------------------------------------------------------- */

@Composable
fun RespoolAmountScreen(
    vm: BleViewModel,
    onBack: () -> Unit
) {
    val status by vm.status.collectAsState()
    val current = status.targetWeight

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.respool_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeaderSpacer()
            SettingsCard {
                SingleChoiceRow(
                    title = stringResource(R.string.respool_entire_spool),
                    selected = current == 0,
                    onClick = { if (current != 0) vm.setTargetWeight(0) }
                )
            }
            SectionFooter(stringResource(R.string.respool_footer_entire))

            SectionHeaderSpacer()
            SettingsCard {
                listOf(
                    1 to stringResource(R.string.weight_1_0kg),
                    2 to stringResource(R.string.weight_0_5kg),
                    3 to stringResource(R.string.weight_0_25kg)
                ).forEach { (tag, label) ->
                    SingleChoiceRow(
                        title = label,
                        selected = current == tag,
                        onClick = { if (current != tag) vm.setTargetWeight(tag) }
                    )
                }
            }
            SectionFooter(stringResource(R.string.respool_footer_fixed))
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                                   UI Bits                                  */
/* -------------------------------------------------------------------------- */

@Composable private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable private fun SectionHeaderSpacer() { Spacer(Modifier.height(8.dp)) }

@Composable private fun SectionFooter(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    )
    HorizontalDivider()
}

@Composable private fun NavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    )
    HorizontalDivider()
}

@Composable private fun StepperRow(
    title: String,
    currentLabel: String,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onMinus, enabled = minusEnabled) { Text("–", style = MaterialTheme.typography.titleLarge) }
                Text(currentLabel, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onPlus, enabled = plusEnabled) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
        }
    )
    HorizontalDivider()
}

@Composable private fun DropdownRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        modifier = if (enabled) Modifier.clickable { expanded = true } else Modifier,
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Box {
                Text(
                    text = options.getOrElse(selectedIndex) { "" },
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyMedium
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                expanded = false
                                onSelected(index)
                            },
                            trailingIcon = if (index == selectedIndex) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }
        }
    )
    HorizontalDivider()
}

@Composable private fun SegmentedRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            SingleChoiceSegmentedButtonRow {
                options.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = index == selectedIndex,
                        onClick = { onSelected(index) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }
        }
    )
    HorizontalDivider()
}

@Composable private fun SingleChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    )
    HorizontalDivider()
}
