package com.lts.control.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.unit.*
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
        else    -> Color.Gray.copy(alpha = 0.20f)
    }

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
            .background(contentGradient())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Pills
            PillRow(
                isConnected = isConnected,
                hasFilament = (status?.hasFilament == true) && isConnected,
                tempLabel = tempLabel(tempC),
                tempIconTint = tempIconColor,
            )

            // Spool + Hintergrund-Glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                SpoolWithGradient()

                // Rotations-Physik wie iOS: läuft beschleunigt, bremst ab bei Pause/Stop
                val targetRpm = when (deviceState) {
                    DeviceState.RUNNING -> 60f
                    DeviceState.UPDATING -> 20f
                    else -> 0f
                }
                val rotation by rememberSmoothRotation(targetRpm = targetRpm)
                TimelapseDisc(rotation = rotation)
            }

            // Status + Progress
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                val progressValue = if (deviceState == DeviceState.IDLE) 0f else progress.coerceIn(0f, 100f)
                StatusProgress(
                    fraction = progressValue / 100f,
                    bar = barColor,
                    track = barBgColor
                )
                Spacer(Modifier.height(10.dp))
                // Optional: Restzeit
                AnimatedContent(
                    targetState = deviceState to remaining,
                    transitionSpec = { fadeIn(tween(180)) with fadeOut(tween(180)) }
                ) { (state, rem) ->
                    val text = when (state) {
                        DeviceState.IDLE      -> "Bereit"
                        DeviceState.RUNNING   -> "Läuft • Rest: ${formatSeconds(rem)}"
                        DeviceState.PAUSED    -> "Pausiert"
                        DeviceState.AUTO_STOP -> "Auto-Stopp"
                        DeviceState.UPDATING  -> "Wird aktualisiert…"
                        DeviceState.DONE      -> "Fertig"
                        DeviceState.ERROR     -> "Fehler"
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Steuerung + Speed
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
                onStart = { vm.start() },
                onPause = { vm.pause() },
                onStop = { vm.stop() },
            )

            Spacer(Modifier.height(12.dp))
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Pill(
            leading = {
                val icon = if (isConnected) Symbols.Antenna else Symbols.AntennaSlash
                Icon(icon, contentDescription = null, tint = if (isConnected) Color(0xFF00BCD4) else LocalContentColor.current, modifier = Modifier.size(22.dp))
            },
            title = "Verbindung",
            subtitle = if (isConnected) "Verbunden" else "Getrennt",
            modifier = Modifier.weight(1f)
        )
        Pill(
            leading = {
                val ok = hasFilament
                Icon(if (ok) Symbols.CheckCircle else Symbols.XCircle, contentDescription = null, tint = if (ok) Color(0xFF8E24AA) else LocalContentColor.current, modifier = Modifier.size(23.5.dp))
            },
            title = "Filament",
            subtitle = if (hasFilament) "Erkannt" else "Nicht erkannt",
            modifier = Modifier.weight(1f)
        )
        Pill(
            leading = { Icon(if (tempIconTint == Color(0xFFD32F2F)) Symbols.ThermoHigh else Symbols.Thermo, contentDescription = null, tint = tempIconTint, modifier = Modifier.size(22.dp)) },
            title = "Temperatur",
            subtitle = tempLabel,
            modifier = Modifier.weight(1f)
        )
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
            .clip(RoundedCornerShape(25))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SpoolWithGradient() {
    // Soft Radial Glow wie in Swift
    val sizePx = with(LocalDensity.current) { 280.dp.toPx() }
    val bgGlow = MaterialTheme.colorScheme.background.copy(alpha = 0.6f)
    Canvas(Modifier.fillMaxSize()) {
        val center = this.center
        val radius = size.minDimension * 0.78f
        // großer radialer Fade
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    bgGlow,
                    Color.Transparent
                ),
                center = center,
                radius = radius * 1.4f
            ),
            radius = radius * 1.4f,
            center = center
        )
    }
}

@Composable
private fun TimelapseDisc(rotation: Float) {
    val base = 0.78f
    val hole = 0.28f
    val discTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    Box(
        modifier = Modifier
            .fillMaxSize(0.78f)
            .aspectRatio(1f)
            .rotate(rotation)
    ) {
        // Disc
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            // outer
            drawCircle(color = discTint)
            // inner cutout
            drawCircle(
                color = Color.Transparent,
                radius = r * hole,
                blendMode = BlendMode.Clear
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
    val anim by animateFloatAsState(targetValue = f, animationSpec = tween(350, easing = FastOutSlowInEasing), label = "progress")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
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
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(36),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PillButton(text = "Start", onClick = onStart, modifier = Modifier.weight(1f))
                PillButton(text = "Pause", onClick = onPause, modifier = Modifier.weight(1f))
                PillButton(text = "Stop",  onClick = onStop,  modifier = Modifier.weight(1f))
            }
            Divider(Modifier.padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = speed.coerceIn(50f, 100f),
                    onValueChange = { onSpeedChange(it) },
                    valueRange = 50f..100f,
                    steps = 50, // 1%-Schritte
                    onValueChangeFinished = onSpeedChangeFinished,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text("${speed.toInt()} %", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pressed = remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        targetValue = if (pressed.value) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        animationSpec = tween(120), label = "btn-bg"
    )
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(11))
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
        Text(text, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

/* -------------------------------------------------------------------------- */
/*                                 Utilities                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun contentGradient(): Brush {
    // Heller Top-Tint wie in Swift (helles Blau bei Light Mode)
    val c = MaterialTheme.colorScheme
    val topTint = c.primary.copy(alpha = if (isSystemInDarkTheme()) 0.00f else 0.05f)
    val mid = c.background
    val bottom = c.surfaceVariant
    return Brush.verticalGradient(
        colors = listOf(topTint, mid, bottom),
        startY = 0f,
        endY = with(LocalDensity.current) { 680.dp.toPx() }
    )
}

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