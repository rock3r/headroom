package dev.c5inco.charts.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.c5inco.charts.core.ChartMetrics
import dev.c5inco.charts.core.Curve
import kotlin.math.ceil

/**
 * Everything about how a chart looks. The scene says *what* and *where*; the style says *how*.
 * Swapping a style re-skins a chart without touching its data or layout code.
 *
 * Fields that move geometry (bar padding, gaps, radii) feed [metrics]; the rest are paint.
 */
@Immutable
data class ChartStyle(
    /** Nonempty series colours, indexed by a series' palette slot. */
    val palette: List<Color>,
    val curve: Curve = Curve.Monotone,
    val line: LineStyle = LineStyle(),
    val area: AreaStyle = AreaStyle(),
    val bar: BarStyle = BarStyle(),
    val dot: DotStyle = DotStyle(),
    val arc: ArcStyle = ArcStyle(),
    val grid: GridStyle,
    val axis: AxisStyle,
    val focus: FocusStyle,
    val tooltip: TooltipStyle,
    val candle: CandleStyle = CandleStyle(),
    /** Colours treemap tiles ([dev.c5inco.charts.core.RectKind.Tile]) by their raw value. */
    val diverging: DivergingColors = DivergingColors(),
    val treemap: TreemapStyle = TreemapStyle(),
    /** Colours heatmap cells ([dev.c5inco.charts.core.RectKind.Cell]) by their intensity. */
    val sequential: SequentialColors = SequentialColors(),
    val heatmap: HeatmapStyle = HeatmapStyle(),
) {
    init { require(palette.isNotEmpty()) { "ChartStyle.palette must contain at least one colour" } }

    fun color(slot: Int): Color = palette[Math.floorMod(slot, palette.size)]

    fun glyph(slot: Int): Glyph = dot.glyphs[Math.floorMod(slot, dot.glyphs.size)]

    fun metrics(density: Density): ChartMetrics = with(density) {
        val linePx = line.width.toPx()
        ChartMetrics(
            labelGap = axis.labelGap.toPx(),
            bandPadding = bar.bandPadding,
            bandOuterPadding = bar.bandPadding / 2f,
            groupPadding = bar.groupPadding,
            maxBarWidth = bar.maxWidth.toPx(),
            stackGap = bar.stackGap.toPx(),
            dotRadius = dot.radius.toPx(),
            markerRadius = line.markerRadius.toPx(),
            minLabelSpacing = axis.minLabelSpacing.toPx(),
            pieInnerRatio = arc.innerRadius,
            pieGap = arc.gap.toPx(),
            pieInset = arc.inset.toPx(),
            strokeInset = maxOf(linePx, area.edgeWidth.toPx(), candle.wickWidth.toPx(), grid.xRuleWidth.toPx()) / 2f,
            candlePadding = candle.bandPadding,
            volumeGap = candle.volumeGap.toPx(),
            tileGap = treemap.gap.toPx(),
            groupGap = treemap.groupGap.toPx(),
            tilePadding = treemap.padding.toPx(),
            cellGap = heatmap.gap.toPx(),
            // A finger's reach, not paint: a zero rose petal stays pressable at any density.
            petalMinHit = 24.dp.toPx(),
            lineDashOn = linePx * 3f,
            lineDashOff = linePx * 2f,
            lineDotSpacing = linePx * 2.5f,
        )
    }

    companion object
}

@Immutable
data class LineStyle(
    val width: Dp = 2.5.dp,
    val markerRadius: Dp = 4.dp,
    /**
     * Data markers: the dots a [dev.c5inco.charts.core.LineMark] asks for with `markers`. False
     * hides them even when the spec asks. Unrelated to [FocusStyle.showMarkers], the focus markers
     * drawn at the needle while scrubbing.
     */
    val showMarkers: Boolean = true,
)

@Immutable
sealed interface AreaFill {
    data class Solid(val alpha: Float) : AreaFill
    /** Fades from [topAlpha] at the top of the plot to [bottomAlpha] at the baseline. */
    data class Gradient(val topAlpha: Float, val bottomAlpha: Float) : AreaFill
}

