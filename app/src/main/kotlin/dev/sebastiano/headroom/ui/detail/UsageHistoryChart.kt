package dev.sebastiano.headroom.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import dev.c5inco.charts.compose.AxisStyle
import dev.c5inco.charts.compose.Chart
import dev.c5inco.charts.compose.ChartMotion
import dev.c5inco.charts.compose.ChartStyle
import dev.c5inco.charts.compose.ChartTooltip
import dev.c5inco.charts.compose.DotStyle
import dev.c5inco.charts.compose.FocusStyle
import dev.c5inco.charts.compose.Glyph
import dev.c5inco.charts.compose.GlyphSwatch
import dev.c5inco.charts.compose.GridStyle
import dev.c5inco.charts.compose.LineStyle
import dev.c5inco.charts.compose.SceneCompile
import dev.c5inco.charts.compose.StrokePattern
import dev.c5inco.charts.compose.TooltipStyle
import dev.c5inco.charts.compose.ViewportActions
import dev.c5inco.charts.compose.rememberChartViewport
import dev.c5inco.charts.core.ChartScene
import dev.c5inco.charts.core.Curve
import dev.c5inco.charts.core.Focus
import dev.c5inco.charts.core.ViewportBounds
import dev.c5inco.charts.core.XY
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.Oklch
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.designsystem.toColor
import dev.sebastiano.headroom.designsystem.toOklch
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.ui.LayoutWidth
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.layoutWidth
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.launch

const val USAGE_HISTORY_CHART_TAG: String = "usage-history-chart"

fun chartRangeTag(range: HistoryRange): String = "chart-range-${range.name}"

/**
 * Long-term usage history chart for one account: overlays all selected weekly and monthly windows,
 * marks past resets with vertical rules, shades the primary window's current period, draws the
 * current period's even-pace diagonal and projection, and supports pan, pinch-zoom, scrub tooltips
 * and range preset buttons.
 */
