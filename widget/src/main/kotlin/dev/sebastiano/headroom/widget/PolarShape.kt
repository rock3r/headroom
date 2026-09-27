package dev.sebastiano.headroom.widget

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Soft Material-like shapes as polar curves: `r(t) = (1 + a * cos(n * t)) / (1 + a)`, so the
 * outermost points touch the unit circle. These approximate the Material shapes closely enough at
 * widget sizes, and a polar curve is cheap to write into a Remote Compose path.
 */
internal enum class PolarShape(val lobes: Int, val depth: Float) {
    Cookie4(lobes = 4, depth = 0.09f),
    Cookie9(lobes = 9, depth = 0.075f),
    Cookie12(lobes = 12, depth = 0.06f),
    Clover4(lobes = 4, depth = 0.2f),
    Flower8(lobes = 8, depth = 0.12f),
    Sunny(lobes = 8, depth = 0.045f),
    SoftBurst(lobes = 10, depth = 0.09f),
    Pentagon(lobes = 5, depth = 0.07f);

    /** Radius at angle [theta], as a share of the full radius. */
    fun radius(theta: Double): Double = (1 + depth * cos(lobes * theta)) / (1 + depth)

    /**
     * Points around the outline, as offsets from the centre in units of the full radius. The first
     * point is at the top; the outline runs clockwise in screen coordinates.
     */
    fun outline(steps: Int = DEFAULT_STEPS): List<UnitPoint> =
        (0 until steps).map { i ->
            val theta = i * 2 * PI / steps
            val r = radius(theta)
            UnitPoint(x = (r * sin(theta)).toFloat(), y = (-r * cos(theta)).toFloat())
        }

    companion object {
        const val DEFAULT_STEPS = 96
    }
}

/** A point relative to a centre, in units of a radius. */
internal data class UnitPoint(val x: Float, val y: Float)

/** The widget shape for a usage level: calm while there is room, busier as the limit nears. */
public enum class UsageShape {
    Cookie,
    Flower,
    Clover;

    internal val polar: PolarShape
        get() =
            when (this) {
                Cookie -> PolarShape.Cookie9
                Flower -> PolarShape.Flower8
                Clover -> PolarShape.Clover4
            }

    public companion object {
        private const val FLOWER_FROM = 70
        private const val CLOVER_FROM = 85

        public fun forPercent(usedPercent: Int): UsageShape =
            when {
                usedPercent >= CLOVER_FROM -> Clover
                usedPercent >= FLOWER_FROM -> Flower
                else -> Cookie
            }
    }
}
