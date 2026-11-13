package de.ltsdesign.ltscontrol.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Fires when the shown tab is tapped again in the tab bar. Only the shown tab is composed, so only it hears it. */
val LocalTabReselects = staticCompositionLocalOf<Flow<Unit>> { emptyFlow() }

/**
 * Runs [action] when the shown tab is tapped again, like on iOS and in the Pixel apps: back to the
 * tab's main page, and there up to the top.
 */
@Composable
fun OnTabReselect(action: suspend () -> Unit) {
    val reselects = LocalTabReselects.current
    val currentAction by rememberUpdatedState(action)
    LaunchedEffect(reselects) { reselects.collect { currentAction() } }
}
