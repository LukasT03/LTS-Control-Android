package de.ltsdesign.ltscontrol.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.ui.CardDivider
import de.ltsdesign.ltscontrol.ui.ChoiceRow
import de.ltsdesign.ltscontrol.ui.DecimalStepperRow
import de.ltsdesign.ltscontrol.ui.SectionCard
import de.ltsdesign.ltscontrol.ui.SectionLabel
import de.ltsdesign.ltscontrol.ui.SettingRow
import de.ltsdesign.ltscontrol.ui.StepperRow
import de.ltsdesign.ltscontrol.ui.StatusPill
import de.ltsdesign.ltscontrol.ui.SwitchRow
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
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
fun SettingsScreen() {
    val backStack = rememberNavBackStack(SettingsHome)
    var targetWeight by rememberSaveable { mutableIntStateOf(0) }
    var boardVariant by rememberSaveable { mutableIntStateOf(1) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 300),
            ) togetherWith slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(durationMillis = 300),
            )
        },
        popTransitionSpec = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(durationMillis = 300),
            ) togetherWith slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 300),
            )
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        entryProvider = entryProvider {
            entry<SettingsHome> {
                SettingsMain(
                    targetWeight = targetWeight,
                    boardVariant = boardVariant,
                    onNavigate = { backStack.add(it) },
                )
            }
            entry<AmountSettings> {
                AmountScreen(
                    selected = targetWeight,
                    onSelected = { targetWeight = it },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<VariantSettings> {
                VariantScreen(
                    selected = boardVariant,
                    onSelected = { boardVariant = it },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<CalibrationSettings> {
                CalibrationScreen(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}

@Composable
private fun SettingsMain(targetWeight: Int, boardVariant: Int, onNavigate: (SettingsDestination) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val glissando = stringResource(R.string.glissando)
    var completionSound by rememberSaveable { mutableStateOf(glissando) }
    var sensorEnabled by rememberSaveable { mutableStateOf(true) }
    var reversed by rememberSaveable { mutableStateOf(false) }
    var brightness by rememberSaveable { mutableIntStateOf(100) }
    var strength by rememberSaveable { mutableIntStateOf(100) }
    var autoStop by rememberSaveable { mutableStateOf("Off") }
    var highSpeed by rememberSaveable { mutableStateOf(false) }
    var servoStep by rememberSaveable { mutableDoubleStateOf(1.75) }
    var servoHome by rememberSaveable { mutableIntStateOf(1) }
    var fanSpeed by rememberSaveable { mutableIntStateOf(60) }
    var fanAlwaysOn by rememberSaveable { mutableStateOf(false) }
    var temperatureUnit by rememberSaveable { mutableIntStateOf(0) }
    var duration by rememberSaveable { mutableIntStateOf(895) }
    var notifications by rememberSaveable { mutableStateOf(false) }

    val amountLabels = listOf(
        stringResource(R.string.whole_spool),
        "1.0 kg",
        "0.5 kg",
        "0.25 kg",
    )
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                StatusPill(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.status),
                    subtitle = stringResource(R.string.not_connected),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

        item { SectionLabel(stringResource(R.string.configuration)) }
        item {
            SectionCard {
                ChoiceRow(
                    title = stringResource(R.string.completion_sound),
                    value = completionSound,
                    options = listOf(stringResource(R.string.off), stringResource(R.string.simple), glissando, "Star Wars"),
                ) { completionSound = it }
                CardDivider()
                StepperRow(stringResource(R.string.led_brightness), brightness, range = 0..100, step = 10) { brightness = it }
                CardDivider()
                SettingRow(stringResource(R.string.respool_amount), amountLabels[targetWeight]) { onNavigate(AmountSettings) }
                CardDivider()
                SwitchRow(
                    title = stringResource(R.string.use_filament_sensor),
                    checked = sensorEnabled,
                    description = stringResource(R.string.use_filament_sensor_note),
                ) { sensorEnabled = it }
            }
        }

        item { SectionLabel(stringResource(R.string.motor)) }
        item {
            SectionCard {
                SwitchRow(stringResource(R.string.reverse_direction), reversed) { reversed = it }
                CardDivider()
                StepperRow(stringResource(R.string.strength), strength, range = 80..120, step = 10) { strength = it }
                CardDivider()
                ChoiceRow(
                    title = stringResource(R.string.auto_stop_sensitivity),
                    value = autoStop,
                    options = listOf(stringResource(R.string.off), stringResource(R.string.low), stringResource(R.string.medium), stringResource(R.string.high)),
                    enabled = !highSpeed,
                    description = stringResource(R.string.auto_stop_note),
                ) { autoStop = it }
            }
        }

        item {
            SectionCard {
                SwitchRow(
                    title = stringResource(R.string.high_speed),
                    checked = highSpeed,
                    description = stringResource(R.string.high_speed_note),
                ) { highSpeed = it }
            }
        }

        if (boardVariant == 1) {
            item { SectionLabel(stringResource(R.string.servo)) }
            item {
                SectionCard {
                    SettingRow(stringResource(R.string.end_positions), onClick = { onNavigate(CalibrationSettings) })
                    CardDivider()
                    DecimalStepperRow(
                        title = stringResource(R.string.step_distance),
                        value = servoStep,
                        range = 0.50..4.00,
                        step = 0.01,
                        suffix = "mm",
                    ) { servoStep = it }
                    CardDivider()
                    SegmentedSetting(
                        options = listOf(stringResource(R.string.left), stringResource(R.string.right)),
                        selected = servoHome,
                        description = stringResource(R.string.servo_note),
                        onSelected = { servoHome = it },
                    )
                }
            }
        }

        item { SectionLabel(stringResource(R.string.cooling_fan)) }
        item {
            SectionCard {
                StepperRow(stringResource(R.string.fan_speed), fanSpeed, range = 10..100, step = 10) { fanSpeed = it }
                CardDivider()
                SwitchRow(
                    title = stringResource(R.string.fan_always_on),
                    checked = fanAlwaysOn,
                    description = stringResource(R.string.fan_note),
                ) { fanAlwaysOn = it }
                CardDivider()
                SegmentedSetting(
                    options = listOf("Celsius", "Fahrenheit"),
                    selected = temperatureUnit,
                    onSelected = { temperatureUnit = it },
                )
            }
        }

        item { SectionLabel(stringResource(R.string.calibration)) }
        item {
            SectionCard {
                DurationStepper(
                    value = duration,
                    description = stringResource(R.string.calibration_duration_note),
                    onValueChange = { duration = it },
                )
            }
        }

        item { SectionLabel(stringResource(R.string.app_section)) }
        item {
            SectionCard {
                SwitchRow(
                    title = stringResource(R.string.notifications),
                    checked = notifications,
                    description = stringResource(R.string.notifications_note),
                ) { notifications = it }
            }
        }

        item {
            SectionCard {
                SettingRow(
                    title = stringResource(R.string.variant),
                    value = if (boardVariant == 1) stringResource(R.string.respooler_pro) else stringResource(R.string.respooler_v4),
                ) { onNavigate(VariantSettings) }
            }
        }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun FooterText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

@Composable
private fun SegmentedSetting(
    options: List<String>,
    selected: Int,
    description: String? = null,
    onSelected: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp),
            )
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(10.dp)) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selected == index,
                    onClick = { onSelected(index) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun DurationStepper(value: Int, description: String, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "${stringResource(R.string.duration)}: ${value / 60}m ${value % 60}s",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onValueChange((value - 5).coerceAtLeast(5)) }, enabled = value > 5) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease Duration")
        }
        IconButton(onClick = { onValueChange((value + 5).coerceAtMost(2000)) }, enabled = value < 2000) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase Duration")
        }
    }
}

@Composable
private fun AmountScreen(selected: Int, onSelected: (Int) -> Unit, onBack: () -> Unit) {
    SubPage(title = stringResource(R.string.respool_amount), onBack = onBack) {
        SectionCard { AmountRow(stringResource(R.string.whole_spool), selected == 0) { onSelected(0) } }
        FooterText(stringResource(R.string.entire_spool_note))
        Spacer(Modifier.height(8.dp))
        SectionCard {
            listOf("1.0 kg", "0.5 kg", "0.25 kg").forEachIndexed { index, option ->
                AmountRow(option, selected == index + 1) { onSelected(index + 1) }
                if (index < 2) CardDivider()
            }
        }
        FooterText("${stringResource(R.string.fixed_amount_note)}\n\n${stringResource(R.string.amount_accuracy_note)}")
    }
}

@Composable
private fun AmountRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun VariantScreen(selected: Int, onSelected: (Int) -> Unit, onBack: () -> Unit) {
    SubPage(title = stringResource(R.string.variant), onBack = onBack) {
        SectionCard {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VariantOption(R.drawable.respooler, stringResource(R.string.respooler_v4), selected == 0, Modifier.weight(1f)) { onSelected(0) }
                VariantOption(R.drawable.respooler_pro, stringResource(R.string.respooler_pro), selected == 1, Modifier.weight(1f)) { onSelected(1) }
            }
        }
        FooterText(stringResource(R.string.variant_note))
    }
}

