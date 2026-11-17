package dev.c5inco.charts.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** One day, the fallback bar slot on a time axis with a single point. */
private const val DAY_MS = 86_400_000.0

/**
 * Compiles a [CartesianChart] in phases: the continuous x domain ([continuousXDomain]); margins,
 * scales and the visible window ([layoutCartesian], which gives the [CartesianFrame] every later
 * phase reads); guides and tick labels ([axes]); each mark in paint order ([marks]); then the scene
 * and its aligned-focus tables ([scene]). The nodes go into one [CartesianNodes].
 *
 * Under a viewport this runs every frame, so the mark phases do O(window) work over the arrays
 * [CartesianPrep] prepared, with one M4 buffer per mark and no per-point lambdas or boxing.
 */
internal fun compileCartesian(
    spec: CartesianChart,
    size: Extent,
    measure: TextMeasure,
    m: ChartMetrics,
    viewport: ViewportRange? = null,
): ChartScene {
    val prep = spec.prep
    val xBase = continuousXDomain(prep, viewport)
    val frame = layoutCartesian(spec, prep, size, measure, m, viewport, xBase)
        ?: return emptyScene(
            size, spec, SceneLayout.Cartesian(AxisOrientation.Vertical, baseline = size.height), FocusMode.NearestX,
            clipPlot = viewport != null,
        )
    val aligned = if (frame.positions > 0) frame.alignedFocus() else null
    val out = CartesianNodes(frame, aligned)
    frame.axes(out, measure)
    frame.marks(out, measure)
    return frame.scene(out, aligned)
}

/**
 * One compile's layout of a [CartesianChart]: its scales, its plot and the window of positions a
 * viewport leaves visible, with the position and formatting rules every mark shares. Read-only once
 * built, and never retained by its scene: the scene's [AlignedFocus] copies the few values it needs.
 */
private class CartesianFrame(
    val spec: CartesianChart,
    val prep: CartesianPrep,
    val size: Extent,
    val m: ChartMetrics,
    val viewport: ViewportRange?,
    val plot: Bounds,
    /** Height of the volume strip at the bottom of the plot; 0 without one. */
    val strip: Float,
    /** The bottom of the value axis: the plot's, or above the volume strip and its gap. */
    val priceBottom: Float,
    val y: LinearScale,
    /** The continuous x scale; null for a category axis. */
    val x: LinearScale?,
    /** The category scale: bands for bars and candles, else points; null for a continuous axis. */
    val band: BandScale?,
    slotPadding: Float,
    /** The value ticks' labels. */
    val yTexts: List<String>,
    /** Category label sizes, measured only when the x axis is shown. */
    val catSizes: List<Extent>,
) {
    val categories = prep.categories
    // Continuous x positions that series values align to; null for a category axis.
    val xValues: DoubleArray? = prep.xValues
    val positions = prep.positions
    val valueFormat = spec.y.valueFormat ?: Formats.Readable
    val cacheValueTexts = spec.y.valueFormat == null || viewport != null

    val zeroY = y(0.0).coerceIn(plot.top, priceBottom)
    /** The plot from top to bottom: every bar's track and every area's gradient span, shared, not built per node. */
    val plotRows = Span(plot.top, plot.bottom)

    val slotWidth: Float = band?.bandwidth ?: x?.let { sx ->
        val stepPx = if (prep.xStep > 0) sx(sx.domainStart + prep.xStep) - sx(sx.domainStart) else plot.width
        stepPx * (1f - slotPadding)
    } ?: 0f

    // The raw x a focus reports: data units on a continuous axis, nothing for categories.
    val boxedX = prep.boxedDomainX

    // Binary-search culling on ascending continuous x positions when a viewport is set: keep
    // points inside [viewLo, viewHi] for focus, plus one point beyond each edge so lines and areas
    // run to the plot edge.
    val viewLo = if (viewport != null) min(viewport.start, viewport.end) else Double.NEGATIVE_INFINITY
    val viewHi = if (viewport != null) max(viewport.start, viewport.end) else Double.POSITIVE_INFINITY
    val inStart: Int
    val inEnd: Int
    val cullStart: Int
    val cullEnd: Int

    init {
        val xValues = xValues
        if (viewport != null && xValues != null && positions > 0 && prep.xAscending) {
            inStart = lowerBound(xValues, viewLo)
            inEnd = upperBound(xValues, viewHi) - 1
            cullStart = (inStart - 1).coerceAtLeast(0)
            cullEnd = (inEnd + 1).coerceAtMost(positions - 1)
        } else {
            inStart = 0
            inEnd = positions - 1
            cullStart = 0
            cullEnd = positions - 1
        }
    }

    val plotCols = max(1, ceil(plot.width).toInt())
    val visibleSpanCount = max(0, inEnd - inStart + 1)
    val xDomLo = x?.let { min(it.domainStart, it.domainEnd) } ?: Double.NaN
    val xDomHi = x?.let { max(it.domainStart, it.domainEnd) } ?: Double.NaN

    // A stack bound of zero is the baseline, which stays in the plot even when zero is not.
    fun yStack(v: Double): Float = if (v == 0.0) zeroY else y(v)

    // A stacked area segment's outer edge, on the series' value (away from zero), and its inner
    // edge, on the layer beneath: the upper bound of a gain and the lower bound of a loss.
    fun yOuter(bounds: Pair<Double, Double>, raw: Double): Float = yStack(if (raw < 0.0) bounds.first else bounds.second)
    fun yInner(bounds: Pair<Double, Double>, raw: Double): Float = yStack(if (raw < 0.0) bounds.second else bounds.first)

    // Where position i sits, and the slot a bar there may fill.
    fun xAt(i: Int): Float = band?.center(i) ?: x!!(xValues!![i])
    fun slotStart(i: Int): Float = band?.start(i) ?: (xAt(i) - slotWidth / 2f)
    fun xLabel(i: Int): String = prep.xLabel(i)
    fun domainX(i: Int): Double? = boxedX?.get(i)

    fun formatVal(markIndex: Int, s: Int, i: Int, v: Double): String =
        prep.valueText(markIndex, s, i, v, valueFormat, cacheValueTexts)
    fun inWindow(i: Int): Boolean = viewport == null || (i in inStart..inEnd && (xValues == null || xValues[i] in viewLo..viewHi))
}

