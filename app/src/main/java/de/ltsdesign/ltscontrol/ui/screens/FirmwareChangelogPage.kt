package de.ltsdesign.ltscontrol.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.data.FirmwareChangelogRepository
import de.ltsdesign.ltscontrol.ui.settings.SettingsCustomItem
import de.ltsdesign.ltscontrol.ui.settings.SettingsPage
import de.ltsdesign.ltscontrol.ui.settings.settingsGroup

/** Firmware changelog from the README on GitHub Pages, one group per version. */
@Composable
fun FirmwareChangelogPage(onBack: () -> Unit) {
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

    val loadFailed = stringResource(R.string.changelog_load_failed)

    SettingsPage(title = stringResource(R.string.version_history), onBack = onBack) {
        when {
            entries.isNotEmpty() -> entries.forEach { entry ->
                settingsGroup(headerText = entry.version) {
                    item {
                        SettingsCustomItem {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                entry.items.forEach { bullet ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("•", style = MaterialTheme.typography.bodyLarge)
                                        Text(bullet, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            isLoading -> item {
                Box(Modifier.fillMaxWidth().fillParentMaxHeight(0.7f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> item {
                Column(
                    modifier = Modifier.fillMaxWidth().fillParentMaxHeight(0.7f).padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(loadFailed, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    errorMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
