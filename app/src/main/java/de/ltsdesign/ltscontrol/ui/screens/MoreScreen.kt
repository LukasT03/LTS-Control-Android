package de.ltsdesign.ltscontrol.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.ui.SectionCard

private enum class MoreDialog { Instructions, History }

@Composable
fun MoreScreen() {
    var dialogName by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(170.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.control_board_pro), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.app_version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.board_version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { dialogName = MoreDialog.Instructions.name }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Info, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.instructions))
                }
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Coffee, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.tip))
                }
            }
            SectionCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.board_update), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.board_not_connected), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.update_firmware))
                    }
                }
            }
            OutlinedButton(
                onClick = { dialogName = MoreDialog.History.name },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.version_history))
            }
            Text(
                stringResource(R.string.about_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }

    when (dialogName?.let(MoreDialog::valueOf)) {
        MoreDialog.Instructions -> InstructionsDialog { dialogName = null }
        MoreDialog.History -> FirmwareHistoryDialog { dialogName = null }
        null -> Unit
    }
}

@Composable
private fun InstructionsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.instructions)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column {
                        HelpItem(stringResource(R.string.connect), stringResource(R.string.instruction_connect))
                        TextButton(onClick = {}) { Text(stringResource(R.string.restart_connection)) }
                    }
                }
                item { HelpItem(stringResource(R.string.board_firmware), stringResource(R.string.instruction_firmware)) }
                item { HelpItem(stringResource(R.string.motor_strength), stringResource(R.string.instruction_strength)) }
                item { HelpItem(stringResource(R.string.auto_stop), stringResource(R.string.instruction_auto_stop)) }
                item { HelpItem(stringResource(R.string.github), stringResource(R.string.instruction_github)) }
                item { HelpItem(stringResource(R.string.feedback), stringResource(R.string.instruction_feedback)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun HelpItem(title: String, body: String) {
    Column {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
