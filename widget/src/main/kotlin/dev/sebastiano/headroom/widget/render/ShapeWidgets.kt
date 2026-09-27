package dev.sebastiano.headroom.widget.render

import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import dev.sebastiano.headroom.widget.Gauge
import dev.sebastiano.headroom.widget.WidgetUiState

/**
 * One account as a shape that changes with usage: a cookie, a flower, then a clover. The number is
 * always inside, so the shape is never the only signal. There is no card behind it.
 */
@RemoteComposable
@Composable
internal fun SingleShapeWidget(
    state: WidgetUiState.SingleShape,
    render: RenderContext,
    modifier: RemoteModifier = RemoteModifier,
) {
    val gauge = state.gauge
    val colors = render.colors
    val strings = render.strings
    RemoteBox(
        modifier =
            modifier.fillMaxSize().clickable(refreshAction(render.appWidgetId)).semantics {
                contentDescription =
                    (strings.gaugeDescription(gauge) + " " + strings.refreshAction()).rs
            },
        contentAlignment = RemoteAlignment.Center,
    ) {
        RemoteCanvas(RemoteModifier.fillMaxSize()) {
            drawPolarShape(gauge.shape.polar, colors.shapeFill(gauge.provider).rc)
        }
        RemoteColumn(horizontalAlignment = RemoteAlignment.CenterHorizontally) {
            WidgetText(
                text = strings.upper(gauge.name),
                color = colors.onShapeFill,
                fontSize = render.sp(LABEL),
                modifier =
                    RemoteModifier.clickable(openAppAction(render.appWidgetId, gauge.accountId))
                        .semantics { contentDescription = strings.openAction(gauge.name).rs },
            )
            WidgetText(
                text = strings.percent(gauge.usedPercent),
                color = colors.onShapeFill,
                fontSize = render.sp(BIG),
                fontWeight = FontWeight.Black,
            )
            gauge.reset?.let { reset ->
                WidgetText(strings.reset(reset), colors.onShapeFill, render.sp(LABEL))
            }
        }
    }
}

/** Two to four accounts as a grid of small shapes, each with its number and glyph. */
@RemoteComposable
@Composable
internal fun ShapeGridWidget(
    state: WidgetUiState.ShapeGrid,
    render: RenderContext,
    modifier: RemoteModifier = RemoteModifier,
) {
    WidgetCard(render, modifier) {
        GaugeGrid(state.gauges, render) { gauge, cellModifier ->
            SmallShape(gauge, render, cellModifier)
        }
    }
}

@RemoteComposable
@Composable
private fun SmallShape(
    gauge: Gauge,
    render: RenderContext,
    modifier: RemoteModifier = RemoteModifier,
) {
    val colors = render.colors
    RemoteBox(modifier = modifier, contentAlignment = RemoteAlignment.Center) {
        RemoteCanvas(RemoteModifier.fillMaxSize()) {
            drawPolarShape(gauge.shape.polar, colors.shapeFill(gauge.provider).rc)
        }
        RemoteColumn(horizontalAlignment = RemoteAlignment.CenterHorizontally) {
            WidgetText(
                text = gauge.usedPercent.toString(),
                color = colors.onShapeFill,
                fontSize = render.sp(SMALL_NUMBER),
                fontWeight = FontWeight.ExtraBold,
            )
            WidgetText(gauge.glyph, colors.onShapeFill, render.sp(SMALL_GLYPH))
        }
    }
}

private const val LABEL = 10f
private const val BIG = 28f
private const val SMALL_NUMBER = 15f
private const val SMALL_GLYPH = 8.5f
