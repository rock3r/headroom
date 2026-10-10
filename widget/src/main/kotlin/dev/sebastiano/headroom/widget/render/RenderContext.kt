package dev.sebastiano.headroom.widget.render

import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteTextUnit
import androidx.compose.remote.creation.compose.state.asRemoteTextUnit
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.ui.unit.sp
import dev.sebastiano.headroom.widget.Gauge
import dev.sebastiano.headroom.widget.WidgetSize

/** What every widget composable needs besides its state. */
internal class RenderContext(
    val appWidgetId: Int,
    val colors: WidgetColors,
    val strings: WidgetStrings,
    val size: WidgetSize,
    /** The tap targets, registered while the document is composed. */
    val taps: WidgetTaps,
    /** Whether the widget player scrolls lists and still sends taps to the right rows. */
    val playerScrolls: Boolean,
    /** Display density, to turn dp into pixels at capture time. */
    private val density: Float,
    private val fontScale: Float,
) {
    /**
     * The design draws a 2×2 widget at 148 dp. Text and spacing scale with the widget, within
     * limits, so a tablet cell or a small phone cell keeps the same proportions.
     */
    private val unit: Float = (size.minDp / DESIGN_WIDGET_DP).coerceIn(MIN_SCALE, MAX_SCALE)

    private val staleColors by lazy { colors.stale() }

    /** The colours to draw [gauge] with: faded when its numbers are stale. */
    fun colorsFor(gauge: Gauge): WidgetColors = if (gauge.stale) staleColors else colors

    /**
     * How [gauge]'s reset counter looks: a disc in its accent colour with the number in the card
     * colour, [radiusPx] wide and with [textPx] text.
     */
    fun counterStyle(gauge: Gauge, radiusPx: Float, textPx: Float): CounterStyle =
        CounterStyle(
            fill = colorsFor(gauge).accent(gauge.provider),
            text = colors.background,
            halo = colors.background,
            radiusPx = radiusPx,
            textPx = textPx,
            haloPx = radiusPx * COUNTER_HALO_SHARE,
        )

    /** Text size for a design size, scaled with the widget. */
    fun sp(design: Float): RemoteTextUnit = (design * unit).sp.asRemoteTextUnit()

    /** Text size in pixels for canvas text, scaled with the widget and the font scale. */
    fun textPx(design: Float): Float = design * unit * density * fontScale

    /**
     * A design length in pixels, scaled with the widget. Sizes are written in pixels because the
     * Android 16 widget player does not understand dp sizes (`EXACT_DP`): they collapse to zero.
     */
    fun px(design: Float): RemoteFloat = pxValue(design).rf

    /** [px] as a plain number, for geometry worked out at capture time. */
    fun pxValue(design: Float): Float = design * unit * density

    /** A fixed length in pixels, not scaled with the widget. */
    fun fixedPx(dp: Float): RemoteFloat = fixedPxValue(dp).rf

    /** [fixedPx] as a plain number, for geometry worked out at capture time. */
    fun fixedPxValue(dp: Float): Float = dp * density

    private companion object {
        const val DESIGN_WIDGET_DP = 148f
        const val MIN_SCALE = 0.6f
        const val MAX_SCALE = 1.6f
        const val COUNTER_HALO_SHARE = 0.25f
    }
}
