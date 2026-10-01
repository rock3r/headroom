package dev.sebastiano.headroom.ui.delights

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush

/**
 * The refresh shimmer: a sheen of light sweeps across the screen and leaves a trail of sparkles.
 * - The front eases in and out, bends and wavers a little, and turns slightly as it travels. It
 *   travels during the first half of the time.
 * - The front is iridescent: a thin, faint core, a whisper of light ahead of it and behind it, and
 *   a soft glow. Its colour runs through the pastel spectrum across the front, changes a little
 *   along it, and drifts as it travels: see [IRIDESCENCE].
 * - Some cells of a jittered grid hold a four-pointed star, each in its own hue. A star lights up
 *   when the front reaches it, drifts on the push the front gave it, slowing down, and twinkles as
 *   it shrinks and fades, its hue shifting a little. After the front has gone, the last stars
 *   linger.
 *
 * Uniforms: `size` in pixels, `progress` from 0 to 1, `density` in pixels per dp, and the shared
 * light: see [DelightLight].
 */
private const val SHIMMER_SHADER =
    IRIDESCENCE +
        """
uniform float2 size;
uniform float progress;
uniform float density;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

// Cubic ease in and out: a slow start, a quick middle and a slow finish.
float ease(float t) {
    if (t < 0.5) return 4.0 * t * t * t;
    float u = 2.0 - 2.0 * t;
    return 1.0 - u * u * u * 0.5;
}

// The inverse of ease(): the time at which the eased value reaches y, for y from 0 to 1.
float easeInverse(float y) {
    if (y < 0.5) return pow(y * 0.25, 1.0 / 3.0);
    return 1.0 - pow(2.0 - 2.0 * y, 1.0 / 3.0) * 0.5;
}

// Where the front is along the sweep at a point, with its gentle bend.
float bent(float2 p, float2 sweep, float2 normal, float reach) {
    float across = dot(p, normal) / size.y;
    return dot(p, sweep) / reach + 0.035 * sin(across * 5.0 + progress * 7.0);
}

half4 main(float2 coord) {
    // The front travels for the first half of the time; the stars it leaves linger after it.
    const float TRAVEL = 0.5;
    float travel = ease(clamp(progress / TRAVEL, 0.0, 1.0));
    float fade = smoothstep(0.0, 0.04, progress) * (1.0 - smoothstep(0.8, 1.0, progress));

    // The sweep turns a little as it travels.
    float angle = mix(0.42, 0.62, travel);
    float2 sweep = float2(cos(angle), sin(angle));
    float2 normal = float2(-sweep.y, sweep.x);
    float reach = abs(size.x * sweep.x) + abs(size.y * sweep.y);
    float front = mix(-0.25, 1.25, travel);
    float across = clamp(dot(coord, normal) / size.y, 0.0, 1.0);

    // Positive ahead of the front, negative behind it.
    float d = bent(coord, sweep, normal, reach) - front;
    // The spectrum runs across the front, changes a little along it, and drifts as it travels.
    float3 colour = iris(d * 10.0 + across * 0.35 + progress * 0.4);
    float core = bell(d / 0.01) * 0.75;
    float ahead = bell((d - 0.024) / 0.015) * 0.18;
    float behind = bell((d + 0.024) / 0.015) * 0.18;
    float glow = bell(d / 0.07) * 0.07;
    float3 rgb = mix(colour, float3(1.0), whiteCore) * core + colour * (ahead + behind + glow);
    float weight = core + ahead + behind + glow;

    // Each star is born when the front reaches it. The birth time is worked out along the sweep at
    // its middle angle, which is close enough for a sparkle.
    float2 midSweep = float2(cos(0.52), sin(0.52));
    float midReach = abs(size.x * midSweep.x) + abs(size.y * midSweep.y);
    float cellSize = 26.0 * density;
    float2 base = floor(coord / cellSize);
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            float2 cell = base + float2(float(i), float(j));
            float h = hash(cell);
            float2 jitter = float2(hash(cell + 1.7), hash(cell + 4.1)) - 0.5;
            float2 home = (cell + 0.5 + jitter * 0.6) * cellSize;
            float reached = clamp((dot(home, midSweep) / midReach + 0.25) / 1.5, 0.0, 1.0);
            float age = progress - TRAVEL * easeInverse(reached);
            if (h > 0.72 && age > 0.0) {
                // The front pushes the star along, and it slows down as it fades.
                float spread = (hash(cell + 3.3) - 0.5) * 1.4;
                float2 heading = float2(cos(0.52 + spread), sin(0.52 + spread));
                float drift = (0.25 + 0.3 * hash(cell + 6.1)) * cellSize;
                float2 position = home + heading * drift * (1.0 - exp(-age * 9.0));
                // About half a second to a second of life, time being 2.4 s in all.
                float life = exp(-age / (0.1 + 0.1 * hash(cell + 9.3)))
                    * smoothstep(0.0, 0.012, age);
                float twinkle =
                    0.6 + 0.4 * sin(progress * (40.0 + 30.0 * hash(cell + 2.9)) + h * 40.0);
                float radius = (1.8 + 2.6 * hash(cell + 7.7)) * density * (0.6 + 0.4 * life);
                // Each star has its own hue, which shifts a little as it twinkles.
                float4 lit = star(coord - position, radius, hash(cell + 5.3) + age * 0.6);
                rgb += lit.rgb * life * twinkle;
                weight += lit.a * life * twinkle;
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
    val light = delightLight()
    val shader = remember { RuntimeShader(SHIMMER_SHADER) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier = modifier) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("progress", progress())
        shader.setFloatUniform("density", density)
        light.applyTo(shader)
        drawRect(brush, blendMode = light.blendMode)
    }
}
