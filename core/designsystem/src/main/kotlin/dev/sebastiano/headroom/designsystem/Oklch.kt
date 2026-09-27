package dev.sebastiano.headroom.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A colour in the OKLCH space: perceptual [lightness] (0 to 1), [chroma] and [hue] in degrees.
 * Provider colours are defined here, so that one lightness and chroma gives every hue the same
 * visual weight.
 */
@Immutable data class Oklch(val lightness: Float, val chroma: Float, val hue: Float)

/**
 * Converts to an opaque sRGB [Color]. A colour outside the sRGB gamut keeps its hue and lightness
 * and loses chroma until it fits, which keeps provider colours on their own hue.
 */
fun Oklch.toColor(): Color {
    var linear = toLinearSrgb(chroma.toDouble())
    if (!linear.inGamut()) {
        var low = 0.0
        var high = chroma.toDouble()
        repeat(GAMUT_SEARCH_STEPS) {
            val mid = (low + high) / 2
            if (toLinearSrgb(mid).inGamut()) low = mid else high = mid
        }
        linear = toLinearSrgb(low)
    }
    return Color(red = encode(linear[0]), green = encode(linear[1]), blue = encode(linear[2]))
}

private const val GAMUT_SEARCH_STEPS = 24
private const val GAMUT_EPSILON = 1e-6

private fun DoubleArray.inGamut() = all { it in -GAMUT_EPSILON..(1 + GAMUT_EPSILON) }

@Suppress("MagicNumber") // The OKLab matrices are published constants.
private fun Oklch.toLinearSrgb(chroma: Double): DoubleArray {
    val radians = Math.toRadians(hue.toDouble())
    val a = chroma * cos(radians)
    val b = chroma * sin(radians)
    val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    return doubleArrayOf(
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    )
}

/** Converts an sRGB colour to OKLCH. Alpha is ignored. */
@Suppress("MagicNumber") // The OKLab matrices are published constants.
fun Color.toOklch(): Oklch {
    val r = decode(red)
    val g = decode(green)
    val b = decode(blue)
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    val lightness = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    val hue = Math.toDegrees(atan2(bb, a)).let { if (it < 0) it + FULL_TURN else it }
    return Oklch(lightness.toFloat(), sqrt(a * a + bb * bb).toFloat(), hue.toFloat())
}

/**
 * Rotates [hue] towards [towards] by half the angle between them, but by no more than
 * [MAX_HARMONIZE_DEGREES]. This is how Material harmonises custom colours with a dynamic scheme, so
 * provider colours stay recognisable while fitting the wallpaper.
 */
fun harmonizeHue(hue: Float, towards: Float): Float {
    var difference = (towards - hue) % FULL_TURN.toFloat()
    if (difference > HALF_TURN) difference -= FULL_TURN.toFloat()
    if (difference < -HALF_TURN) difference += FULL_TURN.toFloat()
    val rotation = (difference / 2f).coerceIn(-MAX_HARMONIZE_DEGREES, MAX_HARMONIZE_DEGREES)
    return ((hue + rotation) % FULL_TURN.toFloat() + FULL_TURN.toFloat()) % FULL_TURN.toFloat()
}

private const val FULL_TURN = 360.0
private const val HALF_TURN = 180f
private const val MAX_HARMONIZE_DEGREES = 15f

@Suppress("MagicNumber") // sRGB transfer function constants.
private fun encode(linear: Double): Float {
    val clamped = linear.coerceIn(0.0, 1.0)
    val encoded =
        if (clamped <= 0.0031308) 12.92 * clamped else 1.055 * clamped.pow(1 / 2.4) - 0.055
    return encoded.toFloat().coerceIn(0f, 1f)
}

@Suppress("MagicNumber") // sRGB transfer function constants.
private fun decode(encoded: Float): Double {
    val value = encoded.toDouble()
    return if (abs(value) <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
}
