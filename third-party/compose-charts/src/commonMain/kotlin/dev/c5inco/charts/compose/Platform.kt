package dev.c5inco.charts.compose

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle

/**
 * Measures the height of a single-line label's glyph ink, which is shorter than its line box (no
 * ascent or descent padding). Each [TextCache] owns one; main thread only.
 */
internal fun interface InkMeter {
    /** The ink height of [text] laid out as [layout] in [style], at most the line box's height. */
    fun height(text: String, style: TextStyle, layout: TextLayoutResult): Float
}

/** The platform's glyph-bounds measurer: `Paint.getTextBounds` on Android, Skia on desktop. */
internal expect fun InkMeter(): InkMeter

/** Opens a system trace section (`android.os.Trace` on Android; nothing on desktop). */
internal expect fun beginTraceSection(name: String)

/** Closes the section [beginTraceSection] opened on this thread. */
internal expect fun endTraceSection()

/**
 * Whether an offscreen graphics layer keeps its pixels between frames until its content changes:
 * Android's renderer does (a hardware layer); desktop Skia paints it again on every draw.
 */
internal expect val layerCachesPixels: Boolean

/**
 * Writes the outline of [path] stroked with [stroke] (its path effect applied) into [out] and
 * returns true, or returns false to leave the stroke to the renderer. Main thread only.
 *
 * On Android this is Skia's stroker at resolution scale 1, the call HWUI's GPU backend makes on the
 * RenderThread, on every frame, before rasterising a wide stroke it cannot draw on the GPU; filling
 * the outline paints the same pixels. Desktop leaves strokes to Skia.
 */
internal expect fun strokeOutline(path: Path, stroke: Stroke, out: Path): Boolean

/**
 * Whether dense lines in a viewport's scenes are outlined while recording and drawn as fills
 * ([strokeOutline], and for solid lines of straight segments [RoundStrokeOutline]): on Android,
 * whose renderer strokes and fills them in software on every frame. Desktop leaves every stroke to
 * Skia.
 */
internal expect val outlinesDenseLines: Boolean
