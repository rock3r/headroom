package dev.sebastiano.headroom.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.MotionPreference
import dev.sebastiano.headroom.model.ThemeMode

/**
 * The theme the user chose in the appearance settings: light, dark or the device's, the colour
 * palette, and reduced motion. The system bar icons follow the chosen light or dark, so the status
 * bar stays readable. [dynamicColor] = false replaces the wallpaper colours with fixed cobalt, for
 * tests and screenshots.
 */
@Composable
fun AppearanceTheme(
    settings: AppSettings,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark =
        when (settings.theme) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
    SystemBarsFollow(dark)
    HeadroomTheme(
        darkTheme = dark,
        dynamicColor = dynamicColor,
        palette = settings.palette,
        reduceMotion = settings.motion == MotionPreference.Reduced,
        content = content,
    )
}

/**
 * Draws the system bars edge to edge with light icons on a [dark] theme and dark icons otherwise.
 * The navigation bar keeps the scrims that `enableEdgeToEdge` uses by default.
 */
@Composable
private fun SystemBarsFollow(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(activity, dark) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
        )
        onDispose {}
    }
}

private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
