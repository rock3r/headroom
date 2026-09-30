package dev.sebastiano.headroom.ui

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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings' title grows out of the settings button, and its header scrolls with the list. The list
 * waits for the reveal to end before it scrolls, so the title never floats over the rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SettingsRevealScrollTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the list scrolls only once the reveal has ended`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph = testGraph(rule.activity, settings = InMemorySettingsRepository())
                )
            }
        }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.mainClock.advanceTimeBy(MID_REVEAL_MILLIS)
        rule.onNodeWithTag(SETTINGS_LIST_TAG).assert(!canScroll)

        rule.mainClock.advanceTimeBy(AFTER_REVEAL_MILLIS)
        rule.onNodeWithTag(SETTINGS_LIST_TAG).assert(canScroll)
    }

    @Test
    fun `the reveal is over within 400 ms`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph = testGraph(rule.activity, settings = InMemorySettingsRepository())
                )
            }
        }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Settings").performClick()
        // The list waits for the reveal, so a slow reveal holds up the whole page.
        rule.mainClock.advanceTimeBy(REVEAL_BUDGET_MILLIS)
        // The page learns that the reveal ended on the frame after its last one.
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(SETTINGS_LIST_TAG).assert(canScroll)
    }

    private val canScroll = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)

    private companion object {
        const val MID_REVEAL_MILLIS = 200L
        const val AFTER_REVEAL_MILLIS = 2_000L
        const val REVEAL_BUDGET_MILLIS = 400L
    }
}
