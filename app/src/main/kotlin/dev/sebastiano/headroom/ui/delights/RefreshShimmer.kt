package dev.sebastiano.headroom.ui.delights

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * The refresh shimmer: a sheen of light sweeps across the screen and leaves a trail of sparkles.
 * - The front eases in and out, bends and wavers a little, and turns slightly as it travels. It
 *   travels during the first 70% of the time.
 * - The front is a thin bright core in the `shine` colour, with a `tint` fringe ahead of it, an
 *   `accent` fringe behind it and a soft glow around it.
 * - Some cells of a jittered grid hold a four-pointed star. A star lights up when the front reaches
 *   it, then twinkles and fades as the front moves on. For the last 30% of the time only the trail
 *   is left, fading out.
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

// Cubic ease in and out: a slow start, a quick middle and a slow finish.
float ease(float t) {
    if (t < 0.5) return 4.0 * t * t * t;
    float u = 2.0 - 2.0 * t;
    return 1.0 - u * u * u * 0.5;
}

// A four-pointed star of radius r: a small round core with two thin crossed rays.
float star(float2 p, float r) {
    float2 q = abs(p) / r;
    float rays = bell(q.x * 6.0) * bell(q.y) + bell(q.y * 6.0) * bell(q.x);
    return bell(length(p) / (r * 0.3)) + rays * 0.7;
}

// Where the front is along the sweep at a point, with its gentle bend.
float bent(float2 p, float2 sweep, float2 normal, float reach) {
    float across = dot(p, normal) / size.y;
    return dot(p, sweep) / reach + 0.035 * sin(across * 5.0 + progress * 7.0);
}

half4 main(float2 coord) {
    float travel = ease(clamp(progress / 0.7, 0.0, 1.0));
    float fade = smoothstep(0.0, 0.06, progress) * (1.0 - smoothstep(0.7, 1.0, progress));

    // The sweep turns a little as it travels.
    float angle = mix(0.42, 0.62, travel);
    float2 sweep = float2(cos(angle), sin(angle));
    float2 normal = float2(-sweep.y, sweep.x);
    float reach = abs(size.x * sweep.x) + abs(size.y * sweep.y);
    float front = mix(-0.25, 1.25, travel);
    float across = clamp(dot(coord, normal) / size.y, 0.0, 1.0);

    // Positive ahead of the front, negative behind it.
    float d = bent(coord, sweep, normal, reach) - front;
    float core = bell(d / 0.012) * 0.9;
    float ahead = bell((d - 0.028) / 0.018) * 0.4;
    float behind = bell((d + 0.028) / 0.018) * 0.4;
    float glow = bell(d / 0.08) * 0.16;
    float3 glowColour = mix(float3(tint.rgb), float3(accent.rgb), across);
    float3 rgb = float3(shine.rgb) * core + float3(tint.rgb) * ahead
        + float3(accent.rgb) * behind + glowColour * glow;
    float weight = core + ahead + behind + glow;

    float cellSize = 22.0 * density;
    float2 base = floor(coord / cellSize);
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            float2 cell = base + float2(float(i), float(j));
            float h = hash(cell);
            float2 jitter = float2(hash(cell + 1.7), hash(cell + 4.1)) - 0.5;
            float2 centre = (cell + 0.5 + jitter * 0.8) * cellSize;
            float passed = front - bent(centre, sweep, normal, reach);
            if (h > 0.68 && passed > -0.005) {
                float life = exp(-max(passed, 0.0) / (0.05 + 0.13 * hash(cell + 9.3)));
                float twinkle =
                    0.55 + 0.45 * sin(progress * (30.0 + 25.0 * hash(cell + 2.9)) + h * 40.0);
                float radius = (2.0 + 3.5 * hash(cell + 7.7)) * density;
                float lit = star(coord - centre, radius) * life * twinkle;
                // Most stars shine in the shine colour; some take the tint or the accent.
                float pick = hash(cell + 5.3);
                float3 colour = pick < 0.6 ? float3(shine.rgb)
                    : (pick < 0.8 ? float3(tint.rgb) : float3(accent.rgb));
                rgb += colour * lit;
                weight += lit;
            }
        }
    }

    float alpha = clamp(weight, 0.0, 1.0) * strength * fade;
    float3 premultiplied = min(rgb * strength * fade, float3(alpha));
    return half4(half3(premultiplied), half(alpha));
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
        // Adding light makes a dark theme glow; on a light theme it would vanish into the white.
        drawRect(brush, blendMode = if (dark) BlendMode.Plus else BlendMode.SrcOver)
    }
}

private const val DARK_SURFACE_LUMINANCE = 0.5f
/** The highest opacity, reached only in the thin core and the stars: bright, but brief. */
private const val LIGHT_STRENGTH = 0.8f
private const val DARK_STRENGTH = 0.85f
/** On a light theme the shine is the primary colour, lightened this much toward white. */
private const val LIGHT_SHINE_WHITENESS = 0.2f