@Composable
fun UsageHistoryChart(
    model: UsageHistoryModel,
    formatter: ResetFormatter,
    display: QuotaDisplay,
    onWindowToggle: (windowId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseStyle = rememberUsageHistoryChartStyle()
    val animate = animationsEnabled()
    // Reveal from the baseline once per account per screen visit, never on range/filter changes.
    val revealed = rememberSaveable(model.accountId) { booleanArrayOf(false) }
    val animateEntrance = remember(model.accountId, animate) { animate && !revealed[0] }
    SideEffect { revealed[0] = true }

    Column(modifier = modifier.fillMaxWidth()) {
        if (model.windows.size > 1) {
            Spacer(Modifier.height(8.dp))
            WindowFilterChips(
                windows = model.windows,
                selectedIds = model.selectedWindowIds,
                style = baseStyle,
                onWindowToggle = onWindowToggle,
            )
        }
        key(model.accountId) {
            UsageHistoryPlot(
                model = model,
                formatter = formatter,
                display = display,
                baseStyle = baseStyle,
                animate = animate,
                animateEntrance = animateEntrance,
            )
        }
    }
}

@Composable
private fun UsageHistoryPlot(
    model: UsageHistoryModel,
    formatter: ResetFormatter,
    display: QuotaDisplay,
    baseStyle: ChartStyle,
    animate: Boolean,
    animateEntrance: Boolean,
) {
    val dataSpec = HeadroomMotion.dataSpec<Float>()
    val availableRanges = model.availableRanges
    val viewport = rememberModelViewport(model)
    val scope = rememberCoroutineScope()
    var animatingRange by remember(viewport) { mutableStateOf<Pair<Long, HistoryRange>?>(null) }
    val animationSerial = remember(viewport) { longArrayOf(0L) }
    val selectedRange = rememberSelectedRange(viewport, model, animatingRange?.second)
    val zoomDensity by remember(viewport) { derivedStateOf { ZoomDensity.forSpan(viewport.span) } }
    val style = remember(baseStyle, zoomDensity) { zoomDensity.applyTo(baseStyle) }
    val lastState = remember { ZoomSyncState(model, zoomDensity) }
    val densityOnlyChange = lastState.model == model && lastState.density != zoomDensity
    SideEffect {
        lastState.model = model
        lastState.density = zoomDensity
    }
    val chartAnimationSpec = if (animate && !densityOnlyChange) dataSpec else ChartMotion.Instant

    Column(modifier = Modifier.fillMaxWidth()) {
        if (availableRanges.size > 1) {
            Spacer(Modifier.height(8.dp))
            RangePresetButtons(
                ranges = availableRanges,
                selectedRange = selectedRange,
                onSelectRange = { range ->
                    val target = model.targetRange(range)
                    val serial = ++animationSerial[0]
                    if (!animate) {
                        animatingRange = null
                        viewport.snapTo(target.start, target.end)
                    } else {
                        animatingRange = serial to range
                        scope.launch {
                            try {
                                viewport.animateTo(target.start, target.end, dataSpec)
                            } finally {
                                if (animatingRange?.first == serial) animatingRange = null
                            }
                        }
                    }
                },
            )
        }

        Spacer(Modifier.height(14.dp))
        val primarySummary = model.primaryShownWindow?.summary ?: model.windows.first().summary
        val paceDescription = paceContentDescription(primarySummary, display)
        val limitLabel = stringResource(R.string.detail_chart_limit)
        val resources = LocalResources.current
        val percentFormat =
            remember(resources) {
                { percent: Int -> resources.getString(R.string.percent, percent) }
            }
        val resetStride = zoomDensity.resetStride
        val spec =
            remember(model, formatter, limitLabel, percentFormat, paceDescription, resetStride) {
                buildUsageHistoryChartSpec(
                    model = model,
                    formatter = formatter,
                    limitLabel = limitLabel,
                    percentFormat = percentFormat,
                    description = paceDescription,
                    resetStride = resetStride,
                )
            }
        val usageSeriesCount = model.shownWindows.size
        Chart(
            spec = spec,
            modifier =
                Modifier.fillMaxWidth()
                    .height(adaptiveChartHeight())
                    .testTag(USAGE_HISTORY_CHART_TAG)
                    .semantics {
                        contentDescription =
                            visibleRangeDescription(
                                resources = resources,
                                model = model,
                                startEpochMs = viewport.start,
                                endEpochMs = viewport.end,
                                formatter = formatter,
                                paceDescription = paceDescription,
                            )
                    },
            style = style,
            animationSpec = chartAnimationSpec,
            animateEntrance = animateEntrance,
            compile = if (densityOnlyChange) SceneCompile.InPlace else SceneCompile.Background,
            viewport = viewport,
        ) { focus, scene ->
            UsageHistoryTooltip(
                focus = focus,
                scene = scene,
                style = style,
                usageSeriesCount = usageSeriesCount,
            )
        }
    }
}

private class ZoomSyncState(var model: UsageHistoryModel, var density: ZoomDensity)

private enum class ZoomDensity(val lineWidth: Dp, val markerRadius: Dp, val resetStride: Int) {
    Detail(lineWidth = 2.5.dp, markerRadius = 4.dp, resetStride = 1),
    Medium(lineWidth = 1.75.dp, markerRadius = 3.5.dp, resetStride = 1),
    Overview(lineWidth = 1.2.dp, markerRadius = 3.dp, resetStride = 4);

    fun applyTo(base: ChartStyle): ChartStyle =
        base.copy(
            line = base.line.copy(width = lineWidth, markerRadius = markerRadius),
            dot = base.dot.copy(radius = markerRadius),
        )

    companion object {
        fun forSpan(spanMs: Double): ZoomDensity =
            when {
                spanMs <= DETAIL_SPAN_MAX_MS -> Detail
                spanMs <= MEDIUM_SPAN_MAX_MS -> Medium
                else -> Overview
            }
    }
}

@Composable
private fun rememberSelectedRange(
    viewport: dev.c5inco.charts.compose.ChartViewport,
    model: UsageHistoryModel,
    animating: HistoryRange?,
): HistoryRange? {
    val bounds =
        remember(viewport) {
            ViewportBounds(
                min = viewport.min,
                max = viewport.max,
                minSpan = viewport.minSpan,
                maxSpan = viewport.maxSpan,
            )
        }
    val matched by
        remember(viewport, bounds, model) {
            derivedStateOf {
                model.availableRanges.firstOrNull { range ->
                    val target = model.targetRange(range)
                    val clamped = bounds.clamp(target.start, target.end)
                    val tolerance =
                        maxOf(RANGE_MATCH_TOLERANCE_MS, clamped.span * RANGE_MATCH_FRACTION)
                    abs(viewport.start - clamped.start) <= tolerance &&
                        abs(viewport.end - clamped.end) <= tolerance
                }
            }
        }
    return animating ?: matched
}

@Composable
private fun rememberModelViewport(
    model: UsageHistoryModel
): dev.c5inco.charts.compose.ChartViewport {
    val actions =
        ViewportActions(
            scrollBack = stringResource(R.string.detail_chart_scroll_back),
            scrollForward = stringResource(R.string.detail_chart_scroll_forward),
            zoomIn = stringResource(R.string.detail_chart_zoom_in),
            zoomOut = stringResource(R.string.detail_chart_zoom_out),
        )
    val totalSpanMs =
        (model.maxBound - model.minBound)
            .inWholeMilliseconds
            .toDouble()
            .coerceAtLeast(MIN_POSITIVE_SPAN_MS)
    val minSpanMs = minOf(MIN_VIEWPORT_SPAN.inWholeMilliseconds.toDouble(), totalSpanMs)
    return rememberChartViewport(
        min = model.minBound.toEpochMilliseconds().toDouble(),
        max = model.maxBound.toEpochMilliseconds().toDouble(),
        initialStart = model.defaultStart.toEpochMilliseconds().toDouble(),
        initialEnd = model.defaultEnd.toEpochMilliseconds().toDouble(),
        minSpan = minSpanMs,
        maxSpan = totalSpanMs,
        actions = actions,
    )
}

@Composable
private fun WindowFilterChips(
    windows: List<WindowHistorySeries>,
    selectedIds: Set<String>,
    style: ChartStyle,
    onWindowToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        windows.forEach { window ->
            val selected = window.id in selectedIds
            FilterChip(
                selected = selected,
                onClick = { onWindowToggle(window.id) },
                label = { Text(window.label) },
                leadingIcon = {
                    GlyphSwatch(
                        glyph = style.glyph(window.colorSlot),
                        color = style.color(window.colorSlot),
                        size = 10.dp,
                    )
                },
                modifier =
                    Modifier.testTag(chartWindowTag(window.id)).semantics {
                        toggleableState = ToggleableState(selected)
                    },
            )
        }
    }
}

