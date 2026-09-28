package dev.sebastiano.headroom.ui.delights

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * The refresh shimmer: one soft diagonal band of light that sweeps from the top-left corner to the
 * bottom-right one. Across the band a faint ripple varies the light, like light through water, and
 * the band shades from the theme's primary colour to its tertiary one. It fades in and out with the
 * sweep, and at its brightest it only tints what is below it, so text stays readable.
 *
 * Uniforms: `size` in pixels, `progress` from 0 to 1, `strength` the highest opacity, and the two
 * colours `tint` and `accent`.
 */
private const val SHIMMER_SHADER =
    """
uniform float2 size;
uniform float progress;
uniform float strength;
layout(color) uniform half4 tint;
layout(color) uniform half4 accent;

half4 main(float2 coord) {
    // Along the sweep: 0 at the top-left corner, 1 at the bottom-right one.
    float2 sweep = normalize(float2(1.0, 0.6));
    float along = dot(coord, sweep) / dot(size, sweep);
    // Across the sweep, scaled by the height so the ripple looks the same on every screen.
    float across = dot(coord, float2(-sweep.y, sweep.x)) / size.y;

    // The band starts off screen and ends off screen.
    float centre = mix(-0.35, 1.35, progress);
    // A narrow bright core inside a wider, fainter glow.
    // Squares by multiplication: pow() is undefined for a negative base.
    float core = (along - centre) / 0.045;
    float glow = (along - centre) / 0.13;
    float band = 0.7 * exp(-core * core) + 0.3 * exp(-glow * glow);
    float ripple = 0.88 + 0.12 * sin(across * 40.0 + along * 12.0 - progress * 6.0)
        * sin(across * 23.0 - progress * 4.0);
    float envelope = sin(3.14159265 * progress);

    half3 colour = mix(tint.rgb, accent.rgb, half(clamp(across, 0.0, 1.0)));
    float alpha = strength * band * ripple * envelope;
    return half4(colour * half(alpha), half(alpha));
}
"""

/** Draws the shimmer at [progress], from 0 to 1, over the whole of [modifier]'s bounds. */
@Composable
internal fun RefreshShimmer(progress: () -> Float, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val strength =
        if (scheme.surface.luminance() < DARK_SURFACE_LUMINANCE) DARK_STRENGTH else LIGHT_STRENGTH
    val shader = remember { RuntimeShader(SHIMMER_SHADER) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier = modifier) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("progress", progress())
        shader.setFloatUniform("strength", strength)
        shader.setColorUniform("tint", scheme.primary.toArgb())
        shader.setColorUniform("accent", scheme.tertiary.toArgb())
        drawRect(brush)
    }
}

private const val DARK_SURFACE_LUMINANCE = 0.5f
/** The highest opacity of the band: enough to notice, too little to hide anything. */
private const val LIGHT_STRENGTH = 0.2f
private const val DARK_STRENGTH = 0.16f
