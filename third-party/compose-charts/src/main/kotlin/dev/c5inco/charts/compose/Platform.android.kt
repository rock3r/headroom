package dev.c5inco.charts.compose

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asAndroidPathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.resolveDefaults
import androidx.compose.ui.unit.sp
import kotlin.math.min

internal actual fun InkMeter(): InkMeter {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val bounds = Rect()
    return InkMeter { text, style, layout ->
        val box = layout.size.height.toFloat()
        if (text.isEmpty() || box <= 0f) return@InkMeter box
        val input = layout.layoutInput
        val resolved = resolveDefaults(style, input.layoutDirection)
        paint.typeface = input.fontFamilyResolver.resolve(
            resolved.fontFamily,
            resolved.fontWeight ?: FontWeight.Normal,
            resolved.fontStyle ?: FontStyle.Normal,
            resolved.fontSynthesis ?: FontSynthesis.All,
        ).value as? Typeface
        paint.textSize = with(input.density) { if (resolved.fontSize.isSp) resolved.fontSize.toPx() else 14.sp.toPx() }
        paint.getTextBounds(text, 0, text.length, bounds)
        val ink = bounds.height().toFloat()
        if (ink > 0f) min(ink, box) else box
    }
}

// ChartScenes.trace closes this section in finally; Lint cannot see that pairing.
@android.annotation.SuppressLint("UnclosedTrace")
internal actual fun beginTraceSection(name: String) = android.os.Trace.beginSection(name)

internal actual fun endTraceSection() = android.os.Trace.endSection()

// A compositing layer is a hardware layer: HWUI redraws its pixels only when its content changes.
internal actual val layerCachesPixels: Boolean = true

internal actual val outlinesDenseLines: Boolean = true

// Drawing is main-thread only, so one paint serves every chart.
private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

internal actual fun strokeOutline(path: Path, stroke: Stroke, out: Path): Boolean {
    val paint = outlinePaint
    paint.strokeWidth = stroke.width
    paint.strokeMiter = stroke.miter
    paint.strokeCap = when (stroke.cap) {
        StrokeCap.Round -> Paint.Cap.ROUND
        StrokeCap.Square -> Paint.Cap.SQUARE
        else -> Paint.Cap.BUTT
    }
    paint.strokeJoin = when (stroke.join) {
        StrokeJoin.Round -> Paint.Join.ROUND
        StrokeJoin.Bevel -> Paint.Join.BEVEL
        else -> Paint.Join.MITER
    }
    paint.pathEffect = stroke.pathEffect?.asAndroidPathEffect()
    out.rewind()
    // False only for a hairline, which is drawn as a stroke after all.
    return paint.getFillPath(path.asAndroidPath(), out.asAndroidPath())
}
