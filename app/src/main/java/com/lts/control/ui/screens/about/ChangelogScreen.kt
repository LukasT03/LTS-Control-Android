package com.lts.control.ui.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lts.control.R
import com.lts.control.core.state.AppPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(
    prefsRepo: AppPreferencesRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<ChangelogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Load cached entries first
        val cachedJson = prefsRepo.preferencesFlow.first()[AppPreferencesRepository.Keys.CHANGELOG_CACHE]
        if (!cachedJson.isNullOrEmpty()) {
            try {
                val cachedList = Json.decodeFromString<List<ChangelogEntry>>(cachedJson)
                if (cachedList.isNotEmpty()) {
                    entries = cachedList
                    isLoading = false
                }
            } catch (_: Exception) {}
        }

        // Fetch from network
        scope.launch {
            try {
                val fetched = withContext(Dispatchers.IO) {
                    val url = URL("https://download.lts-design.com/Firmware/README.md")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                        setRequestProperty("Cache-Control", "no-cache")
                        setRequestProperty("Pragma", "no-cache")
                    }
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        throw IllegalStateException("Server returned HTTP $code")
                    }
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    ChangelogParser.parse(text)
                }
                entries = fetched
                errorMessage = null
                isLoading = false
                prefsRepo.setChangelogCache(Json.encodeToString(fetched))
            } catch (e: Exception) {
                if (entries.isEmpty()) {
                    errorMessage = e.message ?: "Failed to load"
                }
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.changelog_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading && entries.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.changelog_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                errorMessage != null && entries.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.changelog_error_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        itemsIndexed(entries, key = { _, item -> item.version }) { index, entry ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = entry.version,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(Modifier.height(8.dp))
                                entry.items.forEach { bullet ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            "•",
                                            modifier = Modifier.width(18.dp),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            bullet,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            if (index < entries.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
