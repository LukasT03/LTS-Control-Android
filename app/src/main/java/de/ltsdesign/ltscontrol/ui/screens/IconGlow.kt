package de.ltsdesign.ltscontrol.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import de.ltsdesign.ltscontrol.ui.settings.SettingsBackground

/**
 * A soft glow around the app icon instead of a shadow: the background a bit darker, in dark mode
 * three quarters toward the next lighter surface gray, white would be too strong there.
 */
@Composable
internal fun iconGlowColor(): Color = if (isSystemInDarkTheme()) {
    lerp(SettingsBackground, MaterialTheme.colorScheme.surfaceContainerHigh, 0.75f)
} else {
    Color.Black.copy(alpha = 0.06f)
}

/** Solid behind the icon, fading out to 2.2 times its radius. */
internal fun DrawScope.drawIconGlow(color: Color, center: Offset, iconRadius: Float) {
    val glowRadius = iconRadius * 2.2f
    drawCircle(
        brush = Brush.radialGradient(
            0f to color,
            iconRadius / glowRadius to color,
            1f to Color.Transparent,
            center = center,
            radius = glowRadius,
        ),
        radius = glowRadius,
        center = center,
    )
}