/** What the phases emit, layer by layer in paint order, and the bookkeeping marks share. */
private class CartesianNodes(f: CartesianFrame, aligned: AlignedFocus?) {
    val guides = ArrayList<Node>(f.prep.yTicks.size + 12)
    val marks = ArrayList<Node>(max(16, f.prep.totalSeries * 2))
    val labels = ArrayList<Node>(f.prep.yTicks.size + 12)
    val points = FocusPointsBuilder(max(16, min(f.visibleSpanCount, f.plotCols * 4) * f.prep.totalSeries), aligned, lazy = f.viewport != null)

    /** Area edges, held back until the last area's fill is down. */
    val areaEdges = mutableListOf<Node>()

    /** Reference labels placed so far, which later ones must not overlap. */
    val refLabelBoxes = mutableListOf<Bounds>()

    /** Series samples read, for [CartesianPrep.lastInspectedSamples]. */
    var inspectedSamples = 0
}

/** 2. Continuous x domain; null for categories. Bars get half a slot of room at each end so the outer bars fit. */
private fun continuousXDomain(prep: CartesianPrep, viewport: ViewportRange?): LinearScale? {
    val numericX = prep.numericX
    val timeX = prep.timeX
    val xStep = prep.xStep
    return if (prep.categories != null) null else if (viewport != null) {
        LinearScale(viewport.start, viewport.end)
    } else {
        var xLo = prep.xDataLo
        var xHi = prep.xDataHi
        if (!xLo.isFinite()) { xLo = 0.0; xHi = 1.0 }
        val unit = if (timeX != null) DAY_MS else 1.0
        // Bars and candles take slots: bands on a category axis, half a slot of room at the ends of a
        // continuous one.
        if (prep.hasSlots) {
            val half = (if (xStep > 0) xStep else unit) / 2
            xLo -= half
            xHi += half
        }
        xLo = numericX?.min ?: timeX?.min?.toEpochMilli()?.toDouble() ?: xLo
        xHi = numericX?.max ?: timeX?.max?.toEpochMilli()?.toDouble() ?: xHi
        if (xLo == xHi) { xLo -= unit / 2; xHi += unit / 2 }
        val raw = LinearScale(xLo, xHi)
        // Scatter plots read better against round extents; series should run edge to edge.
        if (numericX != null && prep.dotsOnly && numericX.min == null && numericX.max == null) raw.nice(numericX.tickCount) else raw
    }
}

/**
 * 3. Margins from measured labels, then the plot, the volume strip and the scales over them. Null
 * when nothing is left to plot in.
 */
private fun layoutCartesian(
    spec: CartesianChart,
    prep: CartesianPrep,
    size: Extent,
    measure: TextMeasure,
    m: ChartMetrics,
    viewport: ViewportRange?,
    xBase: LinearScale?,
): CartesianFrame? {
    val categories = prep.categories
    val xVisible = spec.x.visible
    val yVisible = spec.y.visible
    val hasBars = prep.hasBars
    // Slot padding: the bars' when there are bars, else the candles'.
    val slotPadding = if (hasBars) m.bandPadding else m.candlePadding
    val yTexts = prep.yTexts()

    // A hidden axis measures nothing and keeps only enough room for what overhangs the data: half
    // the widest stroke, or a marker or dot when there are any.
    var inset = m.strokeInset
    if (prep.hasMarkers) inset = max(inset, m.markerRadius)
    if (prep.hasDots) inset = max(inset, m.dotRadius)
    val ySizes = yTexts.map { measure.measure(it, TextKind.TickY) }
    val catSizes = if (categories != null && xVisible) categories.map { measure.measure(it, TextKind.TickX) } else emptyList()
    val tickHeight = if (xVisible) measure.measure("0", TextKind.TickX).height else 0f
    val usesBands = categories != null && prep.hasSlots
    var left = if (yVisible) max((ySizes.maxOfOrNull { it.width } ?: 0f) + m.labelGap, inset) else inset
    var right = when {
        !xVisible -> inset
        categories == null -> max(m.markerRadius, inset)
        else -> 0f
    }
    if (categories != null && !usesBands && xVisible) {
        // Points sit on the plot edges, so their labels overhang it.
        left = max(left, (catSizes.firstOrNull()?.width ?: 0f) / 2f)
        right = (catSizes.lastOrNull()?.width ?: 0f) / 2f
    }
    val halfLabel = (ySizes.firstOrNull()?.height ?: 0f) / 2f
    val top = if (yVisible) max(halfLabel, m.markerRadius) else inset
    val bottom = if (xVisible) tickHeight + m.labelGap else max(halfLabel, inset)
    val plot = Bounds(left, top, size.width - right, size.height - bottom)
    if (plot.width <= 0f || plot.height <= 0f) return null

    // A volume strip, when a candle mark asks for one, takes the bottom of the plot; the value
    // axis (prices, and every other mark) keeps the rest above a gap.
    val stripMark = prep.stripMark
    val strip = if (stripMark < 0) 0f else plot.height * (spec.marks[stripMark] as CandleMark).volumeFraction
    val priceBottom = if (stripMark < 0) plot.bottom else plot.bottom - strip - m.volumeGap
    if (priceBottom <= plot.top) return null

    val y = prep.yBaseScale.withRange(priceBottom, plot.top)
    val x = xBase?.withRange(plot.left, plot.right)
    val band = categories?.let {
        val outer = if (hasBars) m.bandOuterPadding else m.candlePadding / 2f
        if (usesBands) BandScale(it.size, plot.left, plot.right, slotPadding, outer)
        else BandScale.point(it.size, plot.left, plot.right)
    }
    return CartesianFrame(spec, prep, size, m, viewport, plot, strip, priceBottom, y, x, band, slotPadding, yTexts, catSizes)
}

/** 4. Guides and tick labels. */
private fun CartesianFrame.axes(out: CartesianNodes, measure: TextMeasure) {
    val xVisible = spec.x.visible
    val yKeys = prep.yKeys(yTexts)
    prep.yTicks.forEachIndexed { i, v ->
        val py = y(v)
        out.guides += RuleNode("grid-y:${yKeys[i]}", XY(plot.left, py), XY(plot.right, py), RuleKind.GridY)
        out.labels += TextNode("tick-y:${yKeys[i]}", yTexts[i], XY(plot.left - m.labelGap, py), HAlign.End, VAlign.Center, TextKind.TickY)
    }
    val categories = categories
    if (xVisible && band != null && categories != null) {
        categoryTicks(out, categories, band)
    } else if (xVisible && x != null) {
        if (viewport != null) viewportXTicks(out, measure, x) else continuousXTicks(out, measure, x)
    }
    if (xVisible) out.guides += RuleNode("axis-x", XY(plot.left, plot.bottom), XY(plot.right, plot.bottom), RuleKind.AxisX)
    if (spec.y.visible) out.guides += RuleNode("axis-y", XY(plot.left, plot.top), XY(plot.left, priceBottom), RuleKind.AxisY)
    // Zero between the domain's ends, in either order: a reversed value axis has its baseline too.
    if (prep.hasLength && y.spans(0.0)) {
        out.guides += RuleNode("baseline", XY(plot.left, zeroY), XY(plot.right, zeroY), RuleKind.Baseline)
    }
}

