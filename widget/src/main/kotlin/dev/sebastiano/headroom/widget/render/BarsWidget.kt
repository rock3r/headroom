package dev.sebastiano.headroom.widget.render

import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.state.asRemoteTextUnit
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import dev.sebastiano.headroom.widget.BarsLayout
import dev.sebastiano.headroom.widget.Gauge
import dev.sebastiano.headroom.widget.WidgetUiState

/**
 * One row per account: avatar, name, a flat bar with a pace tick, and the percentage. The row count
 * comes from the widget height, see [BarsLayout]. Rows share the height, so a taller widget gets
 * bigger rows, and rows tall enough also show when the window resets. Tapping a row opens that
 * account.
 */
@RemoteComposable
@Composable
internal fun BarsWidget(
    state: WidgetUiState.Bars,
    render: RenderContext,
    modifier: RemoteModifier = RemoteModifier,
) {
    val metrics = BarsMetrics.of(render.size, state.gauges.size)
    WidgetCard(render, modifier) {
        RemoteColumn(
            modifier =
                RemoteModifier.fillMaxSize()
                    .padding(
                        horizontal = render.fixedPx(HORIZONTAL_PADDING * metrics.textScale),
                        vertical = render.fixedPx(BarsMetrics.PADDING_DP),
                    ),
            verticalArrangement =
                RemoteArrangement.spacedBy(
                    render.fixedPx(BarsMetrics.GAP_DP),
                    RemoteAlignment.CenterVertically,
                ),
            horizontalAlignment = RemoteAlignment.Start,
        ) {
            state.gauges.forEach { gauge -> BarRow(gauge, render, metrics) }
        }
    }
}

@RemoteComposable
@Composable
private fun BarRow(
    gauge: Gauge,
    render: RenderContext,
    metrics: BarsMetrics,
    modifier: RemoteModifier = RemoteModifier,
) {
    val colors = render.colors
    val strings = render.strings
    val scale = metrics.textScale
    RemoteRow(
        modifier =
            modifier
                .fillMaxWidth()
                .height(render.fixedPx(metrics.rowDp))
                .clickable(render.taps.openApp(gauge.accountId))
                .semantics {
                    contentDescription =
                        (strings.gaugeDescription(gauge) + " " + strings.openAction(gauge.name)).rs
                },
        // Spacing comes from padding: the Android 16 player does not take arranged spacing out of
        // the width it gives to weighted children, so the row would overflow.
        verticalAlignment = RemoteAlignment.CenterVertically,
    ) {
        ProviderAvatar(gauge.provider, render, AVATAR_DP * scale)
        RemoteColumn(
            modifier =
                RemoteModifier.padding(start = render.fixedPx(ROW_SPACING * scale))
                    .width(render.fixedPx(NAME_WIDTH * scale)),
            verticalArrangement = RemoteArrangement.Center,
            horizontalAlignment = RemoteAlignment.Start,
        ) {
            WidgetText(
                text = gauge.name,
                color = colors.onSurface,
                fontSize = (NAME_SP * scale).sp.asRemoteTextUnit(),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start,
            )
            val reset = gauge.reset
            if (metrics.showResetLine && reset != null) {
                WidgetText(
                    text = strings.reset(reset),
                    color = colors.onSurfaceVariant,
                    fontSize = (RESET_SP * scale).sp.asRemoteTextUnit(),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Start,
                )
            }
        }
        // The canvas sits in a box: a canvas sized directly by a row draws nothing in the
        // Android 16 widget player.
        RemoteBox(
            RemoteModifier.weight(1f.rf)
                .padding(horizontal = render.fixedPx(ROW_SPACING * scale))
                .height(render.fixedPx(BAR_HEIGHT * metrics.scale))
        ) {
            RemoteCanvas(RemoteModifier.fillMaxSize()) {
                drawGaugeBar(
                    fraction = gauge.usedPercent / PERCENT,
                    paceFraction = gauge.pacePercent?.let { it / PERCENT },
                    active = colors.accent(gauge.provider).rc,
                    track = colors.track.rc,
                    tick = colors.paceTick.rc,
                )
            }
        }
        WidgetText(
            text = strings.percent(gauge.usedPercent),
            color = colors.onSurface,
            fontSize = (VALUE_SP * scale).sp.asRemoteTextUnit(),
            modifier = RemoteModifier.width(render.fixedPx(VALUE_WIDTH * scale)),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

/** Text grows with the rows, but less than the bars do, so names still fit. */
private val BarsMetrics.textScale: Float
    get() = scale.coerceAtMost(MAX_TEXT_SCALE)

private const val MAX_TEXT_SCALE = 1.45f
private const val HORIZONTAL_PADDING = 14f
private const val ROW_SPACING = 8f
private const val AVATAR_DP = 22f
private const val NAME_WIDTH = 60f
private const val VALUE_WIDTH = 42f
private const val BAR_HEIGHT = 12f
private const val NAME_SP = 12f
private const val VALUE_SP = 13f
private const val RESET_SP = 9.5f
