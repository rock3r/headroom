package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.ui.settings.REFRESH_SHIMMER_TAG
import dev.sebastiano.headroom.ui.settings.RESET_CONFETTI_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_LIST_TAG
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Delights section: two switches, on by default, and a note when motion is reduced. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class DelightsSettingsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val settings = InMemorySettingsRepository()

    private fun openDelights(reduceMotion: Boolean = false) {
        rule.setContent {
            HeadroomTheme(dynamicColor = false, reduceMotion = reduceMotion) {
                HeadroomApp(graph = testGraph(rule.activity, settings = settings))
            }
        }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag(RESET_CONFETTI_TAG))
    }

    @Test
    fun `both delights are listed and on by default`() {
        openDelights()
        rule.onNodeWithText("Delights").assertIsDisplayed()
        rule.onNodeWithTag(REFRESH_SHIMMER_TAG).assertIsDisplayed().assertIsOn()
        rule.onNodeWithTag(RESET_CONFETTI_TAG).assertIsDisplayed().assertIsOn()
        rule.onNodeWithText(DELIGHTS_REDUCED_NOTE).assertDoesNotExist()
    }

    @Test
    fun `turning a delight off stores it, and on again stores that too`() {
        openDelights()

        rule.onNodeWithTag(REFRESH_SHIMMER_TAG).performClick()
        rule.onNodeWithTag(REFRESH_SHIMMER_TAG).assertIsOff()
        assertFalse(settings.settings.value.refreshShimmer)
        assertTrue(settings.settings.value.resetConfetti)

        rule.onNodeWithTag(RESET_CONFETTI_TAG).performClick()
        rule.onNodeWithTag(RESET_CONFETTI_TAG).assertIsOff()
        assertFalse(settings.settings.value.resetConfetti)

        rule.onNodeWithTag(REFRESH_SHIMMER_TAG).performClick()
        rule.onNodeWithTag(REFRESH_SHIMMER_TAG).assertIsOn()
        assertTrue(settings.settings.value.refreshShimmer)
    }

    @Test
    fun `with motion reduced the section says the delights do not play`() {
        openDelights(reduceMotion = true)
        rule.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasText(DELIGHTS_REDUCED_NOTE))
        rule.onNodeWithText(DELIGHTS_REDUCED_NOTE).assertIsDisplayed()
    }
}

private const val DELIGHTS_REDUCED_NOTE = "Neither plays while motion is reduced."