@Composable
private fun RangePresetButtons(
    ranges: List<HistoryRange>,
    selectedRange: HistoryRange?,
    onSelectRange: (HistoryRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ranges.forEachIndexed { index, range ->
            val weight =
                if (range == HistoryRange.Period) PERIOD_BUTTON_WEIGHT else SHORT_BUTTON_WEIGHT
            SegmentedButton(
                selected = range == selectedRange,
                onClick = { onSelectRange(range) },
                shape = SegmentedButtonDefaults.itemShape(index, ranges.size),
                icon = {},
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                modifier = Modifier.weight(weight).testTag(chartRangeTag(range)),
            ) {
                Text(text = rangeLabel(range), maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
@ReadOnlyComposable
private fun rangeLabel(range: HistoryRange): String =
    stringResource(
        when (range) {
            HistoryRange.Period -> R.string.detail_chart_range_period
            HistoryRange.OneMonth -> R.string.detail_chart_range_1m
            HistoryRange.ThreeMonths -> R.string.detail_chart_range_3m
            HistoryRange.OneYear -> R.string.detail_chart_range_1y
        }
    )

@Composable
private fun UsageHistoryTooltip(
    focus: Focus,
    scene: ChartScene,
    style: ChartStyle,
    usageSeriesCount: Int,
) {
    val usagePoints = focus.group.filter { it.series in 0 until usageSeriesCount }
    if (usagePoints.isEmpty()) return
    val primary =
        if (focus.primary.series in 0 until usageSeriesCount) focus.primary else usagePoints.first()
    val filtered =
        focus.copy(
            primary = primary,
            group = usagePoints,
            anchor = XY(focus.anchor.x, usagePoints.minOf { it.y }),
        )
    ChartTooltip(filtered, scene, style)
}

@Composable
@ReadOnlyComposable
internal fun paceContentDescription(
    summary: dev.sebastiano.headroom.model.ChartSummary,
    display: QuotaDisplay,
): String {
    val shown = display.percent(summary.usedPercent).roundToInt()
    val above = summary.usedPercent > summary.expectedPercent
    return stringResource(
        when (display) {
            QuotaDisplay.Used ->
                if (above) R.string.detail_chart_description_above
                else R.string.detail_chart_description_below
            QuotaDisplay.Left ->
                if (above) R.string.detail_chart_description_above_left
                else R.string.detail_chart_description_below_left
        },
        shown,
    )
}

private fun visibleRangeDescription(
    resources: android.content.res.Resources,
    model: UsageHistoryModel,
    startEpochMs: Double,
    endEpochMs: Double,
    formatter: ResetFormatter,
    paceDescription: String,
): String {
    val windowNames = model.shownWindows.joinToString { it.label }
    val startInstant = Instant.fromEpochMilliseconds(startEpochMs.roundToLong())
    val endInstant = Instant.fromEpochMilliseconds(endEpochMs.roundToLong())
    val longSpan = (endInstant - startInstant) > LONG_DATE_SPAN
    val startText = if (longSpan) formatter.date(startInstant) else formatter.day(startInstant)
    val endText = if (longSpan) formatter.date(endInstant) else formatter.day(endInstant)
    val resetsInView = model.resetsInRange(startEpochMs, endEpochMs)
    val resetsText =
        if (resetsInView == 0) {
            resources.getString(R.string.detail_chart_no_resets_in_view)
        } else {
            resources.getQuantityString(
                R.plurals.detail_chart_resets_in_view,
                resetsInView,
                resetsInView,
            )
        }
    return resources.getString(
        R.string.detail_chart_visible_description,
        windowNames,
        startText,
        endText,
        paceDescription,
        resetsText,
    )
}

@Composable
private fun adaptiveChartHeight(): Dp {
    val width = layoutWidth()
    val sizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val compactHeight =
        !sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    return when {
        compactHeight -> LandscapeChartHeight
        width == LayoutWidth.Expanded -> ExpandedChartHeight
        width == LayoutWidth.Medium -> MediumChartHeight
        else -> CompactChartHeight
    }
}

/**
 * Theme-aware, colour-blind distinguishable [ChartStyle] for the usage history chart.
 *
 * Slot 0 uses `colorScheme.primary`; slots 1..4 rotate around the OKLCH hue wheel from the primary
 * hue at contrast-safe lightness for light and dark surfaces, and pair each slot with a distinct
 * marker [Glyph] (`Circle`, `Square`, `Diamond`, `Triangle`) so colour is never the sole cue. Slot
 * [PACE_COLOR_SLOT] is reserved for the dashed even-pace guide line.
 */
@Composable
internal fun rememberUsageHistoryChartStyle(): ChartStyle {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val surface = scheme.surfaceContainer
    val dark = surface.luminance() < DARK_SURFACE_LUMINANCE
    return remember(scheme, typography, dark) {
        val baseHue = scheme.primary.toOklch().hue
        val palette =
            listOf(
                scheme.primary,
                seriesAccent(baseHue + SECOND_HUE_SHIFT, dark, lightnessDelta = 0f),
                seriesAccent(baseHue + THIRD_HUE_SHIFT, dark, lightnessDelta = -0.04f),
                seriesAccent(baseHue + FOURTH_HUE_SHIFT, dark, lightnessDelta = 0.04f),
                seriesAccent(baseHue + FIFTH_HUE_SHIFT, dark, lightnessDelta = -0.02f),
                scheme.onSurfaceVariant.copy(alpha = PACE_LINE_ALPHA),
            )
        val labelStyle = typography.labelSmall.copy(color = scheme.onSurfaceVariant)
        val limitLabelStyle = typography.labelSmall.copy(color = scheme.error)
        ChartStyle(
            palette = palette,
            curve = Curve.Linear,
            line = LineStyle(width = 2.5.dp, markerRadius = 4.dp, showMarkers = true),
            dot =
                DotStyle(
                    radius = 4.dp,
                    glyphs = SeriesGlyphs,
                    outline = surface,
                    outlineWidth = 1.5.dp,
                ),
            grid =
                GridStyle(
                    color = scheme.outlineVariant.copy(alpha = GRID_ALPHA),
                    width = 1.dp,
                    dash = null,
                    horizontal = true,
                    vertical = false,
                    baseline = scheme.outlineVariant,
                    reference = scheme.error,
                    referenceChip = surface,
                    referenceWidth = 1.dp,
                    referencePattern = StrokePattern.Dashed(2.dp, 3.dp),
                    xRule = scheme.onSurfaceVariant.copy(alpha = XRULE_ALPHA),
                    xRuleWidth = 1.25.dp,
                    xRulePattern = StrokePattern.Dashed(4.dp, 4.dp),
                    band =
                        scheme.primary.copy(
                            alpha = if (dark) DARK_BAND_ALPHA else LIGHT_BAND_ALPHA
                        ),
                ),
            axis =
                AxisStyle(
                    labels = labelStyle,
                    referenceLabel = limitLabelStyle,
                    labelGap = 6.dp,
                    minLabelSpacing = 12.dp,
                ),
            focus =
                FocusStyle(
                    rule = scheme.onSurfaceVariant.copy(alpha = FOCUS_RULE_ALPHA),
                    ruleWidth = 1.dp,
                    halo = surface,
                    markerRadius = 5.dp,
                    dimAlpha = FOCUS_DIM_ALPHA,
                ),
            tooltip =
                TooltipStyle(
                    container = scheme.inverseSurface,
                    title = typography.labelMedium.copy(color = scheme.inverseOnSurface),
                    body =
                        typography.labelSmall.copy(
                            color = scheme.inverseOnSurface.copy(alpha = TOOLTIP_BODY_ALPHA)
                        ),
                    cornerRadius = 10.dp,
                    swatch = 8.dp,
                ),
        )
    }
}

private fun seriesAccent(hue: Float, dark: Boolean, lightnessDelta: Float): Color {
    val wrappedHue = ((hue % FULL_TURN) + FULL_TURN) % FULL_TURN
    val lightness = (if (dark) DARK_SERIES_L else LIGHT_SERIES_L) + lightnessDelta
    val chroma = if (dark) DARK_SERIES_C else LIGHT_SERIES_C
    return Oklch(lightness = lightness, chroma = chroma, hue = wrappedHue).toColor()
}

private val DiamondGlyph: Glyph =
    Glyph.path(
        Path().apply {
            moveTo(0.5f, 0f)
            lineTo(1f, 0.5f)
            lineTo(0.5f, 1f)
            lineTo(0f, 0.5f)
            close()
        }
    )

private val TriangleGlyph: Glyph =
    Glyph.path(
        Path().apply {
            moveTo(0.5f, 0f)
            lineTo(1f, 1f)
            lineTo(0f, 1f)
            close()
        }
    )

private val SeriesGlyphs: List<Glyph> =
    listOf(Glyph.Circle, Glyph.Square, DiamondGlyph, TriangleGlyph, Glyph.Circle, Glyph.Circle)

private val MIN_VIEWPORT_SPAN = 6.hours
private val LONG_DATE_SPAN = 180.days
private val DETAIL_SPAN_MAX_MS = 45.days.inWholeMilliseconds.toDouble()
private val MEDIUM_SPAN_MAX_MS = 180.days.inWholeMilliseconds.toDouble()
private const val MIN_POSITIVE_SPAN_MS = 1.0
private const val RANGE_MATCH_TOLERANCE_MS = 3_600_000.0
private const val RANGE_MATCH_FRACTION = 0.02
private const val PERIOD_BUTTON_WEIGHT = 1.6f
private const val SHORT_BUTTON_WEIGHT = 1f

private val LandscapeChartHeight = 148.dp
private val CompactChartHeight = 180.dp
private val MediumChartHeight = 220.dp
private val ExpandedChartHeight = 240.dp

private const val DARK_SURFACE_LUMINANCE = 0.5f
private const val FULL_TURN = 360f
private const val SECOND_HUE_SHIFT = 145f
private const val THIRD_HUE_SHIFT = 250f
private const val FOURTH_HUE_SHIFT = 75f
private const val FIFTH_HUE_SHIFT = 310f
private const val LIGHT_SERIES_L = 0.52f
private const val LIGHT_SERIES_C = 0.14f
private const val DARK_SERIES_L = 0.80f
private const val DARK_SERIES_C = 0.12f
private const val PACE_LINE_ALPHA = 0.75f
private const val GRID_ALPHA = 0.35f
private const val XRULE_ALPHA = 0.65f
private const val LIGHT_BAND_ALPHA = 0.06f
private const val DARK_BAND_ALPHA = 0.08f
private const val FOCUS_RULE_ALPHA = 0.6f
private const val FOCUS_DIM_ALPHA = 0.35f
private const val TOOLTIP_BODY_ALPHA = 0.9f
