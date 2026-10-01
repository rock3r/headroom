package dev.sebastiano.headroom.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.ui.settings.SETTINGS_LIST_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings opens in about 450 ms and closes in about 400, by its button or by the back gesture. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SettingsRevealTimingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun showApp() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph = testGraph(rule.activity, settings = InMemorySettingsRepository())
                )
            }
        }
    }

    private fun openSettings() {
        showApp()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    @Test
    fun `opening takes about 450 ms`() {
        showApp()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Settings").performClick()

        // The list scrolls once the reveal has ended. A few frames go to the tap and the layout.
        advanceFrames(440)
        rule.onNodeWithTag(SETTINGS_LIST_TAG).assert(!canScroll)
        advanceFrames(100)
        rule.onNodeWithTag(SETTINGS_LIST_TAG).assert(canScroll)
    }

    @Test
    fun `closing with the button takes about 400 ms`() {
        openSettings()
        rule.onNodeWithContentDescription("Close settings").performClick()

        advanceFrames(380)
        rule.onNodeWithTag(SETTINGS_TAG).assertExists()
        advanceFrames(100)
        rule.onNodeWithTag(SETTINGS_TAG).assertDoesNotExist()
    }

    @Test
    fun `once the back gesture lets go, the rest plays at the closing pace`() {
        openSettings()
        val back = rule.activity.onBackPressedDispatcher
        rule.runOnUiThread { back.dispatchOnBackStarted(backEvent(0f)) }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnUiThread { back.dispatchOnBackProgressed(backEvent(EARLY_PROGRESS)) }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnUiThread { back.onBackPressed() }

        // Following the finger, the rest of a short gesture would play out in a linear rush.
        advanceFrames(350)
        rule.onNodeWithTag(SETTINGS_TAG).assertExists()
        advanceFrames(150)
        rule.onNodeWithTag(SETTINGS_TAG).assertDoesNotExist()
    }

    /** Steps frame by frame, so the page sees each frame as a device would. */
    private fun advanceFrames(millis: Long) {
        val start = rule.mainClock.currentTime
        while (rule.mainClock.currentTime - start < millis) rule.mainClock.advanceTimeByFrame()
    }

    private fun backEvent(progress: Float) =
        BackEventCompat(progress * 100f, 0f, progress, BackEventCompat.EDGE_LEFT)

    private val canScroll = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)

    private companion object {
        const val EARLY_PROGRESS = 0.1f
    }
}
