package dev.sebastiano.headroom.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidRippleTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var state: LiquidRippleState
    private var buttonClicks = 0

    private fun launch(enabled: Boolean = true) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            state = rememberLiquidRippleState()
            HeadroomTheme(dynamicColor = false) { RippleSurface(enabled) }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    @Composable
    private fun RippleSurface(enabled: Boolean) {
        Box(Modifier.size(300.dp, 200.dp).testTag(SURFACE).liquidRipple(state, enabled)) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(48.dp).testTag(BUTTON).clickable {
                    buttonClicks++
                }
            )
        }
    }

    private fun frames(count: Int) = repeat(count) { rule.mainClock.advanceTimeByFrame() }

    @Test
    fun `a tap on the surface starts a ripple that settles`() {
        launch()
        assertFalse(state.isActive)
        rule.onNodeWithTag(SURFACE).performTouchInput { click(Offset(40f, 40f)) }
        frames(2)
        assertTrue(state.isActive)
        rule.mainClock.advanceTimeBy(SETTLED_MILLIS)
        assertFalse(state.isActive)
    }

    @Test
    fun `the ripple draws while it runs`() {
        launch()
        rule.onNodeWithTag(SURFACE).performTouchInput { click(center) }
        frames(FRAMES_TO_MIDWAY)
        assertTrue(state.isActive)
        // Drawing compiles the shader and applies the effect. Robolectric may or may not render
        // it, but it must not crash.
        rule.onNodeWithTag(SURFACE).captureToImage()
    }

    @Test
    fun `a tap that a child handles clicks the child and does not ripple`() {
        launch()
        rule.onNodeWithTag(BUTTON).performClick()
        frames(2)
        assertEquals(1, buttonClicks)
        assertFalse(state.isActive)
    }

    @Test
    fun `a drag does not ripple`() {
        launch()
        rule.onNodeWithTag(SURFACE).performTouchInput { swipeDown() }
        frames(2)
        assertFalse(state.isActive)
    }

    @Test
    fun `a disabled ripple ignores taps`() {
        launch(enabled = false)
        rule.onNodeWithTag(SURFACE).performTouchInput { click(center) }
        frames(2)
        assertFalse(state.isActive)
    }

    private companion object {
        const val SURFACE = "surface"
        const val BUTTON = "button"
        const val SETTLED_MILLIS = 1_000L
        const val FRAMES_TO_MIDWAY = 25
    }
}
