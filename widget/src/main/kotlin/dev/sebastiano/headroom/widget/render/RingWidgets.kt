package dev.sebastiano.headroom.widget.render

import androidx.compose.remote.creation.compose.action.valueChange
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
import androidx.compose.remote.creation.compose.modifier.fillMaxHeight
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rememberMutableRemoteBoolean
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import dev.sebastiano.headroom.widget.Gauge
import dev.sebastiano.headroom.widget.WidgetUiState

/** Ring sizes, from the design: an 8 px stroke on a 136 px ring, a 5 px stroke on 50 px. */
private val HeroOuter = RingGeometry(radius = 0.455f, stroke = 0.059f)
private val HeroInner = RingGeometry(radius = 0.34f, stroke = 0.059f)
private val SmallRing = RingGeometry(radius = 0.44f, stroke = 0.1f)

/**
 * One account: the main window as the outer ring, the session as the inner ring. Tapping the ring
 * swaps the big number between the two with a value-change action inside the widget, and dims the
 * ring that is not being read. The number and its label are drawn on the canvas, see
 * [RestrictedRemoteApis].
 */
@RemoteComposable
@Composable
internal fun SingleRingWidget(
    state: WidgetUiState.SingleRing,
    render: RenderContext,
    modifier: RemoteModifier = RemoteModifier,
) {
    val gauge = state.gauge
    val session = state.session
    val colors = render.colors
    val strings = render.strings
    val showSession = rememberMutableRemoteBoolean(false)

    val mainColor = colors.accent(gauge.provider)
    val sessionColor = colors.secondaryAccent(gauge.provider)
    val outerColor =
        if (session == null) mainColor.rc
        else showSession.select(mainColor.copy(alpha = DIMMED).rc, mainColor.rc)
    val innerColor = showSession.select(sessionColor.rc, sessionColor.copy(alpha = DIMMED).rc)

    val number =
        if (session == null) strings.percent(gauge.shownPercent).rs
        else
            showSession.select(
                strings.percent(session.shownPercent).rs,
                strings.percent(gauge.shownPercent).rs,
            )
    val word =
        if (session == null) strings.windowWord(gauge.window).rs
        else
            showSession.select(
                strings.windowWord(session.window).rs,
                strings.windowWord(gauge.window).rs,
            )
    val description =
        listOfNotNull(
                strings.gaugeDescription(gauge),
                session?.let { strings.gaugeDescription(it) },
                session?.let { strings.flipAction(gauge.window) },
            )
            .joinToString(" ")
    val ringModifier =
        if (session != null) {
            RemoteModifier.fillMaxSize().clickable(valueChange(showSession, !showSession))
        } else {
            RemoteModifier.fillMaxSize()
        }

    WidgetCard(render, modifier) {
        RemoteBox(
            modifier = ringModifier.semantics { contentDescription = description.rs },
            contentAlignment = RemoteAlignment.Center,
        ) {
            RemoteCanvas(RemoteModifier.fillMaxSize().padding(render.px(RING_PADDING))) {
                drawGaugeRing(
                    fraction = gauge.shownPercent / PERCENT,
                    geometry = HeroOuter,
                    active = outerColor,
                    track = colors.track.rc,
                    wavy = state.wavy,
                )
                if (session != null) {
                    drawGaugeRing(
                        fraction = session.shownPercent / PERCENT,
                        geometry = HeroInner,
                        active = innerColor,
                        track = colors.track.rc,
                    )
                }
            }
            RemoteColumn(horizontalAlignment = RemoteAlignment.CenterHorizontally) {
                WidgetText(
                    text = strings.upper(gauge.name),
                    color = colors.onSurfaceVariant,
                    fontSize = render.sp(LABEL),
                    modifier =
                        RemoteModifier.clickable(render.taps.openApp(gauge.accountId)).semantics {
                            contentDescription = strings.openAction(gauge.name).rs
                        },
                )
                val bigPx = render.textPx(BIG)
                val labelPx = render.textPx(LABEL)
                RemoteBox(
                    RemoteModifier.width(render.px(NUMBER_BOX_WIDTH))
                        .height((bigPx * NUMBER_LINE + labelPx * LABEL_LINE).rf)
                ) {
                    RemoteCanvas(RemoteModifier.fillMaxSize()) {
                        with(RestrictedRemoteApis) {
                            drawCentredText(
                                number,
                                width / 2f.rf,
                                (bigPx * NUMBER_LINE / 2f).rf,
                                textPaint(colors.onSurface, bigPx, bold = true),
                            )
                            drawCentredText(
                                word,
                                width / 2f.rf,
                                (bigPx * NUMBER_LINE + labelPx * LABEL_LINE / 2f).rf,
                                textPaint(colors.onSurfaceVariant, labelPx, bold = true),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Two to four accounts as a grid of small rings, each with its number inside. */
@RemoteComposable
@Composable
internal fun RingGridWidget(
    state: WidgetUiState.RingGrid,
    render: RenderContext,
    modifier: RemoteModifier = RemoteModifier,
) {
    WidgetCard(render, modifier) {
        GaugeGrid(state.gauges, render) { gauge, cellModifier ->
            SmallRing(gauge, render, cellModifier)
        }
    }
}

/**
 * A grid that fills the widget, one cell per gauge. A wide widget puts every account in one row, a
 * tall one stacks them, and a squarish one uses two columns; see [GridLayout].
 */
@RemoteComposable
@Composable
internal fun GaugeGrid(
    gauges: List<Gauge>,
    render: RenderContext,
    modifier: RemoteModifier = RemoteModifier,
    cell: @Composable (Gauge, RemoteModifier) -> Unit,
) {
    RemoteColumn(
        modifier = modifier.fillMaxSize().padding(render.px(GRID_PADDING)),
        verticalArrangement = RemoteArrangement.SpaceEvenly,
        horizontalAlignment = RemoteAlignment.CenterHorizontally,
    ) {
        gauges.chunked(GridLayout.columns(render.size, gauges.size)).forEach { rowGauges ->
            RemoteRow(
                modifier = RemoteModifier.fillMaxWidth().weight(1f.rf),
                horizontalArrangement = RemoteArrangement.SpaceEvenly,
                verticalAlignment = RemoteAlignment.CenterVertically,
            ) {
                rowGauges.forEach { gauge ->
                    cell(
                        gauge,
                        RemoteModifier.weight(1f.rf)
                            .fillMaxHeight()
                            .clickable(render.taps.openApp(gauge.accountId))
                            .semantics {
                                contentDescription =
                                    (render.strings.gaugeDescription(gauge) +
                                            " " +
                                            render.strings.openAction(gauge.name))
                                        .rs
                            },
                    )
                }
            }
        }
    }
}

@RemoteComposable
@Composable
private fun SmallRing(
    gauge: Gauge,
    render: RenderContext,
    modifier: RemoteModifier = RemoteModifier,
) {
    val colors = render.colors
    RemoteColumn(
        modifier = modifier,
        verticalArrangement = RemoteArrangement.Center,
        horizontalAlignment = RemoteAlignment.CenterHorizontally,
    ) {
        RemoteBox(
            modifier = RemoteModifier.fillMaxWidth().weight(1f.rf),
            contentAlignment = RemoteAlignment.Center,
        ) {
            RemoteCanvas(RemoteModifier.fillMaxSize()) {
                drawGaugeRing(
                    fraction = gauge.shownPercent / PERCENT,
                    geometry = SmallRing,
                    active = colors.accent(gauge.provider).rc,
                    track = colors.track.rc,
                )
            }
            WidgetText(
                text = gauge.shownPercent.toString(),
                color = colors.onSurface,
                fontSize = render.sp(SMALL_NUMBER),
                fontWeight = FontWeight.ExtraBold,
            )
        }
        WidgetText(
            gauge.name,
            colors.onSurfaceVariant,
            render.sp(SMALL_NAME),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

internal const val PERCENT = 100f
private const val DIMMED = 0.35f
private const val RING_PADDING = 6f
private const val GRID_PADDING = 8f
private const val LABEL = 10f
private const val BIG = 30f
private const val SMALL_NUMBER = 13f
private const val SMALL_NAME = 9.5f
private const val NUMBER_BOX_WIDTH = 110f
private const val NUMBER_LINE = 1.15f
private const val LABEL_LINE = 1.4f
