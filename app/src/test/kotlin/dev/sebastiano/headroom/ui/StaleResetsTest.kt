package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.DemoData
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class StaleResetsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a reset that has already passed is not listed as upcoming`() {
        // A snapshot from 16 hours ago: Grok's weekly reset (15h 28m after it) has passed.
        val stale = DemoData.accounts(FIXED_NOW.minus(Duration.ofHours(16)))
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(graph = testGraph(rule.activity, realAccounts = stale))
            }
        }
        rule.onNodeWithContentDescription("Resets").performClick()
        rule.onNodeWithText("Alerts are on for 3 of 4 windows").assertIsDisplayed()
        rule.onAllNodesWithText("Grok · Weekly credits").assertCountEquals(0)
    }
}
