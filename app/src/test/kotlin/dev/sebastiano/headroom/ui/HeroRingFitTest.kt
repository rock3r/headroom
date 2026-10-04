package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.ui.overview.accountCardTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The detail screen's hero ring keeps its number inside the inner ring, also with three digits:
 * "100%" in the hero's large style is wider than the room the session ring leaves.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class HeroRingFitTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a full quota's number fits inside the inner ring`() {
        val accounts =
            DemoData.accounts(FIXED_NOW).map { state ->
                if (state.account.id != "demo-claude") state
                else
                    state.copy(
                        snapshot =
                            state.snapshot?.let { snapshot ->
                                snapshot.copy(
                                    windows =
                                        snapshot.windows.map {
                                            if (it.id == "seven_day") it.copy(usedPercent = 100.0)
                                            else it
                                        }
                                )
                            }
                    )
            }
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(graph = testGraph(rule.activity, realAccounts = accounts))
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.waitForIdle()
        // Let the ring and its number finish counting up.
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()

        val number = rule.onAllNodesWithText("100%")[0].getUnclippedBoundsInRoot()
        // The 176 dp ring with its session ring inside leaves a circle 128 dp across. At the edges
        // of the number, 30 dp above and below the middle, the circle is 113 dp wide.
        assertTrue("The number is ${number.width} wide", number.width <= 112.dp)
    }
}
