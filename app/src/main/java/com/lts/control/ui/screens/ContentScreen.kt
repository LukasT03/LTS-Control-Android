package com.lts.control.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lts.control.R
import com.lts.control.core.ble.BleViewModel
import com.lts.control.core.ble.model.DeviceState
import com.lts.control.core.ble.model.homeLabelRes
import com.lts.control.core.state.DeviceStatus
import com.lts.control.ui.theme.*
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ContentScreen(
    vm: BleViewModel,
    onDismissSplashIfConnected: () -> Unit = {}
) {
    val status by vm.status.collectAsState()
    val isConnected by vm.isConnected.collectAsState()

    LaunchedEffect(isConnected) {
        if (isConnected) onDismissSplashIfConnected()
    }

    val deviceState = status.deviceState
    val isPaused = deviceState == DeviceState.PAUSED
    val isDone = deviceState == DeviceState.DONE
    val isError = deviceState == DeviceState.AUTO_STOP

    val barColor = when {
        isError -> MaterialTheme.colorScheme.error
        isDone -> StateDoneGreen
        isPaused -> StatePausedOrange
        else -> MaterialTheme.colorScheme.primary
    }
    val barBgColor = when {
        isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.20f)
        isPaused -> StatePausedOrange.copy(alpha = 0.20f)
        else -> Color.Gray.copy(alpha = 0.14f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // Pills (TOP, fixed)
            PillRow(
                status = status,
                isConnected = isConnected
            )

            // Spool (MIDDLE, flexible – fills available space)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                InteractiveSpool(deviceState = deviceState)
            }

            // Status & Controls (BOTTOM, fixed)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 8.dp)
            ) {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    val progressValue = if (deviceState == DeviceState.IDLE) 0f else status.progress.coerceIn(0f, 100f)

                    // Three-column status row above progress bar
                    StatusHeaderRow(
                        deviceState = deviceState,
                        progress = status.progress,
                        remainingSeconds = status.remainingTime
                    )

                    Spacer(Modifier.height(10.dp))

                    StatusProgress(
                        fraction = progressValue / 100f,
                        bar = barColor,
                        track = barBgColor
                    )
                }

                Spacer(Modifier.height(16.dp))

                val isStartEnabled = !((status.useFilamentSensor && !status.hasFilament) || !isConnected || deviceState == DeviceState.UPDATING)
                val isStopEnabled = isConnected && deviceState != DeviceState.UPDATING

                ControlCard(
                    speed = status.speedPercent.toFloat(),
                    onSpeedChange = { vm.setSpeed(it.toInt()) },
                    isRunning = (deviceState == DeviceState.RUNNING),
                    isStartEnabled = isStartEnabled,
                    isStopEnabled = isStopEnabled,
                    onStartOrPause = {
                        if (deviceState == DeviceState.RUNNING) vm.pause() else vm.start()
                    },
                    onStop = { vm.stop() }
                )
            }
        }
    }
}

