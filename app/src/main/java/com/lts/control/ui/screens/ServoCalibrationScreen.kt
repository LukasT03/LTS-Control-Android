package com.lts.control.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lts.control.R
import com.lts.control.core.ble.BleViewModel
import com.lts.control.core.ble.model.DeviceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServoCalibrationScreen(
    vm: BleViewModel,
    onBack: () -> Unit
) {
    val status by vm.status.collectAsState()
    val isConnected by vm.isConnected.collectAsState()
    val haptic = LocalHapticFeedback.current

    var currentSide by remember { mutableStateOf(if (status.servoHome.uppercase() == "L") "L" else "R") }

    val currentAngle = if (currentSide == "L") status.servoAngleL else status.servoAngleR

    val isMotorIdleOrDone = status.deviceState == DeviceState.IDLE || status.deviceState == DeviceState.DONE
    val isCalibrationAllowed = isConnected && isMotorIdleOrDone

    DisposableEffect(Unit) {
        val initialSide = if (status.servoHome.uppercase() == "L") "L" else "R"
        currentSide = initialSide
        if (isCalibrationAllowed) {
            vm.servoGoto(initialSide)
        }
        onDispose {
            if (isConnected) {
                vm.servoGoto("HOME")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.set_calibrate_end_positions)) },
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
                .padding(16.dp)
        ) {
            if (!isCalibrationAllowed) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.servo_calibration_disabled_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    // Segmented L/R side picker
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = currentSide == "L",
                            onClick = {
                                currentSide = "L"
                                if (isCalibrationAllowed) vm.servoGoto("L")
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            enabled = isCalibrationAllowed
                        ) {
                            Text(stringResource(R.string.servo_side_left))
                        }
                        SegmentedButton(
                            selected = currentSide == "R",
                            onClick = {
                                currentSide = "R"
                                if (isCalibrationAllowed) vm.servoGoto("R")
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            enabled = isCalibrationAllowed
                        ) {
                            Text(stringResource(R.string.servo_side_right))
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Angle Adjustment Row with preserved inversion:
                    // Chevron-left increments toward 180, chevron-right decrements toward 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                if (currentAngle < 180 && isCalibrationAllowed) {
                                    val newAngle = currentAngle + 1
                                    if (currentSide == "L") {
                                        vm.setServoAngleL(newAngle)
                                    } else {
                                        vm.setServoAngleR(newAngle)
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            enabled = isCalibrationAllowed && currentAngle < 180,
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "+1°",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = stringResource(R.string.servo_angle_label, currentAngle),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            textAlign = TextAlign.Center
                        )

                        FilledTonalIconButton(
                            onClick = {
                                if (currentAngle > 0 && isCalibrationAllowed) {
                                    val newAngle = currentAngle - 1
                                    if (currentSide == "L") {
                                        vm.setServoAngleL(newAngle)
                                    } else {
                                        vm.setServoAngleR(newAngle)
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            enabled = isCalibrationAllowed && currentAngle > 0,
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "-1°",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.servo_calibration_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
