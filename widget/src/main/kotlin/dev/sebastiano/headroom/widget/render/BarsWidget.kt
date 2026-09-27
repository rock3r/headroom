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
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.sebastiano.headroom.widget.BarsLayout
import dev.sebastiano.headroom.widget.Gauge
import dev.sebastiano.headroom.widget.WidgetUiState

/**
 * One row per account: avatar, name, a flat bar with a pace tick, and the percentage. The row count
 * comes from the widget height, see [BarsLayout]. Tapping a row opens that account.
 */
@RemoteComposable
@Composable
internal fun BarsWidget(
    state: WidgetUiState.Bars,
    render: RenderContext,
    modifier: RemoteModifier = RemoteModifier,
) {
    WidgetCard(render, modifier) {
        RemoteColumn(
            modifier =
                RemoteModifier.fillMaxSize()
                    .padding(
                        horizontal = render.fixedPx(HORIZONTAL_PADDING),
                        vertical = render.fixedPx(BarsLayout.PADDING_DP),
                    ),
            verticalArrangement =
                RemoteArrangement.spacedBy(
                    render.fixedPx(BarsLayout.GAP_DP),
                    RemoteAlignment.CenterVertically,
                ),
            horizontalAlignment = RemoteAlignment.Start,
        ) {
            state.gauges.forEach { gauge -> BarRow(gauge, render) }
        }
    }
}

@RemoteComposable
@Composable
private fun BarRow(gauge: Gauge, render: RenderContext, modifier: RemoteModifier = RemoteModifier) {
    val colors = render.colors
    val strings = render.strings
    RemoteRow(
        modifier =
            modifier
                .fillMaxWidth()
                .height(render.fixedPx(BarsLayout.ROW_DP))
                .clickable(openAppAction(render.appWidgetId, gauge.accountId))
                .semantics {
                    contentDescription =
                        (strings.gaugeDescription(gauge) + " " + strings.openAction(gauge.name)).rs
                },
        // Spacing comes from padding: the Android 16 player does not take arranged spacing out of
        // the width it gives to weighted children, so the row would overflow.
        verticalAlignment = RemoteAlignment.CenterVertically,
    ) {
        ProviderAvatar(gauge.provider, render, AVATAR_DP)
        WidgetText(
            text = gauge.name,
            color = colors.onSurface,
            fontSize = NAME_SP.rsp,
            modifier =
                RemoteModifier.padding(start = render.fixedPx(ROW_SPACING))
                    .width(render.fixedPx(NAME_WIDTH)),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Start,
        )
        // The canvas sits in a box: a canvas sized directly by a row draws nothing in the
        // Android 16 widget player.
        RemoteBox(
            RemoteModifier.weight(1f.rf)
                .padding(horizontal = render.fixedPx(ROW_SPACING))
                .height(render.fixedPx(BAR_HEIGHT))
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
            fontSize = NAME_SP.rsp,
            modifier = RemoteModifier.width(render.fixedPx(VALUE_WIDTH)),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

private const val HORIZONTAL_PADDING = 14f
private const val ROW_SPACING = 8f
private const val AVATAR_DP = 22f
private const val NAME_WIDTH = 56f
private const val VALUE_WIDTH = 40f
private const val BAR_HEIGHT = 14f
private const val NAME_SP = 12
