package dev.sebastiano.headroom.designsystem

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeVibrant
import dev.sebastiano.headroom.model.ThemePalette
import java.util.concurrent.ConcurrentHashMap

/**
 * The seed colour of a fixed palette: a bright, cheerful hue that the whole scheme grows from. The
 * wallpaper palette has none; its colours come from the device.
 */
val ThemePalette.seed: Color?
    get() =
        when (this) {
            ThemePalette.Wallpaper -> null
            ThemePalette.Coral -> Color(CORAL)
            ThemePalette.Tangerine -> Color(TANGERINE)
            ThemePalette.Lemon -> Color(LEMON)
            ThemePalette.Lime -> Color(LIME)
            ThemePalette.Lagoon -> Color(LAGOON)
            ThemePalette.Sky -> Color(SKY)
            ThemePalette.Grape -> Color(GRAPE)
            ThemePalette.Bubblegum -> Color(BUBBLEGUM)
        }

private const val CORAL = 0xFFFF6F61
private const val TANGERINE = 0xFFFF8C1A
private const val LEMON = 0xFFFFD60A
private const val LIME = 0xFF8BD41A
private const val LAGOON = 0xFF00BFA5
private const val SKY = 0xFF2E9BFF
private const val GRAPE = 0xFF8A4DFF
private const val BUBBLEGUM = 0xFFFF4FA3

/**
 * The colour scheme for [palette]. [Wallpaper][ThemePalette.Wallpaper] uses the device's dynamic
 * colours, or the fixed cobalt scheme when [dynamicColor] is false (previews and screenshots). The
 * app and the widgets both take their colours from here.
 */
fun headroomColorScheme(
    context: Context,
    palette: ThemePalette,
    dark: Boolean,
    dynamicColor: Boolean = true,
): ColorScheme =
    when {
        palette != ThemePalette.Wallpaper -> seededColorScheme(palette, dark)
        dynamicColor && dark -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        dark -> CobaltDark
        else -> CobaltLight
    }

/**
 * A full Material 3 scheme grown from a fixed [palette]'s seed with Material Color Utilities (the
 * MaterialKolor port). It uses the vibrant variant, so the containers keep the seed's bright
 * colour, on the 2021 spec. The 2025 spec tints every surface strongly with the seed, which made
 * the pages loud and harder to read; tonal spot, the variant Android uses for the wallpaper, dulled
 * the seeds too much. Both were compared in screenshots. Schemes are made once and kept.
 */
fun seededColorScheme(palette: ThemePalette, dark: Boolean): ColorScheme {
    val seed = requireNotNull(palette.seed) { "$palette has no seed colour" }
    return seededSchemes.getOrPut(palette to dark) {
        SchemeVibrant(
                sourceColorHct = Hct.fromInt(seed.toArgb()),
                isDark = dark,
                contrastLevel = STANDARD_CONTRAST,
                specVersion = ColorSpec.SpecVersion.SPEC_2021,
            )
            .toColorScheme()
    }
}

private const val STANDARD_CONTRAST = 0.0

private val seededSchemes = ConcurrentHashMap<Pair<ThemePalette, Boolean>, ColorScheme>()

/** Every Material 3 colour role, read from the scheme's dynamic colours. */
private fun DynamicScheme.toColorScheme(): ColorScheme =
    ColorScheme(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(inversePrimary),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary),
        onTertiary = Color(onTertiary),
        tertiaryContainer = Color(tertiaryContainer),
        onTertiaryContainer = Color(onTertiaryContainer),
        background = Color(background),
        onBackground = Color(onBackground),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(surfaceTint),
        inverseSurface = Color(inverseSurface),
        inverseOnSurface = Color(inverseOnSurface),
        error = Color(error),
        onError = Color(onError),
        errorContainer = Color(errorContainer),
        onErrorContainer = Color(onErrorContainer),
        outline = Color(outline),
        outlineVariant = Color(outlineVariant),
        scrim = Color(scrim),
        surfaceBright = Color(surfaceBright),
        surfaceDim = Color(surfaceDim),
        surfaceContainer = Color(surfaceContainer),
        surfaceContainerHigh = Color(surfaceContainerHigh),
        surfaceContainerHighest = Color(surfaceContainerHighest),
        surfaceContainerLow = Color(surfaceContainerLow),
        surfaceContainerLowest = Color(surfaceContainerLowest),
        primaryFixed = Color(primaryFixed),
        primaryFixedDim = Color(primaryFixedDim),
        onPrimaryFixed = Color(onPrimaryFixed),
        onPrimaryFixedVariant = Color(onPrimaryFixedVariant),
        secondaryFixed = Color(secondaryFixed),
        secondaryFixedDim = Color(secondaryFixedDim),
        onSecondaryFixed = Color(onSecondaryFixed),
        onSecondaryFixedVariant = Color(onSecondaryFixedVariant),
        tertiaryFixed = Color(tertiaryFixed),
        tertiaryFixedDim = Color(tertiaryFixedDim),
        onTertiaryFixed = Color(onTertiaryFixed),
        onTertiaryFixedVariant = Color(onTertiaryFixedVariant),
    )
