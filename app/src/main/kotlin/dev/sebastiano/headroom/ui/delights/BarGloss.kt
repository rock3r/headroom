package dev.sebastiano.headroom.ui.delights

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.unit.dp

/**
 * A gloss that sweeps once along a bar that a reset has just refilled, so it reads as shiny and
 * brand new. It is the refresh shimmer's light, made small: see [RefreshShimmer].
 * - A soft, narrow band of iridescent light leans a little and travels from left to right. The
 *   pastel spectrum runs across its width, around a brighter core, and its hues drift as it moves:
 *   see [IRIDESCENCE]. It is clipped to the bar's rounded shape, and it travels during the first
 *   two thirds of the time.
 * - As the band passes, a few four-pointed stars light up around it, in and a little beyond the
 *   bar, each in its own hue. Each one swells, turns a little, and fades within about a quarter of
 *   a second.
 *
 * Uniforms: `bar` the bar's size in pixels, `progress` from 0 to 1, `density` in pixels per dp, and
 * the shared light: see [DelightLight]. The shader draws in the bar's coordinates, where the room
 * above and below the bar has a negative or a larger y.
 */
private const val GLOSS_SHADER =
    IRIDESCENCE +
        """
uniform float2 bar;
uniform float progress;
uniform float density;

float hash(float n) {
    return fract(sin(n * 127.1 + 311.7) * 43758.5453);
}

// The signed distance to a rounded rectangle centred on the origin.
float roundRect(float2 p, float2 extent, float r) {
    float2 q = abs(p) - extent + r;
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 coord) {
    const float TRAVEL = 0.66;
    const int STARS = 9;
    float t = clamp(progress / TRAVEL, 0.0, 1.0);
    float travel = t * t * (3.0 - 2.0 * t);
    float2 centre = bar * 0.5;
    float width = 7.0 * density;
    float margin = width * 3.0;
    float front = mix(-margin, bar.x + margin, travel);

    // The band leans: lower down, it is a little further back.
    float d = coord.x - front - (coord.y - centre.y) * 0.7;
    float inside = 1.0 - smoothstep(-0.75, 0.75, roundRect(coord - centre, centre, centre.y));
    float passing = progress < TRAVEL ? 1.0 : 0.0;
    float band = (bell(d / width) * 0.9 + bell(d / (width * 3.0)) * 0.2) * inside * passing;
    // The spectrum runs across the band and drifts as it moves; the middle is brighter.
    float3 colour = iris(0.22 * d / width + progress * 0.5);
    float3 rgb = mix(colour, float3(1.0), whiteCore * bell(d / (width * 0.45))) * band;
    float weight = band;

    for (int i = 0; i < STARS; i++) {
        float n = float(i);
        if (hash(n + 3.7) < 0.15) continue;
        float along = (n + 0.5 + (hash(n + 1.3) - 0.5) * 0.8) / float(STARS);
        float spread = bar.y * 0.5 + 8.0 * density;
        float2 home = float2(along * bar.x, centre.y + (hash(n + 7.1) - 0.5) * 2.0 * spread);
        // Born as the band reaches it, give or take a little.
        float reached = (home.x + margin) / (bar.x + margin * 2.0);
        float born = TRAVEL * reached;
        float life = (progress - born) / 0.34;
        if (life <= 0.0 || life >= 1.0) continue;
        float swell = smoothstep(0.0, 0.25, life) * (1.0 - smoothstep(0.3, 1.0, life));
        float radius = (3.4 + 3.0 * hash(n + 5.5)) * density * (0.5 + 0.5 * swell);
        float turn = (hash(n + 8.8) - 0.5) * 1.6 * life;
        float2 p = coord - home;
        float2 turned = float2(p.x * cos(turn) - p.y * sin(turn), p.x * sin(turn) + p.y * cos(turn));
        // Each star has its own hue, which shifts a little as it twinkles.
        float4 lit = star(turned, radius, hash(n + 9.9) + life * 0.15) * swell * 1.3;
        rgb += lit.rgb;
        weight += lit.a;
    }

    float alpha = clamp(weight, 0.0, 1.0) * strength;
    float3 premultiplied = min(rgb * strength, float3(alpha));
    return half4(half3(premultiplied), half(alpha));
}
"""

/**
 * Draws the gloss at [progress], from 0 to 1, over a bar of [modifier]'s size: give it the bar's
 * bounds. Before 0 and from 1 on it draws nothing. The stars spread a little beyond those bounds,
 * so nothing around it may clip.
 */
@Composable
internal fun BarGloss(progress: () -> Float, modifier: Modifier = Modifier) {
    val light = delightLight()
    val shader = remember { RuntimeShader(GLOSS_SHADER) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier = modifier) {
        val shown = progress()
        if (shown <= 0f || shown >= 1f) return@Canvas
        val room = StarRoom.toPx()
        shader.setFloatUniform("bar", size.width, size.height)
        shader.setFloatUniform("progress", shown)
        shader.setFloatUniform("density", density)
        light.applyTo(shader)
        drawRect(
            brush = brush,
            topLeft = Offset(-room, -room),
            size = Size(size.width + room * 2, size.height + room * 2),
            blendMode = light.blendMode,
        )
    }
}

/** How far the stars may spread beyond the bar. */
private val StarRoom = 14.dp
