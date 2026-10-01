package dev.sebastiano.headroom.ui.delights

import android.graphics.RuntimeShader
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.luminance

/**
 * The AGSL (Android Graphics Shading Language) code that every delight shader shares: the
 * iridescent light of the refresh shimmer and of the gloss on a refilled bar. Put it before a
 * shader's own code.
 *
 * The light is holographic, like light on a sticker or a soap bubble: a soft pastel spectrum from
 * pink through peach, butter yellow, mint and sky blue to lavender, and back to pink. It never
 * takes the theme's colours, so it looks the same in every palette.
 *
 * Uniforms, set by [DelightLight.applyTo]: `strength` the highest opacity, `pastel` how far the
 * spectrum of a sweep is washed toward white, `whiteCore` how white the core of a sweep or a star
 * is, and `halo` how strong the soft, deeper halo around each star is.
 */
internal const val IRIDESCENCE =
    """
uniform float strength;
uniform float pastel;
uniform float whiteCore;
uniform float halo;

// exp(-x²), squared by multiplication: pow() is undefined for a negative base.
float bell(float x) {
    return exp(-x * x);
}

// One stop of the spectrum.
float3 irisStop(float i) {
    if (i < 0.5) return float3(1.00, 0.56, 0.80); // pink
    if (i < 1.5) return float3(1.00, 0.70, 0.52); // peach
    if (i < 2.5) return float3(1.00, 0.87, 0.45); // butter yellow
    if (i < 3.5) return float3(0.52, 0.92, 0.72); // mint
    if (i < 4.5) return float3(0.50, 0.78, 1.00); // sky blue
    return float3(0.74, 0.62, 1.00); // lavender
}

// The spectrum at h: it repeats every 1, and blends smoothly from one stop to the next.
float3 spectrum(float h) {
    float x = fract(h) * 6.0;
    float i = floor(x);
    float3 from = irisStop(i);
    float3 to = irisStop(i > 4.5 ? 0.0 : i + 1.0);
    return mix(from, to, smoothstep(0.0, 1.0, x - i));
}

// The spectrum for a sweep, washed toward white by pastel. Faint colour added to a dark surface
// reads as murky, so a dark theme washes it more and it reads as light.
float3 iris(float h) {
    return mix(spectrum(h), float3(1.0), pastel);
}

// A four-pointed star of radius r in the colour of the spectrum at hue: a small bright core, two
// thin crossed rays in the colour, and a soft halo of the same hue, deeper, that lifts it off a
// pale surface. Stars are bright, so they keep the full colour. Returns the colour, weighted, in
// rgb and the weight in a.
float4 star(float2 p, float r, float hue) {
    float2 q = abs(p) / r;
    float rays = (bell(q.x * 6.0) * bell(q.y) + bell(q.y * 6.0) * bell(q.x)) * 0.7;
    float centre = bell(length(p) / (r * 0.3));
    float glow = bell(length(p) / (r * 0.8)) * halo;
    float3 colour = spectrum(hue);
    float3 rgb = mix(colour, float3(1.0), whiteCore) * centre + colour * rays
        + colour * colour * glow;
    return float4(rgb, centre + rays + glow);
}
"""

/**
 * How the delights' iridescent light is drawn in the current theme: see [IRIDESCENCE]. Only the
 * brightness of the surface matters, never its colours.
 */
@Immutable
internal class DelightLight(
    /** The highest opacity, reached only in the thin core and the stars: bright, but brief. */
    val strength: Float,
    /** How far the spectrum of a sweep is washed toward white. Stars keep the full colour. */
    val pastel: Float,
    /** How white the core of a sweep or a star is. */
    val whiteCore: Float,
    /** How strong the soft halo around each star is. */
    val halo: Float,
    /** Adding light makes a dark theme glow; on a light theme it would vanish into the white. */
    val blendMode: BlendMode,
) {
    /** Sets the uniforms every delight shader shares. */
    fun applyTo(shader: RuntimeShader) {
        shader.setFloatUniform("strength", strength)
        shader.setFloatUniform("pastel", pastel)
        shader.setFloatUniform("whiteCore", whiteCore)
        shader.setFloatUniform("halo", halo)
    }
}

/**
 * The delight light for the current theme. On a dark theme the light is added, and the sweeps are
 * washed toward white, so they read as light rather than murky colour. On a light one white would
 * vanish into the surface, so the spectrum is drawn normally at its full strength, the cores keep
 * some colour, and each star gets a soft halo.
 */
@Composable
@ReadOnlyComposable
internal fun delightLight(): DelightLight {
    val dark = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE
    return if (dark) {
        DelightLight(
            strength = DARK_STRENGTH,
            pastel = DARK_PASTEL,
            whiteCore = DARK_WHITE_CORE,
            halo = 0f,
            blendMode = BlendMode.Plus,
        )
    } else {
        DelightLight(
            strength = LIGHT_STRENGTH,
            pastel = 0f,
            whiteCore = LIGHT_WHITE_CORE,
            halo = LIGHT_HALO,
            blendMode = BlendMode.SrcOver,
        )
    }
}

private const val DARK_SURFACE_LUMINANCE = 0.5f
private const val LIGHT_STRENGTH = 0.8f
private const val DARK_STRENGTH = 0.7f
private const val DARK_PASTEL = 0.45f
private const val DARK_WHITE_CORE = 0.6f
private const val LIGHT_WHITE_CORE = 0.3f
private const val LIGHT_HALO = 0.35f
