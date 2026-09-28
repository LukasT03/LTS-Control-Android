package com.lts.control.ui.screens.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lts.control.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsScreen(
    onNavigateBack: () -> Unit,
    onRestartConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.instructions_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            // 1. Connection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InstructionsRow(
                    icon = Icons.Rounded.SettingsInputAntenna,
                    title = stringResource(R.string.instr_connect_title),
                    description = stringResource(R.string.instr_connect_desc)
                )
                TextButton(
                    onClick = onRestartConnection,
                    modifier = Modifier.padding(start = 48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.instr_restart_connection),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 2. Firmware
            InstructionsRow(
                icon = Icons.Rounded.Download,
                title = stringResource(R.string.instr_firmware_title),
                description = stringResource(R.string.instr_firmware_desc)
            )

            // 3. Motor Strength
            InstructionsRow(
                icon = Icons.Rounded.Warning,
                title = stringResource(R.string.instr_motor_title),
                description = stringResource(R.string.instr_motor_desc)
            )

            // 4. Auto-stop
            InstructionsRow(
                icon = Icons.Rounded.StopCircle,
                title = stringResource(R.string.instr_autostop_title),
                description = stringResource(R.string.instr_autostop_desc)
            )

            // 5. GitHub
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InstructionsRow(
                    icon = Icons.Rounded.Code,
                    title = stringResource(R.string.instr_github_title),
                    description = stringResource(R.string.instr_github_desc)
                )
                TextButton(
                    onClick = {
                        try {
                            uriHandler.openUri("https://github.com/LukasT03/LTS-Respooler")
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.padding(start = 48.dp)
                ) {
                    Text(
                        text = "https://github.com/LukasT03/LTS-Respooler",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 6. Feedback
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InstructionsRow(
                    icon = Icons.Rounded.MailOutline,
                    title = stringResource(R.string.instr_feedback_title),
                    description = stringResource(R.string.instr_feedback_desc)
                )
                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:info@lts-design.com")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.padding(start = 48.dp)
                ) {
                    Text(
                        text = "info@lts-design.com",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun InstructionsRow(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 4.dp)
                .size(28.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