/** A grid line at every category, and a label at every stride-th, so labels never overlap. */
private fun CartesianFrame.categoryTicks(out: CartesianNodes, categories: List<String>, band: BandScale) {
    val widest = catSizes.maxOfOrNull { it.width } ?: 0f
    val stride = if (band.step <= 0f) 1 else max(1, ceil((widest + m.minLabelSpacing) / band.step).toInt())
    categories.indices.forEach { i ->
        out.guides += RuleNode("grid-x:$i", XY(band.center(i), plot.top), XY(band.center(i), plot.bottom), RuleKind.GridX)
        if (i % stride == 0) {
            out.labels += TextNode("tick-x:$i", categories[i], XY(band.center(i), plot.bottom + m.labelGap), HAlign.Center, VAlign.Top, TextKind.TickX)
        }
    }
}

/** Continuous x ticks without a viewport: fewer ticks until the widest label fits between neighbours. */
private fun CartesianFrame.continuousXTicks(out: CartesianNodes, measure: TextMeasure, x: LinearScale) {
    val numericX = prep.numericX
    val timeX = prep.timeX
    var count = numericX?.tickCount ?: timeX?.tickCount ?: 5
    var ticks: List<Double>
    var format: ValueFormat
    var sizes: List<Extent>
    var tickTexts: List<String>
    while (true) {
        ticks = if (timeX != null) TimeTicks.ticks(x.domainStart, x.domainEnd, count, timeX.zone) else x.ticks(count)
        format = numericX?.format ?: timeX?.format ?: if (timeX != null) timeX.defaultTickFormat else Formats.forTicks(ticks)
        tickTexts = ticks.map(format::format)
        sizes = tickTexts.map { measure.measure(it, TextKind.TickX) }
        val widest = sizes.maxOfOrNull { it.width } ?: 0f
        val gap = ticks.zipWithNext { a, b -> x(b) - x(a) }.minOrNull() ?: Float.POSITIVE_INFINITY
        if (count <= 2 || gap >= widest + m.minLabelSpacing) break
        count--
    }
    val tickKeys = occurrenceKeys(ticks.map { it.toString() })
    ticks.forEachIndexed { i, v ->
        val px = x(v)
        val w = sizes[i].width
        out.guides += RuleNode("grid-x:${tickKeys[i]}", XY(px, plot.top), XY(px, plot.bottom), RuleKind.GridX)
        // Labels near the edges slide inward rather than leaving the canvas.
        val lx = px.coerceIn(w / 2f, max(w / 2f, size.width - w / 2f))
        out.labels += TextNode("tick-x:${tickKeys[i]}", tickTexts[i], XY(lx, plot.bottom + m.labelGap), HAlign.Center, VAlign.Top, TextKind.TickX)
    }
}

/**
 * Continuous x ticks under a viewport: the count comes from the window's span and a reference
 * label width so the tick interval stays constant while panning across month lengths or year
 * boundaries; a custom format then measures its own labels.
 */
private fun CartesianFrame.viewportXTicks(out: CartesianNodes, measure: TextMeasure, x: LinearScale) {
    val numericX = prep.numericX
    val timeX = prep.timeX
    // Fewer ticks until the widest label fits between neighbours.
    var count = numericX?.tickCount ?: timeX?.tickCount ?: 5
    var ticks: List<Double>
    var format: ValueFormat
    val span = kotlin.math.abs(x.domainEnd - x.domainStart)
    val sampleWidth = measure.measure(if (timeX != null) "Sep 30" else "000,000", TextKind.TickX).width
    while (count > 2) {
        val stepDomain = if (timeX != null) {
            val iv = TimeTicks.interval(min(x.domainStart, x.domainEnd), max(x.domainStart, x.domainEnd), count)
            when {
                iv == null -> kotlin.math.abs(Ticks.tickStep(x.domainStart, x.domainEnd, count))
                iv.unit == TimeTicks.Unit.Year -> iv.millis.toDouble() * iv.step
                else -> iv.millis.toDouble()
            }
        } else {
            kotlin.math.abs(Ticks.tickStep(x.domainStart, x.domainEnd, count))
        }
        val stepPx = if (span > 0.0) (stepDomain / span).toFloat() * plot.width else Float.POSITIVE_INFINITY
        if (stepPx >= sampleWidth + m.minLabelSpacing) break
        count--
    }
    val customFormat = numericX?.format != null || timeX?.format != null
    if (!customFormat) {
        // The axis's own format: labels depend on the ticks alone, kept while a pan keeps them.
        ticks = if (timeX != null) TimeTicks.ticks(x.domainStart, x.domainEnd, count, timeX.zone) else x.ticks(count)
        val labels = prep.viewportXTickLabels(ticks) { t ->
            val axisFormat = if (timeX != null) timeX.defaultTickFormat else Formats.forTicks(t)
            t.map(axisFormat::format)
        }
        ticks.forEachIndexed { i, v ->
            val px = x(v)
            out.guides += RuleNode(labels.gridKeys[i], XY(px, plot.top), XY(px, plot.bottom), RuleKind.GridX)
            out.labels += TextNode(labels.labelKeys[i], labels.texts[i], XY(px, plot.bottom + m.labelGap), HAlign.Center, VAlign.Top, TextKind.TickX)
        }
        return
    }
    var tickTexts: List<String>
    while (true) {
        ticks = if (timeX != null) TimeTicks.ticks(x.domainStart, x.domainEnd, count, timeX.zone) else x.ticks(count)
        format = numericX?.format ?: timeX?.format ?: if (timeX != null) timeX.defaultTickFormat else Formats.forTicks(ticks)
        tickTexts = ticks.map(format::format)
        if (count <= 2) break
        val sizes = tickTexts.map { measure.measure(it, TextKind.TickX) }
        val widest = sizes.maxOfOrNull { it.width } ?: 0f
        val gap = ticks.zipWithNext { a, b -> x(b) - x(a) }.minOrNull() ?: Float.POSITIVE_INFINITY
        if (gap >= widest + m.minLabelSpacing) break
        count--
    }
    val tickKeys = occurrenceKeys(ticks.map { it.toString() })
    ticks.forEachIndexed { i, v ->
        val px = x(v)
        out.guides += RuleNode("grid-x:${tickKeys[i]}", XY(px, plot.top), XY(px, plot.bottom), RuleKind.GridX)
        out.labels += TextNode("tick-x:${tickKeys[i]}", tickTexts[i], XY(px, plot.bottom + m.labelGap), HAlign.Center, VAlign.Top, TextKind.TickX)
    }
}

