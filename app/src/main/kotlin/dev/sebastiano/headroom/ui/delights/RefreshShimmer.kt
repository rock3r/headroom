package dev.sebastiano.headroom.ui.delights

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * The refresh shimmer: a sheen of light that sweeps diagonally across the screen, like light across
 * glass. It has four parts:
 * - a thin, bright core in the `shine` colour;
 * - a coloured fringe on each side of the core: `tint` ahead of it, `accent` behind it;
 * - a soft glow that shades from `tint` to `accent` across the screen;
 * - small glints on a jittered grid, which twinkle as the core passes them.
 *
 * It fades in and out with the sweep, and stays mostly clear away from the core, so text stays
 * readable.
 *
 * Uniforms: `size` in pixels, `progress` from 0 to 1, `strength` the highest opacity, `density` in
 * pixels per dp, and the colours `shine`, `tint` and `accent`.
 */
private const val SHIMMER_SHADER =
    """
uniform float2 size;
uniform float progress;
uniform float strength;
uniform float density;
layout(color) uniform half4 shine;
layout(color) uniform half4 tint;
layout(color) uniform half4 accent;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

// exp(-x²), squared by multiplication: pow() is undefined for a negative base.
float bell(float x) {
    return exp(-x * x);
}

half4 main(float2 coord) {
    // Along the sweep: 0 at the top-left corner, 1 at the bottom-right one.
    float2 sweep = normalize(float2(1.0, 0.55));
    float along = dot(coord, sweep) / dot(size, sweep);
    float across = dot(coord, float2(-sweep.y, sweep.x)) / size.y;

    // The sheen starts off screen and ends off screen.
    float d = along - mix(-0.3, 1.3, progress);
    float core = bell(d / 0.016);
    float ahead = bell((d - 0.032) / 0.02);
    float behind = bell((d + 0.032) / 0.02);
    float glow = bell(d / 0.1);

    // One glint in some cells of a 32dp grid, lit while the core is near it.
    float cellSize = 32.0 * density;
    float2 cell = floor(coord / cellSize);
    float2 offset = float2(hash(cell + 1.7), hash(cell + 4.1)) - 0.5;
    float2 local = fract(coord / cellSize) - 0.5 - offset * 0.6;
    float radius = 2.2 * density / cellSize;
    float glint = step(0.82, hash(cell)) * bell(length(local) / radius) * bell(d / 0.06);

    float3 glowColour = mix(float3(tint.rgb), float3(accent.rgb), clamp(across, 0.0, 1.0));
    float coreWeight = core * 0.95;
    float fringeWeight = (ahead + behind) * 0.45;
    float glowWeight = glow * 0.22;
    float glintWeight = glint;
    float weight = coreWeight + fringeWeight + glowWeight + glintWeight;
    if (weight < 0.0001) return half4(0.0);
    float3 colour = (float3(shine.rgb) * (coreWeight + glintWeight)
        + float3(tint.rgb) * ahead * 0.45
        + float3(accent.rgb) * behind * 0.45
        + glowColour * glowWeight) / weight;

    float envelope = smoothstep(0.0, 0.12, progress) * (1.0 - smoothstep(0.88, 1.0, progress));
    float alpha = clamp(weight, 0.0, 1.0) * strength * envelope;
    return half4(half3(colour * alpha), half(alpha));
}
"""

/** Draws the shimmer at [progress], from 0 to 1, over the whole of [modifier]'s bounds. */
@Composable
internal fun RefreshShimmer(progress: () -> Float, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < DARK_SURFACE_LUMINANCE
    val strength = if (dark) DARK_STRENGTH else LIGHT_STRENGTH
    val shader = remember { RuntimeShader(SHIMMER_SHADER) }
    val brush = remember(shader) { ShaderBrush(shader) }
    // On a dark theme the core shines white; on a light one white would vanish into the surface,
    // so it shines in a light tint of the primary colour.
    val shine = if (dark) Color.White else lerp(scheme.primary, Color.White, LIGHT_SHINE_WHITENESS)
    Canvas(modifier = modifier) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("progress", progress())
        shader.setFloatUniform("strength", strength)
        shader.setFloatUniform("density", density)
        shader.setColorUniform("shine", shine.toArgb())
        shader.setColorUniform("tint", scheme.primary.toArgb())
        shader.setColorUniform("accent", scheme.tertiary.toArgb())
        drawRect(brush)
    }
}

private const val DARK_SURFACE_LUMINANCE = 0.5f
/** The highest opacity, reached only in the thin core: bright, but gone in a moment. */
private const val LIGHT_STRENGTH = 0.6f
private const val DARK_STRENGTH = 0.7f
private const val LIGHT_SHINE_WHITENESS = 0.55f
