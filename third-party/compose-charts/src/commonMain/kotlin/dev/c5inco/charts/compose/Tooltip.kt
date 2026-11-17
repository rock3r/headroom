package dev.c5inco.charts.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.c5inco.charts.core.ChartScene
import dev.c5inco.charts.core.ChartSpec
import dev.c5inco.charts.core.Focus
import dev.c5inco.charts.core.FocusDetail
import dev.c5inco.charts.core.FocusMode
import dev.c5inco.charts.core.FocusPoint
import dev.c5inco.charts.core.Formats
import dev.c5inco.charts.core.RectHit
import dev.c5inco.charts.core.TreemapChart
import dev.c5inco.charts.core.ValueFormat
import dev.c5inco.charts.core.colorSlot
import dev.c5inco.charts.core.legend
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where a chart's tooltip sits relative to its [Focus]. Placements are clamped to the chart's
 * sides and bottom edge, and may overflow its top edge (`y < 0`) so short charts and touches near
 * the top keep the tooltip above the mark and the finger ([TooltipStyle.fingerClearance]).
 */
public enum class TooltipPlacement {
    /**
     * [BesideNeedle] when scrubbing ([FocusMode.NearestX]), [AboveAnchor] otherwise. Rows
     * ([FocusMode.NearestY]) get a row's version of above: centred on the far end of the row and
     * clear of every bar in it. A heatmap cell is treated the same way: above the cell, never over
     * it.
     */
    Auto,

    /**
     * Beside the needle at the top of the plot: right of it, or left when it would overflow the
     * right edge; lifted above the finger on short charts.
     */
    BesideNeedle,

    /** Centred above the focused point; overflows the chart's top edge when needed. */
    AboveAnchor,

    /**
     * Centred on the focus x and pinned above the top edge of the chart, outside its bounds,
     * clamped to its sides. For sparklines and other charts too short to hold a tooltip inside.
     */
    AboveChart,

    /**
     * Centred on the needle and pinned to the top edge of the chart, clamped to its sides. For a
     * scrub label (a time or a date) above a chart that shows its value elsewhere.
     */
    TopCenter,
}

/**
 * Where the top-left of a [width] × [height] tooltip goes for a [maxWidth] × [maxHeight] chart.
 * Pure arithmetic, so placement is testable without layout.
 *
 * When [pointerY] is finite and [fingerClearance] is positive (a touch gesture), the tooltip's
 * bottom edge also stays at least [fingerClearance] above [pointerY]; a negative y overflows above
 * the chart's top edge rather than clamping onto the data or falling under the finger.
 */
internal fun tooltipPosition(
    placement: TooltipPlacement,
    focus: Focus,
    plotTop: Float,
    width: Int,
    height: Int,
    gap: Int,
    maxWidth: Int,
    maxHeight: Int,
    pointerY: Float = Float.NaN,
    fingerClearance: Int = 0,
): IntOffset {
    val maxX = (maxWidth - width).coerceAtLeast(0)
    val maxY = if (pointerY.isFinite() && fingerClearance > 0) {
        minOf(maxHeight - height, (pointerY - fingerClearance).roundToInt() - height)
    } else {
        maxHeight - height
    }
    val ax = focus.anchor.x.roundToInt()
    val ay = focus.anchor.y.roundToInt()
    val centeredX = (ax - width / 2).coerceIn(0, maxX)
    if (placement == TooltipPlacement.Auto && focus.mode == FocusMode.NearestY) {
        // Clear of every bar in the row, not just the anchor at its centre line.
        val bars = focus.group.mapNotNull { (it.hit as? RectHit)?.bounds }
        val top = bars.minOfOrNull { it.top } ?: focus.anchor.y
        return IntOffset(centeredX, ((top - gap).roundToInt() - height).coerceAtMost(maxY))
    }
    val cell = (focus.primary.detail as? FocusDetail.Cell)?.hit?.bounds
    if (placement == TooltipPlacement.Auto && focus.mode == FocusMode.Containment && cell != null) {
        // Clear of the whole cell: above it, never over it.
        return IntOffset(centeredX, ((cell.top - gap).roundToInt() - height).coerceAtMost(maxY))
    }
    val resolved = when (placement) {
        TooltipPlacement.Auto -> if (focus.mode == FocusMode.NearestX) TooltipPlacement.BesideNeedle else TooltipPlacement.AboveAnchor
        else -> placement
    }
    return when (resolved) {
        TooltipPlacement.BesideNeedle -> {
            val right = ax + gap
            val x = if (right + width <= maxWidth) right else ax - gap - width
            IntOffset(x.coerceIn(0, maxX), plotTop.roundToInt().coerceAtLeast(0).coerceAtMost(maxY))
        }
        TooltipPlacement.AboveChart -> IntOffset(centeredX, -height - gap)
        TooltipPlacement.TopCenter -> IntOffset(centeredX, 0)
        else -> IntOffset(centeredX, (ay - gap - height).coerceAtMost(maxY))
    }
}

