package dev.sebastiano.headroom.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.ui.delights.CONFETTI_OVERLAY_TAG
import dev.sebastiano.headroom.ui.delights.Delights
import dev.sebastiano.headroom.ui.delights.DelightsHost
import dev.sebastiano.headroom.ui.delights.LocalDelights
import dev.sebastiano.headroom.ui.delights.SHIMMER_OVERLAY_TAG
import dev.sebastiano.headroom.ui.delights.delightAnchor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The refresh shimmer and the reset confetti play only when their switch is on and motion is not
 * reduced, and they never take a touch from the app underneath.
 */
@RunWith(RobolectricTestRunner::class)
class DelightsTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var delights: Delights
    private var clicks = 0
    private var buttonShown by mutableStateOf(true)

    private fun show(
        shimmer: Boolean = true,
        confetti: Boolean = true,
        reduceMotion: Boolean = false,
    ) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HeadroomTheme(dynamicColor = false, reduceMotion = reduceMotion) {
                DelightsHost(refreshShimmer = shimmer, resetConfetti = confetti) {
                    delights = requireNotNull(LocalDelights.current)
                    Box(Modifier.fillMaxSize()) {
                        if (buttonShown) {
                            Button(
                                onClick = { clicks++ },
                                modifier = Modifier.align(Alignment.Center).delightAnchor(ANCHOR),
                            ) {
                                Text("Tap")
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `the shimmer plays once over the app and then goes`() {
        show()
        rule.runOnIdle { delights.playShimmer() }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(SHIMMER_OVERLAY_TAG).assertExists()

        rule.mainClock.advanceTimeBy(SHIMMER_DONE_MILLIS)
        rule.onNodeWithTag(SHIMMER_OVERLAY_TAG).assertDoesNotExist()
    }

    @Test
    fun `the shimmer does not play when its switch is off`() {
        show(shimmer = false)
        rule.runOnIdle { delights.playShimmer() }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(SHIMMER_OVERLAY_TAG).assertDoesNotExist()
        rule.runOnIdle { assertFalse(delights.isShimmering) }
    }

    @Test
    fun `the shimmer does not play when motion is reduced`() {
        show(reduceMotion = true)
        rule.runOnIdle { delights.playShimmer() }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(SHIMMER_OVERLAY_TAG).assertDoesNotExist()
        rule.runOnIdle { assertFalse(delights.isShimmering) }
    }

    @Test
    fun `confetti bursts from its anchor and is gone after about two seconds`() {
        show()
        var done = false
        rule.runOnIdle { done = delights.burstFrom(listOf(ANCHOR), COLOURS) }
        rule.mainClock.advanceTimeByFrame()
        assertTrue(done)
        rule.onNodeWithTag(CONFETTI_OVERLAY_TAG).assertExists()

        rule.mainClock.advanceTimeBy(CONFETTI_DONE_MILLIS)
        rule.onNodeWithTag(CONFETTI_OVERLAY_TAG).assertDoesNotExist()
    }

    @Test
    fun `confetti waits while none of its anchors is on screen`() {
        show()
        var done = true
        rule.runOnIdle { done = delights.burstFrom(listOf("elsewhere"), COLOURS) }
        rule.mainClock.advanceTimeByFrame()
        assertFalse(done)
        rule.onNodeWithTag(CONFETTI_OVERLAY_TAG).assertDoesNotExist()
        rule.runOnIdle { assertTrue(delights.bursts.isEmpty()) }
    }

    @Test
    fun `confetti does not play when its switch is off, and the burst counts as done`() {
        show(confetti = false)
        var done = false
        rule.runOnIdle { done = delights.burstFrom(listOf(ANCHOR), COLOURS) }
        rule.mainClock.advanceTimeByFrame()
        assertTrue(done)
        rule.onNodeWithTag(CONFETTI_OVERLAY_TAG).assertDoesNotExist()
        rule.runOnIdle { assertTrue(delights.bursts.isEmpty()) }
    }

    @Test
    fun `confetti does not play when motion is reduced`() {
        show(reduceMotion = true)
        rule.runOnIdle { delights.burstFrom(listOf(ANCHOR), COLOURS) }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(CONFETTI_OVERLAY_TAG).assertDoesNotExist()
        rule.runOnIdle { assertTrue(delights.bursts.isEmpty()) }
    }

    @Test
    fun `taps reach the app while both effects play`() {
        show()
        rule.runOnIdle {
            delights.playShimmer()
            delights.burstFrom(listOf(ANCHOR), COLOURS)
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(SHIMMER_OVERLAY_TAG).assertExists()
        rule.onNodeWithTag(CONFETTI_OVERLAY_TAG).assertExists()

        rule.onNodeWithText("Tap").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `an anchor that leaves the screen is forgotten`() {
        show()
        rule.runOnIdle { assertTrue(delights.hasAnchor(ANCHOR)) }
        rule.mainClock.autoAdvance = true
        buttonShown = false
        rule.onNodeWithText("Tap").assertDoesNotExist()
        rule.runOnIdle { assertFalse(delights.hasAnchor(ANCHOR)) }
    }

    private companion object {
        const val ANCHOR = "card"
        val COLOURS = listOf(Color.Red, Color.Blue)
        const val SHIMMER_DONE_MILLIS = 1_900L
        const val CONFETTI_DONE_MILLIS = 2_300L
    }
}
