package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import kotlin.time.Duration.Companion.days
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ResetMomentTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a weekly reset seen on the overview says just reset`() {
        val demo = FakeQuotaRepository({ FIXED_NOW })
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(graph = testGraph(rule.activity, demo = demo))
            }
        }
        val grok = accountCardTag("demo-grok")
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).performScrollToNode(hasTestTag(grok))
        rule.onNodeWithText("On pace", useUnmergedTree = true).assertExists()

        demo.set(
            demo.accounts.value.map { state ->
                if (state.account.id != "demo-grok") return@map state
                val snapshot = requireNotNull(state.snapshot)
                state.copy(
                    snapshot =
                        snapshot.copy(
                            windows =
                                snapshot.windows.map {
                                    it.copy(
                                        usedPercent = 0.0,
                                        resetsAt = it.resetsAt?.plus(7.days),
                                    )
                                }
                        )
                )
            }
        )
        rule.waitForIdle()
        rule.onNodeWithText("Just reset", useUnmergedTree = true).assertExists()
    }
}
