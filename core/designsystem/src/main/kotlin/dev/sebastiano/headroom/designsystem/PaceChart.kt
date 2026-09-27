package dev.sebastiano.headroom.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * The window's usage drawn to scale: the usage line and area, the even-pace diagonal (dashed), the
 * projection to the limit (dotted) and the limit line. The chart is one image for screen readers;
 * [contentDescription] should say what it shows in words.
 */
@Composable
fun PaceChart(
    model: PaceChartModel,
    limitLabel: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors =
        ChartColors(
            line = MaterialTheme.colorScheme.primary,
            pace = MaterialTheme.colorScheme.onSurfaceVariant,
            limit = MaterialTheme.colorScheme.error,
            grid = MaterialTheme.colorScheme.outlineVariant,
        )
    val labelStyle =
        MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier =
            modifier.fillMaxWidth().height(ChartHeight).semantics {
                this.contentDescription = contentDescription
                role = Role.Image
            }
    ) {
        val labelSpace = LabelSpace.toPx()
        val geometry =
            PaceChartGeometry(
                start = model.start,
                end = model.end,
                left = EdgeInset.toPx(),
                top = TopInset.toPx(),
                right = size.width - EdgeInset.toPx(),
                bottom = size.height - labelSpace,
            )
        drawGrid(model, geometry, colors, measurer, labelStyle, labelSpace)
        drawLimit(geometry, colors, measurer, labelStyle, limitLabel)
        drawPaceLine(model, geometry, colors)
        drawUsage(model, geometry, colors)
    }
}

@Immutable
private data class ChartColors(val line: Color, val pace: Color, val limit: Color, val grid: Color)

private fun DrawScope.drawGrid(
    model: PaceChartModel,
    geometry: PaceChartGeometry,
    colors: ChartColors,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    labelSpace: Float,
) {
    val ticks = model.tickLabels.size.coerceAtLeast(1)
    val xs = geometry.tickXs(ticks)
    xs.forEach { x ->
        drawLine(
            color = colors.grid,
            start = Offset(x, geometry.y(FULL_PERCENT)),
            end = Offset(x, geometry.y(0.0)),
            strokeWidth = 1.dp.toPx(),
        )
    }
    model.tickLabels.forEachIndexed { index, label ->
        val layout = measurer.measure(label, labelStyle)
        val center = (xs[index] + xs[index + 1]) / 2
        drawText(
            layout,
            topLeft =
                Offset(
                    center - layout.size.width / 2,
                    size.height - labelSpace + (labelSpace - layout.size.height) / 2,
                ),
        )
    }
}

private fun DrawScope.drawLimit(
    geometry: PaceChartGeometry,
    colors: ChartColors,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    limitLabel: String,
) {
    val y = geometry.y(FULL_PERCENT)
    drawLine(
        color = colors.limit,
        start = Offset(EdgeInset.toPx(), y),
        end = Offset(size.width - EdgeInset.toPx(), y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
    )
    val layout = measurer.measure(limitLabel, labelStyle)
    drawText(layout, topLeft = Offset(EdgeInset.toPx() + 2.dp.toPx(), y - layout.size.height))
}

private fun DrawScope.drawPaceLine(
    model: PaceChartModel,
    geometry: PaceChartGeometry,
    colors: ChartColors,
) {
    drawLine(
        color = colors.pace,
        start = Offset(geometry.x(model.start), geometry.y(0.0)),
        end = Offset(geometry.x(model.end), geometry.y(FULL_PERCENT)),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
    )
}

private fun DrawScope.drawUsage(
    model: PaceChartModel,
    geometry: PaceChartGeometry,
    colors: ChartColors,
) {
    val nowX = geometry.x(model.now)
    val nowY = geometry.y(model.usedPercent)
    if (model.points.isNotEmpty()) {
        val line = Path()
        model.points.forEachIndexed { index, point ->
            val x = geometry.x(point.at)
            val y = geometry.y(point.usedPercent)
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val area =
            Path().apply {
                addPath(line)
                lineTo(geometry.x(model.points.last().at), geometry.y(0.0))
                lineTo(geometry.x(model.points.first().at), geometry.y(0.0))
                close()
            }
        drawPath(area, color = colors.line.copy(alpha = AREA_ALPHA))
        drawPath(
            line,
            color = colors.line,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
    model.projectedLimitAt?.let { hit ->
        drawLine(
            color = colors.line,
            start = Offset(nowX, nowY),
            end = Offset(geometry.x(hit), geometry.y(FULL_PERCENT)),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 5.dp.toPx())),
        )
    }
    drawCircle(color = colors.line, radius = 4.dp.toPx(), center = Offset(nowX, nowY))
}

private const val AREA_ALPHA = 0.16f
private const val FULL_PERCENT = 100.0
private val ChartHeight = 136.dp
private val EdgeInset = 4.dp
private val TopInset = 18.dp
private val LabelSpace = 20.dp