/** 5. Marks. Bands go behind everything, then area fills so lines and bars are never buried. */
private fun CartesianFrame.marks(out: CartesianNodes, measure: TextMeasure) {
    // Reserve horizontal RuleMark label boxes before placing XBandMark and XRuleMark labels.
    spec.marks.forEach { mark ->
        if (mark is RuleMark && mark.label != null) {
            val s = measure.measure(mark.label, TextKind.Reference)
            val rx = plot.right - m.labelGap
            val ry = y(mark.y) - m.labelGap
            out.refLabelBoxes += Bounds(rx - s.width, ry - s.height, rx, ry)
        }
    }
    val lastArea = prep.lastArea
    for (markIndex in prep.orderedMarks) {
        when (val mark = spec.marks[markIndex]) {
            is BarMark -> {
                val stack = prep.stacks[markIndex]
                if (stack == null) groupedBars(out, markIndex, mark) else stackedBars(out, markIndex, mark, stack)
            }
            is LineMark -> lineMark(out, markIndex, mark)
            is AreaMark -> areaMark(out, markIndex, mark)
            is DotMark -> dotMark(out, markIndex, mark)
            is CandleMark -> candleMark(out, markIndex)
            is RuleMark -> ruleMark(out, prep.annotationKeys[markIndex]!!, mark)
            is XBandMark -> xBandMark(out, measure, prep.annotationKeys[markIndex]!!, mark)
            is XRuleMark -> xRuleMark(out, measure, prep.annotationKeys[markIndex]!!, mark)
        }
        // Edges go on once the last area is down, so no fill covers a stroke.
        if (markIndex == lastArea) out.marks += out.areaEdges
    }
}

/** The palette slot of scene series [series], which its nodes carry so they paint the same in any scene. */
private fun CartesianFrame.colorOf(series: Int): Int = prep.legend[series].color

private fun CartesianFrame.groupedBars(out: CartesianNodes, markIndex: Int, mark: BarMark) {
    val first = prep.firstSeries[markIndex]
    val seriesKeys = prep.seriesKeys[markIndex]
    val barEnd = min(cullEnd, positions - 1)
    val k = mark.series.size
    val cap = groupCap(k, m)
    for (i in cullStart..barEnd) {
        val (gStart, gWidth) = capped(slotStart(i), slotWidth, cap)
        if (viewport != null && (gStart + gWidth < plot.left || gStart > plot.right)) continue
        val inner = groupSlots(k, gStart, gWidth, m)
        mark.series.forEachIndexed { s, series ->
            val v = series.at(i)
            if (v.isNaN()) return@forEachIndexed
            val l = inner.start(s)
            val py = y(v)
            val rect = Bounds(l, min(zeroY, py), l + inner.bandwidth, max(zeroY, py))
            if (viewport != null && (rect.right < plot.left || rect.left > plot.right)) return@forEachIndexed
            out.marks += RectNode(
                "bar:${seriesKeys[s]}:$i", rect,
                RectKind.Bar(series = first + s, color = colorOf(first + s), datum = i, base = baseEdge(v, y, horizontal = false, from = zeroY, to = py), value = v, track = plotRows),
            )
            if (inWindow(i)) {
                out.points += FocusPoint(
                    series = first + s, datum = i, x = rect.centerX, y = py, slot = xAt(i),
                    label = xLabel(i), value = v, valueText = formatVal(markIndex, s, i, v),
                    detail = FocusDetail.Bar(RectHit(rect), domainX(i)),
                )
            }
        }
    }
}

private fun CartesianFrame.stackedBars(out: CartesianNodes, markIndex: Int, mark: BarMark, stack: List<List<Pair<Double, Double>>>) {
    val first = prep.firstSeries[markIndex]
    val seriesKeys = prep.seriesKeys[markIndex]
    val barEnd = min(cullEnd, positions - 1)
    for (i in cullStart..barEnd) {
        val (l, w) = capped(slotStart(i), slotWidth, m.maxBarWidth)
        if (viewport != null && (l + w < plot.left || l > plot.right)) continue
        val outermost = stackEnds(mark.series, i)
        mark.series.forEachIndexed { s, series ->
            val v = series.at(i)
            if (v.isNaN() || v == 0.0) return@forEachIndexed
            val (v0, v1) = stack[s][i]
            val end = if (v >= 0) yStack(v1) else yStack(v0)
            val base = baseEdge(v, y, horizontal = false, from = if (v >= 0) yStack(v0) else yStack(v1), to = end)
            val r = stackGap(Bounds(l, min(yStack(v0), yStack(v1)), l + w, max(yStack(v0), yStack(v1))), base, v0, v1, m.stackGap)
            out.marks += RectNode(
                "bar:${seriesKeys[s]}:$i", r,
                RectKind.Bar(series = first + s, color = colorOf(first + s), datum = i, base = base, value = v, track = plotRows, isEnd = s in outermost),
            )
            if (inWindow(i)) {
                out.points += FocusPoint(
                    series = first + s, datum = i, x = r.centerX, y = end, slot = xAt(i),
                    label = xLabel(i), value = v, valueText = formatVal(markIndex, s, i, v),
                    detail = FocusDetail.Bar(RectHit(r), domainX(i)),
                )
            }
        }
    }
}

