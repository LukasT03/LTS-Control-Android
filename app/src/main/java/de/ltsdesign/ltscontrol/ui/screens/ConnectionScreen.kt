package de.ltsdesign.ltscontrol.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.ui.SectionCard
import de.ltsdesign.ltscontrol.ui.SectionLabel
import de.ltsdesign.ltscontrol.ui.StatusPill

@Composable
fun ConnectionScreen() {
    var password by rememberSaveable { mutableStateOf("") }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.title_connection)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        Icons.Rounded.BluetoothDisabled,
                        stringResource(R.string.bluetooth),
                        stringResource(R.string.disconnected),
                        Modifier.weight(1f),
                    )
                    StatusPill(
                        Icons.Rounded.WifiOff,
                        stringResource(R.string.wifi),
                        stringResource(R.string.disconnected),
                        Modifier.weight(1f),
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.wifi)) }
            item {
                SectionCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text(stringResource(R.string.network)) },
                            placeholder = { Text(stringResource(R.string.select_network)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            enabled = false,
                            label = { Text(stringResource(R.string.password)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.send_credentials))
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.wifi_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
            item {
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_networks))
                }
            }
            item { SectionLabel(stringResource(R.string.bluetooth)) }
            item {
                SectionCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connect)) }
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.remove_saved_device))
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.bluetooth_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
        }
    }
}
