package dev.sebastiano.headroom.ui.resets

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.model.QuotaDisplay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How full each window was when it reset. Every window gets a row of equal columns on a shared 0 to
 * 100% scale, oldest reset first and the running window last, then the running window's value.
 */
@Composable
internal fun ResetHistoryCard(
    windows: List<HistoryWindow>,
    display: QuotaDisplay,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        BoxWithConstraints(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // On a wide card the title sits beside the columns; on a narrow one, above them.
            val wide = maxWidth >= WideRowWidth
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                windows.forEach { window -> key(window.key) { HistoryRow(window, display, wide) } }
                Legend(display)
            }
        }
    }
}

@Composable
private fun HistoryRow(window: HistoryWindow, display: QuotaDisplay, wide: Boolean) {
    val now = display.percent(window.current).roundToInt()
    val description = historyDescription(window, display, now)
    val rowModifier =
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }
    if (wide) {
        Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = window.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 16.dp),
            )
            Plot(window, display, Modifier.width(WidePlotWidth))
            NowValue(now)
        }
    } else {
        Column(modifier = rowModifier) {
            Text(
                text = window.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Plot(window, display, Modifier.weight(1f))
                NowValue(now)
            }
        }
    }
}

@Composable
@ReadOnlyComposable
private fun historyDescription(window: HistoryWindow, display: QuotaDisplay, now: Int): String =
    if (window.past.isEmpty()) {
        stringResource(
            when (display) {
                QuotaDisplay.Used -> R.string.resets_history_description_empty
                QuotaDisplay.Left -> R.string.resets_history_description_empty_left
            },
            window.title,
            now,
        )
    } else {
        stringResource(
            when (display) {
                QuotaDisplay.Used -> R.string.resets_history_description
                QuotaDisplay.Left -> R.string.resets_history_description_left
            },
            window.title,
            window.past.joinToString { "${display.percent(it).roundToInt()}%" },
            now,
        )
    }

/**
 * The columns. The running window always gets its column, so a window that has not reset since the
 * app started recording still shows how far it is, next to a short note.
 */
@Composable
private fun Plot(window: HistoryWindow, display: QuotaDisplay, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(PlotHeight)) {
        ResetColumns(
            past = window.past,
            current = window.current,
            display = display,
            modifier = Modifier.matchParentSize(),
        )
        if (window.past.isEmpty()) {
            Text(
                text = stringResource(R.string.resets_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        .padding(end = ColumnPitch + GuideOverhang, bottom = 2.dp),
            )
        }
    }
}

