package dev.sebastiano.headroom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * A theme change keeps the old frame on top and uncovers the new theme from where the user tapped,
 * or fades the old frame out when motion is reduced.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeRevealTest {
    @get:Rule val rule = createComposeRule()

    private var theme by mutableIntStateOf(0)
    private lateinit var reveal: ThemeReveal

    private fun show() {
        rule.setContent {
            ThemeRevealHost(themeKey = theme) {
                reveal = requireNotNull(LocalThemeReveal.current)
                Box(Modifier.fillMaxSize().background(if (theme == 0) Color.White else Color.Black))
            }
        }
        // Robolectric only draws for a capture; on a device every frame is drawn.
        rule.onRoot().captureToImage()
    }

    @Test
    fun `the change applies once, and the old frame covers the screen until the new theme shows`() {
        show()
        rule.mainClock.autoAdvance = false
        var changes = 0

        rule.runOnIdle {
            reveal.start(center = Offset(10f, 10f), animate = true) {
                changes++
                theme = 1
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        assertEquals(1, changes)
        assertTrue(reveal.isRunning)
        assertEquals(RevealStyle.Circle, reveal.style)

        rule.mainClock.advanceTimeBy(REVEAL_MILLIS)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertFalse(reveal.isRunning)
        assertEquals(1, changes)
    }

    @Test
    fun `with motion reduced the old frame fades instead`() {
        show()
        rule.mainClock.autoAdvance = false

        rule.runOnIdle { reveal.start(center = Offset.Zero, animate = false) { theme = 1 } }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        assertTrue(reveal.isRunning)
        assertEquals(RevealStyle.Fade, reveal.style)

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertFalse(reveal.isRunning)
    }

    @Test
    fun `a change that never shows does not leave the old frame on screen`() {
        show()

        rule.mainClock.autoAdvance = false
        rule.runOnIdle { reveal.start(center = Offset.Zero, animate = true) {} }
        rule.mainClock.advanceTimeByFrame()
        assertTrue(reveal.isRunning)

        rule.mainClock.advanceTimeBy(WAIT_MILLIS)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertFalse(reveal.isRunning)
    }

    private companion object {
        const val REVEAL_MILLIS = 2_000L
        const val WAIT_MILLIS = 1_500L
    }
}
