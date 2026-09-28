package dev.sebastiano.headroom.widget.render

import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import dev.sebastiano.headroom.widget.Gauge
import dev.sebastiano.headroom.widget.WidgetUiState
import java.time.Duration

/**
 * Time left until the next weekly reset across the chosen accounts. The number ticks inside the
 * widget from a time expression (see [RestrictedRemoteApis]), so the app does not wake up every
 * minute to redraw it.
 */
@RemoteComposable
@Composable
internal fun CountdownWidget(
    state: WidgetUiState.Countdown,
    render: RenderContext,
    modifier: RemoteModifier = RemoteModifier,
) {
    val colors = render.colors
    val strings = render.strings
    val next = state.next
    RemoteBox(
        modifier =
            modifier
                .fillMaxSize()
                .clip(RemoteRoundedCornerShape(render.px(CORNER)))
                .background(colors.countdownContainer.rc)
                .clickable(render.taps.refresh())
                .semantics {
                    contentDescription =
                        (next?.let { strings.countdownDescription(it) }
                                ?: strings.countdownTitle(null))
                            .rs
                },
        contentAlignment = RemoteAlignment.Center,
    ) {
        RemoteColumn(
            verticalArrangement = RemoteArrangement.spacedBy(render.px(GAP)),
            horizontalAlignment = RemoteAlignment.CenterHorizontally,
        ) {
            WidgetText(
                text = strings.countdownTitle(next?.window),
                color = colors.onCountdownContainer,
                fontSize = render.sp(LABEL),
            )
            if (next != null) {
                val bigPx = render.textPx(BIG)
                val live =
                    RestrictedRemoteApis.liveCountdown(
                        resetsAt = next.resetsAt,
                        whenPassed = strings.remaining(Duration.ZERO),
                    )
                RemoteBox(RemoteModifier.fillMaxWidth().height((bigPx * LINE).rf)) {
                    RemoteCanvas(RemoteModifier.fillMaxSize()) {
                        with(RestrictedRemoteApis) {
                            drawCentredText(
                                live,
                                width / 2f.rf,
                                height / 2f.rf,
                                textPaint(colors.onCountdownContainer, bigPx, bold = true),
                            )
                        }
                    }
                }
                WidgetText(
                    text = strings.accountAndTime(next),
                    color = colors.onCountdownContainer,
                    fontSize = render.sp(DETAIL),
                    fontWeight = FontWeight.SemiBold,
                    modifier =
                        RemoteModifier.clickable(render.taps.openApp(next.accountId)).semantics {
                            contentDescription = strings.openAction(next.name).rs
                        },
                )
            }
        }
    }
}

/**
 * The lock screen layout: a row of small rings with the provider logo inside, the percentage and
 * name below, and the next reset underneath. It is readable without unlocking and uses light marks
 * on a dark, translucent card whatever the style.
 */
@RemoteComposable
@Composable
internal fun LockScreenWidget(
    state: WidgetUiState.LockScreen,
    render: RenderContext,
    modifier: RemoteModifier = RemoteModifier,
) {
    val colors = render.colors
    WidgetCard(render, modifier, cornerDesign = LOCK_CORNER) {
        RemoteColumn(
            modifier = RemoteModifier.fillMaxSize().padding(render.px(LOCK_PADDING)),
            verticalArrangement = RemoteArrangement.SpaceEvenly,
            horizontalAlignment = RemoteAlignment.CenterHorizontally,
        ) {
            RemoteRow(
                modifier = RemoteModifier.fillMaxWidth().weight(1f.rf),
                horizontalArrangement = RemoteArrangement.SpaceEvenly,
                verticalAlignment = RemoteAlignment.CenterVertically,
            ) {
                state.gauges.forEach { gauge ->
                    LockScreenGauge(
                        gauge,
                        render,
                        RemoteModifier.weight(1f.rf)
                            .clickable(render.taps.openApp(gauge.accountId))
                            .semantics {
                                contentDescription = render.strings.gaugeDescription(gauge).rs
                            },
                    )
                }
            }
            state.next?.let { next ->
                WidgetText(
                    text = render.strings.lockScreenFooter(next),
                    color = colors.onSurfaceVariant,
                    fontSize = render.sp(LOCK_FOOTER),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@RemoteComposable
@Composable
private fun LockScreenGauge(
    gauge: Gauge,
    render: RenderContext,
    modifier: RemoteModifier = RemoteModifier,
) {
    val colors = render.colors
    RemoteColumn(modifier = modifier, horizontalAlignment = RemoteAlignment.CenterHorizontally) {
        RemoteBox(
            modifier = RemoteModifier.width(render.px(LOCK_RING)).height(render.px(LOCK_RING)),
            contentAlignment = RemoteAlignment.Center,
        ) {
            RemoteCanvas(RemoteModifier.fillMaxSize()) {
                drawGaugeRing(
                    fraction = gauge.shownPercent / PERCENT,
                    geometry = LockRing,
                    active = colors.accent(gauge.provider).rc,
                    track = colors.track.rc,
                )
            }
            ProviderLogoIcon(gauge.provider, render.pxValue(LOCK_LOGO), colors.onSurface)
        }
        WidgetText(
            text = render.strings.percent(gauge.shownPercent),
            color = colors.onSurface,
            fontSize = render.sp(LOCK_NUMBER),
            fontWeight = FontWeight.ExtraBold,
        )
        WidgetText(
            gauge.name,
            colors.onSurfaceVariant,
            render.sp(LOCK_NAME),
            fontWeight = FontWeight.Medium,
        )
    }
}

private val LockRing = RingGeometry(radius = 0.44f, stroke = 0.1f)
private const val CORNER = 44f
private const val GAP = 2f
private const val LABEL = 11f
private const val BIG = 31f
private const val LINE = 1.2f
private const val DETAIL = 11f
private const val LOCK_CORNER = 24f
private const val LOCK_PADDING = 10f
private const val LOCK_RING = 44f
private const val LOCK_LOGO = 22f
private const val LOCK_NUMBER = 15f
private const val LOCK_NAME = 10f
private const val LOCK_FOOTER = 11.5f
