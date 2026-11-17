package dev.c5inco.charts.core

import kotlin.math.max

/**
 * [this] shrunk by [d] on each side that is inside [outer], so neighbours end up 2·[d] apart while
 * rectangles on [outer]'s edges keep them. Never inverts: a rectangle narrower than its gaps
 * collapses to zero width or height.
 */
private fun Bounds.insetWithin(d: Float, outer: Bounds): Bounds {
    if (d <= 0f) return this
    val l = if (left > outer.left + 0.5f) left + d else left
    val t = if (top > outer.top + 0.5f) top + d else top
    val r = if (right < outer.right - 0.5f) right - d else right
    val b = if (bottom < outer.bottom - 0.5f) bottom - d else bottom
    return Bounds(l, t, max(l, r), max(t, b))
}

/**
 * A two-level squarified treemap over the whole canvas. Groups are tiled first, then each group's
 * leaves inside it under an optional header strip; both largest first, ties broken by id.
 *
 * - Only leaves with a positive, finite size take part; groups without one are left out, and with
 *   none at all the scene is empty.
 * - Gaps come out of the tiles after squarifying ([Bounds.insetWithin]), so areas are proportional
 *   to size within a group before gaps and headers, and close to it across groups.
 * - A header is reserved only when its label fits (cut with an ellipsis if needed) and the group
 *   keeps at least as much height for its tiles as the header takes.
 * - Tile text: the label (cut with an ellipsis when it keeps at least [MIN_CUT_CHARS] characters,
 *   otherwise left out), and under it the sublabel or value when that fits whole; numbers are
 *   never cut.
 * - A tile whose gaps leave it no area keeps its node (so motion can pair it) but gets no focus
 *   point: there is nothing painted to touch.
 */
internal fun compileTreemap(spec: TreemapChart, size: Extent, measure: TextMeasure, m: ChartMetrics): ChartScene {
    val plot = Bounds(0f, 0f, size.width, size.height)
    fun takesArea(leaf: TreemapLeaf) = leaf.size.isFinite() && leaf.size > 0.0
    val sizes = spec.groups.map { g -> g.leaves.filter(::takesArea).sumOf { it.size } }
    val total = sizes.sum()
    if (plot.width <= 0f || plot.height <= 0f || !(total > 0.0) || !total.isFinite()) {
        return emptyScene(size, spec, SceneLayout.Free, FocusMode.Containment)
    }
    val valueFormat = spec.valueFormat ?: Formats.Readable
    val sizeFormat = spec.sizeFormat ?: Formats.Readable
    val pad = m.tilePadding

    val groupOrder = spec.groups.indices.filter { sizes[it] > 0.0 }
        .sortedWith(compareByDescending<Int> { sizes[it] }.thenBy { spec.groups[it].id })
    val groupRects = Treemap.squarify(groupOrder.map { sizes[it] }, plot)

    val marks = mutableListOf<Node>()
    val labels = mutableListOf<Node>()
    val points = mutableListOf<FocusPoint>()
    groupOrder.forEachIndexed { k, g ->
        val group = spec.groups[g]
        var area = groupRects[k].insetWithin(m.groupGap / 2f, plot)
        if (spec.groupLabels) {
            val text = fitLabel(group.label, area.width - pad * 2, TextKind.GroupLabel, measure)
            val header = if (text.isEmpty()) 0f else measure.measure(text, TextKind.GroupLabel).height + pad
            if (header > 0f && header * 2 <= area.height) {
                labels += TextNode("group-label:${group.id}", text, XY(area.left + pad, area.top + pad / 2f), HAlign.Start, VAlign.Top, TextKind.GroupLabel)
                area = area.copy(top = area.top + header)
            }
        }
        val leafOrder = group.leaves.indices.filter { takesArea(group.leaves[it]) }
            .sortedWith(compareByDescending<Int> { group.leaves[it].size }.thenBy { group.leaves[it].id })
        val rects = Treemap.squarify(leafOrder.map { group.leaves[it].size }, area)
        leafOrder.forEachIndexed { r, j ->
            val leaf = group.leaves[j]
            val tile = rects[r].insetWithin(m.tileGap / 2f, area)
            marks += RectNode("tile:${leaf.id}", tile, RectKind.Tile(group = g, leaf = j, value = leaf.value))
            val valueText = if (leaf.value.isNaN()) "" else valueFormat.format(leaf.value)
            if (tile.width > 0f && tile.height > 0f) {
                points += FocusPoint(
                    series = g, datum = j, x = tile.centerX, y = tile.centerY, slot = tile.centerX,
                    label = leaf.label, value = leaf.value, valueText = valueText,
                    detail = FocusDetail.Tile(
                        hit = RectHit(tile), id = leaf.id, size = leaf.size, sizeText = sizeFormat.format(leaf.size), share = leaf.size / total,
                    ),
                )
            }
            // Text: the label, and the sublabel or value under it when both fit as a block.
            val innerW = tile.width - pad * 2
            val innerH = tile.height - pad * 2
            if (innerW <= 0f || innerH <= 0f) return@forEachIndexed
            val name = fitLabel(leaf.label, innerW, TextKind.TileLabel, measure)
            if (name.isEmpty()) return@forEachIndexed
            val nameSize = measure.measure(name, TextKind.TileLabel)
            if (nameSize.height > innerH) return@forEachIndexed
            val detail = leaf.sublabel ?: valueText
            val detailSize = if (detail.isEmpty()) null else measure.measure(detail, TextKind.TileDetail)
            if (detailSize != null && detailSize.width <= innerW && nameSize.height + detailSize.height <= innerH) {
                val top = tile.centerY - (nameSize.height + detailSize.height) / 2f
                labels += TextNode("tile-label:${leaf.id}", name, XY(tile.centerX, top), HAlign.Center, VAlign.Top, TextKind.TileLabel)
                labels += TextNode("tile-detail:${leaf.id}", detail, XY(tile.centerX, top + nameSize.height), HAlign.Center, VAlign.Top, TextKind.TileDetail)
            } else {
                labels += TextNode("tile-label:${leaf.id}", name, XY(tile.centerX, tile.centerY), HAlign.Center, VAlign.Center, TextKind.TileLabel)
            }
        }
    }
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
    )
}