private fun CartesianFrame.lineMark(out: CartesianNodes, markIndex: Int, mark: LineMark) {
    val first = prep.firstSeries[markIndex]
    val seriesKeys = prep.seriesKeys[markIndex]
    val sx = x
    val xValues = xValues
    val decimateMark = viewport != null && categories == null && xValues != null && sx != null && prep.xAscending && visibleSpanCount > plotCols
    val xDomainSpan = if (sx != null) sx.domainEnd - sx.domainStart else 0.0
    val colScale = if (xDomainSpan != 0.0) plot.width / xDomainSpan else 0.0
    val markArrays = prep.seriesArrays[markIndex]!!
    val prevArrays = prep.prevDefinedArrays[markIndex]!!
    val nextArrays = prep.nextDefinedArrays[markIndex]!!
    // Size for the visible window (+ overhang) so even an alternating finite/NaN series cannot overflow.
    val m4Buf = if (decimateMark) prep.takeIndexBuffer(visibleSpanCount + 4) else null
    mark.series.forEachIndexed { s, _ ->
        val sValues = markArrays[s]
        val n = min(sValues.size, positions)
        val slice = culledSeriesSlice(
            n, inStart, inEnd, sValues, prevArrays[s], nextArrays[s], mark.gaps, viewport != null, prep.xAscending,
        )
        out.inspectedSamples += slice.inspectedCount
        // A missing value keeps its x but has no y, which breaks the line there.
        if (decimateMark && slice.winCount > plotCols && m4Buf != null) {
            decimatedLine(out, markIndex, mark, first, s, seriesKeys[s], slice, sValues, sx, xValues, colScale, m4Buf)
        } else {
            undecimatedLine(out, markIndex, mark, first, s, seriesKeys[s], slice, sValues)
        }
    }
    m4Buf?.let(prep::returnIndexBuffer)
}

/** A line series over a dense window: M4-decimated per pixel column, with up to four focus points a column. */
private fun CartesianFrame.decimatedLine(
    out: CartesianNodes,
    markIndex: Int,
    mark: LineMark,
    first: Int,
    s: Int,
    seriesKey: String,
    slice: SeriesSlice,
    sValues: DoubleArray,
    sx: LinearScale,
    xValues: DoubleArray,
    colScale: Double,
    m4Buf: IntArray,
) {
    val keptCount = decimateM4Indices(
        slice, xValues, sx.domainStart, colScale, plotCols, sValues, mark.gaps, m4Buf,
    )
    val pts = ArrayList<XY>(keptCount)
    var lastFocusCol = -1
    var colFocusCount = 0
    val lastDefinedIdx = slice.lastDefinedInWin
    for (k in 0 until keptCount) {
        val i = m4Buf[k]
        val raw = sValues[i]
        val px = sx(xValues[i])
        val defined = raw.isFinite()
        val pt = XY(px, if (defined) y(raw) else Float.NaN)
        pts += pt
        if (defined && i in inStart..inEnd) {
            val col = ((xValues[i] - sx.domainStart) * colScale).toInt().coerceIn(0, plotCols - 1)
            if (col != lastFocusCol) {
                lastFocusCol = col
                colFocusCount = 0
            }
            if (colFocusCount < 4 || i == lastDefinedIdx) {
                colFocusCount++
                out.points.addAligned(first + s, i) {
                    FocusPoint(
                        series = first + s, datum = i, x = pt.x, y = pt.y, slot = pt.x,
                        label = prep.xLabel(i), value = raw, valueText = prep.valueText(markIndex, s, i, raw, valueFormat, cacheValueTexts),
                        detail = FocusDetail.Line(boxedX?.get(i)),
                    )
                }
            }
        }
    }
    out.marks += LineNode("line:$seriesKey", pts, first + s, colorOf(first + s), mark.curve, pattern = mark.pattern)
    when (mark.markers) {
        Markers.None -> Unit
        Markers.Last -> {
            val lastIdx = prep.lastDefined(markIndex, s)
            if (slice.contains(lastIdx)) {
                val pt = XY(sx(xValues[lastIdx]), y(sValues[lastIdx]))
                out.marks += DotNode("marker:$seriesKey", pt, m.markerRadius, first + s, colorOf(first + s), lastIdx, isMarker = true)
            }
        }
        Markers.All -> {
            for (k in 0 until keptCount) {
                val pt = pts[k]
                if (pt.isDefined) {
                    out.marks += DotNode("marker:$seriesKey:${m4Buf[k]}", pt, m.markerRadius, first + s, colorOf(first + s), m4Buf[k], isMarker = true)
                }
            }
        }
    }
}

/** A line series point for point: without a viewport, or over a window no denser than the plot's pixel columns. */
private fun CartesianFrame.undecimatedLine(
    out: CartesianNodes,
    markIndex: Int,
    mark: LineMark,
    first: Int,
    s: Int,
    seriesKey: String,
    slice: SeriesSlice,
    sValues: DoubleArray,
) {
    val pts = buildUndecimatedPoints(slice, sValues, mark.gaps) { _, v -> y(v) }
    out.marks += LineNode("line:$seriesKey", pts, first + s, colorOf(first + s), mark.curve, pattern = mark.pattern)
    when (mark.markers) {
        Markers.None -> Unit
        Markers.Last -> {
            val lastIdx = prep.lastDefined(markIndex, s)
            if (slice.contains(lastIdx)) {
                val pt = XY(xAt(lastIdx), y(sValues[lastIdx]))
                out.marks += DotNode("marker:$seriesKey", pt, m.markerRadius, first + s, colorOf(first + s), lastIdx, isMarker = true)
            }
        }
        Markers.All -> {
            fun emitMarker(i: Int) {
                val raw = sValues[i]
                if (raw.isFinite()) {
                    out.marks += DotNode("marker:$seriesKey:$i", XY(xAt(i), y(raw)), m.markerRadius, first + s, colorOf(first + s), i, isMarker = true)
                }
            }
            if (slice.leftIdx >= 0) emitMarker(slice.leftIdx)
            for (i in slice.winStart until slice.winEndExclusive) emitMarker(i)
            if (slice.rightIdx >= 0) emitMarker(slice.rightIdx)
        }
    }
    val xValues = xValues
    val checkX = viewport != null && !prep.xAscending && xValues != null
    for (i in slice.winStart until slice.winEndExclusive) {
        val raw = sValues[i]
        if (!raw.isFinite()) continue
        if (checkX && (xValues[i] < viewLo || xValues[i] > viewHi)) continue
        out.points.addAligned(first + s, i) {
            val px = xAt(i)
            val py = y(raw)
            FocusPoint(
                series = first + s, datum = i, x = px, y = py, slot = px,
                label = prep.xLabel(i), value = raw, valueText = prep.valueText(markIndex, s, i, raw, valueFormat, cacheValueTexts),
                detail = FocusDetail.Line(boxedX?.get(i)),
            )
        }
    }
}

