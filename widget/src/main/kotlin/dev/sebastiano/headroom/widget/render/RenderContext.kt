package dev.sebastiano.headroom.widget.render

import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteTextUnit
import androidx.compose.remote.creation.compose.state.asRemoteTextUnit
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.ui.unit.sp
import dev.sebastiano.headroom.widget.WidgetSize

/** What every widget composable needs besides its state. */
internal class RenderContext(
    val appWidgetId: Int,
    val colors: WidgetColors,
    val strings: WidgetStrings,
    val size: WidgetSize,
    /** Display density, to turn dp into pixels at capture time. */
    private val density: Float,
    private val fontScale: Float,
) {
    /**
     * The design draws a 2×2 widget at 148 dp. Text and spacing scale with the widget, within
     * limits, so a tablet cell or a small phone cell keeps the same proportions.
     */
    private val unit: Float = (size.minDp / DESIGN_WIDGET_DP).coerceIn(MIN_SCALE, MAX_SCALE)

    /** Text size for a design size, scaled with the widget. */
    fun sp(design: Float): RemoteTextUnit = (design * unit).sp.asRemoteTextUnit()

    /** Text size in pixels for canvas text, scaled with the widget and the font scale. */
    fun textPx(design: Float): Float = design * unit * density * fontScale

    /**
     * A design length in pixels, scaled with the widget. Sizes are written in pixels because the
     * Android 16 widget player does not understand dp sizes (`EXACT_DP`): they collapse to zero.
     */
    fun px(design: Float): RemoteFloat = (design * unit * density).rf

    /** A fixed length in pixels, not scaled with the widget. */
    fun fixedPx(dp: Float): RemoteFloat = (dp * density).rf

    private companion object {
        const val DESIGN_WIDGET_DP = 148f
        const val MIN_SCALE = 0.6f
        const val MAX_SCALE = 1.6f
    }
}
