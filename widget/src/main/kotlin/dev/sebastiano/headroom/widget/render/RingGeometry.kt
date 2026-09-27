package dev.sebastiano.headroom.widget.render

/**
 * Proportions of a ring, as shares of the drawing area's smaller side. Using shares instead of
 * fixed sizes lets the widget player lay the document out at any size.
 */
internal data class RingGeometry(val radius: Float, val stroke: Float) {
    /** Angle left empty between the arc and its track, in degrees. */
    val gapDegrees: Float
        get() = Math.toDegrees(((stroke + GAP_SHARE) / radius).toDouble()).toFloat()

    /** Wave amplitude for a wavy arc. */
    val amplitude: Float
        get() = stroke * WAVE_AMPLITUDE_OF_STROKE

    private companion object {
        const val GAP_SHARE = 0.035f
        const val WAVE_AMPLITUDE_OF_STROKE = 0.2f
    }
}