private fun CartesianFrame.areaMark(out: CartesianNodes, markIndex: Int, mark: AreaMark) {
    val first = prep.firstSeries[markIndex]
    val seriesKeys = prep.seriesKeys[markIndex]
    val stack = prep.stacks[markIndex]
    val sx = x
    val xValues = xValues
    val decimateMark = viewport != null && categories == null && xValues != null && sx != null && prep.xAscending && visibleSpanCount > plotCols
    val xDomainSpan = if (sx != null) sx.domainEnd - sx.domainStart else 0.0
    val colScale = if (xDomainSpan != 0.0) plot.width / xDomainSpan else 0.0
    val markArrays = prep.seriesArrays[markIndex]!!
    val prevArrays = prep.prevDefinedArrays[markIndex]!!
    val nextArrays = prep.nextDefinedArrays[markIndex]!!
    val markUpperArrays = prep.stackUpperArrays[markIndex]
    val markLowerArrays = prep.stackLowerArrays[markIndex]
    // Size for the visible window (+ overhang) so even an alternating finite/NaN series cannot overflow.
    val m4Buf = if (decimateMark) prep.takeIndexBuffer(visibleSpanCount + 4) else null
    // Unclamped: with zero cropped out of the domain, every value is on one side of it.
    val zeroRow = y(0.0)
    val negativeBelow = y.domainEnd > y.domainStart
    // The row the fill closes on: zero (clamped), or the bottom of the price area.
    val floor = if (mark.base == AreaBase.PlotBottom) priceBottom else null
    val closeY = floor ?: zeroY
    // The plot edge each end of the value axis lies on: gains toward the top unless it is reversed.
    val gainsEdge = if (y.domainEnd < y.domainStart) plot.bottom else plot.top
    val lossesEdge = if (y.domainEnd < y.domainStart) plot.top else plot.bottom
    val falls = prep.areaFalls[markIndex]!!
    mark.series.forEachIndexed { s, _ ->
        val kind = AreaKind.Upright(zeroY, plotRows, if (floor == null && falls[s]) lossesEdge else if (floor == null) gainsEdge else plot.top)
        // Each series takes its own colour below zero.
        val split = if (mark.diverging) SignSplit(zeroRow, checkNotNull(prep.legend[first + s].colorBelow), negativeBelow) else null
        val sValues = markArrays[s]
        val n = min(sValues.size, positions)
        val slice = culledSeriesSlice(
            n, inStart, inEnd, sValues, prevArrays[s], nextArrays[s], mark.gaps, viewport != null, prep.xAscending,
        )
        out.inspectedSamples += slice.inspectedCount
        val sStack = stack?.get(s)
        if (decimateMark && slice.winCount > plotCols && m4Buf != null) {
            val upperVals = markUpperArrays?.get(s) ?: sValues
            val lowerVals = markLowerArrays?.get(s)
            val keptCount = decimateM4Indices(
                slice, xValues, sx.domainStart, colScale, plotCols, upperVals, mark.gaps, m4Buf, lowerVals,
            )
            decimatedArea(out, markIndex, mark, first, s, seriesKeys[s], slice, sValues, sStack, sx, xValues, colScale, m4Buf, keptCount, split, floor, closeY, kind)
        } else {
            undecimatedArea(out, markIndex, mark, first, s, seriesKeys[s], slice, sValues, sStack, split, floor, closeY, kind)
        }
    }
    m4Buf?.let(prep::returnIndexBuffer)
}

/** An area series over a dense window, at the indices M4 kept, with up to six focus points a column. */
private fun CartesianFrame.decimatedArea(
    out: CartesianNodes,
    markIndex: Int,
    mark: AreaMark,
    first: Int,
    s: Int,
    seriesKey: String,
    slice: SeriesSlice,
    sValues: DoubleArray,
    sStack: List<Pair<Double, Double>>?,
    sx: LinearScale,
    xValues: DoubleArray,
    colScale: Double,
    m4Buf: IntArray,
    keptCount: Int,
    split: SignSplit?,
    floor: Float?,
    closeY: Float,
    kind: AreaKind.Upright,
) {
    val top = ArrayList<XY>(keptCount)
    val bot = ArrayList<XY>(keptCount)
    var lastFocusCol = -1
    var colFocusCount = 0
    val lastDefinedIdx = slice.lastDefinedInWin
    for (k in 0 until keptCount) {
        val i = m4Buf[k]
        val raw = sValues[i]
        val px = sx(xValues[i])
        if (!raw.isFinite()) {
            top += XY(px, Float.NaN)
            bot += XY(px, Float.NaN)
        } else {
            val bounds = sStack?.get(i)
            val py = if (bounds != null) yOuter(bounds, raw) else y(raw)
            top += XY(px, py)
            bot += XY(px, if (bounds != null) yInner(bounds, raw) else closeY)
            if (i in inStart..inEnd) {
                val col = ((xValues[i] - sx.domainStart) * colScale).toInt().coerceIn(0, plotCols - 1)
                if (col != lastFocusCol) {
                    lastFocusCol = col
                    colFocusCount = 0
                }
                if (colFocusCount < 6 || i == lastDefinedIdx) {
                    colFocusCount++
                    out.points.addAligned(first + s, i) {
                        FocusPoint(
                            series = first + s, datum = i, x = px, y = py, slot = px,
                            label = prep.xLabel(i), value = raw, valueText = prep.valueText(markIndex, s, i, raw, valueFormat, cacheValueTexts),
                            detail = FocusDetail.Area(boxedX?.get(i)),
                        )
                    }
                }
            }
        }
    }
    out.marks += AreaNode("area:$seriesKey", top, bot, first + s, colorOf(first + s), kind, mark.curve, signSplit = split, floor = floor)
    out.areaEdges += LineNode("area-edge:$seriesKey", top, first + s, colorOf(first + s), mark.curve, isAreaEdge = true, signSplit = split, floor = floor)
}

