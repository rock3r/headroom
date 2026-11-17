package dev.c5inco.charts.core

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Horizontal bars: categories down the left on a [BandScale], values along the bottom on a
 * [LinearScale]. The upright compiler's bar rules, turned sideways: bars sit on a vertical zero
 * line, a stack's zero bound stays on the (clamped) baseline while its other bounds follow the
 * scale, missing values are skipped and zero-length stack segments are not drawn.
 */
internal fun compileHorizontal(spec: HorizontalBarChart, size: Extent, measure: TextMeasure, m: ChartMetrics): ChartScene {
    val categories = spec.categories.labels
    val rows = categories.size
    val mark = spec.bars
    val seriesKeys = nameKeys(mark.series.map { it.name })
    val stack = if (mark.layout == BarLayout.Stacked) Stack.diverging(mark.series.map { it.values.take(rows) }) else null

    // 1. Value domain, from the rows that exist. A stack's zero floor is the baseline, which
    // includeZero governs; every other bound is data.
    var lo = Double.POSITIVE_INFINITY
    var hi = Double.NEGATIVE_INFINITY
    fun take(v: Double) {
        if (v.isFinite()) { lo = min(lo, v); hi = max(hi, v) }
    }
    if (stack != null) stack.forEach { s -> s.forEach { (a, b) -> if (a != 0.0) take(a); if (b != 0.0) take(b) } }
    else mark.series.forEach { s -> for (i in 0 until rows) take(s.at(i)) }
    spec.rules.forEach { take(it.y) }
    var x = valueScale(spec.value, lo, hi, hasLength = true)
    val valueVisible = spec.value.visible
    val ticks = if (valueVisible) x.ticks(spec.value.tickCount) else emptyList()
    val tickFormat = spec.value.format ?: Formats.forTicks(ticks)
    val tickTexts = ticks.map(tickFormat::format)
    // A custom format may repeat a label; repeats are numbered so every tick keeps its own key.
    val tickKeys = occurrenceKeys(tickTexts)
    val tickSizes = tickTexts.map { measure.measure(it, TextKind.TickX) }
    val valueFormat = spec.value.valueFormat ?: Formats.Readable

    // 2. Margins. Category labels on the left, cut short past their share of the width; value
    // ticks along the bottom, whose end labels may overhang the plot by half their width.
    val maxLabel = size.width * MAX_LABEL_FRACTION - m.labelGap
    val catTexts = if (spec.categories.visible && maxLabel > 0f) categories.map { fit(it, maxLabel, TextKind.TickY, measure) } else emptyList()
    val catSizes = catTexts.map { measure.measure(it, TextKind.TickY) }
    val widest = catTexts.indices.filter { catTexts[it].isNotEmpty() }.maxOfOrNull { catSizes[it].width } ?: 0f
    var left = if (widest > 0f) widest + m.labelGap else 0f
    var right = 0f
    if (tickSizes.isNotEmpty()) {
        left = max(left, tickSizes.first().width / 2f)
        right = tickSizes.last().width / 2f
    }
    val bottom = if (valueVisible) measure.measure("0", TextKind.TickX).height + m.labelGap else 0f
    val plot = Bounds(left, 0f, size.width - right, size.height - bottom)
    val plotColumns = Span(plot.left, plot.right)
    if (plot.width <= 0f || plot.height <= 0f) return emptyScene(size, spec, SceneLayout.Cartesian(AxisOrientation.Horizontal, baseline = 0f), FocusMode.NearestY)

    x = x.withRange(plot.left, plot.right)
    val band = BandScale(rows, plot.top, plot.bottom, m.bandPadding, m.bandOuterPadding)
    val zeroX = x(0.0).coerceIn(plot.left, plot.right)
    // A stack bound of zero is the baseline, which stays in the plot even when zero is not.
    fun xStack(v: Double): Float = if (v == 0.0) zeroX else x(v)

    val guides = mutableListOf<Node>()
    val marks = mutableListOf<Node>()
    val labels = mutableListOf<Node>()
    val points = mutableListOf<FocusPoint>()

    // 3. Guides and labels. There are no grid lines between rows: they would run through the bars.
    ticks.forEachIndexed { i, v ->
        val px = x(v)
        val w = tickSizes[i].width
        guides += RuleNode("grid-x:${tickKeys[i]}", XY(px, plot.top), XY(px, plot.bottom), RuleKind.GridX)
        val lx = px.coerceIn(w / 2f, max(w / 2f, size.width - w / 2f))
        labels += TextNode("tick-x:${tickKeys[i]}", tickTexts[i], XY(lx, plot.bottom + m.labelGap), HAlign.Center, VAlign.Top, TextKind.TickX)
    }
    if (catTexts.isNotEmpty()) {
        // Every stride-th label, so labels taller than their rows never overlap.
        val tallest = catSizes.maxOfOrNull { it.height } ?: 0f
        val stride = if (band.step <= 0f) 1 else max(1, ceil(tallest / band.step).toInt())
        categories.indices.forEach { i ->
            if (i % stride == 0 && catTexts[i].isNotEmpty()) {
                labels += TextNode("tick-y:$i", catTexts[i], XY(plot.left - m.labelGap, band.center(i)), HAlign.End, VAlign.Center, TextKind.TickY)
            }
        }
    }
    if (spec.categories.visible) guides += RuleNode("axis-y", XY(plot.left, plot.top), XY(plot.left, plot.bottom), RuleKind.AxisY)
    if (valueVisible) guides += RuleNode("axis-x", XY(plot.left, plot.bottom), XY(plot.right, plot.bottom), RuleKind.AxisX)
    // Zero between the domain's ends, in either order: a reversed value axis has its baseline too.
    if (x.spans(0.0)) {
        guides += RuleNode("baseline", XY(zeroX, plot.top), XY(zeroX, plot.bottom), RuleKind.Baseline)
    }

    // 4. Bars, row by row from the top, then reference rules over them. Each carries its series'
    // palette slot, so it paints the same in any scene.
    val legend = spec.legend()
    if (stack == null) {
        val k = mark.series.size
        val cap = groupCap(k, m)
        for (i in 0 until rows) {
            val (gStart, gHeight) = capped(band.start(i), band.bandwidth, cap)
            val inner = groupSlots(k, gStart, gHeight, m)
            mark.series.forEachIndexed { s, series ->
                val v = series.at(i)
                if (v.isNaN()) return@forEachIndexed
                val t = inner.start(s)
                val px = x(v)
                val rect = Bounds(min(zeroX, px), t, max(zeroX, px), t + inner.bandwidth)
                marks += RectNode("bar:${seriesKeys[s]}:$i", rect, RectKind.Bar(series = s, color = legend[s].color, datum = i, base = baseEdge(v, x, horizontal = true, from = zeroX, to = px), value = v, track = plotColumns))
                points += FocusPoint(
                    series = s, datum = i, x = px, y = rect.centerY, slot = band.center(i),
                    label = categories[i], value = v, valueText = valueFormat.format(v), detail = FocusDetail.Bar(RectHit(rect)),
                )
            }
        }
    } else {
        for (i in 0 until rows) {
            val (t, h) = capped(band.start(i), band.bandwidth, m.maxBarWidth)
            val outermost = stackEnds(mark.series, i)
            mark.series.forEachIndexed { s, series ->
                val v = series.at(i)
                if (v.isNaN() || v == 0.0) return@forEachIndexed
                val (v0, v1) = stack[s][i]
                val end = if (v >= 0) xStack(v1) else xStack(v0)
                val base = baseEdge(v, x, horizontal = true, from = if (v >= 0) xStack(v0) else xStack(v1), to = end)
                val r = stackGap(Bounds(min(xStack(v0), xStack(v1)), t, max(xStack(v0), xStack(v1)), t + h), base, v0, v1, m.stackGap)
                marks += RectNode("bar:${seriesKeys[s]}:$i", r, RectKind.Bar(series = s, color = legend[s].color, datum = i, base = base, value = v, track = plotColumns, isEnd = s in outermost))
                points += FocusPoint(
                    series = s, datum = i, x = end, y = r.centerY, slot = band.center(i),
                    label = categories[i], value = v, valueText = valueFormat.format(v), detail = FocusDetail.Bar(RectHit(r)),
                )
            }
        }
    }
    // Rules at the same value are numbered by occurrence; a line and its label share a key.
    val ruleKeys = occurrenceKeys(spec.rules.map { it.y.toString() })
    spec.rules.forEachIndexed { r, rule ->
        val px = x(rule.y)
        marks += RuleNode("ref:${ruleKeys[r]}", XY(px, plot.top), XY(px, plot.bottom), RuleKind.Reference)
        rule.label?.let {
            // Right of the line, or left of it when that would leave the chart.
            val fits = px + m.labelGap + measure.measure(it, TextKind.Reference).width <= size.width
            val at = XY(if (fits) px + m.labelGap else px - m.labelGap, plot.top)
            labels += TextNode("ref-label:${ruleKeys[r]}", it, at, if (fits) HAlign.Start else HAlign.End, VAlign.Top, TextKind.Reference)
        }
    }
    return ChartScene(
        size = size,
        plot = plot,
        layout = SceneLayout.Cartesian(AxisOrientation.Horizontal, baseline = zeroX),
        series = legend,
        guides = guides,
        marks = marks,
        labels = labels,
        points = points,
        focusMode = FocusMode.NearestY,
        description = spec.description,
    )
}
