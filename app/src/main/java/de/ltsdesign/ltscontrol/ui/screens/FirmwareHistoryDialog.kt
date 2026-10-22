package de.ltsdesign.ltscontrol.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.data.ChangelogEntry
import de.ltsdesign.ltscontrol.data.FirmwareChangelogRepository

@Composable
fun FirmwareHistoryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { FirmwareChangelogRepository(context) }
    var entries by remember { mutableStateOf(repository.loadCached()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) {
        repository.refresh()
            .onSuccess {
                entries = it
                errorMessage = null
            }
            .onFailure { errorMessage = it.localizedMessage }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.version_history)) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 520.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    entries.isNotEmpty() -> ChangelogList(entries)
                    isLoading -> CircularProgressIndicator()
                    else -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(stringResource(R.string.changelog_load_failed), fontWeight = FontWeight.SemiBold)
                        errorMessage?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun ChangelogList(entries: List<ChangelogEntry>) {
    LazyColumn {
        itemsIndexed(entries, key = { _, entry -> entry.version }) { index, entry ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(entry.version, fontWeight = FontWeight.Bold)
                entry.items.forEach { item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•")
                        Text(item, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (index < entries.lastIndex) HorizontalDivider()
        }
    }
}
