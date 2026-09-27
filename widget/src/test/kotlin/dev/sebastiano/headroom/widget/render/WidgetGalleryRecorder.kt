package dev.sebastiano.headroom.widget.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.createBitmap
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.widget.WidgetConfig
import dev.sebastiano.headroom.widget.WidgetHostCategory
import dev.sebastiano.headroom.widget.WidgetSize
import dev.sebastiano.headroom.widget.WidgetStyle
import dev.sebastiano.headroom.widget.WidgetUiState
import dev.sebastiano.headroom.widget.testing.RecordingHostApplication
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Records the widget pictures for the README by playing each widget in the Android 16 widget
 * player. It only runs when asked to: `./gradlew :widget:recordWidgetGallery`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = RecordingHostApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetGalleryRecorder {
    private val context = RuntimeEnvironment.getApplication()
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val accounts = DemoData.accounts(now)
    private val strings = WidgetStrings(context, ZoneOffset.UTC, Locale.US, is24Hour = true)

    @Test
    fun recordGallery() = runTest {
        val outDir = System.getProperty(OUTPUT_PROPERTY)
        assumeTrue("Set $OUTPUT_PROPERTY to record the gallery", !outDir.isNullOrBlank())
        val dir = File(checkNotNull(outDir)).apply { mkdirs() }
        shots.forEach { shot ->
            val state = WidgetUiState.from(accounts, shot.config, now, shot.size, shot.host)
            val document = WidgetRenderer.capture(context, state, APP_WIDGET_ID, shot.size, strings)
            val widget = WidgetRenderer.remoteViews(document).draw(shot.size)
            File(dir, "${shot.name}.png").outputStream().use {
                onWallpaper(widget).compress(Bitmap.CompressFormat.PNG, FULL_QUALITY, it)
            }
        }
    }

    private fun android.widget.RemoteViews.draw(size: WidgetSize): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = (size.widthDp * density).toInt()
        val height = (size.heightDp * density).toInt()
        val view = apply(context, FrameLayout(context))
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        return createBitmap(width, height).also { view.draw(Canvas(it)) }
    }

    /** Puts the widget on a soft wallpaper gradient, as it would sit on a home screen. */
    private fun onWallpaper(widget: Bitmap): Bitmap {
        val padding = (PADDING_DP * context.resources.displayMetrics.density).toInt()
        val out = createBitmap(widget.width + padding * 2, widget.height + padding * 2)
        val canvas = Canvas(out)
        val paint =
            Paint().apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        out.width.toFloat(),
                        out.height.toFloat(),
                        WALLPAPER_START,
                        WALLPAPER_END,
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
        canvas.drawBitmap(widget, padding.toFloat(), padding.toFloat(), null)
        return out
    }

    private data class Shot(
        val name: String,
        val config: WidgetConfig,
        val size: WidgetSize,
        val host: WidgetHostCategory = WidgetHostCategory.HomeScreen,
    )

    private companion object {
        const val OUTPUT_PROPERTY = "headroom.widgetGalleryDir"
        const val APP_WIDGET_ID = 1
        const val FULL_QUALITY = 100
        const val PADDING_DP = 20
        const val WALLPAPER_START = 0xFFB7C4F5.toInt()
        const val WALLPAPER_END = 0xFF6F8AD8.toInt()

        val shots =
            listOf(
                Shot(
                    "widget-rings",
                    WidgetConfig(WidgetStyle.Rings, listOf("demo-claude")),
                    WidgetSize(160f, 160f),
                ),
                Shot("widget-rings-grid", WidgetConfig(WidgetStyle.Rings), WidgetSize(160f, 160f)),
                Shot("widget-bars", WidgetConfig(WidgetStyle.Bars), WidgetSize(320f, 140f)),
                Shot("widget-shape", WidgetConfig(WidgetStyle.Shape), WidgetSize(160f, 160f)),
                Shot(
                    "widget-lock-screen",
                    WidgetConfig(WidgetStyle.Rings),
                    WidgetSize(320f, 140f),
                    WidgetHostCategory.Keyguard,
                ),
            )
    }
}
