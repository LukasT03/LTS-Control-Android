package com.lts.control.ui

import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lts.control.R
import com.lts.control.core.ble.BleViewModel
import com.lts.control.core.ble.model.BoardVariant
import com.lts.control.ui.screens.*
import com.lts.control.ui.screens.about.AboutScreen
import com.lts.control.ui.screens.about.ChangelogScreen
import com.lts.control.ui.screens.about.InstructionsScreen
import com.lts.control.ui.screens.about.UpgradeCodeScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val CONNECTION = "connection"
    const val MORE = "more"

    const val RESPOOL_AMOUNT = "respool_amount"
    const val BOARD_VARIANT_SETTINGS = "board_variant_settings"
    const val SERVO_CALIBRATION = "servo_calibration"
    const val INSTRUCTIONS = "instructions"
    const val CHANGELOG = "changelog"
    const val UPGRADE_CODE = "upgrade_code"
}

private val topLevelTabs = listOf(
    Triple(Routes.HOME, Icons.Rounded.Home, R.string.nav_control),
    Triple(Routes.SETTINGS, Icons.Filled.Settings, R.string.nav_settings),
    Triple(Routes.CONNECTION, Icons.Filled.Wifi, R.string.nav_connection),
    Triple(Routes.MORE, Icons.Filled.MoreHoriz, R.string.nav_more)
)

/** Switches bottom-nav tabs, keeping each tab's back stack and state. */
private fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) {
        saveState = true
    }
    launchSingleTop = true
    restoreState = true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LtsControlApp(vm: BleViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.HOME

    // Read status only inside derivedStateOf so 2 Hz telemetry doesn't recompose the whole nav tree
    val status = vm.status.collectAsState()
    val isConnected by vm.isConnected.collectAsState()
    val hasCompletedSetup by vm.hasCompletedSetup.collectAsState()
    val lastDeviceAddress by vm.lastDeviceAddress.collectAsState()

    var showSplash by remember { mutableStateOf(false) }
    val settingsFocus = remember { mutableStateOf<SettingsSection?>(null) }

    LaunchedEffect(hasCompletedSetup, lastDeviceAddress) {
        if (!hasCompletedSetup && lastDeviceAddress.isNullOrEmpty()) {
            showSplash = true
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected && showSplash) {
            showSplash = false
        }
    }

    val isTopLevel = topLevelTabs.any { it.first == currentRoute }

    // Forced Board Variant Modal condition (§5.2)
    val isVariantUnknown by remember {
        derivedStateOf { status.value.didReceiveBoardVariant && status.value.boardVariant == BoardVariant.UNKNOWN }
    }
    val showForcedVariantModal = isVariantUnknown && isConnected && !showSplash

    if (showForcedVariantModal) {
        BoardVariantSelectionDialog(
            vm = vm,
            onDismiss = { /* Forced modal: dismissed when saved or BLE disconnects */ }
        )
    }

    Crossfade(targetState = showSplash, label = "SplashTransition") { isShowingSplash ->
        if (isShowingSplash) {
            OnboardingScreen(
                onConnectClicked = {
                    showSplash = false
                    navController.navigateTopLevel(Routes.CONNECTION)
                }
            )
        } else {
            Scaffold(
                topBar = {
                    // Only show host TopAppBar on Home & Settings; sub-screens and connection/more have their own
                    val titleRes = when (currentRoute) {
                        Routes.HOME -> R.string.title_home
                        Routes.SETTINGS -> R.string.title_settings
                        else -> null
                    }
                    if (titleRes != null) {
                        CenterAlignedTopAppBar(
                            title = { Text(stringResource(titleRes)) },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    }
                },
                bottomBar = {
                    if (isTopLevel) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            val navItemColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            topLevelTabs.forEach { (route, icon, labelRes) ->
                                NavigationBarItem(
                                    selected = currentRoute == route,
                                    onClick = { navController.navigateTopLevel(route) },
                                    icon = { Icon(icon, contentDescription = null) },
                                    label = { Text(stringResource(labelRes)) },
                                    colors = navItemColors
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.HOME,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(Routes.HOME) {
                            ContentScreen(
                                vm = vm,
                                nav = HomeNavigation(
                                    onOpenConnection = { navController.navigateTopLevel(Routes.CONNECTION) },
                                    onOpenSettings = { section ->
                                        settingsFocus.value = section
                                        navController.navigateTopLevel(Routes.SETTINGS)
                                    }
                                ),
                                onDismissSplashIfConnected = { showSplash = false }
                            )
                        }

                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                vm = vm,
                                nav = SettingsNavigation(
                                    onOpenRespoolAmount = { navController.navigate(Routes.RESPOOL_AMOUNT) },
                                    onOpenBoardVariant = { navController.navigate(Routes.BOARD_VARIANT_SETTINGS) },
                                    onOpenServoCalibration = { navController.navigate(Routes.SERVO_CALIBRATION) }
                                ),
                                focusRequest = settingsFocus
                            )
                        }

                        composable(Routes.CONNECTION) {
                            ConnectionScreen(
                                vm = vm,
                                onBack = if (navController.previousBackStackEntry != null) {
                                    { navController.popBackStack() }
                                } else null
                            )
                        }

                        composable(Routes.MORE) {
                            AboutScreen(
                                vm = vm,
                                onNavigateToInstructions = { navController.navigate(Routes.INSTRUCTIONS) },
                                onNavigateToChangelog = { navController.navigate(Routes.CHANGELOG) },
                                onNavigateToUpgradeCode = { navController.navigate(Routes.UPGRADE_CODE) }
                            )
                        }

                        // Sub-screens
                        composable(Routes.RESPOOL_AMOUNT) {
                            RespoolAmountScreen(
                                vm = vm,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Routes.BOARD_VARIANT_SETTINGS) {
                            BoardVariantSettingsScreen(
                                vm = vm,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Routes.SERVO_CALIBRATION) {
                            ServoCalibrationScreen(
                                vm = vm,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Routes.INSTRUCTIONS) {
                            InstructionsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onRestartConnection = { navController.navigateTopLevel(Routes.CONNECTION) }
                            )
                        }

                        composable(Routes.CHANGELOG) {
                            ChangelogScreen(
                                prefsRepo = vm.repo.preferencesRepo,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Routes.UPGRADE_CODE) {
                            UpgradeCodeScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
