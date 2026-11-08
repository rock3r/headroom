package dev.sebastiano.headroom.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.partColors
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.ui.home.WindowSummary
import kotlin.math.roundToInt

/**
 * What fills a window several products share, such as The X Subscription's weekly pool: one bar
 * with a segment per product, a legend that names each product with its colour and share, and the
 * total. The segments always show what was used, since that is what the products did.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SharedWindowParts(
    window: WindowSummary,
    provider: Provider,
    modifier: Modifier = Modifier,
) {
    val colors = partColors(provider, window.parts.map { it.colorSlot })
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StackedBar(
            fractions = window.partFractions,
            colors = colors,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            window.parts.forEachIndexed { index, part ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colors[index]))
                    Text(
                        text =
                            stringResource(
                                R.string.detail_part,
                                part.label,
                                part.usedPercent.roundToInt(),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.detail_parts_total, window.usedPercent.roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The segments side by side from the start of the bar, with a hairline between them. A segment that
 * rounds to almost nothing still shows, as a sliver, so every product in the legend has its colour
 * on the bar. The legend says the same, so the bar itself is left out of accessibility.
 */
@Composable
private fun StackedBar(
    fractions: List<Double>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.height(8.dp).clip(CircleShape).clearAndSetSemantics {}) {
        drawRect(track)
        val gap = GAP.toPx()
        val widths = stackedBarWidths(fractions, size.width, gap, MIN_SEGMENT.toPx())
        var x = 0f
        widths.forEachIndexed { index, width ->
            if (width <= 0f) return@forEachIndexed
            drawRect(colors[index], topLeft = Offset(x, 0f), size = Size(width, size.height))
            x += width + gap
        }
    }
}

/**
 * How wide each segment of a stacked bar [width] wide is, for [fractions] of the whole bar: zero
 * for a part that used nothing. The visible segments and the [gap]s between them end exactly where
 * the share used ends, so the bar never shows more than was used, and every segment is at least
 * [minWidth], so a product that rounds to nothing still has its colour. What the gaps and the
 * slivers take comes out of the bigger segments, in proportion. The iOS app's `StackedBar` lays out
 * the same way.
 */
internal fun stackedBarWidths(
    fractions: List<Double>,
    width: Float,
    gap: Float,
    minWidth: Float,
): List<Float> {
    val visible = fractions.indices.filter { fractions[it] > 0.0 }
    if (visible.isEmpty()) return fractions.map { 0f }
    val used = visible.sumOf { fractions[it] }.coerceAtMost(1.0).toFloat() * width
    val available = used - gap * (visible.size - 1)
    val widths = FloatArray(fractions.size)
    if (available <= minWidth * visible.size) {
        visible.forEach { widths[it] = minWidth }
        return widths.toList()
    }
    // Pin the segments too thin to see at minWidth, then share the rest among the others; pinning
    // one makes the others thinner, so repeat until no more need it.
    val pinned = mutableSetOf<Int>()
    while (true) {
        val free = visible.filterNot { it in pinned }
        val room = available - minWidth * pinned.size
        val total = free.sumOf { fractions[it] }
        free.forEach { widths[it] = (fractions[it] / total * room).toFloat() }
        pinned.forEach { widths[it] = minWidth }
        val tooThin = free.filter { widths[it] < minWidth }
        if (tooThin.isEmpty()) return widths.toList()
        pinned += tooThin
    }
}

private val MIN_SEGMENT = 3.dp
private val GAP = 2.dp
