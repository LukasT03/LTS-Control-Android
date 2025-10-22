package com.lts.control.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

import com.lts.control.R

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.lts.control.core.ble.BleViewModel
import com.lts.control.core.ble.model.DeviceState
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ContentScreen(
    vm: BleViewModel,
    onDismissSplashIfConnected: () -> Unit = {}
) {
    val connection by vm.connection.collectAsState()
    val status by vm.status.collectAsState()
    val deviceState by vm.deviceState.collectAsState()
    val progress by vm.progressBarValue.collectAsState()
    val remaining by vm.remainingSeconds.collectAsState()

    // iOS-Logik: wenn verbunden -> Splash schließen
    val isConnected = remember(status) { status != null }
    LaunchedEffect(isConnected) { if (isConnected) onDismissSplashIfConnected() }

    // Lokaler Slider-State (nur bei Drag, danach persistieren)
    var localSpeed by remember { mutableFloatStateOf(max(50f, (status?.speedPercent ?: 80).toFloat())) }
    var isEditing by remember { mutableStateOf(false) }
    LaunchedEffect(status?.speedPercent) {
        if (!isEditing) localSpeed = max(50f, (status?.speedPercent ?: 80).toFloat())
    }

    // Farben wie in Swift
    val isPaused = deviceState == DeviceState.PAUSED
    val isDone = deviceState == DeviceState.DONE
    val isError = deviceState == DeviceState.AUTO_STOP // Rot für Auto-Stop
    val barColor = when {
        isError -> MaterialTheme.colorScheme.error
        isDone  -> Color(0xFF2ECC71)
        isPaused-> Color(0xFFFFA000)
        else    -> Color(0xFF0C4C98)      // ltsBlue (light)
    }
    val barBgColor = when {
        isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.20f)
        isPaused-> Color(0xFFFFA000).copy(alpha = 0.20f)
        else    -> Color.Gray.copy(alpha = 0.14f)
    }

    // Light blue for screen background
    val screenLightBlue = Color(0xFFF4F9FF)

    // Temperatur-Anzeige wie in Swift (>=65°C kritisch)
    val tempC = status?.chipTemperatureC
    val tempCritical = (tempC ?: 0) >= 65
    val tempIconColor = when {
        tempC == null -> LocalContentColor.current
        tempCritical  -> Color(0xFFD32F2F)
        else          -> Color(0xFF2E7D32)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to screenLightBlue,
                        0.8f to screenLightBlue,
                        1.0f to Color.White
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // Pills (TOP, fixed)
            PillRow(
                isConnected = isConnected,
                hasFilament = (status?.hasFilament == true) && isConnected,
                tempLabel = tempLabel(tempC),
                tempIconTint = tempIconColor,
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
                // --- Interactive rotation state (drag + physics + auto) ---
                var rotation by remember { mutableFloatStateOf(0f) }
                var angularVel by remember { mutableStateOf(0f) }
                var isDragging by remember { mutableStateOf(false) }
                var lastAngle by remember { mutableStateOf<Float?>(null) }
                var boxSize by remember { mutableStateOf(IntSize.Zero) }

                // Physics loop: inertia when released; auto-rotate when device runs
                LaunchedEffect(deviceState) {
                    val maxVel = 15f
                    val friction = 0.992f
                    var last = withFrameNanos { it }
                    while (true) {
                        val now = withFrameNanos { it }
                        val dt = (now - last) / 1_000_000_000f
                        last = now

                        val autoVel = when (deviceState) {
                            DeviceState.RUNNING -> maxVel
                            DeviceState.UPDATING -> 5f
                            else -> 0f
                        }

                        // If user is dragging, keep current angularVel (set by gestures)
                        angularVel = when {
                            isDragging -> angularVel
                            kotlin.math.abs(angularVel) > 0.01f -> angularVel * friction
                            else -> autoVel
                        }

                        val clamped = angularVel.coerceIn(-maxVel, maxVel)
                        rotation = (rotation + clamped * dt * 60f) % 360f
                    }
                }

                // Gesture: one-finger drag to spin (angle around center)
                val center by remember(boxSize) {
                    mutableStateOf(Offset(boxSize.width / 2f, boxSize.height / 2f))
                }
                fun angleAt(p: Offset): Float = Math.toDegrees(kotlin.math.atan2(
                    (p.y - center.y).toDouble(),
                    (p.x - center.x).toDouble()
                )).toFloat()
                val deadZoneRadiusPx = remember(boxSize) { (min(boxSize.width, boxSize.height) * 0.25f) }

                TimelapseDisc(
                    rotation = rotation,
                    modifier = Modifier
                        .onSizeChanged { boxSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val d = (pos - center).getDistance()
                                    if (d < deadZoneRadiusPx) {
                                        // ignore drags starting in the hub
                                        return@detectDragGestures
                                    }
                                    isDragging = true
                                    lastAngle = angleAt(pos)
                                    angularVel = 0f
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
                                    angularVel = delta * 6f // scale for inertia feel
                                }
                                lastAngle = a
                                change.consume()
                            }
                        }
                )
            }

            // Status & Controls (BOTTOM, fixed)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 0.dp)
            ) {
                Column(Modifier.padding(horizontal = 34.dp)) {
                    val progressValue = if (deviceState == DeviceState.IDLE) 0f else progress.coerceIn(0f, 100f)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimatedContent(
                            targetState = deviceState to remaining,
                            transitionSpec = { fadeIn(tween(180)) with fadeOut(tween(180)) }
                        ) { (state, rem) ->
                            val text = when (state) {
                                DeviceState.IDLE      -> "Leerlauf"
                                DeviceState.RUNNING   -> "Läuft..."
                                DeviceState.PAUSED    -> "Pausiert"
                                DeviceState.AUTO_STOP -> "Auto-Stopp"
                                DeviceState.UPDATING  -> "Wird aktualisiert..."
                                DeviceState.DONE      -> "Fertig"
                                DeviceState.ERROR     -> "Fehler"
                            }
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 17.sp).copy(color = Color.Gray),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        StatusProgress(
                            fraction = progressValue / 100f,
                            bar = barColor,
                            track = barBgColor
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                ControlCard(
                    speed = localSpeed,
                    onSpeedChange = {
                        isEditing = true
                        localSpeed = it
                    },
                    onSpeedChangeFinished = {
                        isEditing = false
                        vm.setSpeed(localSpeed.toInt())
                    },
                    isRunning = (deviceState == DeviceState.RUNNING),
                    onStartOrPause = { if (deviceState == DeviceState.RUNNING) vm.pause() else vm.start() },
                    onStop = { vm.stop() },
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                              Helper UI Pieces                              */
/* -------------------------------------------------------------------------- */

@Composable
private fun PillRow(
    isConnected: Boolean,
    hasFilament: Boolean,
    tempLabel: String,
    tempIconTint: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Verbindung (links oben)
            Pill(
                leading = {
                    val icon = if (isConnected) Symbols.Antenna else Symbols.AntennaSlash
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isConnected) Color(0xFF00BCD4) else LocalContentColor.current,
                        modifier = Modifier.size(22.dp)
                    )
                },
                title = "Verbindung",
                subtitle = if (isConnected) "Verbunden" else "Getrennt",
                modifier = Modifier.weight(1f)
            )
            // Temperatur (rechts oben)
            Pill(
                leading = {
                    Icon(
                        if (tempIconTint == Color(0xFFD32F2F)) Symbols.ThermoHigh else Symbols.Thermo,
                        contentDescription = null,
                        tint = tempIconTint,
                        modifier = Modifier.size(22.dp)
                    )
                },
                title = "Temperatur",
                subtitle = tempLabel,
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Filament (links unten)
            Pill(
                leading = {
                    val ok = hasFilament
                    Icon(
                        if (ok) Symbols.CheckCircle else Symbols.XCircle,
                        contentDescription = null,
                        tint = if (ok) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        modifier = Modifier.size(23.5.dp)
                    )
                },
                title = "Filament",
                subtitle = if (hasFilament) "Erkannt" else "Nicht erkannt",
                modifier = Modifier.weight(1f)
            )
            // Lüfter (rechts unten)
            Pill(
                leading = {
                    Icon(Symbols.Fan, contentDescription = null, tint = LocalContentColor.current, modifier = Modifier.size(22.dp))
                },
                title = "Lüfter",
                subtitle = "—",
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TimelapseDisc(rotation: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize(0.7f)
            .aspectRatio(1f)
            .rotate(rotation),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.bambu_spool),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
    }
}

@Composable
private fun StatusProgress(
    fraction: Float,
    bar: Color,
    track: Color
) {
    val f = fraction.coerceIn(0f, 1f)
    val anim by animateFloatAsState(targetValue = f, animationSpec = tween(350, easing = FastOutSlowInEasing), label = "progress")
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
    onSpeedChangeFinished: () -> Unit,
    isRunning: Boolean,
    onStartOrPause: () -> Unit,
    onStop: () -> Unit
) {
    val controlBg = Color.White
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = 34.dp,
            topEnd = 34.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        ),
        colors = CardDefaults.cardColors(containerColor = controlBg)
    ) {
        Column(
            Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 0.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PillButton(
                    text = if (isRunning) "Pause" else "Start",
                    onClick = onStartOrPause,
                    modifier = Modifier.weight(1f)
                )
                PillButton(
                    text = "Stopp",
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                    danger = true
                )
            }
            Divider(Modifier.padding(vertical = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                ) {
                    Slider(
                        value = speed.coerceIn(50f, 100f),
                        onValueChange = { onSpeedChange(it) },
                        valueRange = 50f..100f,
                        onValueChangeFinished = onSpeedChangeFinished,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.14f),
                            thumbColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.width(50.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "${speed.toInt()} %",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 17.sp).copy(color = Color.Gray)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    val base = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val pressed = remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        targetValue = if (pressed.value) base.copy(alpha = 0.15f) else base.copy(alpha = 0.12f),
        animationSpec = tween(120), label = "btn-bg"
    )
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed.value = true
                        tryAwaitRelease()
                        pressed.value = false
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = base, style = MaterialTheme.typography.titleMedium)
    }
}

/* -------------------------------------------------------------------------- */
/*                                 Utilities                                  */
/* -------------------------------------------------------------------------- */


@Composable
private fun rememberSmoothRotation(targetRpm: Float): State<Float> {
    // Physikalisch anmutende Glättung ähnlich deiner iOS-Timeline-Animation.  [oai_citation:0‡ContentView.rtf](sediment://file_000000005be861f5bdf817be07036721)
    val rotation = remember { mutableFloatStateOf(0f) }
    var angularVel by remember { mutableStateOf(0f) }

    LaunchedEffect(targetRpm) {
        val maxVel = 15f
        val accel = 0.12f
        val decel = 0.18f
        val friction = 0.995f
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now

            val targetVel = if (targetRpm <= 0f) 0f else maxVel
            val smoothing = if (targetVel > angularVel) accel else decel
            angularVel += (targetVel - angularVel) * smoothing
            angularVel *= friction

            val clamped = angularVel.coerceIn(-maxVel, maxVel)
            rotation.floatValue = (rotation.floatValue + clamped * dt * 60f) % 360f
        }
    }
    return rotation
}

