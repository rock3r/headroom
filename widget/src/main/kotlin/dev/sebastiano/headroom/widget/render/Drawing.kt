package dev.sebastiano.headroom.widget.render

import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.shapes.RemoteOutline
import androidx.compose.remote.creation.compose.shapes.drawOutline
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.min as remoteMin
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.text.RemoteTypeface
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import dev.sebastiano.headroom.widget.PolarShape
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws one ring: an arc for [fraction] (0 to 1) starting at the top, and a track for the rest. The
 * arc is flat unless [wavy] is true. The value is static, so the arc never overshoots.
 */
internal fun RemoteDrawScope.drawGaugeRing(
    fraction: Float,
    geometry: RingGeometry,
    active: RemoteColor,
    track: RemoteColor,
    wavy: Boolean = false,
) {
    val minSide = remoteMin(width, height)
    val radius = minSide * geometry.radius.rf
    val topLeft = RemoteOffset(width / 2f.rf - radius, height / 2f.rf - radius)
    val box = RemoteSize(radius * 2f.rf, radius * 2f.rf)
    val strokeWidth = minSide * geometry.stroke.rf
    val sweep = fraction.coerceIn(0f, 1f) * FULL_TURN
    val gap = geometry.gapDegrees

    val trackPaint = strokePaint(track, strokeWidth)
    if (sweep < MIN_VISIBLE_DEGREES) {
        drawArc(trackPaint, 0f.rf, FULL_TURN.rf, false, topLeft, box)
        return
    }
    val trackSweep = FULL_TURN - sweep - 2 * gap
    if (trackSweep > 0f) {
        drawArc(trackPaint, (TOP + sweep + gap).rf, trackSweep.rf, false, topLeft, box)
    }
    val activePaint = strokePaint(active, strokeWidth)
    if (wavy) {
        drawWavyArc(sweep, geometry, minSide, activePaint)
    } else {
        drawArc(activePaint, TOP.rf, sweep.rf, false, topLeft, box)
    }
}

/**
 * A wavy arc as a path of short lines. The wave eases in at both ends so the caps stay round. It
 * does not move: a moving wave needs the restricted time APIs, and a still wave keeps its meaning
 * when animations are off.
 */
private fun RemoteDrawScope.drawWavyArc(
    sweep: Float,
    geometry: RingGeometry,
    minSide: RemoteFloat,
    paint: RemotePaint,
) {
    val steps = max(MIN_WAVE_STEPS, (sweep / DEGREES_PER_WAVE_STEP).roundToInt())
    val cx = width / 2f.rf
    val cy = height / 2f.rf
    val start = Math.toRadians(TOP.toDouble())
    val end = start + Math.toRadians(sweep.toDouble())
    val ramp = Math.toRadians(WAVE_RAMP_DEGREES)
    val outline = RemoteOutline.Generic {
        for (i in 0..steps) {
            val theta = start + (end - start) * i / steps
            val edge = min(1.0, min((theta - start) / ramp, (end - theta) / ramp + RAMP_FLOOR))
            val r = geometry.radius + geometry.amplitude * edge * sin(WAVES * theta)
            val x = cx + minSide * (r * cos(theta)).toFloat().rf
            val y = cy + minSide * (r * sin(theta)).toFloat().rf
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawOutline(outline, paint)
}

/**
 * Draws a horizontal bar: the used part from the start, a track after a small gap, a stop dot at
 * the end and, when [paceFraction] is set, a tick where even pace would be.
 */
internal fun RemoteDrawScope.drawGaugeBar(
    fraction: Float,
    paceFraction: Float?,
    active: RemoteColor,
    track: RemoteColor,
    tick: RemoteColor,
) {
    val value = fraction.coerceIn(0f, 1f)
    val stroke = height * BAR_STROKE_OF_HEIGHT.rf
    val halfStroke = stroke / 2f.rf
    val y = height / 2f.rf
    val usable = width - stroke
    val end = halfStroke + usable * value.rf

    if (value < BAR_FULL) {
        val trackStart = if (value > BAR_EMPTY) end + height * BAR_GAP_OF_HEIGHT.rf else halfStroke
        drawLine(
            strokePaint(track, stroke),
            RemoteOffset(trackStart, y),
            RemoteOffset(width - halfStroke, y),
        )
        drawCircle(
            fillPaint(active),
            height * BAR_STOP_OF_HEIGHT.rf,
            RemoteOffset(width - halfStroke, y),
        )
    }
    if (value > BAR_EMPTY) {
        drawLine(strokePaint(active, stroke), RemoteOffset(halfStroke, y), RemoteOffset(end, y))
    }
    if (paceFraction != null) {
        val tickWidth = height * BAR_TICK_OF_HEIGHT.rf
        val x = halfStroke + usable * paceFraction.coerceIn(0f, 1f).rf - tickWidth / 2f.rf
        drawRect(fillPaint(tick), RemoteOffset(x, 0f.rf), RemoteSize(tickWidth, height))
    }
}

/** Fills a [PolarShape] centred in the drawing area, [scale] times the smaller side. */
internal fun RemoteDrawScope.drawPolarShape(
    shape: PolarShape,
    color: RemoteColor,
    scale: Float = 1f,
) {
    val radius = remoteMin(width, height) * (scale / 2f).rf
    val cx = width / 2f.rf
    val cy = height / 2f.rf
    val points = shape.outline()
    val outline = RemoteOutline.Generic {
        points.forEachIndexed { i, p ->
            val x = cx + radius * p.x.rf
            val y = cy + radius * p.y.rf
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawOutline(outline, fillPaint(color))
}

/** A paint for canvas text. */
internal fun textPaint(color: androidx.compose.ui.graphics.Color, sizePx: Float, bold: Boolean) =
    RemotePaint {
        this.color = color.rc
        textSize = sizePx.rf
        typeface = if (bold) RemoteTypeface.DefaultBold else RemoteTypeface.Default
    }

private fun strokePaint(color: RemoteColor, width: RemoteFloat) = RemotePaint {
    this.color = color
    style = PaintingStyle.Stroke
    strokeWidth = width
    strokeCap = StrokeCap.Round
    strokeJoin = StrokeJoin.Round
}

private fun fillPaint(color: RemoteColor) = RemotePaint {
    this.color = color
    style = PaintingStyle.Fill
}

private const val FULL_TURN = 360f
private const val TOP = -90f
private const val MIN_VISIBLE_DEGREES = 1.5f
private const val MIN_WAVE_STEPS = 24
private const val DEGREES_PER_WAVE_STEP = 3f
private const val WAVE_RAMP_DEGREES = 12.0
private const val RAMP_FLOOR = 0.3
private const val WAVES = 20.0
private const val BAR_STROKE_OF_HEIGHT = 4f / 14f
private const val BAR_GAP_OF_HEIGHT = 5f / 14f
private const val BAR_STOP_OF_HEIGHT = 1.6f / 14f
private const val BAR_TICK_OF_HEIGHT = 2f / 14f
private const val BAR_FULL = 0.995f
private const val BAR_EMPTY = 0.004f
