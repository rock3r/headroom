package dev.sebastiano.headroom.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.designsystem.providerColors
import dev.sebastiano.headroom.model.UsagePoint
import java.time.Duration
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One mark per reset, oldest first: a dot for a clean reset, a square for a limit hit. */
@Composable
internal fun ResetMarks(timeline: List<Boolean>, modifier: Modifier = Modifier) {
    val clean = MaterialTheme.colorScheme.primary
    val hit = MaterialTheme.colorScheme.error
    FlowRow(
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        timeline.forEach { hitLimit ->
            Box(
                Modifier.size(MarkSize)
                    .background(
                        if (hitLimit) hit else clean,
                        if (hitLimit) RoundedCornerShape(3.dp) else CircleShape,
                    )
            )
        }
    }
}

/**
 * A donut of the providers' shares, in their own colours, with the biggest share in the middle. The
 * arcs sweep in once, clockwise from the top.
 */
@Composable
internal fun ShareDonut(shares: List<ProviderShare>, modifier: Modifier = Modifier) {
    val colors = shares.map { providerColors(it.provider).accent }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val entrance = rememberEntrance(shares)
    val top = shares.first()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = DonutStroke.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, FULL_TURN, false, topLeft, arcSize, style = Stroke(stroke))
            val gap = if (shares.size > 1) DONUT_GAP_DEGREES else 0f
            var start = START_ANGLE
            shares.forEachIndexed { index, share ->
                val sweep = (share.fraction.toFloat() * FULL_TURN * entrance.value - gap)
                if (sweep > 0f) {
                    drawArc(
                        color = colors[index],
                        startAngle = start + gap / 2,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Butt),
                    )
                }
                start += share.fraction.toFloat() * FULL_TURN * entrance.value
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ProviderAvatar(top.provider, size = 26.dp)
            Text(
                text = stringResource(R.string.percent, (top.fraction * LIMIT).roundToInt()),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/**
 * The week as 7 rows of 24 hours, Monday first. Each cell is darker the more quota was used in that
 * hour, relative to the busiest hour. Hours with no use keep the track colour.
 */
@Composable
internal fun BurnHeatmapChart(
    heatmap: BurnHeatmap,
    dayLabels: List<String>,
    modifier: Modifier = Modifier,
) {
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val full = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val measurer = rememberTextMeasurer()
    val entrance = rememberEntrance(heatmap)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cell = (maxWidth - LabelGutter) / BurnHeatmap.HOURS
        Canvas(modifier = Modifier.fillMaxWidth().height(cell * BurnHeatmap.DAYS + AxisHeight)) {
            val gutter = LabelGutter.toPx()
            val side = (size.width - gutter) / BurnHeatmap.HOURS
            val gap = CellGap.toPx()
            val max = heatmap.max
            dayLabels.forEachIndexed { row, label ->
                val text = measurer.measure(label, labelStyle)
                drawText(
                    text,
                    topLeft = Offset(0f, row * side + (side - text.size.height) / 2),
                )
            }
            for (row in 0 until BurnHeatmap.DAYS) {
                for (column in 0 until BurnHeatmap.HOURS) {
                    val value = heatmap.cells[row * BurnHeatmap.HOURS + column]
                    val strength = if (max > 0.0) sqrt(value / max).toFloat() else 0f
                    val color =
                        if (value <= 0.0) empty
                        else lerp(empty, full, MIN_STRENGTH + (1 - MIN_STRENGTH) * strength)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(gutter + column * side + gap / 2, row * side + gap / 2),
                        size = Size(side - gap, side - gap),
                        cornerRadius = CornerRadius(side * CELL_ROUNDNESS),
                        alpha = if (value <= 0.0) 1f else entrance.value,
                    )
                }
            }
            HOUR_TICKS.forEach { hour ->
                val text = measurer.measure(hour.toString(), labelStyle)
                drawText(
                    text,
                    topLeft =
                        Offset(
                            gutter + hour * side,
                            BurnHeatmap.DAYS * side + AxisGap.toPx(),
                        ),
                )
            }
        }
    }
}

/**
 * The usage of one limit over the sparkline's span: time runs left to right, 0% at the bottom and
 * 100% at the top. It draws itself in from the left once.
 */
@Composable
internal fun SparklineChart(line: Sparkline, color: Color, modifier: Modifier = Modifier) {
    val entrance = rememberEntrance(line.points)
    val baseline = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val span = Duration.between(line.start, line.end).toMillis().toFloat()
        if (span <= 0f) return@Canvas
        val dot = SparkDot.toPx()
        val top = dot
        val bottom = size.height - dot
        fun x(point: UsagePoint) =
            Duration.between(line.start, point.at).toMillis() / span * size.width
        fun y(point: UsagePoint) =
            bottom - (point.usedPercent / LIMIT).toFloat().coerceIn(0f, 1f) * (bottom - top)
        drawLine(baseline, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        val path = Path()
        line.points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(x(point), y(point)) else path.lineTo(x(point), y(point))
        }
        val area =
            Path().apply {
                addPath(path)
                lineTo(x(line.points.last()), bottom)
                lineTo(x(line.points.first()), bottom)
                close()
            }
        clipRect(right = size.width * entrance.value) {
            drawPath(area, color.copy(alpha = AREA_ALPHA))
            drawPath(
                path,
                color,
                style = Stroke(SparkStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        if (entrance.value >= 1f) {
            val last = line.points.last()
            drawCircle(color, dot, Offset(x(last), y(last)))
        }
    }
}

private const val FULL_TURN = 360f
private const val START_ANGLE = -90f
private const val DONUT_GAP_DEGREES = 3f
private const val MIN_STRENGTH = 0.18f
/** The corner radius of a heatmap cell, as a share of its side. */
private const val CELL_ROUNDNESS = 0.25f
private const val AREA_ALPHA = 0.14f
private val HOUR_TICKS = listOf(0, 6, 12, 18)
private val MarkSize = 12.dp
private val DonutStroke = 20.dp
private val LabelGutter = 16.dp
private val CellGap = 2.dp
private val AxisHeight = 18.dp
private val AxisGap = 4.dp
private val SparkStroke = 2.dp
private val SparkDot = 3.dp