/** An area series point for point: without a viewport, or over a window no denser than the plot's pixel columns. */
private fun CartesianFrame.undecimatedArea(
    out: CartesianNodes,
    markIndex: Int,
    mark: AreaMark,
    first: Int,
    s: Int,
    seriesKey: String,
    slice: SeriesSlice,
    sValues: DoubleArray,
    sStack: List<Pair<Double, Double>>?,
    split: SignSplit?,
    floor: Float?,
    closeY: Float,
    kind: AreaKind.Upright,
) {
    val top = buildUndecimatedPoints(slice, sValues, mark.gaps) { i, v ->
        sStack?.get(i)?.let { yOuter(it, v) } ?: y(v)
    }
    val bot = if (mark.gaps == Gaps.Break) {
        buildUndecimatedAreaBottom(slice, sValues, sStack, closeY)
    } else {
        top.map { p -> XY(p.x, if (p.y.isFinite()) closeY else Float.NaN) }
    }
    out.marks += AreaNode("area:$seriesKey", top, bot, first + s, colorOf(first + s), kind, mark.curve, signSplit = split, floor = floor)
    out.areaEdges += LineNode("area-edge:$seriesKey", top, first + s, colorOf(first + s), mark.curve, isAreaEdge = true, signSplit = split, floor = floor)
    for (i in slice.winStart until slice.winEndExclusive) {
        val raw = sValues[i]
        if (!raw.isFinite() || !inWindow(i)) continue
        out.points.addAligned(first + s, i) {
            val px = xAt(i)
            val py = sStack?.get(i)?.let { yOuter(it, raw) } ?: y(raw)
            FocusPoint(
                series = first + s, datum = i, x = px, y = py, slot = px,
                label = xLabel(i), value = raw, valueText = formatVal(markIndex, s, i, raw),
                detail = FocusDetail.Area(domainX(i)),
            )
        }
    }
}

/** The points of a line or an area's top over [slice], every index kept, following [gaps]. */
private fun CartesianFrame.buildUndecimatedPoints(
    slice: SeriesSlice,
    sValues: DoubleArray,
    gaps: Gaps,
    yFor: (Int, Double) -> Float,
): List<XY> {
    val cap = slice.inspectedCount
    val out = ArrayList<XY>(cap)
    if (gaps == Gaps.Break) {
        if (slice.leftIdx >= 0) {
            val i = slice.leftIdx
            val raw = sValues[i]
            out += XY(xAt(i), if (raw.isFinite()) yFor(i, raw) else Float.NaN)
        }
        for (i in slice.winStart until slice.winEndExclusive) {
            val raw = sValues[i]
            out += XY(xAt(i), if (raw.isFinite()) yFor(i, raw) else Float.NaN)
        }
        if (slice.rightIdx >= 0) {
            val i = slice.rightIdx
            val raw = sValues[i]
            out += XY(xAt(i), if (raw.isFinite()) yFor(i, raw) else Float.NaN)
        }
        return out
    }
    var lastDefined = -1
    fun visit(i: Int) {
        val raw = sValues[i]
        if (!raw.isFinite()) return
        if (lastDefined >= 0 && i > lastDefined + 1 && gaps is Gaps.ConnectWithin) {
            val xPrev = xValues?.get(lastDefined) ?: lastDefined.toDouble()
            val xCurr = xValues?.get(i) ?: i.toDouble()
            if (abs(xCurr - xPrev) > gaps.maxGap) {
                if (viewport == null) {
                    for (k in (lastDefined + 1) until i) out += XY(xAt(k), Float.NaN)
                } else {
                    out += XY(xAt(lastDefined + 1), Float.NaN)
                }
            }
        }
        out += XY(xAt(i), yFor(i, raw))
        lastDefined = i
    }
    if (slice.leftIdx >= 0) visit(slice.leftIdx)
    for (i in slice.winStart until slice.winEndExclusive) visit(i)
    if (slice.rightIdx >= 0) visit(slice.rightIdx)
    return out
}

/** The bottom of an area over [slice] under [Gaps.Break]: each stacked segment's inner edge (see [CartesianFrame.yInner]), or [closeY]. */
private fun CartesianFrame.buildUndecimatedAreaBottom(
    slice: SeriesSlice,
    sValues: DoubleArray,
    sStack: List<Pair<Double, Double>>?,
    closeY: Float,
): List<XY> {
    val out = ArrayList<XY>(slice.inspectedCount)
    fun visit(i: Int) {
        val raw = sValues[i]
        out += XY(xAt(i), if (!raw.isFinite()) Float.NaN else sStack?.get(i)?.let { yInner(it, raw) } ?: closeY)
    }
    if (slice.leftIdx >= 0) visit(slice.leftIdx)
    for (i in slice.winStart until slice.winEndExclusive) visit(i)
    if (slice.rightIdx >= 0) visit(slice.rightIdx)
    return out
}

private fun CartesianFrame.dotMark(out: CartesianNodes, markIndex: Int, mark: DotMark) {
    val first = prep.firstSeries[markIndex]
    val seriesKeys = prep.seriesKeys[markIndex]
    // CartesianChart rejects dots on a category axis, so there is always a continuous x scale.
    val sx = checkNotNull(x) { "DotMark needs a continuous x axis" }
    mark.series.forEachIndexed { s, series ->
        series.points.forEachIndexed { j, d ->
            if (!d.x.isFinite() || !d.y.isFinite()) return@forEachIndexed
            val c = XY(sx(d.x), y(d.y))
            if (viewport != null && (c.x + m.dotRadius < plot.left || c.x - m.dotRadius > plot.right)) return@forEachIndexed
            out.marks += DotNode("dot:${seriesKeys[s]}:$j", c, m.dotRadius, first + s, colorOf(first + s), j)
            if (viewport == null || d.x in viewLo..viewHi) {
                out.points += FocusPoint(
                    series = first + s, datum = j, x = c.x, y = c.y, slot = c.x,
                    label = prep.pointFormat.format(d.x), value = d.y, valueText = valueFormat.format(d.y),
                    detail = FocusDetail.Dot(d.x),
                )
            }
        }
    }
}

