package de.ltsdesign.ltscontrol.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.ModeFanOff
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.ui.InteractiveSpool
import de.ltsdesign.ltscontrol.ui.PageTitle
import de.ltsdesign.ltscontrol.ui.StatusPill

@Composable
fun ControlScreen() {
    var speed by rememberSaveable { mutableFloatStateOf(85f) }

    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(32.dp))
            Column { PageTitle(stringResource(R.string.title_respooler)) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(Icons.Rounded.BluetoothDisabled, stringResource(R.string.status_connection), stringResource(R.string.disconnected), Modifier.weight(1f))
                    StatusPill(Icons.Rounded.DeviceThermostat, stringResource(R.string.status_temperature), stringResource(R.string.chip_unknown), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.status_filament), stringResource(R.string.not_detected), Modifier.weight(1f))
                    StatusPill(Icons.Rounded.ModeFanOff, stringResource(R.string.status_fan), stringResource(R.string.off), Modifier.weight(1f))
                }
            }
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
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
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(
                    stringResource(R.string.not_connected),
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(12.dp),
                )
                Spacer(Modifier.height(4.dp))
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text(stringResource(R.string.start))
                        }
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text(stringResource(R.string.stop))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = speed,
                            onValueChange = { speed = it },
                            valueRange = 0f..100f,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors().let {
                                androidx.compose.material3.SliderDefaults.colors()
                            },
                        )
                        Text("${speed.toInt()} %", modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            }
        }
    }
}