/** The running window's value, as a number beside its column. */
@Composable
private fun NowValue(percent: Int) {
    Column(modifier = Modifier.width(ValueWidth), horizontalAlignment = Alignment.End) {
        Text(
            text = stringResource(R.string.percent, percent),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End,
        )
        Text(
            text = stringResource(R.string.resets_history_now),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The columns of one window, right-aligned so the running window sits next to its value. When the
 * row is too narrow for every past reset, the oldest ones are left out.
 */
@Composable
private fun ResetColumns(
    past: List<Double>,
    current: Double,
    display: QuotaDisplay,
    modifier: Modifier = Modifier,
) {
    val colors = columnColors()
    val guide = MaterialTheme.colorScheme.outlineVariant
    val animate = animationsEnabled()
    val spec = HeadroomMotion.dataSpec<Float>()
    // The columns rise from the baseline once; with motion reduced they are drawn in place.
    val rise = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) { if (animate) rise.animateTo(1f, spec) else rise.snapTo(1f) }
    Canvas(modifier = modifier) {
        val line = 1.dp.toPx()
        val dash = 3.dp.toPx()
        val pitch = ColumnPitch.toPx()
        val width = ColumnWidth.toPx()
        // The running window's outline stays inside the canvas.
        val end = size.width - RunningStroke.toPx() / 2
        val fits = max(1, ((end - width) / pitch).toInt() + 1)
        val values = (past + current).takeLast(fits)
        // The 100% line, light and dashed, and the baseline, under the columns only.
        val start = max(0f, end - values.lastIndex * pitch - width - GuideOverhang.toPx())
        drawLine(
            color = guide,
            start = Offset(start, line / 2),
            end = Offset(size.width, line / 2),
            strokeWidth = line,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
        )
        drawLine(
            color = guide,
            start = Offset(start, size.height - line / 2),
            end = Offset(size.width, size.height - line / 2),
            strokeWidth = line,
        )
        values.forEachIndexed { index, used ->
            val right = end - (values.lastIndex - index) * pitch
            val fraction = (display.percent(used) / FULL).toFloat().coerceIn(0f, 1f)
            val height = max(MinColumn.toPx(), fraction * (size.height - line)) * rise.value
            drawColumn(
                topLeft = Offset(right - width, size.height - height),
                size = Size(width, height),
                hitLimit = used >= FULL,
                running = index == values.lastIndex,
                colors = colors,
            )
        }
    }
}

@Immutable private data class ColumnColors(val past: Color, val limit: Color)

@Composable
private fun columnColors() =
    ColumnColors(past = MaterialTheme.colorScheme.primary, limit = MaterialTheme.colorScheme.error)

/**
 * A column with rounded top corners. A past reset is filled; the running window is outlined over a
 * light fill, because its value can still grow.
 */
private fun DrawScope.drawColumn(
    topLeft: Offset,
    size: Size,
    hitLimit: Boolean,
    running: Boolean,
    colors: ColumnColors,
) {
    if (size.height <= 0f) return
    val color = if (hitLimit) colors.limit else colors.past
    val radius = CornerRadius(min(ColumnCorner.toPx(), min(size.width / 2, size.height)))
    val path =
        Path().apply {
            addRoundRect(
                RoundRect(
                    left = topLeft.x,
                    top = topLeft.y,
                    right = topLeft.x + size.width,
                    bottom = topLeft.y + size.height,
                    topLeftCornerRadius = radius,
                    topRightCornerRadius = radius,
                    bottomRightCornerRadius = CornerRadius.Zero,
                    bottomLeftCornerRadius = CornerRadius.Zero,
                )
            )
        }
    if (running) {
        drawPath(path, color.copy(alpha = RUNNING_FILL_ALPHA))
        drawPath(path, color, style = Stroke(width = RunningStroke.toPx()))
    } else {
        drawPath(path, color)
    }
}

/** What the column styles mean. Screen readers get the values from each row instead. */
@Composable
private fun Legend(display: QuotaDisplay) {
    val colors = columnColors()
    FlowRow(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendItem(stringResource(R.string.resets_legend_past)) {
            drawColumn(Offset.Zero, size, hitLimit = false, running = false, colors = colors)
        }
        LegendItem(
            stringResource(
                when (display) {
                    QuotaDisplay.Used -> R.string.resets_legend_limit
                    QuotaDisplay.Left -> R.string.resets_legend_limit_left
                }
            )
        ) {
            drawColumn(Offset.Zero, size, hitLimit = true, running = false, colors = colors)
        }
        LegendItem(stringResource(R.string.resets_legend_current)) {
            drawColumn(Offset.Zero, size, hitLimit = false, running = true, colors = colors)
        }
    }
}

@Composable
private fun LegendItem(label: String, swatch: DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = ColumnWidth, height = 12.dp), onDraw = swatch)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val FULL = 100.0
private const val RUNNING_FILL_ALPHA = 0.24f
private val PlotHeight = 48.dp
private val WideRowWidth = 480.dp
private val WidePlotWidth = 260.dp
private val ValueWidth = 56.dp
private val ColumnWidth = 14.dp
private val ColumnPitch = 20.dp
private val GuideOverhang = 6.dp
private val ColumnCorner = 3.dp
private val MinColumn = 2.dp
private val RunningStroke = 1.5.dp
