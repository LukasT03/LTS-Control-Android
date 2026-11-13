package de.ltsdesign.ltscontrol.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.bluetooth.BleManager
import de.ltsdesign.ltscontrol.bluetooth.DevicePairing
import de.ltsdesign.ltscontrol.ui.settings.SettingsBackground

private val IconSize = 130.dp
private val IconTop = 24.dp

/**
 * Shown on launch until a board was chosen once, like the welcome sheet on iOS. It closes itself
 * once a board is connected; swiped away it comes again on the next launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeSheet(bleManager: BleManager, pairing: DevicePairing, onDismiss: () -> Unit) {
    val connect = rememberConnectAction(bleManager, pairing)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(bleManager.isConnected) {
        if (!bleManager.isConnected) return@LaunchedEffect
        sheetState.hide()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SettingsBackground,
    ) {
        val scrollState = rememberScrollState()
        val glow = iconGlowColor()
        // Almost full height like the large sheet on iOS, the app stays visible above it.
        Column(
            Modifier
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                // The icon's glow like on the More tab, drawn here because the scrolling content
                // below would cut it off at its top edge. It follows the icon when scrolled.
                .drawBehind {
                    val iconRadius = (IconSize / 2).toPx()
                    val iconCenterY = (IconTop + IconSize / 2).toPx() - scrollState.value
                    drawIconGlow(glow, Offset(size.width / 2, iconCenterY), iconRadius)
                },
        ) {
            // Content hangs from the top like Apple's welcome screens; it scrolls where the height runs short.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 32.dp)
                    .padding(top = IconTop, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    // The app icon, cropped to a circle like on the More tab; the night resource is the dark icon.
                    painter = painterResource(R.drawable.app_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(IconSize).clip(CircleShape),
                )
                Text(
                    stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Column(
                    modifier = Modifier.padding(top = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    FeatureRow(Icons.Rounded.PlayCircle, R.string.welcome_control_title, R.string.welcome_control_text)
                    FeatureRow(Icons.Rounded.NotificationsActive, R.string.welcome_status_title, R.string.welcome_status_text)
                    FeatureRow(Icons.Rounded.Tune, R.string.welcome_settings_title, R.string.welcome_settings_text)
                }
            }
            SheetButton(stringResource(R.string.connect_respooler), onClick = connect)
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: Int, text: Int) {
    Row(Modifier.fillMaxWidth()) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp).size(36.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