@Composable
private fun StatusHeaderRow(
    deviceState: DeviceState,
    progress: Float,
    remainingSeconds: Int?
) {
    val leftText = when (deviceState) {
        DeviceState.AUTO_STOP -> stringResource(R.string.home_state_error)
        DeviceState.RUNNING, DeviceState.PAUSED -> stringResource(R.string.percent_value, progress.toInt())
        else -> ""
    }
    val leftColor = if (deviceState == DeviceState.AUTO_STOP) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    val showTime = (deviceState == DeviceState.RUNNING || deviceState == DeviceState.AUTO_STOP || deviceState == DeviceState.PAUSED)
    val rightText = if (showTime && remainingSeconds != null) {
        val t = maxOf(0, remainingSeconds)
        String.format(Locale.US, "-%02d:%02d", t / 60, t % 60)
    } else ""

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(70.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = leftText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                color = leftColor,
                maxLines = 1
            )
        }

        AnimatedContent(
            targetState = deviceState,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            modifier = Modifier.weight(1f),
            label = "state-label"
        ) { state ->
            Text(
                text = stringResource(state.homeLabelRes()),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )
        }

        Box(modifier = Modifier.width(70.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = rightText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun InteractiveSpool(deviceState: DeviceState) {
    var rotation by remember { mutableFloatStateOf(0f) }
    var angularVel by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var lastAngle by remember { mutableStateOf<Float?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    // Physics constants matching iOS: maxVel = 5, accel = 0.01, decel = 0.05, friction = 0.98
    LaunchedEffect(deviceState) {
        val maxVel = 5f
        val accel = 0.01f
        val decel = 0.05f
        val friction = 0.98f
        var lastNanos = withFrameNanos { it }

        while (true) {
            val nowNanos = withFrameNanos { it }
            val dt = (nowNanos - lastNanos) / 1_000_000_000f
            lastNanos = nowNanos

            val targetVel = when (deviceState) {
                DeviceState.RUNNING -> maxVel
                DeviceState.UPDATING -> 2f
                else -> 0f
            }

            // Idle with the spool at rest: stop requesting frames until the user grabs it
            if (targetVel == 0f && !isDragging && abs(angularVel) < 0.01f) {
                angularVel = 0f
                snapshotFlow { isDragging }.first { it }
                lastNanos = withFrameNanos { it }
                continue
            }

            if (!isDragging) {
                val smoothing = if (targetVel > angularVel) accel else decel
                angularVel += (targetVel - angularVel) * smoothing
                angularVel *= friction
                val clamped = angularVel.coerceIn(-15f, 15f)
                rotation = (rotation + clamped * dt * 60f) % 360f
            }
        }
    }

    val center by remember(boxSize) {
        mutableStateOf(Offset(boxSize.width / 2f, boxSize.height / 2f))
    }
    fun angleAt(p: Offset): Float = Math.toDegrees(
        kotlin.math.atan2((p.y - center.y).toDouble(), (p.x - center.x).toDouble())
    ).toFloat()

    val deadZoneRadiusPx = remember(boxSize) { min(boxSize.width, boxSize.height) * 0.25f }

    Box(
        modifier = Modifier
            .fillMaxSize(0.75f)
            .aspectRatio(1f)
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val d = (pos - center).getDistance()
                        if (d >= deadZoneRadiusPx) {
                            isDragging = true
                            lastAngle = angleAt(pos)
                            angularVel = 0f
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        lastAngle = null
                    },
                    onDragCancel = {
                        isDragging = false
                        lastAngle = null
                    }
                ) { change, _ ->
                    val a = angleAt(change.position)
                    lastAngle?.let { prev ->
                        var delta = a - prev
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f
                        rotation = (rotation + delta) % 360f
                        angularVel = delta * 3f
                    }
                    lastAngle = a
                    change.consume()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Rotation is read in the draw phase so animating it doesn't recompose every frame
                .graphicsLayer { rotationZ = rotation },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.bambu_spool),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
            // Center cutout hub hole matching iOS diameter = size / 3.55
            Box(
                modifier = Modifier
                    .fillMaxSize(1f / 3.55f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }
}

@Composable
private fun PillRow(
    status: DeviceStatus,
    isConnected: Boolean
) {
    val isV3 = status.boardVersion?.contains("CtrBoard V3") == true
    val tempC = status.chipTemperature
    val isFahrenheit = status.temperatureInFahrenheit

    val tempLabel = when {
        tempC == null -> stringResource(R.string.value_none)
        isFahrenheit -> {
            val f = (tempC * 9 / 5.0 + 32).roundToInt()
            stringResource(R.string.pill_chip_temp_f, f)
        }
        else -> stringResource(R.string.pill_chip_temp_c, tempC)
    }

    val tempIconTint = when {
        tempC == null -> MaterialTheme.colorScheme.onSurfaceVariant
        tempC >= 65 -> StateCriticalRed
        else -> StateNormalGreen
    }

    val fanOn = isConnected && status.isFanOn
    val fanSubtitle = when {
        isV3 -> stringResource(R.string.pill_fan_not_supported)
        fanOn -> stringResource(R.string.pill_fan_on, status.fanSpeed)
        else -> stringResource(R.string.pill_fan_off)
    }
    val fanIconTint = when {
        isV3 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        fanOn -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Connection
            Pill(
                leading = {
                    Icon(
                        imageVector = if (isConnected) Icons.Rounded.SettingsInputAntenna else Icons.Rounded.PortableWifiOff,
                        contentDescription = null,
                        tint = if (isConnected) Color(0xFF00BCD4) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.pill_connection),
                subtitle = if (isConnected) stringResource(R.string.common_connected) else stringResource(R.string.common_disconnected),
                modifier = Modifier.weight(1f)
            )

            // Temperature
            Pill(
                leading = {
                    Icon(
                        imageVector = Icons.Rounded.DeviceThermostat,
                        contentDescription = null,
                        tint = tempIconTint,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.pill_temperature),
                subtitle = tempLabel,
                modifier = Modifier.weight(1f)
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Filament
            val hasFil = status.hasFilament && isConnected
            Pill(
                leading = {
                    Icon(
                        imageVector = if (hasFil) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                        contentDescription = null,
                        tint = if (hasFil) StateNormalGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.pill_filament),
                subtitle = if (hasFil) stringResource(R.string.filament_detected) else stringResource(R.string.filament_not_detected),
                modifier = Modifier.weight(1f)
            )

            // Fan
            Pill(
                leading = {
                    Icon(
                        imageVector = if (isV3) Icons.Rounded.AirplanemodeInactive else Icons.Rounded.Air,
                        contentDescription = null,
                        tint = fanIconTint,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.pill_fan),
                subtitle = fanSubtitle,
                isTitleDimmed = isV3,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Pill(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    isTitleDimmed: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isTitleDimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusProgress(
    fraction: Float,
    bar: Color,
    track: Color
) {
    val f = fraction.coerceIn(0f, 1f)
    val anim by animateFloatAsState(
        targetValue = f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "progress"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(track)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(anim)
                .background(bar)
        )
    }
}

@Composable
private fun ControlCard(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    isRunning: Boolean,
    isStartEnabled: Boolean,
    isStopEnabled: Boolean,
    onStartOrPause: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = 34.dp,
            topEnd = 34.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PillButton(
                    text = if (isRunning) stringResource(R.string.action_pause) else stringResource(R.string.action_start),
                    onClick = onStartOrPause,
                    enabled = isStartEnabled,
                    modifier = Modifier.weight(1f)
                )
                PillButton(
                    text = stringResource(R.string.action_stop),
                    onClick = onStop,
                    enabled = isStopEnabled,
                    danger = true,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Slider(
                        value = speed.coerceIn(50f, 100f),
                        onValueChange = { onSpeedChange(it) },
                        valueRange = 50f..100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            thumbColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.width(54.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = stringResource(R.string.percent_value, speed.toInt()),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 17.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val baseColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val contentColor = if (enabled) baseColor else baseColor.copy(alpha = 0.4f)
    val bgColor = if (enabled) baseColor.copy(alpha = 0.12f) else baseColor.copy(alpha = 0.05f)

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(bgColor)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = baseColor),
                        onClick = onClick
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}
