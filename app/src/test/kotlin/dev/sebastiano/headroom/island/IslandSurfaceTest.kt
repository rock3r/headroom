package dev.sebastiano.headroom.island

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.IntOffset
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Provider
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** How the island's surface grows, holds and shrinks, with the clock in the test's hands. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class IslandSurfaceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val geometry = phoneGeometry()

    private var stage by mutableStateOf(IslandStage.Collapsed)
    private var finished = 0

    private fun show(reduceMotion: Boolean = false, host: Boolean = false) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HeadroomTheme(darkTheme = true, dynamicColor = false, reduceMotion = reduceMotion) {
                WindowFrame(geometry) {
                    if (host) {
                        ResetIslandHost(
                            request = demoRequest(),
                            geometry = geometry,
                            onFinish = { finished++ },
                        )
                    } else {
                        ResetIslandSurface(geometry, stage, demoRequest())
                    }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun surfaceWidthPx(): Int = surfaceSizePx().first

    private fun surfaceSizePx(): Pair<Int, Int> {
        val size = rule.onNodeWithTag(ISLAND_SURFACE_TAG).fetchSemanticsNode().size
        return Pair(size.width, size.height)
    }

    private fun goTo(next: IslandStage) {
        rule.runOnIdle { stage = next }
        rule.waitForIdle()
        // The change is recomposed on the next frame, and the animation starts on the one after.
        rule.mainClock.advanceTimeByFrame()
    }

    /** Plays [millis] frame by frame: a spring starts from the time of the first frame it sees. */
    private fun play(millis: Long) =
        repeat((millis / FRAME_MILLIS).toInt()) { rule.mainClock.advanceTimeBy(FRAME_MILLIS) }

    private fun settle() = play(SETTLE_MILLIS)

    @Test
    fun `collapsed, the surface is the size of the camera hole`() {
        show()
        settle()
        val (width, height) = surfaceSizePx()
        assertEquals(geometry.collapsed.width, width, 1)
        assertEquals(geometry.collapsed.height, height, 1)
    }

    @Test
    fun `once open, the surface grows to the size of the pill`() {
        show()
        goTo(IslandStage.Showing)
        settle()
        val (width, height) = surfaceSizePx()
        assertEquals(geometry.expanded.width, width, 1)
        assertEquals(geometry.expanded.height, height, 1)
    }

    @Test
    fun `while it grows, the surface is between the hole and the pill`() {
        show()
        goTo(IslandStage.Growing)
        play(MID_GROW_MILLIS)
        val width = surfaceWidthPx()
        assertTrue(width > geometry.collapsed.width, "grew from the hole: $width")
        assertTrue(width < geometry.expanded.width, "not there yet: $width")
    }

    @Test
    fun `back to collapsed, the surface shrinks into the hole again`() {
        show()
        goTo(IslandStage.Showing)
        settle()
        goTo(IslandStage.Collapsed)
        settle()
        assertEquals(geometry.collapsed.width, surfaceWidthPx(), 1)
    }

    @Test
    fun `once it shows, the pill says what reset, and the ring is followed by the word reset`() {
        show()
        goTo(IslandStage.Showing)
        settle()
        rule.onNodeWithContentDescription(MESSAGE).assertExists()
        rule.onNodeWithText("reset").assertExists()
    }

    @Test
    fun `with motion reduced the pill does not grow, it is at full size from the start`() {
        show(reduceMotion = true)
        goTo(IslandStage.Growing)
        play(FRAME_MILLIS)
        assertEquals(geometry.expanded.width, surfaceWidthPx(), 1)
    }

    @Test
    fun `the host plays grow, hold and shrink, then says it is done`() {
        show(host = true)
        rule.mainClock.advanceTimeBy(IslandTiming.GROW + IslandTiming.HOLD)
        assertEquals(0, finished, "still holding")
        rule.mainClock.advanceTimeBy(IslandTiming.FADE_OUT + IslandTiming.SHRINK + SETTLE_MILLIS)
        assertEquals(1, finished)
    }

    @Test
    fun `the whole island is on screen for about four seconds`() {
        val total =
            IslandTiming.GROW + IslandTiming.HOLD + IslandTiming.FADE_OUT + IslandTiming.SHRINK
        assertTrue(total in 3_800L..4_500L, "on screen for $total ms")
        assertTrue(IslandTiming.RING_FILL < IslandTiming.HOLD, "the ring fills while it holds")
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) {
        assertTrue(
            kotlin.math.abs(expected - actual) <= tolerance,
            "expected $expected (+-$tolerance) but was $actual",
        )
    }

    private companion object {
        const val MESSAGE = "Claude weekly limit reset"
        const val SETTLE_MILLIS = 1_500L
        const val MID_GROW_MILLIS = 96L
        const val FRAME_MILLIS = 32L
    }
}

/** A phone 411 dp wide at 3 px per dp, with a camera hole in the top middle. */
/** The Try button's island: Claude, 85% used before the reset, so the ring fills up from 15%. */
internal fun demoRequest(badge: String? = null, serial: Long = 1): IslandRequest =
    IslandRequest(
        provider = Provider.Claude,
        badge = badge,
        leftBefore = 0.15f,
        description = "Claude weekly limit reset",
        serial = serial,
    )

internal fun phoneGeometry(): IslandGeometry =
    islandGeometry(
        cutout = PxRect(left = 586, top = 36, right = 646, bottom = 96),
        screenWidth = 1233,
        screenHeight = 2673,
        density = 3f,
    )

/** Lays the island's window out where it would be on the screen. */
@Composable
internal fun WindowFrame(geometry: IslandGeometry, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val size = with(density) { geometry.window.width.toDp() to geometry.window.height.toDp() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.offset { IntOffset(geometry.window.left, geometry.window.top) }
                .size(size.first, size.second)
        ) {
            content()
        }
    }
}