@Immutable
data class AreaStyle(
    val fill: AreaFill = AreaFill.Gradient(0.28f, 0.02f),
    /** Width of the stroke along the top of each area; zero hides it. */
    val edgeWidth: Dp = 2.dp,
)

@Immutable
sealed interface BarCorners {
    data object Square : BarCorners
    /** Rounds the end away from the baseline, on the outermost segment of a stack. */
    data class End(val radius: Dp) : BarCorners
    /** Fully rounded on every segment: pills. */
    data object Pill : BarCorners
}

@Immutable
data class BarStyle(
    val corners: BarCorners = BarCorners.End(6.dp),
    /** A full-height backdrop behind each bar, showing the scale's extent. */
    val track: Color? = null,
    val stackGap: Dp = 2.dp,
    val bandPadding: Float = 0.35f,
    val groupPadding: Float = 0.15f,
    val maxWidth: Dp = 28.dp,
)

/**
 * Candles and their volume bars. Up and down are paint, so a style may use any pair; direction also
 * reaches the scene as a flag and the body's open edge, and [hollowUp] gives it a second channel
 * beside colour.
 */
@Immutable
data class CandleStyle(
    val up: Color = Color(0xFF26A69A),
    val down: Color = Color(0xFFEF5350),
    val wickWidth: Dp = 1.dp,
    val bodyCorner: Dp = 1.dp,
    /** Up bodies drawn as outlines over the surface ([FocusStyle.halo]): the hollow-candle look. */
    val hollowUp: Boolean = false,
    /** Volume bars take their candle's colour at this alpha, so they read as context. */
    val volumeAlpha: Float = 0.35f,
    /** Fraction of each slot left empty between candles (bars' padding wins when a chart has both). */
    val bandPadding: Float = 0.3f,
    /** Space between the price area and the volume strip. */
    val volumeGap: Dp = 4.dp,
) {
    fun color(down: Boolean): Color = if (down) this.down else up
}

/**
 * A diverging colour scale for values with a meaningful zero (today's change, profit and loss):
 * [neutral] at zero, mixing linearly toward [negative] and [positive] until minus and plus
 * [domain], and clamped beyond (infinities included). NaN, meaning no value, is [neutral].
 * A market heatmap in percent with `domain = 3.0` saturates at ±3%.
 */
@Immutable
data class DivergingColors(
    val negative: Color = Color(0xFFE5484D),
    val neutral: Color = Color(0xFF8B8D98),
    val positive: Color = Color(0xFF30A46C),
    val domain: Double = 3.0,
) {
    init {
        require(domain > 0.0 && domain.isFinite()) { "DivergingColors.domain must be positive and finite, was $domain" }
    }

    fun color(value: Double): Color {
        if (value.isNaN()) return neutral
        val t = (value / domain).coerceIn(-1.0, 1.0).toFloat()
        return if (t < 0f) lerp(neutral, negative, -t) else lerp(neutral, positive, t)
    }
}

/**
 * Treemap tiles and their text. Tiles take their colour from [ChartStyle.diverging]; [gap],
 * [groupGap] and [padding] move geometry and feed [ChartStyle.metrics].
 */
