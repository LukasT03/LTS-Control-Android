package com.lts.control.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lts.control.R
import com.lts.control.core.ble.BleViewModel
import com.lts.control.core.ble.model.BoardVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardVariantSettingsScreen(
    vm: BleViewModel,
    onBack: () -> Unit
) {
    val status by vm.status.collectAsState()
    var selected by remember(status.boardVariant) {
        mutableStateOf(if (status.boardVariant == BoardVariant.PRO) BoardVariant.PRO else BoardVariant.STANDARD)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.set_variant)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        VariantOptionItem(
                            title = stringResource(R.string.variant_v4),
                            drawableRes = R.drawable.respooler,
                            isSelected = selected == BoardVariant.STANDARD,
                            onClick = {
                                selected = BoardVariant.STANDARD
                                vm.setBoardVariant(BoardVariant.STANDARD)
                            }
                        )
                        VariantOptionItem(
                            title = stringResource(R.string.variant_pro),
                            drawableRes = R.drawable.respooler_pro,
                            isSelected = selected == BoardVariant.PRO,
                            onClick = {
                                selected = BoardVariant.PRO
                                vm.setBoardVariant(BoardVariant.PRO)
                            }
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.variant_footer_settings),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Forced modal variant picker (§5.2) shown when didReceiveBoardVariant && boardVariant == UNKNOWN.
 */
@Composable
fun BoardVariantSelectionDialog(
    vm: BleViewModel,
    onDismiss: () -> Unit
) {
    var selectedOption by remember { mutableStateOf<BoardVariant?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.variant_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    VariantOptionItem(
                        title = stringResource(R.string.variant_v4),
                        drawableRes = R.drawable.respooler,
                        isSelected = selectedOption == BoardVariant.STANDARD,
                        onClick = { selectedOption = BoardVariant.STANDARD }
                    )
                    VariantOptionItem(
                        title = stringResource(R.string.variant_pro),
                        drawableRes = R.drawable.respooler_pro,
                        isSelected = selectedOption == BoardVariant.PRO,
                        onClick = { selectedOption = BoardVariant.PRO }
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.variant_footer_selection),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        selectedOption?.let {
                            vm.setBoardVariant(it)
                            onDismiss()
                        }
                    },
                    enabled = selectedOption != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun VariantOptionItem(
    title: String,
    drawableRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = title,
            modifier = Modifier
                .size(100.dp)
                .padding(4.dp)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )

        Spacer(Modifier.height(8.dp))

        Icon(
            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}
