package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ComposeUiTestConfig
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.designsystem.LiquidRippleActiveKey
import dev.sebastiano.headroom.ui.overview.NEXT_RESET_ALERT_TAG
import dev.sebastiano.headroom.ui.overview.NEXT_RESET_CARD_TAG
import dev.sebastiano.headroom.ui.overview.NextResetDecorationDriftKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val rippling = SemanticsMatcher.expectValue(LiquidRippleActiveKey, true)
private val notRippling = SemanticsMatcher.expectValue(LiquidRippleActiveKey, false)
private val noRipple = SemanticsMatcher.keyNotDefined(LiquidRippleActiveKey)

private fun drifting(drifts: Boolean) =
    SemanticsMatcher.expectValue(NextResetDecorationDriftKey, drifts)

private fun AndroidComposeTestRule<*, ComponentActivity>.launchOverview(
    reduceMotion: Boolean = false
) {
    setContent {
        HeadroomTheme(dynamicColor = false, reduceMotion = reduceMotion) {
            HeadroomApp(graph = testGraph(activity))
        }
    }
}

/** Taps the card's background, in the top-end corner, clear of the text and the buttons. */
private fun AndroidComposeTestRule<*, *>.tapCardBackground() {
    onNodeWithTag(NEXT_RESET_CARD_TAG).performTouchInput { click(percentOffset(0.92f, 0.1f)) }
}

/**
 * The next reset card's delight: its shape tilts and drifts slowly, and a tap on the card sends a
 * liquid ripple across it. Drawn natively, so the ripple's shader is compiled and applied.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class NextResetCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the shape drifts`() {
        rule.launchOverview()
        rule.onNodeWithTag(NEXT_RESET_CARD_TAG).assert(drifting(true))
    }

    @Test
    fun `a tap on the card starts a ripple that settles`() {
        rule.launchOverview()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(NEXT_RESET_CARD_TAG).assert(notRippling)
        rule.tapCardBackground()
        repeat(FRAMES_TO_MIDWAY) { rule.mainClock.advanceTimeByFrame() }
        rule.onNodeWithTag(NEXT_RESET_CARD_TAG).assert(rippling)
        // Draw the card mid-ripple: the shader must compile and apply without crashing.
        rule.onRoot().captureToImage()
        rule.mainClock.advanceTimeBy(SETTLED_MILLIS)
        rule.onNodeWithTag(NEXT_RESET_CARD_TAG).assert(notRippling)
    }

    @Test
    fun `the alert button still toggles, without a ripple`() {
        rule.launchOverview()
        rule.onNodeWithTag(NEXT_RESET_ALERT_TAG).assertIsOn().performClick()
        rule.onNodeWithTag(NEXT_RESET_ALERT_TAG).assertIsOff()
        rule.onNodeWithTag(NEXT_RESET_CARD_TAG).assert(notRippling)
    }
}

/** With animations off on the device, the shape rests at its tilt and a tap does not ripple. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class NextResetCardWithoutAnimationsTest {
    @get:Rule
    val rule =
        createAndroidComposeRule<ComponentActivity>(ComposeUiTestConfig(effectContext = ZeroMotion))

    @Test
    fun `the shape is still and a tap does not ripple`() {
        rule.launchOverview()
        rule.onNodeWithTag(NEXT_RESET_CARD_TAG).assert(drifting(false)).assert(noRipple)
        rule.tapCardBackground()
        rule.onNodeWithTag(NEXT_RESET_CARD_TAG).assert(noRipple)
        rule.onNodeWithTag(NEXT_RESET_ALERT_TAG).assertIsOn().performClick()
        rule.onNodeWithTag(NEXT_RESET_ALERT_TAG).assertIsOff()
    }
}

/** Reduce motion in the app's settings does the same while the device still animates. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class NextResetCardReducedMotionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the shape is still and a tap does not ripple`() {
        rule.launchOverview(reduceMotion = true)
        rule.onNodeWithTag(NEXT_RESET_CARD_TAG).assert(drifting(false)).assert(noRipple)
        rule.tapCardBackground()
        rule.onNodeWithTag(NEXT_RESET_CARD_TAG).assert(noRipple)
        rule.onNodeWithTag(NEXT_RESET_ALERT_TAG).assertIsOn().performClick()
        rule.onNodeWithTag(NEXT_RESET_ALERT_TAG).assertIsOff()
    }
}

private const val FRAMES_TO_MIDWAY = 25
private const val SETTLED_MILLIS = 1_000L
