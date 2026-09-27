package dev.sebastiano.headroom.widget.render

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.capture.createCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.createProfile
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.createBitmap
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GaugeBarDrawingTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `a nearly full bar draws no track past its end`() = runTest {
        val pixels = drawBar(fraction = 0.99f, width = BAR_WIDTH, height = BAR_HEIGHT)

        assertEquals(0, pixels.count { it.isTrack() })
        assertTrue(pixels.any { it.isActive() })
    }

    @Test
    fun `a narrow bar draws no track when the gap leaves no room`() = runTest {
        val pixels = drawBar(fraction = 0.9f, width = NARROW_WIDTH, height = BAR_HEIGHT)

        assertEquals(0, pixels.count { it.isTrack() })
    }

    @Test
    fun `a half full bar still draws its track`() = runTest {
        val pixels = drawBar(fraction = 0.5f, width = BAR_WIDTH, height = BAR_HEIGHT)

        assertTrue(pixels.count { it.isTrack() } > 0)
    }

    private suspend fun drawBar(fraction: Float, width: Int, height: Int): IntArray {
        val doc =
            captureSingleRemoteDocument(
                context,
                creationDisplayInfo =
                    createCreationDisplayInfo(context, Size(width.toFloat(), height.toFloat())),
                profile = createProfile(docApiLevel = 6),
            ) {
                RemoteCanvas(RemoteModifier.fillMaxSize()) {
                    drawGaugeBar(
                        fraction = fraction,
                        paceFraction = null,
                        active = Color.Red.rc,
                        track = Color.Green.rc,
                        tick = Color.Blue.rc,
                    )
                }
            }
        val view =
            RemoteViews(RemoteViews.DrawInstructions.Builder(listOf(doc.bytes)).build())
                .apply(context, FrameLayout(context))
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val bitmap = createBitmap(width, height)
        view.draw(Canvas(bitmap))
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels
    }

    private fun Int.isTrack() =
        AndroidColor.green(this) > STRONG &&
            AndroidColor.red(this) < WEAK &&
            AndroidColor.alpha(this) > STRONG

    private fun Int.isActive() =
        AndroidColor.red(this) > STRONG &&
            AndroidColor.green(this) < WEAK &&
            AndroidColor.alpha(this) > STRONG

    private companion object {
        const val BAR_WIDTH = 300
        const val NARROW_WIDTH = 60
        const val BAR_HEIGHT = 42
        const val STRONG = 150
        const val WEAK = 100
    }
}