private fun CartesianFrame.candleMark(out: CartesianNodes, markIndex: Int) {
    val first = prep.firstSeries[markIndex]
    val seriesKeys = prep.seriesKeys[markIndex]
    val periods = prep.candles.getValue(markIndex)
    // The strip's own scale: zero on its floor, the largest volume at its top.
    val volumeY = if (markIndex != prep.stripMark) null else {
        val most = prep.maxVolume[markIndex] ?: 1.0
        LinearScale(0.0, most, plot.bottom, plot.bottom - strip)
    }
    val candleEnd = min(cullEnd, periods.size - 1)
    for (i in cullStart..candleEnd) {
        val c = periods[i] ?: continue // missing: the slot stays empty
        val (l, w) = capped(slotStart(i), slotWidth, m.maxBarWidth)
        if (viewport != null && (l + w < plot.left || l > plot.right)) continue
        val cx = xAt(i) // exactly where a line's point at i sits
        val down = c.isDown
        val yOpen = y(c.open)
        val yClose = y(c.close)
        val yHigh = y(c.high)
        val yLow = y(c.low)
        if (volumeY != null && c.volume != null) {
            val v = Bounds(l, volumeY(c.volume), l + w, plot.bottom)
            out.marks += RectNode("volume:${seriesKeys[0]}:$i", v, RectKind.Volume(series = first, datum = i, volume = c.volume, down = down))
        }
        out.marks += RuleNode("wick:${seriesKeys[0]}:$i", XY(cx, yHigh), XY(cx, yLow), RuleKind.Wick(datum = i, down = down))
        // The open's edge: the bottom of an up candle, unless the value axis is reversed.
        val base = if (yOpen >= yClose) Edge.Bottom else Edge.Top
        val body = Bounds(l, min(yOpen, yClose), l + w, max(yOpen, yClose))
        out.marks += RectNode("candle:${seriesKeys[0]}:$i", body, RectKind.Candle(series = first, datum = i, base = base, close = c.close, down = down))
        if (inWindow(i)) {
            val top = min(yHigh, yLow)
            val detail = FocusDetail.Candle(
                hit = RectHit(Bounds(l, top, l + w, max(yHigh, yLow))),
                domainX = domainX(i),
                ohlc = c,
                open = valueFormat.format(c.open),
                high = valueFormat.format(c.high),
                low = valueFormat.format(c.low),
                close = valueFormat.format(c.close),
                volume = c.volume?.let(Formats.Readable::format),
            )
            out.points += FocusPoint(
                series = first, datum = i, x = cx, y = top, slot = xAt(i),
                label = xLabel(i), value = c.close, valueText = detail.close, detail = detail,
            )
        }
    }
}

/** [key], from [CartesianPrep.annotationKeys], names the rule and its label alike. */
private fun CartesianFrame.ruleMark(out: CartesianNodes, key: String, mark: RuleMark) {
    val py = y(mark.y)
    out.marks += RuleNode("ref:$key", XY(plot.left, py), XY(plot.right, py), RuleKind.Reference)
    mark.label?.let {
        out.labels += TextNode("ref-label:$key", it, XY(plot.right - m.labelGap, py - m.labelGap), HAlign.End, VAlign.Bottom, TextKind.Reference)
    }
}

/** Whether a reference label at [box] stays on the canvas and clear of the reference labels placed so far. */
private fun CartesianFrame.referenceFits(out: CartesianNodes, box: Bounds): Boolean {
    if (box.left < 0f || box.right > size.width) return false
    return out.refLabelBoxes.none { existing ->
        box.top < existing.bottom && box.bottom > existing.top &&
            box.left < existing.right + m.minLabelSpacing && box.right + m.minLabelSpacing > existing.left
    }
}

private fun CartesianFrame.xBandMark(out: CartesianNodes, measure: TextMeasure, key: String, mark: XBandMark) {
    val sx = x ?: return
    if (mark.to < xDomLo || mark.from > xDomHi) return
    val cFrom = mark.from.coerceIn(xDomLo, xDomHi)
    val cTo = mark.to.coerceIn(xDomLo, xDomHi)
    val bLeft = min(sx(cFrom), sx(cTo))
    val bRight = max(sx(cFrom), sx(cTo))
    if (bRight <= bLeft) return
    val rect = Bounds(bLeft, plot.top, bRight, plot.bottom)
    out.marks += RectNode("x-band:$key", rect, RectKind.Band(color = mark.color))
    mark.label?.takeIf { it.isNotEmpty() }?.let { text ->
        val s = measure.measure(text, TextKind.Reference)
        val cx = rect.centerX.coerceIn(s.width / 2f, max(s.width / 2f, size.width - s.width / 2f))
        val ly = plot.top + m.labelGap / 2f
        val box = Bounds(cx - s.width / 2f, ly, cx + s.width / 2f, ly + s.height)
        if (referenceFits(out, box)) {
            out.refLabelBoxes += box
            out.labels += TextNode("x-band-label:$key", text, XY(cx, ly), HAlign.Center, VAlign.Top, TextKind.Reference)
        }
    }
}

private fun CartesianFrame.xRuleMark(out: CartesianNodes, measure: TextMeasure, key: String, mark: XRuleMark) {
    val sx = x ?: return
    if (mark.x < xDomLo || mark.x > xDomHi) return
    val px = sx(mark.x)
    out.marks += RuleNode("x-rule:$key", XY(px, plot.top), XY(px, plot.bottom), RuleKind.XRule(color = mark.color, pattern = mark.pattern))
    mark.label?.takeIf { it.isNotEmpty() }?.let { text ->
        val s = measure.measure(text, TextKind.Reference)
        val ly = plot.top + m.labelGap / 2f
        val rightBox = Bounds(px + m.labelGap, ly, px + m.labelGap + s.width, ly + s.height)
        val leftBox = Bounds(px - m.labelGap - s.width, ly, px - m.labelGap, ly + s.height)
        when {
            referenceFits(out, rightBox) -> {
                out.refLabelBoxes += rightBox
                out.labels += TextNode("x-rule-label:$key", text, XY(px + m.labelGap, ly), HAlign.Start, VAlign.Top, TextKind.Reference)
            }
            referenceFits(out, leftBox) -> {
                out.refLabelBoxes += leftBox
                out.labels += TextNode("x-rule-label:$key", text, XY(px - m.labelGap, ly), HAlign.End, VAlign.Top, TextKind.Reference)
            }
        }
    }
}

/** 6. The scene, with the resolver for the aligned points that M4 decimation left out. */
private fun CartesianFrame.scene(out: CartesianNodes, aligned: AlignedFocus?): ChartScene {
    prep.lastInspectedSamples = out.inspectedSamples
    return ChartScene(
        size = size,
        plot = plot,
        layout = SceneLayout.Cartesian(AxisOrientation.Vertical, baseline = zeroY),
        series = prep.legend,
        guides = out.guides,
        marks = out.marks,
        labels = out.labels,
        points = out.points.build(),
        focusMode = prep.focusMode,
        description = spec.description,
        clipPlot = viewport != null,
        alignedFocus = aligned,
    )
}

/** Only what resolution needs: the prepared tables, the scales and the window. Not this frame. */
private fun CartesianFrame.alignedFocus() = AlignedFocus(
    prep = prep,
    y = y,
    zeroY = zeroY,
    x = x,
    band = band,
    valueFormat = valueFormat,
    cacheValueTexts = cacheValueTexts,
    windowed = viewport != null,
    inStart = inStart,
    inEnd = inEnd,
    viewLo = viewLo,
    viewHi = viewHi,
)