/** Positions a tooltip for [focus] by [placement]. An empty [content] places nothing. */
@Composable
internal fun PlaceTooltip(
    focus: Focus,
    scene: ChartScene,
    placement: TooltipPlacement,
    gap: Dp,
    fingerClearance: Dp = 0.dp,
    pointerY: () -> Float = { Float.NaN },
    content: @Composable () -> Unit,
) {
    Layout(content) { measurables, constraints ->
        // A tooltip slot may choose to show nothing for a focus. Unbounded height so a tooltip on a
        // short sparkline measures at its natural height rather than being crushed to the chart's.
        val placeable = measurables.firstOrNull()?.measure(Constraints(maxWidth = constraints.maxWidth))
            ?: return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}
        val gapPx = gap.roundToPx()
        val clearancePx = fingerClearance.roundToPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            val at = tooltipPosition(
                placement, focus, scene.plot.top, placeable.width, placeable.height, gapPx,
                constraints.maxWidth, constraints.maxHeight, pointerY(), clearancePx,
            )
            placeable.place(at)
        }
    }
}

/**
 * The default tooltip: a title, then one row per focused point. [FocusMode] decides the grouping;
 * each row's content follows what its mark means, so only part-to-whole marks show a share, a
 * candle lists its open, high, low, close and volume, a treemap tile shows its group and value
 * in the tile's colour, then its size and share, and a heatmap cell shows its label, then its raw
 * value beside a swatch in the cell's colour (no share: intensity is not a part of a whole). A rose
 * petal shows its wedge's label, then its value once beside a swatch in the petal's colour; a radar
 * vertex shows its series, then its axis and value.
 */
