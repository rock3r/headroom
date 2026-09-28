package dev.sebastiano.headroom.designsystem

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.max

/**
 * Where a [liquidRipple] started and how far it has spread. One ripple plays at a time: a new tap
 * restarts it from the new point.
 */
@Stable
class LiquidRippleState {
    private val progress = Animatable(0f)
    private var origin by mutableStateOf(Offset.Zero)
    private val shader by lazy { RuntimeShader(LIQUID_RIPPLE_SHADER) }

    /** True while a ripple spreads. The effect is only applied, and only costs, while it is. */
    val isActive: Boolean
        get() = progress.isRunning

    /** Plays a ripple from [origin], in the coordinates of the rippling layout. */
    suspend fun play(origin: Offset) {
        this.origin = origin
        progress.snapTo(0f)
        progress.animateTo(1f, HeadroomMotion.rippleSpec())
    }

    /** The effect for the current frame. Read it only while drawing. */
    internal fun renderEffect(size: Size, density: Density, highlight: Color): RenderEffect {
        val wavelength = with(density) { RippleWavelength.toPx() }
        val farthest =
            max(
                max(hypot(origin.x, origin.y), hypot(size.width - origin.x, origin.y)),
                max(
                    hypot(origin.x, size.height - origin.y),
                    hypot(size.width - origin.x, size.height - origin.y),
                ),
            )
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("origin", origin.x, origin.y)
        shader.setFloatUniform("progress", progress.value)
        // Far enough that the first ring reaches the farthest corner just as the ripple ends.
        shader.setFloatUniform("maxRadius", farthest + wavelength)
        shader.setFloatUniform("amplitude", with(density) { RippleAmplitude.toPx() })
        shader.setFloatUniform("wavelength", wavelength)
        shader.setColorUniform("highlight", highlight.toArgb())
        shader.setFloatUniform("highlightStrength", HIGHLIGHT_STRENGTH)
        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}

@Composable fun rememberLiquidRippleState(): LiquidRippleState = remember { LiquidRippleState() }

/** How far a ring pushes the content, at its strongest. A few pixels read as water, not a wave. */
private val RippleAmplitude = 3.dp

/** The distance between two crests, and so the width of each ring. */
private val RippleWavelength = 28.dp

/** How much of [liquidRipple]'s highlight colour the crest of the first ring shows. */
private const val HIGHLIGHT_STRENGTH = 0.28f

/**
 * The ripple, in AGSL. For each pixel it finds how far behind the leading ring it is, and if it is
 * inside the two rings it samples the content a little along the ring's radius instead of right
 * under the pixel. That displacement is what refracts the content.
 *
 * The wave is a sine along the radius. The rings start softly at the front (a smoothstep over the
 * first quarter wavelength) and decay behind it (an exponential), so the second ring is about a
 * third as strong as the first and the tail ends on zero. The whole ripple fades with `(1 -
 * progress)^1.5`, so it is gone when it has spread across the surface. The crests are lit by mixing
 * in the highlight colour in proportion to the square of the wave, which keeps the light on the
 * crest and off the troughs. Sample points are clamped to the layer, so the edges of the content
 * never pull in transparent pixels.
 *
 * Uniforms: `size` of the layer and `origin` of the tap, in pixels; `progress`, from 0 to 1;
 * `maxRadius`, how far the front travels by the end; `amplitude` and `wavelength`, in pixels; and
 * the `highlight` colour with its `highlightStrength`.
 */
private const val LIQUID_RIPPLE_SHADER =
    """
uniform shader content;
uniform float2 size;
uniform float2 origin;
uniform float progress;
uniform float maxRadius;
uniform float amplitude;
uniform float wavelength;
layout(color) uniform half4 highlight;
uniform float highlightStrength;

const float TAU = 6.2831853;
const float RINGS = 2.0;

half4 main(float2 coord) {
    float2 delta = coord - origin;
    float dist = length(delta);
    float behind = progress * maxRadius - dist;
    if (behind <= 0.0 || behind >= RINGS * wavelength) {
        return content.eval(coord);
    }
    float envelope = smoothstep(0.0, 0.25 * wavelength, behind) * exp(-behind / wavelength);
    float fade = pow(1.0 - progress, 1.5);
    float wave = sin(TAU * behind / wavelength) * envelope * fade;
    float2 direction = dist > 0.0 ? delta / dist : float2(0.0);
    float2 samplePoint = clamp(coord - direction * wave * amplitude, float2(0.5), size - 0.5);
    half4 color = content.eval(samplePoint);
    float crest = max(wave, 0.0);
    return mix(color, highlight, half(crest * crest * highlightStrength));
}
"""
