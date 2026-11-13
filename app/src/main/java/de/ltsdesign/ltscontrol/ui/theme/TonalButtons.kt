package de.ltsdesign.ltscontrol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/** In dark mode a lighter LTS blue, so the label is as bright as the red one. */
private val TonalBlueDark = Color(0xFF4A9EF0)

/**
 * Signal colors like the system colors on iOS, a bit darker in light mode so they stay readable
 * as text and on the light status cards.
 */
object StatusColors {
    val red: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF453A) else Color(0xFFE3261C)
    val green: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF30D158) else Color(0xFF2DB04F)
    val orange: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF9F0A) else Color(0xFFFF9500)
    val cyan: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF3CD3FE) else Color(0xFF00ADD6)
    val purple: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFBF5AF2) else Color(0xFFAF52DE)
    val indigo: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF7D7AFF) else Color(0xFF5856D6)
}

/**
 * Blue button like the bordered buttons on iOS: the color in the label and, a bit transparent,
 * as the fill. [background] is what the button sits on.
 */
@Composable
fun blueTonalButtonColors(background: Color): ButtonColors {
    val dark = isSystemInDarkTheme()
    return tonalButtonColors(
        fill = LtsBrightBlue,
        label = if (dark) TonalBlueDark else MaterialTheme.colorScheme.primary,
        background = background,
    )
}

/** Red counterpart of [blueTonalButtonColors], e.g. for stopping the motor. */
@Composable
fun redTonalButtonColors(background: Color): ButtonColors {
    val red = StatusColors.red
    return tonalButtonColors(fill = red, label = red, background = background)
}

/** Green counterpart of [blueTonalButtonColors], e.g. for firmware updates. */
@Composable
fun greenTonalButtonColors(background: Color): ButtonColors {
    val green = StatusColors.green
    return tonalButtonColors(fill = green, label = green, background = background)
}

/**
 * The fill is [fill] mixed into the [background], so it keeps a bit of the system palette's tint
 * like the card it sits on. In light mode the background is lifted halfway to white first, mixed
 * straight into a tinted card the color would turn dull.
 */
@Composable
private fun tonalButtonColors(fill: Color, label: Color, background: Color): ButtonColors {
    val dark = isSystemInDarkTheme()
    val base = if (dark) background else lerp(background, Color.White, 0.5f)
    return ButtonDefaults.buttonColors(
        containerColor = fill.copy(alpha = if (dark) 0.22f else 0.24f).compositeOver(base),
        contentColor = label,
    )
}
