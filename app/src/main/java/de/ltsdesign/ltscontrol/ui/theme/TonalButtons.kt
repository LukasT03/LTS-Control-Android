package de.ltsdesign.ltscontrol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** In dark mode a lighter LTS blue, so the label is as bright as the red one. */
private val TonalBlueDark = Color(0xFF4A9EF0)

/** Signal colors like the system colors on iOS, a bit darker in light mode so they stay readable as text. */
object StatusColors {
    val red: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF453A) else Color(0xFFE3261C)
    val green: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF30D158) else Color(0xFF2DB04F)
    val orange: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF9F0A) else Color(0xFFFF9500)
}

/**
 * Blue button like the bordered buttons on iOS: the color in the label and, a bit transparent,
 * as the fill. [background] is what the button sits on.
 */
@Composable
fun blueTonalButtonColors(background: Color): ButtonColors {
    val dark = isSystemInDarkTheme()
    return tonalButtonColors(
        fill = LtsBrightBlue.copy(alpha = 0.24f),
        label = if (dark) TonalBlueDark else MaterialTheme.colorScheme.primary,
        background = background,
    )
}

/** Red counterpart of [blueTonalButtonColors], e.g. for stopping the motor. */
@Composable
fun redTonalButtonColors(background: Color): ButtonColors {
    val red = StatusColors.red
    return tonalButtonColors(fill = red.copy(alpha = 0.2f), label = red, background = background)
}

/** Green counterpart of [blueTonalButtonColors], e.g. for firmware updates. */
@Composable
fun greenTonalButtonColors(background: Color): ButtonColors {
    val green = StatusColors.green
    return tonalButtonColors(fill = green.copy(alpha = 0.2f), label = green, background = background)
}

/** In light mode the fill is mixed on white, a gray background would make it dull. */
@Composable
private fun tonalButtonColors(fill: Color, label: Color, background: Color): ButtonColors =
    ButtonDefaults.buttonColors(
        containerColor = fill.compositeOver(if (isSystemInDarkTheme()) background else Color.White),
        contentColor = label,
    )
