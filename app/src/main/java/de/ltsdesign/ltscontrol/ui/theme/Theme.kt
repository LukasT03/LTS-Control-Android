package de.ltsdesign.ltscontrol.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val LtsBlue = Color(0xFF0C4C98)
val LtsBrightBlue = Color(0xFF2179C9)

/**
 * Switches, sliders, buttons and other accents use the LTS blue like on iOS,
 * surfaces follow the system (wallpaper colors on Android 12 and newer).
 */
private fun ColorScheme.withBrandAccent(dark: Boolean): ColorScheme {
    val brand = if (dark) DarkColors else LightColors
    return copy(
        primary = brand.primary,
        onPrimary = brand.onPrimary,
        primaryContainer = brand.primaryContainer,
        onPrimaryContainer = brand.onPrimaryContainer,
        inversePrimary = brand.inversePrimary,
        surfaceTint = brand.surfaceTint,
    )
}

private val LightColors = lightColorScheme(
    primary = LtsBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E8FF),
    onPrimaryContainer = Color(0xFF001C39),
)

private val DarkColors = darkColorScheme(
    primary = LtsBrightBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF154A7E),
    onPrimaryContainer = Color(0xFFD7E8FF),
)

/**
 * Pixel Settings asks for Google Sans and falls back to Roboto where the device has no such font,
 * so the app looks the same as the system on every phone.
 */
private fun deviceFont(name: String) = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map {
        Font(DeviceFontFamilyName(name), weight = it)
    },
)

private val HeadlineFont = deviceFont("google-sans")
private val TextFont = deviceFont("google-sans-text")

private fun Typography.withFonts(): Typography {
    fun TextStyle.headline() = copy(fontFamily = HeadlineFont)
    fun TextStyle.text() = copy(fontFamily = TextFont)
    return copy(
        displayLarge = displayLarge.headline(),
        displayMedium = displayMedium.headline(),
        displaySmall = displaySmall.headline(),
        headlineLarge = headlineLarge.headline(),
        headlineMedium = headlineMedium.headline(),
        headlineSmall = headlineSmall.headline(),
        titleLarge = titleLarge.headline(),
        titleMedium = titleMedium.headline(),
        titleSmall = titleSmall.headline(),
        bodyLarge = bodyLarge.text(),
        bodyMedium = bodyMedium.text(),
        bodySmall = bodySmall.text(),
        labelLarge = labelLarge.text(),
        labelMedium = labelMedium.text(),
        labelSmall = labelSmall.text(),
    )
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LtsControlTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // Wallpaper colors like the system apps; the LTS blue on Android 11 and older.
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme.withBrandAccent(dark),
        motionScheme = MotionScheme.expressive(),
        typography = Typography().withFonts(),
        content = content,
    )
}