@Composable
public fun ChartTooltip(focus: Focus, scene: ChartScene, style: ChartStyle) {
    val t = style.tooltip
    val shape = RoundedCornerShape(t.cornerRadius)
    Layout(
        content = { TooltipContent(focus, scene, style) },
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .background(t.container, shape)
            .then(if (t.border != null) Modifier.border(1.dp, t.border, shape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        measurePolicy = TooltipColumn,
    )
}

/** The tooltip's title and rows, stacked by [TooltipColumn]. */
@Composable
internal fun TooltipContent(focus: Focus, scene: ChartScene, style: ChartStyle) {
    val t = style.tooltip
    val p = focus.primary
    when (focus.mode) {
        FocusMode.NearestX, FocusMode.NearestY -> {
            BasicText(p.label, style = t.title)
            // Rows in the order the marks appear, top to bottom.
            focus.group.sortedBy { it.y }.forEach { point -> PointRow(point, scene.series[point.series].name, scene, style) }
        }
        FocusMode.NearestXY -> {
            BasicText(scene.series[p.series].name, style = t.title)
            PointRow(p, p.label, scene, style)
        }
        FocusMode.Containment -> {
            BasicText(p.label, style = t.title)
            // A heatmap cell's column is not a series: it has no name to show.
            PointRow(p, scene.series.getOrNull(p.series)?.name.orEmpty(), scene, style)
        }
    }
}

/** The tooltip's gap between its title and each row. */
internal val TooltipRowGap = 4.dp

/**
 * The tooltip's column: what `Column(verticalArrangement = Arrangement.spacedBy(4.dp))` measures and
 * places, line for line (children stacked from the top at the start edge, each measured with no
 * minimum, `4.dp` between them, the trailing gap dropped and shortened when space runs out), and the
 * same intrinsics, but for how it answers [maxIntrinsicWidth], which `Modifier.width(IntrinsicSize.Max)`
 * asks on every measure.
 *
 * A Column answers it by asking every child its max intrinsic height first, and a row's height is its
 * text laid out: every text that changed was laid out once for that and again when measured, on
 * every pointer move. Each child here is a title or a row of fixed-size swatches and spacers and
 * one-line texts, whose max intrinsic width does not depend on the height it is offered, so the
 * widest child's max intrinsic width is the Column's answer without asking any heights.
 */
internal object TooltipColumn : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val spacing = TooltipRowGap.roundToPx()
        val mainAxisMax = constraints.maxHeight
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        var fixedSpace = 0
        var crossAxisSpace = 0
        var spaceAfterLast = 0
        measurables.forEachIndexed { i, child ->
            val remaining = mainAxisMax - fixedSpace
            val placeable = child.measure(
                Constraints(
                    minWidth = 0,
                    maxWidth = constraints.maxWidth,
                    minHeight = 0,
                    maxHeight = if (mainAxisMax == Constraints.Infinity) Constraints.Infinity else remaining.coerceAtLeast(0),
                ),
            )
            spaceAfterLast = min(spacing, (remaining - placeable.height).coerceAtLeast(0))
            fixedSpace += placeable.height + spaceAfterLast
            crossAxisSpace = max(crossAxisSpace, placeable.width)
            placeables[i] = placeable
        }
        fixedSpace -= spaceAfterLast
        val height = max(fixedSpace.coerceAtLeast(0), constraints.minHeight)
        val width = max(crossAxisSpace, constraints.minWidth)
        return layout(width, height) {
            // Arrangement.spacedBy's top-down positions.
            var occupied = 0
            for (placeable in placeables) {
                placeable!!
                val y = min(occupied, height - placeable.height)
                occupied = y + placeable.height + min(spacing, height - y - placeable.height)
                placeable.place(Alignment.Start.align(placeable.width, width, layoutDirection), y)
            }
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
        var widest = 0
        for (child in measurables) widest = max(widest, child.maxIntrinsicWidth(Constraints.Infinity))
        return widest
    }

    // As a Column with no weights: across, the widest child given the height it asks for; down, the
    // children's heights and the gaps between them.
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
        if (measurables.isEmpty()) return 0
        val spacing = TooltipRowGap.roundToPx()
        var fixedSpace = min((measurables.size - 1) * spacing, height)
        var widest = 0
        for (child in measurables) {
            val remaining = if (height == Constraints.Infinity) Constraints.Infinity else height - fixedSpace
            val space = min(child.maxIntrinsicHeight(Constraints.Infinity), remaining)
            fixedSpace += space
            widest = max(widest, child.minIntrinsicWidth(space))
        }
        return widest
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        if (measurables.isEmpty()) 0
        else measurables.sumOf { it.minIntrinsicHeight(width) } + (measurables.size - 1) * TooltipRowGap.roundToPx()

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        if (measurables.isEmpty()) 0
        else measurables.sumOf { it.maxIntrinsicHeight(width) } + (measurables.size - 1) * TooltipRowGap.roundToPx()
}

/**
 * A share column only where there is a real whole to be a part of. The swatch keeps the series'
 * glyph and takes the colour the point is painted in: a loss on a diverging area shows its loss
 * colour.
 */
@Composable
private fun PointRow(point: FocusPoint, name: String, scene: ChartScene, style: ChartStyle) {
    val glyph = style.glyph(scene.slot(point.series))
    when (val detail = point.detail) {
        is FocusDetail.Candle -> CandleRows(detail, glyph, name, style)
        is FocusDetail.Tile -> TileRows(point, detail, name, style)
        // A heatmap cell: its raw value beside a swatch in the cell's colour. Never a share.
        is FocusDetail.Cell -> TooltipRow(Glyph.Square, style.sequential.color(detail.intensity), "", point.valueText, style)
        // A rose petal: the title already names the wedge, so the row is its value, once, beside a
        // swatch in the petal's colour. Never a share: a petal is not a part of a whole.
        is FocusDetail.Petal -> TooltipRow(glyph, style.color(scene.colorSlot(point)), "", point.valueText, style)
        // A pie slice: its value and its share of the whole.
        is FocusDetail.Arc -> if (detail.share.isFinite()) {
            TooltipRow(glyph, style.color(scene.colorSlot(point)), point.valueText, Formats.percent(detail.share), style)
        } else {
            TooltipRow(glyph, style.color(scene.colorSlot(point)), name, point.valueText, style)
        }
        is FocusDetail.Bar, is FocusDetail.Line, is FocusDetail.Area, is FocusDetail.Dot ->
            TooltipRow(glyph, style.color(scene.colorSlot(point)), name, point.valueText, style)
    }
}

/** A candle: its name with a swatch in its direction's colour, then its prices and volume, labelled. */
@Composable
private fun CandleRows(candle: FocusDetail.Candle, glyph: Glyph, name: String, style: ChartStyle) {
    TooltipRow(glyph, style.candle.color(candle.down), name, "", style)
    DetailRow("Open", candle.open, style)
    DetailRow("High", candle.high, style)
    DetailRow("Low", candle.low, style)
    DetailRow("Close", candle.close, style)
    candle.volume?.let { DetailRow("Volume", it, style) }
}

/**
 * A treemap tile: its group with a swatch in the tile's colour and its value (none when the value
 * is missing), then its size and its share of the whole.
 */
@Composable
private fun TileRows(point: FocusPoint, tile: FocusDetail.Tile, group: String, style: ChartStyle) {
    TooltipRow(Glyph.Square, style.diverging.color(point.value), group, point.valueText, style)
    DetailRow("Size", tile.sizeText, style)
    if (tile.share.isFinite()) DetailRow("Share", Formats.percent(tile.share), style)
}

/** A labelled value under a point's row, aligned with the row's name. */
@Composable
private fun DetailRow(label: String, value: String, style: ChartStyle) {
    val t = style.tooltip
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(t.swatch + 6.dp))
        BasicText(label, style = t.body)
        Spacer(Modifier.width(16.dp))
        Spacer(Modifier.weight(1f))
        BasicText(value, style = t.body.merge(TextStyle(fontWeight = FontWeight.SemiBold)))
    }
}

