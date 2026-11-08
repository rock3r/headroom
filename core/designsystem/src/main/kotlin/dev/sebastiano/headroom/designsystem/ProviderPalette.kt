package dev.sebastiano.headroom.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.sebastiano.headroom.model.Provider

/** The colours that identify one provider: its avatar, and its bars in provider-coloured views. */
@Immutable
data class ProviderColors(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * Provider colours. Each provider owns a hue; lightness and chroma are shared so no provider looks
 * louder than another. The hue is harmonised with the theme's primary colour, so the colours belong
 * to the wallpaper palette while staying recognisable.
 */
object ProviderPalette {
    @Suppress("MagicNumber") // One hue per provider, in degrees, spread round the colour wheel.
    fun baseHue(provider: Provider): Float =
        when (provider) {
            Provider.Claude -> 45f
            Provider.OpenCodeGo -> 100f
            Provider.Codex -> 155f
            Provider.ZAi -> 195f
            Provider.Grok -> 235f
            Provider.Kimi -> 270f
            Provider.Copilot -> 305f
            Provider.JetBrains -> 350f
            Provider.Muse -> 215f
        }

    fun colors(provider: Provider, primary: Color, dark: Boolean): ProviderColors {
        val hue = harmonizeHue(baseHue(provider), primary.toOklch().hue)
        return if (dark) {
            ProviderColors(
                accent = Oklch(DARK_ACCENT_L, DARK_ACCENT_C, hue).toColor(),
                onAccent = Oklch(DARK_ON_ACCENT_L, ON_C, hue).toColor(),
                container = Oklch(DARK_CONTAINER_L, CONTAINER_C, hue).toColor(),
                onContainer = Oklch(DARK_ON_CONTAINER_L, ON_C, hue).toColor(),
            )
        } else {
            ProviderColors(
                accent = Oklch(LIGHT_ACCENT_L, LIGHT_ACCENT_C, hue).toColor(),
                onAccent = Oklch(LIGHT_ON_ACCENT_L, ON_C, hue).toColor(),
                container = Oklch(LIGHT_CONTAINER_L, CONTAINER_C, hue).toColor(),
                onContainer = Oklch(LIGHT_ON_CONTAINER_L, ON_C, hue).toColor(),
            )
        }
    }

    /**
     * The colour of one product's segment in a window several products share. Slot 0 is the
     * provider's own accent, so the biggest part matches the ring above it; the others come from
     * [PART_COLORS_LIGHT] and [PART_COLORS_DARK], which the iOS app copies, so a product has the
     * same colour on both. They are this palette's accents at fixed hues: orange, magenta, green,
     * coral and olive.
     */
    fun partColor(provider: Provider, slot: Int, primary: Color, dark: Boolean): Color {
        if (slot == 0) return colors(provider, primary, dark).accent
        val table = if (dark) PART_COLORS_DARK else PART_COLORS_LIGHT
        return Color(table[Math.floorMod(slot - 1, table.size)])
    }

    @Suppress("MagicNumber")
    private val PART_COLORS_LIGHT =
        longArrayOf(0xFFB05A00, 0xFF9C54A1, 0xFF34893C, 0xFFB84C59, 0xFF857500)

    @Suppress("MagicNumber")
    private val PART_COLORS_DARK =
        longArrayOf(0xFFF0AD7F, 0xFFDFA8E1, 0xFF95CF96, 0xFFF7A3A9, 0xFFCDC072)

    private const val LIGHT_ACCENT_L = 0.56f
    private const val LIGHT_ACCENT_C = 0.14f
    private const val LIGHT_ON_ACCENT_L = 0.99f
    private const val LIGHT_CONTAINER_L = 0.86f
    private const val LIGHT_ON_CONTAINER_L = 0.3f
    private const val DARK_ACCENT_L = 0.8f
    private const val DARK_ACCENT_C = 0.1f
    private const val DARK_ON_ACCENT_L = 0.2f
    private const val DARK_CONTAINER_L = 0.42f
    private const val DARK_ON_CONTAINER_L = 0.92f
    private const val CONTAINER_C = 0.07f
    private const val ON_C = 0.02f
}

private const val DARK_SURFACE_LUMINANCE = 0.5f

/** The colours of the parts in [slots] of a window [provider] shares, for the current theme. */
@Composable
fun partColors(provider: Provider, slots: List<Int>): List<Color> {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < DARK_SURFACE_LUMINANCE
    return remember(provider, slots, scheme.primary, dark) {
        slots.map { ProviderPalette.partColor(provider, it, scheme.primary, dark) }
    }
}

/** The provider's colours for the current theme. */
@Composable
fun providerColors(provider: Provider): ProviderColors {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < DARK_SURFACE_LUMINANCE
    return remember(provider, scheme.primary, dark) {
        ProviderPalette.colors(provider, scheme.primary, dark)
    }
}