private fun tempLabel(tempC: Int?): String = when (tempC) {
    null -> "–"
    else -> "${tempC} °C"
}

private fun formatSeconds(s: Int): String {
    val sec = max(0, s)
    val m = sec / 60
    val r = sec % 60
    return if (m > 0) "%d:%02d".format(m, r) else "${r}s"
}

/* Simple Symbol Aliases (Material Icons ersetzbar) */
private object Symbols {
    val Antenna = Icons.DefaultSignal
    val AntennaSlash = Icons.DefaultSignalSlash
    val CheckCircle = Icons.DefaultCheck
    val XCircle = Icons.DefaultX
    val Thermo = Icons.DefaultThermo
    val ThermoHigh = Icons.DefaultThermoHigh
    val Fan = Icons.DefaultFan
}

/** Dummy vector icons – ersetze durch deine bevorzugte Icon-Lib (z.B. phosphor, lucide, material). */
private object Icons {
    val DefaultSignal: ImageVector
        get() = materialPathIcon { moveTo(0f,0f); lineTo(0f,0f) } // placeholder
    val DefaultSignalSlash: ImageVector
        get() = materialPathIcon { moveTo(0f,0f); lineTo(0f,0f) }
    val DefaultCheck: ImageVector
        get() = materialPathIcon { moveTo(0f,0f); lineTo(0f,0f) }
    val DefaultX: ImageVector
        get() = materialPathIcon { moveTo(0f,0f); lineTo(0f,0f) }
    val DefaultThermo: ImageVector
        get() = materialPathIcon { moveTo(0f,0f); lineTo(0f,0f) }
    val DefaultThermoHigh: ImageVector
        get() = materialPathIcon { moveTo(0f,0f); lineTo(0f,0f) }
    val DefaultFan: ImageVector
        get() = materialPathIcon { moveTo(0f,0f); lineTo(0f,0f) }
}

private fun materialPathIcon(builder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector {
    return ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        // Minimaler, gültiger leerer Pfad – erzeugt ein neutrales Platzhalter-Icon
        path { moveTo(0f, 0f) }
    }.build()
}