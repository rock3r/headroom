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
        val minWidth = MIN_SEGMENT.toPx()
        val gap = GAP.toPx()
        var x = 0f
        fractions.forEachIndexed { index, fraction ->
            if (fraction <= 0.0) return@forEachIndexed
            val width = (fraction.toFloat() * size.width).coerceAtLeast(minWidth)
            drawRect(colors[index], topLeft = Offset(x, 0f), size = Size(width, size.height))
            x += width
            if (index < fractions.lastIndex) {
                drawRect(track, topLeft = Offset(x, 0f), size = Size(gap, size.height))
                x += gap
            }
        }
    }
}

private val MIN_SEGMENT = 3.dp
private val GAP = 2.dp