@Composable
private fun TooltipRow(glyph: Glyph, color: Color, name: String, value: String, style: ChartStyle) {
    val t = style.tooltip
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlyphSwatch(glyph, color, t.swatch)
        Spacer(Modifier.width(6.dp))
        BasicText(name, style = t.body)
        Spacer(Modifier.width(16.dp))
        Spacer(Modifier.weight(1f))
        BasicText(value, style = t.body.merge(TextStyle(fontWeight = FontWeight.SemiBold)))
    }
}

/** A glyph in [color]; with [below], its lower half in that colour, for a diverging area. */
@Composable
public fun GlyphSwatch(glyph: Glyph, color: Color, size: Dp, below: Color? = null) {
    Canvas(Modifier.size(size)) {
        val center = Offset(this@Canvas.size.width / 2f, this@Canvas.size.height / 2f)
        val radius = this@Canvas.size.minDimension / 2f
        with(glyph) { draw(center, radius, color, 1f) }
        if (below != null) clipRect(top = center.y) { with(glyph) { draw(center, radius, below, 1f) } }
    }
}

/**
 * Series names with their colour and glyph, in palette order. A diverging area shows both colours,
 * and candles their up colour over their down colour. A [TreemapChart], whose tiles are coloured by
 * value rather than by group, gets its colour scale instead: [DivergingKey].
 */
@Composable
public fun ChartLegend(spec: ChartSpec, style: ChartStyle, modifier: Modifier = Modifier, textStyle: TextStyle = style.axis.labels) {
    if (spec is TreemapChart) {
        DivergingKey(style.diverging, spec.valueFormat ?: Formats.Readable, modifier, textStyle)
        return
    }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        spec.legend().forEach { info ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info.candle) GlyphSwatch(style.glyph(info.color), style.candle.up, 9.dp, style.candle.down)
                else GlyphSwatch(style.glyph(info.color), style.color(info.color), 9.dp, info.colorBelow?.let(style::color))
                Spacer(Modifier.width(6.dp))
                BasicText(info.name, style = textStyle)
            }
        }
    }
}

/**
 * A diverging scale's key: a bar from [DivergingColors.negative] through neutral to
 * [DivergingColors.positive], between its two ends printed by [format]. Values beyond the ends
 * take the end colours.
 */
@Composable
public fun DivergingKey(colors: DivergingColors, format: ValueFormat, modifier: Modifier = Modifier, textStyle: TextStyle) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BasicText(format.format(-colors.domain), style = textStyle)
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.size(width = 96.dp, height = 8.dp)) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(colors.negative, colors.neutral, colors.positive)),
                cornerRadius = CornerRadius(size.height / 2f),
            )
        }
        Spacer(Modifier.width(6.dp))
        BasicText(format.format(colors.domain), style = textStyle)
    }
}
