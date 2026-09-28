package dev.sebastiano.headroom.widget.render

import dev.sebastiano.headroom.widget.BarsLayout
import dev.sebastiano.headroom.widget.WidgetSize

/**
 * How big the Bars rows are for a widget size. Rows share the height they are given, so a taller
 * widget gets taller rows with bigger text instead of empty space, up to a limit.
 */
internal data class BarsMetrics(val rowDp: Float, val scale: Float, val showResetLine: Boolean) {
    companion object {
        const val PADDING_DP: Float = BarsLayout.PADDING_DP
        const val GAP_DP: Float = BarsLayout.GAP_DP
        const val MAX_ROW_DP: Float = 60f
        const val MIN_SCALE: Float = 0.85f
        const val MAX_SCALE: Float = 1.8f

        /** The row height the design sizes are drawn for. */
        private const val DESIGN_ROW_DP = 28f

        /** A second line with the reset time needs this much row height and width. */
        private const val RESET_LINE_MIN_ROW_DP = 42f
        private const val RESET_LINE_MIN_WIDTH_DP = 250f

        fun of(size: WidgetSize, rows: Int): BarsMetrics {
            val count = rows.coerceAtLeast(1)
            val available = size.heightDp - 2 * PADDING_DP - GAP_DP * (count - 1)
            val row = (available / count).coerceIn(BarsLayout.ROW_DP, MAX_ROW_DP)
            return BarsMetrics(
                rowDp = row,
                scale = (row / DESIGN_ROW_DP).coerceIn(MIN_SCALE, MAX_SCALE),
                showResetLine =
                    row >= RESET_LINE_MIN_ROW_DP && size.widthDp >= RESET_LINE_MIN_WIDTH_DP,
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
