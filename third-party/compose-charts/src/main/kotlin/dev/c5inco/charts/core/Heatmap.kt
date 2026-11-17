package dev.c5inco.charts.core

import kotlin.math.max
import kotlin.math.min

/** Half the diagonal of a unit square, rounded up so the middle of a gap crossing is inside it. */
private const val HALF_DIAGONAL = 0.7072f

/**
 * A grid of square cells, one per column and row, coloured by intensity, with column labels above
 * and row labels on the left.
 *
 * - **Geometry.** Cells are square: the step (cell plus gap) is the largest that fits both the
 *   columns across and the rows down, and the grid sits at the top left of the room the labels
 *   leave. The gap is [ChartMetrics.cellGap], shrunk to a quarter of the step when cells are tiny,
 *   so a cell never has zero or negative size. No columns, no rows, or no room after the labels
 *   give an empty scene in [FocusMode.Containment].
 * - **Semantics.** See [HeatmapChart]: a non-finite value is missing (an empty cell, no focus
 *   point); zero and negative values are real (focusable, printed raw) and negatives take
 *   intensity 0; intensity is the value over the maximum, clamped to 0..1, and 0 when there is
 *   no positive finite maximum.
 * - **Labels.** Blank labels are skipped. Column labels start at their column's left edge, pulled
 *   inside the chart at the right edge, and one that would come within
 *   [ChartMetrics.minLabelSpacing] of the label before it is left out. A row label whose ink
 *   ([TextMeasure.inkHeight]) would overlap the one above is left out; row labels are cut with an
 *   ellipsis past 40% of the width.
 * - **Focus.** A [RectHit] of the painted cell; the point is anchored at the cell's top centre, so
 *   a tooltip placed above it leaves the cell visible. A pointer in a gap focuses the nearest cell
 *   within [ChartScene.focusSlop], half the gap's diagonal.
 */
internal fun compileHeatmap(spec: HeatmapChart, size: Extent, measure: TextMeasure, m: ChartMetrics): ChartScene {
    val cols = spec.columns.size
    val rows = spec.rows.size
    if (cols == 0 || rows == 0 || !(size.width > 0f) || !(size.height > 0f)) return emptyScene(size, spec, SceneLayout.Free, FocusMode.Containment)

    // Margins: row labels on the left (cut short past their share of the width), column labels on top.
    val maxRowLabel = size.width * MAX_LABEL_FRACTION - m.labelGap
    val rowTexts = spec.rows.map { if (it.isEmpty() || maxRowLabel <= 0f) "" else fit(it, maxRowLabel, TextKind.TickY, measure) }
    val rowWidth = rowTexts.filter { it.isNotEmpty() }.maxOfOrNull { measure.measure(it, TextKind.TickY).width } ?: 0f
    val colHeight = spec.columns.filter { it.isNotEmpty() }.maxOfOrNull { measure.measure(it, TextKind.TickX).height } ?: 0f
    val left = if (rowWidth > 0f) rowWidth + m.labelGap else 0f
    val top = if (colHeight > 0f) colHeight + m.labelGap else 0f
    val roomW = size.width - left
    val roomH = size.height - top
    if (roomW <= 0f || roomH <= 0f) return emptyScene(size, spec, SceneLayout.Free, FocusMode.Containment)

    // Square cells: n cells and n - 1 gaps fill the tighter direction exactly.
    var gap = max(0f, m.cellGap)
    var step = min((roomW + gap) / cols, (roomH + gap) / rows)
    if (step < gap * 4f) {
        // Too small for the full gap: a quarter of the step, so cells keep three quarters.
        step = min(roomW / (cols - 0.25f), roomH / (rows - 0.25f))
        gap = step * 0.25f
    }
    val side = step - gap
    if (!(side > 0f)) return emptyScene(size, spec, SceneLayout.Free, FocusMode.Containment)
    val plot = Bounds(left, top, left + cols * step - gap, top + rows * step - gap)

    val peak = spec.max ?: spec.values.maxOfOrNull { c -> c.filter { it.isFinite() }.maxOrNull() ?: Double.NEGATIVE_INFINITY }
    val scale = peak?.takeIf { it.isFinite() && it > 0.0 }
    val format = spec.valueFormat ?: Formats.Readable

    val marks = ArrayList<Node>(cols * rows)
    val labels = mutableListOf<Node>()
    val points = mutableListOf<FocusPoint>()
    for (c in 0 until cols) {
        val column = spec.values[c]
        val x = left + c * step
        for (r in 0 until rows) {
            val v = column.getOrNull(r)?.takeIf { it.isFinite() } ?: Double.NaN
            val y = top + r * step
            val cell = Bounds(x, y, x + side, y + side)
            val intensity = when {
                v.isNaN() -> Float.NaN
                scale == null -> 0f
                else -> (v / scale).coerceIn(0.0, 1.0).toFloat()
            }
            marks += RectNode("cell:$c:$r", cell, RectKind.Cell(column = c, row = r, value = v, intensity = intensity))
            if (v.isNaN()) continue // missing: drawn empty, never focused
            val label = spec.cellLabels?.get(c)?.getOrNull(r)?.takeIf { it.isNotEmpty() }
                ?: listOf(spec.columns[c], spec.rows[r]).filter { it.isNotEmpty() }.joinToString(" ")
            points += FocusPoint(
                series = c, datum = r, x = cell.centerX, y = cell.top, slot = cell.centerX,
                label = label, value = v, valueText = format.format(v), detail = FocusDetail.Cell(RectHit(cell), intensity),
            )
        }
    }

    // Column labels: left to right, each clear of the one before it.
    var lastEnd = Float.NEGATIVE_INFINITY
    spec.columns.forEachIndexed { c, text ->
        if (text.isEmpty()) return@forEachIndexed
        val w = measure.measure(text, TextKind.TickX).width
        val x = (left + c * step).coerceAtMost(size.width - w)
        if (x < 0f || x < lastEnd + m.minLabelSpacing) return@forEachIndexed
        labels += TextNode("col:$c", text, XY(x, top - m.labelGap), HAlign.Start, VAlign.Bottom, TextKind.TickX)
        lastEnd = x + w
    }
    // Row labels: top to bottom, each clear of the one above it by ink height (not the full line
    // box, whose ascent and descent padding is taller than a compact row).
    var lastBottom = Float.NEGATIVE_INFINITY
    rowTexts.forEachIndexed { r, text ->
        if (text.isEmpty()) return@forEachIndexed
        val h = measure.inkHeight(text, TextKind.TickY)
        val cy = top + r * step + side / 2f
        if (cy - h / 2f < lastBottom) return@forEachIndexed
        labels += TextNode("row:$r", text, XY(left - m.labelGap, cy), HAlign.End, VAlign.Center, TextKind.TickY)
        lastBottom = cy + h / 2f
    }
    // A pointer in a gap focuses the nearest cell (see ChartScene.focusSlop). Half the gap's
    // diagonal, so the middle of a crossing, where every cell is diagonal, still reaches one; the
    // edge of a missing cell, a whole gap from its neighbours, does not.
    return ChartScene(
        size = size,
        plot = plot,
        layout = SceneLayout.Free,
        series = spec.legend(),
        guides = emptyList(),
        marks = marks,
        labels = labels,
        points = points,
        focusMode = FocusMode.Containment,
        description = spec.description,
        focusSlop = gap * HALF_DIAGONAL,
    )
}
