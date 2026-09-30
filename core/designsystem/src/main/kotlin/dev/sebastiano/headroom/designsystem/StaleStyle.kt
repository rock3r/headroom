package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/**
 * How stale data looks: see-through and pulled towards grey. It reads as "old" at a glance, but
 * every number stays readable. The same values in every theme and palette.
 */
object StaleStyle {
    /** How much of the content shows through. */
    const val ALPHA: Float = 0.6f

    /** 1 keeps the colours, 0 is grey. */
    const val SATURATION: Float = 0.15f
}

/** Marks content drawn with [stale], for tests and tools. */
val StaleKey: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey("Stale")

private var SemanticsPropertyReceiver.isStale: Boolean by StaleKey

/**
 * Draws this content as stale data when [stale] is true: faded and desaturated, see [StaleStyle].
 * It changes only how the content is drawn, so it looks the same with animations on or off. Callers
 * say in words why the data is stale: the look alone carries no meaning for a screen reader.
 */
fun Modifier.stale(stale: Boolean): Modifier {
    if (!stale) return this
    return semantics { isStale = true }
        .drawWithCache {
            val paint =
                Paint().apply {
                    alpha = StaleStyle.ALPHA
                    colorFilter =
                        ColorFilter.colorMatrix(
                            ColorMatrix().apply { setToSaturation(StaleStyle.SATURATION) }
                        )
                }
            onDrawWithContent {
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                    drawContent()
                    canvas.restore()
                }
            }
        }
}
