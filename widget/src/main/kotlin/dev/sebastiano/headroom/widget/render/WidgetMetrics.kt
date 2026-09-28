package dev.sebastiano.headroom.widget.render

import dev.sebastiano.headroom.widget.WidgetSize
import kotlin.math.floor

/**
 * How big the Bars rows are for a widget size, and how many of them there are room for.
 *
 * Rows share the height they are given, so a taller widget gets taller rows with bigger text
 * instead of empty space, up to a limit. Rows never get shorter than [MIN_ROW_DP]. When they do not
 * fit at that height, a player that can scroll gets a list that [scrolls]. A player that cannot
 * scroll gets rows as short as [COMPACT_ROW_DP], and the accounts that still do not fit become a
 * single "+N more" row.
 *
 * @property shownRows how many account rows are drawn.
 * @property hiddenRows how many accounts the "+N more" row stands for. Zero means no such row.
 */
internal data class BarsMetrics(
    val rowDp: Float,
    val scale: Float,
    val showResetLine: Boolean,
    val scrolls: Boolean,
    val shownRows: Int,
    val hiddenRows: Int,
) {
    companion object {
        const val PADDING_DP: Float = 12f
        const val GAP_DP: Float = 6f
        const val MIN_ROW_DP: Float = 24f
        const val COMPACT_ROW_DP: Float = 20f
        const val MAX_ROW_DP: Float = 60f
        const val MIN_SCALE: Float = 0.85f
        const val MAX_SCALE: Float = 1.8f

        /** The row height the design sizes are drawn for. */
        private const val DESIGN_ROW_DP = 28f

        /** A second line with the reset time needs this much row height and width. */
        private const val RESET_LINE_MIN_ROW_DP = 42f
        private const val RESET_LINE_MIN_WIDTH_DP = 250f

        /** Tolerance for rounding when checking that the rows fit. */
        private const val FIT_TOLERANCE_DP = 0.01f

        /**
         * Metrics for [rows] accounts in a widget of [size]. [canScroll] says whether the widget
         * player handles a scrolling list, see `WidgetRenderer`.
         */
        fun of(size: WidgetSize, rows: Int, canScroll: Boolean): BarsMetrics {
            val count = rows.coerceAtLeast(1)
            val inner = size.heightDp - 2 * PADDING_DP
            val fitsAtMinimum = fits(inner, count, MIN_ROW_DP)
            return when {
                fitsAtMinimum || canScroll ->
                    metrics(size, count, MIN_ROW_DP, count, hidden = 0, scrolls = !fitsAtMinimum)
                fits(inner, count, COMPACT_ROW_DP) ->
                    metrics(size, count, COMPACT_ROW_DP, count, hidden = 0, scrolls = false)
                else -> {
                    val slots = slotsFor(inner).coerceAtLeast(1)
                    val shown = slots - 1
                    metrics(size, slots, COMPACT_ROW_DP, shown, count - shown, scrolls = false)
                }
            }
        }

        private fun fits(inner: Float, slots: Int, rowDp: Float): Boolean =
            slots * rowDp + GAP_DP * (slots - 1) <= inner + FIT_TOLERANCE_DP

        /** How many compact rows fit in [inner] dp. */
        private fun slotsFor(inner: Float): Int =
            floor((inner + GAP_DP + FIT_TOLERANCE_DP) / (COMPACT_ROW_DP + GAP_DP)).toInt()

        private fun metrics(
            size: WidgetSize,
            slots: Int,
            minRowDp: Float,
            shown: Int,
            hidden: Int,
            scrolls: Boolean,
        ): BarsMetrics {
            val inner = size.heightDp - 2 * PADDING_DP
            val shared = (inner - GAP_DP * (slots - 1)) / slots
            val row = shared.coerceIn(minRowDp, MAX_ROW_DP)
            return BarsMetrics(
                rowDp = row,
                scale = (row / DESIGN_ROW_DP).coerceIn(MIN_SCALE, MAX_SCALE),
                showResetLine =
                    row >= RESET_LINE_MIN_ROW_DP && size.widthDp >= RESET_LINE_MIN_WIDTH_DP,
                scrolls = scrolls,
                shownRows = shown,
                hiddenRows = hidden,
            )
        }
    }
}

/** How many columns a grid of accounts uses, from the shape of the widget. */
internal object GridLayout {
    private const val WIDE_RATIO = 1.6f
    private const val TALL_RATIO = 0.6f
    private const val SQUARE_COLUMNS = 2

    fun columns(size: WidgetSize, count: Int): Int {
        val ratio = size.widthDp / size.heightDp
        return when {
            count <= 1 -> 1
            ratio >= WIDE_RATIO -> count
            ratio <= TALL_RATIO -> 1
            else -> SQUARE_COLUMNS
        }
    }
}
