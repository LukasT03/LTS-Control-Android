package com.lts.control.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.lts.control.core.ble.BleViewModel
import com.lts.control.ui.screens.ConnectionScreen
import com.lts.control.ui.screens.ContentScreen
import com.lts.control.ui.screens.SettingsScreen
import com.lts.control.R

private enum class Tab { Home, Settings, Connection, More }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LtsControlApp(vm: BleViewModel) {
    var current by remember { mutableStateOf(Tab.Home) }

    val title = when (current) {
        Tab.Home -> stringResource(R.string.title_home)
        Tab.Settings -> stringResource(R.string.title_settings)
        Tab.Connection -> stringResource(R.string.title_connection)
        Tab.More -> stringResource(R.string.title_more)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White
            ) {
                val navItemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                )
                NavigationBarItem(
                    selected = current == Tab.Home,
                    onClick = { current = Tab.Home },
                    icon = { Icon(Icons.Rounded.Home, null) },
                    label = { Text(stringResource(R.string.nav_control)) },
                    colors = navItemColors
                )
                NavigationBarItem(
                    selected = current == Tab.Settings,
                    onClick = { current = Tab.Settings },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    colors = navItemColors
                )
                NavigationBarItem(
                    selected = current == Tab.Connection,
                    onClick = { current = Tab.Connection },
                    icon = { Icon(Icons.Filled.Wifi, null) },
                    label = { Text(stringResource(R.string.nav_connection)) },
                    colors = navItemColors
                )
                NavigationBarItem(
                    selected = current == Tab.More,
                    onClick = { current = Tab.More },
                    icon = { Icon(Icons.Filled.MoreHoriz, null) },
                    label = { Text(stringResource(R.string.nav_more)) },
                    colors = navItemColors
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (current) {
                Tab.Home -> ContentScreen(vm, onDismissSplashIfConnected = { })
                Tab.Settings -> SettingsScreen(vm, onOpenRespoolAmount = { /* später */ })
                Tab.Connection -> ConnectionScreen(vm)
                Tab.More -> AboutScreenPlaceholder()
            }
        }
    }
}


/* ------------------------------ Platzhalter Mehr --------------------------- */
@Composable
private fun AboutScreenPlaceholder() {
    Surface(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.more_placeholder),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}