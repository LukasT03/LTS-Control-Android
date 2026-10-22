package de.ltsdesign.ltscontrol.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.ui.screens.ConnectionScreen
import de.ltsdesign.ltscontrol.ui.screens.ControlScreen
import de.ltsdesign.ltscontrol.ui.screens.MoreScreen
import de.ltsdesign.ltscontrol.ui.screens.SettingsScreen

private enum class MainTab(val label: Int, val icon: ImageVector) {
    Control(R.string.tab_control, Icons.Rounded.Home),
    Settings(R.string.tab_settings, Icons.Rounded.Settings),
    Connection(R.string.tab_connection, Icons.Rounded.Wifi),
    More(R.string.tab_more, Icons.Rounded.MoreHoriz),
}

@Composable
fun LtsControlApp() {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Control) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
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
                    MainTab.Control -> ControlScreen()
                    MainTab.Settings -> SettingsScreen()
                    MainTab.Connection -> ConnectionScreen()
                    MainTab.More -> MoreScreen()
                }
            }
        }
    }
}
