package dev.c5inco.charts.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

/**
 * Turns a value into display text. Axis ticks and tooltips each take one.
 *
 * Called while a scene compiles, which the Compose `Chart` does off the main thread, possibly for
 * several charts at once: a format must be thread-safe. Pure functions, `String.format` and
 * `java.time` formatters are; a shared `java.text.DecimalFormat` or `SimpleDateFormat` is not (give
 * each thread its own, with a `ThreadLocal`).
 */
fun interface ValueFormat {
    fun format(value: Double): String
}

object Formats {
    /**
     * Compact tick labels for a set of ticks: `0, 2.5k, 5k` or `0, 0.25, 0.5`. Retains enough
     * decimal precision to distinguish the supplied ticks, including after suffix scaling.
     */
    fun forTicks(ticks: List<Double>): ValueFormat {
        val finite = ticks.filter { it.isFinite() }
        val maxAbs = finite.maxOfOrNull { abs(it) } ?: 0.0
        val (divisor, suffix) = suffixFor(maxAbs)
        val decimalDivisor = BigDecimal.valueOf(divisor)
        fun scaled(value: Double) = BigDecimal.valueOf(value).divide(decimalDivisor).stripTrailingZeros()
        // Work in decimal before applying the suffix: dividing doubles can collapse adjacent
        // values even before formatting. Ticks already have their shortest decimal form.
        val decimals = finite.maxOfOrNull { scaled(it).scale().coerceAtLeast(0) } ?: 0
        return ValueFormat { value ->
            when {
                value == 0.0 -> "0"
                !value.isFinite() -> value.toString()
                // Use an exact compact representation rather than hundreds of decimal places
                // for very small ticks. No precision cap may turn different ticks into one label.
                decimals > 12 -> scaled(value).toString() + suffix
                else -> scaled(value).setScale(decimals, RoundingMode.HALF_UP).toPlainString() + suffix
            }
        }
    }

    /**
     * Readable values for tooltips: up to two decimals, grouped thousands, trailing zeros trimmed.
     *
     * Prints exactly what `String.format(Locale.ROOT, "%,.Nf")` would, trimmed: half-up rounding of
     * the value's shortest decimal form, a `-` kept on a negative that rounds to zero. It runs once
     * per datum in every compile, where the formatter's pattern parsing cost about 4 ms per 288
     * values on a Pixel 4 XL; this costs about 0.16 ms.
     */
    val Readable = ValueFormat { value ->
        val absValue = abs(value)
        val (divisor, suffix) = if (absValue >= 1e6) suffixFor(absValue) else 1.0 to ""
        val scaled = value / divisor
        val decimals = if (abs(scaled) >= 100) 0 else if (abs(scaled) >= 10) 1 else 2
        grouped(scaled, decimals) + suffix
    }

    /**
     * A signed change already in percent points, to two decimals: `+1.20%`, `−0.40%` (a true
     * minus, U+2212), and `0.00%` unsigned for anything that rounds to zero. For values that are a
     * change, such as a treemap tile's day move. A missing (non-finite) value prints nothing.
     */
    val Change = ValueFormat { value ->
        if (!value.isFinite()) return@ValueFormat ""
        val text = String.format(Locale.ROOT, "%.2f", abs(value))
        when {
            text.trim('0', '.').isEmpty() -> "$text%"
            value < 0 -> "\u2212$text%"
            else -> "+$text%"
        }
    }

    fun percent(share: Double): String {
        val pct = share * 100
        return trim(String.format(Locale.ROOT, "%.${if (pct < 10) 1 else 0}f", pct)) + "%"
    }

    private fun suffixFor(maxAbs: Double): Pair<Double, String> = when {
        maxAbs >= 1e9 -> 1e9 to "B"
        maxAbs >= 1e6 -> 1e6 to "M"
        maxAbs >= 1e3 -> 1e3 to "k"
        else -> 1.0 to ""
    }

    /** [value] to [decimals] places, half up, thousands grouped with commas, trailing zeros trimmed. */
    private fun grouped(value: Double, decimals: Int): String {
        if (!value.isFinite()) return trim(String.format(Locale.ROOT, "%,.${decimals}f", value))
        val negative = value < 0.0 || (value == 0.0 && 1.0 / value < 0.0)
        // valueOf reads the shortest decimal form, as the formatter does before it rounds.
        val digits = BigDecimal.valueOf(abs(value)).setScale(decimals, RoundingMode.HALF_UP).toPlainString()
        val dot = digits.indexOf('.')
        val whole = if (dot < 0) digits.length else dot
        val out = StringBuilder(digits.length + whole / 3 + 1)
        if (negative) out.append('-')
        for (i in 0 until whole) {
            if (i > 0 && (whole - i) % 3 == 0) out.append(',')
            out.append(digits[i])
        }
        if (dot >= 0) {
            var end = digits.length
            while (end > dot + 1 && digits[end - 1] == '0') end--
            if (end > dot + 1) out.append(digits, dot, end)
        }
        return out.toString()
    }

    private fun trim(text: String): String =
        if ('.' in text) text.trimEnd('0').trimEnd('.') else text
}