@Immutable
data class TreemapStyle(
    val corner: Dp = 4.dp,
    /** Gap between neighbouring tiles. */
    val gap: Dp = 2.dp,
    /** Gap between groups. */
    val groupGap: Dp = 6.dp,
    /** Space between a tile's edge and its text; also around a group's header label. */
    val padding: Dp = 4.dp,
    val label: TextStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    val detail: TextStyle = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp),
    val groupLabel: TextStyle = TextStyle(color = Color(0xFF6B7A90), fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

/**
 * A sequential colour scale for magnitudes (a heatmap cell's intensity, 0 to 1): [colors] from the
 * lowest value to the highest, evenly spaced stops mixed linearly in between. Intensity is clamped
 * to 0..1; NaN, which has no position, takes the lowest colour (heatmaps paint missing cells with
 * [HeatmapStyle.empty] and never ask).
 *
 * [steps] above zero quantizes the scale into that many levels, a contribution graph's look: level
 * 0 is exactly intensity 0 (a real zero), and any positive intensity takes one of the levels
 * 1 until [steps] - 1, so the smallest value above zero never reads as zero. Each level is the ramp's
 * colour at that level's fraction.
 */
@Immutable
data class SequentialColors(
    val colors: List<Color> = listOf(Color(0xFFD3EEEA), Color(0xFF14A39A), Color(0xFF0B5550)),
    val steps: Int = 0,
) {
    init {
        require(colors.isNotEmpty()) { "SequentialColors needs at least one colour" }
        require(steps == 0 || steps >= 2) { "SequentialColors.steps must be 0 (continuous) or at least 2, was $steps" }
    }

    fun color(intensity: Float): Color {
        var t = if (intensity.isNaN()) 0f else intensity.coerceIn(0f, 1f)
        if (steps > 0 && t > 0f) t = ceil(t * (steps - 1)).coerceIn(1f, (steps - 1).toFloat()) / (steps - 1)
        if (colors.size == 1) return colors[0]
        val x = t * (colors.size - 1)
        val i = x.toInt().coerceAtMost(colors.size - 2)
        return lerp(colors[i], colors[i + 1], x - i)
    }
}

/**
 * Heatmap cells. [empty] fills a cell with no data (a day still to come), so missing never looks
 * like zero, which takes the lowest colour of [ChartStyle.sequential]. [gap] moves geometry and
 * feeds [ChartStyle.metrics].
 */
@Immutable
data class HeatmapStyle(
    val empty: Color = Color(0x0F000000),
    val corner: Dp = 3.dp,
    /** Gap between neighbouring cells; shrinks to a quarter of a cell's step when cells are tiny. */
    val gap: Dp = 3.dp,
)

@Immutable
data class DotStyle(
    val radius: Dp = 4.5.dp,
    /** Nonempty glyphs cycled per series slot. Shape is a second channel beside colour. */
    val glyphs: List<Glyph> = listOf(Glyph.Circle),
    /** A ring drawn behind each dot to separate overlapping points. */
    val outline: Color? = null,
    val outlineWidth: Dp = 1.5.dp,
    val alpha: Float = 0.9f,
) {
    init { require(glyphs.isNotEmpty()) { "DotStyle.glyphs must contain at least one glyph" } }
}

enum class ArcCap { Butt, Round }

@Immutable
data class ArcStyle(
    /** Inner radius as a fraction of the outer. Zero is a pie. */
    val innerRadius: Float = 0.62f,
    val gap: Dp = 2.dp,
    val cap: ArcCap = ArcCap.Butt,
    val inset: Dp = 4.dp,
)

/** How a rule is stroked. */
@Immutable
sealed interface StrokePattern {
    data object Solid : StrokePattern

    data class Dashed(val on: Dp, val off: Dp) : StrokePattern

    /** Round dots as wide as the stroke, [spacing] apart centre to centre. */
    data class Dotted(val spacing: Dp) : StrokePattern
}

@Immutable
data class GridStyle(
    val color: Color,
    val width: Dp = 1.dp,
    /** On/off lengths for a dashed grid. Null is solid; a near-zero "on" with a round cap gives dots. */
    val dash: Pair<Dp, Dp>? = null,
    val roundCaps: Boolean = false,
    val horizontal: Boolean = true,
    val vertical: Boolean = false,
    val axisLines: Color? = null,
    /** The zero line under bars and areas. */
    val baseline: Color? = null,
    val reference: Color,
    /** A chip behind reference labels so they stay legible over marks. */
    val referenceChip: Color? = null,
    val referenceWidth: Dp = 1.5.dp,
    /** Dashed by default. A previous-close line usually reads better [StrokePattern.Dotted] and thin. */
    val referencePattern: StrokePattern = StrokePattern.Dashed(6.dp, 4.dp),
    /** Vertical reference lines on the x axis ([dev.c5inco.charts.core.XRuleMark]). */
    val xRule: Color = reference,
    val xRuleWidth: Dp = 1.dp,
    val xRulePattern: StrokePattern = StrokePattern.Dashed(4.dp, 4.dp),
    /** Shaded vertical bands on the x axis ([dev.c5inco.charts.core.XBandMark]). */
    val band: Color = reference.copy(alpha = 0.1f),
)

@Immutable
data class AxisStyle(
    val labels: TextStyle,
    val referenceLabel: TextStyle = labels,
    val labelGap: Dp = 8.dp,
    val minLabelSpacing: Dp = 12.dp,
)

@Immutable
data class FocusStyle(
    val rule: Color,
    val ruleWidth: Dp = 1.dp,
    /** Ring around focused points, usually the surface colour. */
    val halo: Color,
    val markerRadius: Dp = 5.dp,
    /** Alpha applied to marks outside the focus. 1 disables dimming. */
    val dimAlpha: Float = 0.35f,
    /** How far a focused slice pops out. */
    val arcExpand: Dp = 6.dp,
    /**
     * While scrubbing, lines, areas and their markers to the right of the needle recede by
     * [dimAlpha] and the rest stay whole: the past is solid, the rest of the range is context.
     */
    val dimTrail: Boolean = false,
    /** The vertical needle while scrubbing. */
    val showRule: Boolean = true,
    /**
     * Focus markers: the haloed dots drawn on each line or area at the needle while scrubbing.
     * Unrelated to [LineStyle.showMarkers], which governs the data markers a spec asks for.
     */
    val showMarkers: Boolean = true,
)

@Immutable
data class TooltipStyle(
    val container: Color,
    val title: TextStyle,
    val body: TextStyle,
    val cornerRadius: Dp = 10.dp,
    val swatch: Dp = 8.dp,
    val border: Color? = null,
    /**
     * How far above a touch pointer an above-placed tooltip sits so the fingertip does not cover
     * it. A mouse or stylus still uses the small gap from the mark.
     */
    val fingerClearance: Dp = 48.dp,
)

/** A point shape. Implementations draw centred on [center] within [radius]. */
@Immutable
fun interface Glyph {
    fun DrawScope.draw(center: Offset, radius: Float, color: Color, alpha: Float)

    companion object {
        val Circle = Glyph { center, radius, color, alpha -> drawCircle(color, radius, center, alpha) }

        val Square = Glyph { center, radius, color, alpha ->
            drawRect(color, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f), alpha)
        }

        /** Draws [unitPath], a path laid out in the unit square, scaled to fill the radius. */
        fun path(unitPath: Path): Glyph = Glyph { center, radius, color, alpha ->
            withTransform({
                translate(center.x - radius, center.y - radius)
                scale(radius * 2f, radius * 2f, Offset.Zero)
            }) { drawPath(unitPath, color, alpha) }
        }
    }
}

