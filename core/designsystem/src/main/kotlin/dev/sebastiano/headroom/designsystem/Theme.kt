package dev.sebastiano.headroom.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.sebastiano.headroom.model.ThemePalette

/**
 * The Headroom theme: Material 3 Expressive with the wallpaper's dynamic colours, or with one of
 * the fixed [palette]s. With the wallpaper palette, pass [dynamicColor] = false for a fixed cobalt
 * palette, which previews and screenshots use so they look the same on every machine. With
 * [reduceMotion], [animationsEnabled] is false everywhere below, as if animations were off.
 */
@Composable
fun HeadroomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    palette: ThemePalette = ThemePalette.Wallpaper,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        remember(context, palette, darkTheme, dynamicColor) {
            headroomColorScheme(context, palette, darkTheme, dynamicColor)
        }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = HeadroomTypography,
            content = content,
        )
    }
}

/** Cobalt, the fallback palette, derived from one hue like a dynamic scheme would be. */
internal val CobaltLight: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF305EB7),
        onPrimary = Color(0xFFFAFCFF),
        primaryContainer = Color(0xFFC9DFFF),
        onPrimaryContainer = Color(0xFF0D275C),
        inversePrimary = Color(0xFFA4C5FF),
        secondary = Color(0xFF546480),
        onSecondary = Color(0xFFFAFCFF),
        secondaryContainer = Color(0xFFD7E2F6),
        onSecondaryContainer = Color(0xFF232E42),
        tertiary = Color(0xFF9D548B),
        onTertiary = Color(0xFFFEFAFD),
        tertiaryContainer = Color(0xFFFDCDEF),
        onTertiaryContainer = Color(0xFF461B3C),
        background = Color(0xFFF8FAFE),
        onBackground = Color(0xFF171B22),
        surface = Color(0xFFF8FAFE),
        onSurface = Color(0xFF171B22),
        surfaceVariant = Color(0xFFDCE2EC),
        onSurfaceVariant = Color(0xFF4F5661),
        surfaceTint = Color(0xFF305EB7),
        inverseSurface = Color(0xFF2A2E36),
        inverseOnSurface = Color(0xFFEBEFF4),
        error = Color(0xFFCC2827),
        onError = Color(0xFFFFFBFA),
        errorContainer = Color(0xFFFFD0C9),
        onErrorContainer = Color(0xFF55110F),
        outline = Color(0xFF808693),
        outlineVariant = Color(0xFFC5CBD5),
        surfaceBright = Color(0xFFF8FAFE),
        surfaceContainer = Color(0xFFEAEEF5),
        surfaceContainerHigh = Color(0xFFE3E8F1),
        surfaceContainerHighest = Color(0xFFDCE2EC),
        surfaceContainerLow = Color(0xFFF0F4FA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFDADEE6),
    )

internal val CobaltDark: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFA4C5FF),
        onPrimary = Color(0xFF0F2A5F),
        primaryContainer = Color(0xFF254582),
        onPrimaryContainer = Color(0xFFD3E5FF),
        inversePrimary = Color(0xFF305EB7),
        secondary = Color(0xFFB0BED8),
        onSecondary = Color(0xFF232E42),
        secondaryContainer = Color(0xFF353D4D),
        onSecondaryContainer = Color(0xFFD3DFF3),
        tertiary = Color(0xFFEEB1DD),
        onTertiary = Color(0xFF461B3C),
        tertiaryContainer = Color(0xFF6B385F),
        onTertiaryContainer = Color(0xFFFBD8F1),
        background = Color(0xFF0D0F14),
        onBackground = Color(0xFFE1E5EB),
        surface = Color(0xFF0D0F14),
        onSurface = Color(0xFFE1E5EB),
        surfaceVariant = Color(0xFF2A2E36),
        onSurfaceVariant = Color(0xFFB2B8C1),
        surfaceTint = Color(0xFFA4C5FF),
        inverseSurface = Color(0xFFDADEE5),
        inverseOnSurface = Color(0xFF1E2229),
        error = Color(0xFFFA887D),
        onError = Color(0xFF55110F),
        errorContainer = Color(0xFF7C2621),
        onErrorContainer = Color(0xFFFFD9D3),
        outline = Color(0xFF7B8089),
        outlineVariant = Color(0xFF3E434B),
        surfaceBright = Color(0xFF2A2E36),
        surfaceContainer = Color(0xFF181C22),
        surfaceContainerHigh = Color(0xFF20242B),
        surfaceContainerHighest = Color(0xFF2A2E36),
        surfaceContainerLow = Color(0xFF13161C),
        surfaceContainerLowest = Color(0xFF07090D),
        surfaceDim = Color(0xFF0D0F14),
    )
