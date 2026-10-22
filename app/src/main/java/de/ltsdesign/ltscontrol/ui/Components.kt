package de.ltsdesign.ltscontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun StatusPill(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(9.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(content = { content() })
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 6.dp),
    )
}

@Composable
fun SettingRow(
    title: String,
    value: String? = null,
    icon: ImageVector? = null,
    description: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(onClick = onClick ?: {}, enabled = onClick != null, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
                Spacer(Modifier.size(10.dp))
            }
            SettingLabel(title = title, description = description, modifier = Modifier.weight(1f))
            if (value != null) {
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ChoiceRow(
    title: String,
    value: String,
    options: List<String>,
    enabled: Boolean = true,
    description: String? = null,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingLabel(title = title, description = description, modifier = Modifier.weight(1f))
        Box {
            TextButton(
                onClick = { expanded = true },
                enabled = enabled,
            ) {
                Text(value)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        trailingIcon = {
                            if (option == value) {
                                Icon(Icons.Rounded.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    description: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingLabel(title = title, description = description, modifier = Modifier.weight(1f))
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun StepperRow(
    title: String,
    value: Int,
    suffix: String = "%",
    range: IntRange = 0..100,
    step: Int = 5,
    description: String? = null,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingLabel(
            title = "$title: $value $suffix",
            description = description,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onValueChange((value - step).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease $title")
        }
        IconButton(onClick = { onValueChange((value + step).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase $title")
        }
    }
}

@Composable
fun DecimalStepperRow(
    title: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    suffix: String,
    description: String? = null,
    onValueChange: (Double) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingLabel(
            title = "$title: ${String.format(Locale.US, "%.2f", value)} $suffix",
            description = description,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onValueChange((value - step).coerceIn(range)) }, enabled = value > range.start) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease $title")
        }
        IconButton(onClick = { onValueChange((value + step).coerceIn(range)) }, enabled = value < range.endInclusive) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase $title")
        }
    }
}

@Composable
private fun SettingLabel(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CardDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun PageTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
}
