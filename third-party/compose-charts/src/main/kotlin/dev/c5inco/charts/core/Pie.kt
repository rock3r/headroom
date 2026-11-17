package dev.c5inco.charts.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal fun compilePie(spec: PieChart, size: Extent, m: ChartMetrics): ChartScene {
    val identities = nameKeys(spec.slices.map { it.label })
    // Resolve equal labels in source order, before sorting can change their rank on an update.
    val order = if (spec.sort) spec.slices.indices.sortedByDescending { spec.slices[it].value } else spec.slices.indices.toList()
    val slices = order.map { spec.slices[it] }
    // Missing and nonpositive slices take no area. Normalize before summing so even several
    // finite values near Double.MAX_VALUE retain their proportions instead of overflowing.
    val peak = slices.maxOfOrNull { if (it.value.isFinite() && it.value > 0.0) it.value else 0.0 } ?: 0.0
    val total = if (peak > 0.0) slices.sumOf { if (it.value.isFinite() && it.value > 0.0) it.value / peak else 0.0 } else 0.0
    val center = XY(size.width / 2f, size.height / 2f)
    val outer = max(0f, min(size.width, size.height) / 2f - m.pieInset)
    val inner = outer * m.pieInnerRatio
    val mid = (outer + inner) / 2f
    val pad = if (mid > 0f) m.pieGap / mid else 0f
    val format = spec.valueFormat ?: Formats.Readable
    // Each slice carries its palette slot, so it paints the same in any scene.
    val legend = spec.legend()
    val marks = mutableListOf<Node>()
    val points = mutableListOf<FocusPoint>()
    var angle = 0.0
    slices.forEachIndexed { i, slice ->
        // Keep the drawn-order indices used by legend(), even when a slice has no geometry.
        if (!slice.value.isFinite() || slice.value <= 0.0) return@forEachIndexed
        val share = (slice.value / peak) / total
        val a0 = angle.toFloat()
        angle += share * 2 * PI
        val a1 = angle.toFloat()
        val inset = min(pad / 2f, (a1 - a0) / 2f)
        marks += ArcNode("arc:${identities[order[i]]}", center, inner, outer, a0 + inset, a1 - inset, i, legend[i].color, i, ArcKind.Slice)
        val am = (a0 + a1) / 2f
        val r = if (inner == 0f) outer * 0.62f else mid
        points += FocusPoint(
            series = i, datum = i, x = center.x + sin(am) * r, y = center.y - cos(am) * r, slot = am,
            label = slice.label, value = slice.value, valueText = format.format(slice.value),
            detail = FocusDetail.Arc(ArcHit(center, inner, outer, a0, a1), share),
        )
    }
    val plot = Bounds(center.x - outer, center.y - outer, center.x + outer, center.y + outer)
    return ChartScene(
        size = size,
        plot = plot,
        layout = SceneLayout.Free,
        series = legend,
        guides = emptyList(),
        marks = marks,
        labels = emptyList(),
        points = points,
        focusMode = FocusMode.Containment,
        description = spec.description,
    )
}
