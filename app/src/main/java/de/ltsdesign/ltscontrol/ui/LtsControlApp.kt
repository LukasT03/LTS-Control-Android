package de.ltsdesign.ltscontrol.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.bluetooth.BleManager
import de.ltsdesign.ltscontrol.bluetooth.DevicePairing
import de.ltsdesign.ltscontrol.data.AppPreferences
import de.ltsdesign.ltscontrol.data.FirmwareUpdateRepository
import de.ltsdesign.ltscontrol.notifications.RespoolNotifications
import de.ltsdesign.ltscontrol.ui.screens.WelcomeSheet
import de.ltsdesign.ltscontrol.ui.settings.SettingsBackground
import de.ltsdesign.ltscontrol.ui.screens.ConnectionScreen
import de.ltsdesign.ltscontrol.ui.screens.ControlScreen
import de.ltsdesign.ltscontrol.ui.screens.MoreScreen
import de.ltsdesign.ltscontrol.ui.screens.SettingsScreen
import de.ltsdesign.ltscontrol.ui.screens.VariantSheet

private enum class MainTab(val label: Int, val icon: ImageVector) {
    Control(R.string.tab_control, Icons.Rounded.Home),
    Settings(R.string.tab_settings, Icons.Rounded.Settings),
    Connection(R.string.tab_connection, Icons.Rounded.Wifi),
    More(R.string.tab_more, Icons.Rounded.MoreHoriz),
}

@Composable
fun LtsControlApp(
    bleManager: BleManager,
    pairing: DevicePairing,
    preferences: AppPreferences,
    notifications: RespoolNotifications,
    firmwareUpdates: FirmwareUpdateRepository,
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Control) }

    // The welcome sheet opens on launch until a board was chosen once, and closes once one is connected.
    var showWelcome by rememberSaveable {
        mutableStateOf(!preferences.hasCompletedSetup && bleManager.savedDeviceAddress == null)
    }
    LaunchedEffect(bleManager.savedDeviceAddress) {
        if (bleManager.savedDeviceAddress != null) preferences.hasCompletedSetup = true
    }

    // Asks for the variant once a connected board reports none, like on iOS, but not on top of the
    // welcome sheet. Swiped away, it asks again after the next connection or when the board loses
    // its variant again.
    val needsVariant = bleManager.isConnected && bleManager.needsBoardVariantSelection && !showWelcome
    var isVariantSheetDismissed by remember(needsVariant) { mutableStateOf(false) }
    if (needsVariant && !isVariantSheetDismissed) {
        VariantSheet(bleManager, onDismiss = { isVariantSheetDismissed = true })
    }
    if (showWelcome) {
        WelcomeSheet(bleManager, pairing, onDismiss = { showWelcome = false })
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = SettingsBackground,
        bottomBar = {
            // The flat Material 3 Expressive bar of the Pixel apps, one step darker than the page.
            ShortNavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                MainTab.entries.forEach { tab ->
                    ShortNavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.fillMaxSize().widthIn(max = 600.dp)) {
                when (selectedTab) {
                    MainTab.Control -> ControlScreen(bleManager, preferences)
                    MainTab.Settings -> SettingsScreen(bleManager, preferences, notifications)
                    MainTab.Connection -> ConnectionScreen(bleManager, pairing)
                    MainTab.More -> MoreScreen(bleManager, pairing, firmwareUpdates)
                }
            }
        }
    }
}
