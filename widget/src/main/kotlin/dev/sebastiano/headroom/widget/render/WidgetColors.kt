package dev.sebastiano.headroom.widget.render

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import dev.sebastiano.headroom.designsystem.StaleStyle
import dev.sebastiano.headroom.designsystem.headroomColorScheme
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ThemePalette
import dev.sebastiano.headroom.widget.ColourMode
import dev.sebastiano.headroom.widget.style
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The colours one widget draws with. The base comes from the palette the user picked in the app:
 * the system dynamic palette (which follows the wallpaper), or one of the fixed palettes. The
 * colour mode decides what the arcs, bars and shapes use.
 */
internal class WidgetColors
private constructor(
    private val scheme: ColorScheme,
    private val mode: ColourMode,
    private val tones: HueTones,
    /** Draws every gauge colour as stale data, see [stale]. */
    private val isStale: Boolean = false,
) {
    val background: Color = scheme.surfaceContainer.copy(alpha = BACKGROUND_ALPHA)
    val onSurface: Color = scheme.onSurface.faded()
    val onSurfaceVariant: Color = scheme.onSurfaceVariant.faded()
    val track: Color = scheme.surfaceContainerHighest.faded()
    val paceTick: Color = scheme.onSurface.faded()
    val countdownContainer: Color = scheme.tertiaryContainer
    val onCountdownContainer: Color = scheme.onTertiaryContainer
    /** The text colour on an avatar, which always has the provider hue. */
    val onAvatar: Color = scheme.surface

    /**
     * These colours for the gauges of an account whose sign-in expired: pulled towards grey and
     * faded into the background, as the app draws stale data. Avatars keep their hue.
     */
    fun stale(): WidgetColors = WidgetColors(scheme, mode, tones, isStale = true)

    /** The main arc or bar of an account. */
    fun accent(provider: Provider): Color = accentColor(provider).faded()

    private fun accentColor(provider: Provider): Color =
        when (mode) {
            ColourMode.Wallpaper -> scheme.primary
            ColourMode.PerAccount -> avatar(provider)
            ColourMode.Mono -> scheme.onSurface
        }

    /** The inner (session) ring of a single-account Rings widget. */
    fun secondaryAccent(provider: Provider): Color = secondaryColor(provider).faded()

    private fun secondaryColor(provider: Provider): Color =
        when (mode) {
            ColourMode.Wallpaper -> scheme.tertiary
            ColourMode.PerAccount -> avatar(provider)
            ColourMode.Mono -> scheme.onSurface.copy(alpha = MONO_SECONDARY_ALPHA)
        }

    fun shapeFill(provider: Provider): Color = shapeColor(provider).faded()

    private fun shapeColor(provider: Provider): Color =
        when (mode) {
            ColourMode.Wallpaper -> scheme.primaryContainer
            ColourMode.PerAccount ->
                oklch(tones.containerLightness, CONTAINER_CHROMA, provider.style.hue)
            ColourMode.Mono -> scheme.surfaceContainerHighest
        }

    /** Text drawn on top of [shapeFill]. */
    val onShapeFill: Color =
        when (mode) {
            ColourMode.Wallpaper -> scheme.onPrimaryContainer
            ColourMode.PerAccount,
            ColourMode.Mono -> scheme.onSurface
        }.faded()

    /** Remote Compose has no layer to fade a group, so each stale colour is faded on its own. */
    private fun Color.faded(): Color {
        if (!isStale) return this
        val grey = luminance()
        val greyed =
            Color(
                red = mix(grey, red, StaleStyle.SATURATION),
                green = mix(grey, green, StaleStyle.SATURATION),
                blue = mix(grey, blue, StaleStyle.SATURATION),
                alpha = alpha,
            )
        return lerp(scheme.surfaceContainer, greyed, StaleStyle.ALPHA)
    }

    private fun mix(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

    /** Avatars keep the provider hue in every mode, so accounts stay recognisable. */
    fun avatar(provider: Provider): Color = oklch(tones.lightness, tones.chroma, provider.style.hue)

    companion object {
        private const val BACKGROUND_ALPHA = 1f
        private const val MONO_SECONDARY_ALPHA = 0.55f
        private const val CONTAINER_CHROMA = 0.07f

        fun from(scheme: ColorScheme, mode: ColourMode, isDark: Boolean): WidgetColors =
            WidgetColors(scheme, mode, if (isDark) HueTones.Dark else HueTones.Light)

        /**
         * Colours from [palette], in the launcher's light or dark (dark when [forceDark]). The
         * widgets follow the device's dark theme rather than the app's, since they live on the
         * launcher.
         */
        fun forPalette(
            context: Context,
            palette: ThemePalette,
            mode: ColourMode,
            forceDark: Boolean = false,
        ): WidgetColors {
            val isDark = forceDark || context.isNightMode()
            return from(headroomColorScheme(context, palette, isDark), mode, isDark)
        }

        private fun Context.isNightMode(): Boolean =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
    }
}

/** OKLCH lightness and chroma for the provider hues, per theme. Values match the design. */
private enum class HueTones(
    val lightness: Float,
    val chroma: Float,
    val containerLightness: Float,
) {
    Light(lightness = 0.6f, chroma = 0.14f, containerLightness = 0.86f),
    Dark(lightness = 0.8f, chroma = 0.1f, containerLightness = 0.42f),
}

/** Converts an OKLCH colour (lightness 0–1, chroma, hue in degrees) to an sRGB [Color]. */
@Suppress("MagicNumber") // The OKLab matrices are published constants.
internal fun oklch(lightness: Float, chroma: Float, hueDegrees: Float): Color {
    val hue = Math.toRadians(hueDegrees.toDouble())
    val a = chroma * cos(hue)
    val b = chroma * sin(hue)
    val l = lightness.toDouble()

    val lPrime = l + 0.3963377774 * a + 0.2158037573 * b
    val mPrime = l - 0.1055613458 * a - 0.0638541728 * b
    val sPrime = l - 0.0894841775 * a - 1.2914855480 * b
    val lCube = lPrime * lPrime * lPrime
    val mCube = mPrime * mPrime * mPrime
    val sCube = sPrime * sPrime * sPrime

    val red = 4.0767416621 * lCube - 3.3077115913 * mCube + 0.2309699292 * sCube
    val green = -1.2684380046 * lCube + 2.6097574011 * mCube - 0.3413193965 * sCube
    val blue = -0.0041960863 * lCube - 0.7034186147 * mCube + 1.7076147010 * sCube
    return Color(gamma(red), gamma(green), gamma(blue))
}

@Suppress("MagicNumber") // sRGB transfer function constants.
private fun gamma(linear: Double): Float {
    val clamped = linear.coerceIn(0.0, 1.0)
    val encoded =
        if (clamped <= 0.0031308) 12.92 * clamped else 1.055 * clamped.pow(1 / 2.4) - 0.055
    return encoded.toFloat().coerceIn(0f, 1f)
}
