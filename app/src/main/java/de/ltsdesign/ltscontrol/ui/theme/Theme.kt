package de.ltsdesign.ltscontrol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LtsBlue = Color(0xFF0C4C98)
val LtsBrightBlue = Color(0xFF2179C9)

private val LightColors = lightColorScheme(
    primary = LtsBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E8FF),
    onPrimaryContainer = Color(0xFF001C39),
    surface = Color(0xFFF7F7FC),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color(0xFFFBFBFF),
    surfaceContainerHigh = Color(0xFFEFEFF5),
    background = Color(0xFFF4F4FA),
    onBackground = Color(0xFF191A20),
    outlineVariant = Color(0xFFE1E2E8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC7FF),
    primaryContainer = Color(0xFF154A7E),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1C1E24),
    background = Color(0xFF111318),
)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LtsControlTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