/** A palette tuned for legibility on light and dark surfaces. */
val CleanPalette = listOf(
    Color(0xFF4F5BD5), Color(0xFF14A39A), Color(0xFFF2A93B), Color(0xFFE5546B), Color(0xFF8B6FE0), Color(0xFF6B7A90),
)

/** d3's `schemeCategory10`, the de-facto default of web charting. */
val Category10 = listOf(
    Color(0xFF1F77B4), Color(0xFFFF7F0E), Color(0xFF2CA02C), Color(0xFFD62728), Color(0xFF9467BD), Color(0xFF8C564B),
)

/**
 * The library default: quiet guides, no axis lines, smooth curves and rounded bar ends, so the
 * data carries the contrast.
 */
fun ChartStyle.Companion.clean(
    content: Color = Color(0xFF1B1B1F),
    surface: Color = Color.White,
    palette: List<Color> = CleanPalette,
) = ChartStyle(
    palette = palette,
    dot = DotStyle(outline = surface),
    grid = GridStyle(
        color = content.copy(alpha = 0.07f), baseline = content.copy(alpha = 0.22f), dash = 4.dp to 4.dp,
        reference = content.copy(alpha = 0.55f), referenceChip = surface,
        xRule = content.copy(alpha = 0.4f),
        band = content.copy(alpha = 0.05f),
    ),
    axis = AxisStyle(
        labels = TextStyle(color = content.copy(alpha = 0.55f), fontSize = 11.sp),
        referenceLabel = TextStyle(color = content.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Medium),
    ),
    focus = FocusStyle(rule = content.copy(alpha = 0.3f), halo = surface),
    treemap = TreemapStyle(groupLabel = TextStyle(color = content.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Medium)),
    // From a tint of the surface to deep teal, moving away from the surface in light and dark.
    sequential = SequentialColors(listOf(lerp(surface, CleanPalette[1], 0.22f), CleanPalette[1], lerp(CleanPalette[1], content, 0.45f))),
    heatmap = HeatmapStyle(empty = content.copy(alpha = 0.06f)),
    // Inverted against the surface, so it reads as a layer above the chart in light and dark.
    tooltip = TooltipStyle(
        container = content.copy(alpha = 0.94f),
        title = TextStyle(color = surface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        body = TextStyle(color = surface.copy(alpha = 0.85f), fontSize = 12.sp),
    ),
)

/**
 * What most charting libraries ship by default: full grid, axis lines, straight segments,
 * square bars and the category10 palette.
 */
fun ChartStyle.Companion.basic(
    content: Color = Color(0xFF1B1B1F),
    surface: Color = Color.White,
) = ChartStyle(
    palette = Category10,
    curve = Curve.Linear,
    line = LineStyle(width = 1.5.dp, showMarkers = false),
    area = AreaStyle(fill = AreaFill.Solid(0.55f), edgeWidth = 1.dp),
    bar = BarStyle(corners = BarCorners.Square, stackGap = 0.dp, bandPadding = 0.1f, groupPadding = 0f, maxWidth = 200.dp),
    dot = DotStyle(radius = 3.dp, alpha = 0.75f, glyphs = listOf(Glyph.Square)),
    arc = ArcStyle(innerRadius = 0.5f, gap = 1.dp, inset = 0.dp),
    grid = GridStyle(
        color = content.copy(alpha = 0.12f), vertical = true, axisLines = content.copy(alpha = 0.6f),
        reference = content.copy(alpha = 0.6f),
        xRule = content.copy(alpha = 0.5f),
        band = content.copy(alpha = 0.08f),
    ),
    axis = AxisStyle(labels = TextStyle(color = content.copy(alpha = 0.7f), fontSize = 10.sp), labelGap = 4.dp),
    focus = FocusStyle(rule = content.copy(alpha = 0.5f), halo = surface, dimAlpha = 1f, arcExpand = 0.dp, markerRadius = 3.5.dp),
    treemap = TreemapStyle(
        corner = 0.dp, gap = 1.dp, groupGap = 3.dp,
        label = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        detail = TextStyle(color = Color.White, fontSize = 10.sp),
        groupLabel = TextStyle(color = content, fontSize = 10.sp, fontWeight = FontWeight.Bold),
    ),
    // d3's sequential blues, square cells on hairline gaps.
    sequential = SequentialColors(listOf(lerp(surface, Category10[0], 0.15f), Category10[0], lerp(Category10[0], content, 0.5f))),
    heatmap = HeatmapStyle(empty = content.copy(alpha = 0.08f), corner = 0.dp, gap = 1.dp),
    tooltip = TooltipStyle(
        container = Color(0xF0FFFFFF),
        title = TextStyle(color = content, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        body = TextStyle(color = content, fontSize = 11.sp),
        cornerRadius = 2.dp,
        border = content.copy(alpha = 0.3f),
    ),
)