@Composable
private fun VariantOption(image: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier = modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(image), label, Modifier.fillMaxWidth().height(110.dp), contentScale = ContentScale.Fit)
        Text(label, fontWeight = FontWeight.Medium)
        Icon(
            if (selected) Icons.Rounded.Check else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

@Composable
private fun CalibrationScreen(onBack: () -> Unit) {
    var side by rememberSaveable { mutableIntStateOf(1) }
    var leftAngle by rememberSaveable { mutableIntStateOf(175) }
    var rightAngle by rememberSaveable { mutableIntStateOf(5) }
    val angle = if (side == 0) leftAngle else rightAngle
    val updateAngle: (Int) -> Unit = { if (side == 0) leftAngle = it else rightAngle = it }

    SubPage(title = stringResource(R.string.end_positions), onBack = onBack) {
        SectionCard {
            Column(Modifier.padding(12.dp)) {
                SegmentedSetting(
                    options = listOf(stringResource(R.string.left_side), stringResource(R.string.right_side)),
                    selected = side,
                    onSelected = { side = it },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { updateAngle((angle + 1).coerceAtMost(180)) }, enabled = angle < 180) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Increase Angle")
                    }
                    Text(
                        stringResource(R.string.angle, angle),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalIconButton(onClick = { updateAngle((angle - 1).coerceAtLeast(0)) }, enabled = angle > 0) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Decrease Angle")
                    }
                }
            }
        }
        FooterText(stringResource(R.string.calibration_note))
    }
}

@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(content = content)
            }
        }
    }
}
