package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.prototype.ResetScenarios
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import dev.sebastiano.headroom.ui.resets.RESETS_CARD_TAG
import dev.sebastiano.headroom.ui.resets.RESETS_TAG
import dev.sebastiano.headroom.ui.resets.useResetTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Z.AI resets can be used, and Z.AI can be asked for a reset card, also when the account holds no
 * reset at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ZAiResetsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var zAiPools = ResetScenarios.zaiPools(FIXED_NOW, fiveHour = 2, week = 1)

    private val provider =
        object : ResetProvider {
            override suspend fun availability(account: Account): ResetAvailability? =
                when (account.provider) {
                    Provider.ZAi -> zAiPools
                    else -> null
                }

            override suspend fun redeem(
                account: Account,
                poolId: String,
                attemptKey: ResetAttemptKey,
            ): RedeemOutcome = RedeemOutcome.Unsupported
        }

    private val inCard = hasAnyAncestor(hasTestTag(RESETS_CARD_TAG))

    private fun launch() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph =
                        testGraph(
                            rule.activity,
                            realAccounts = DemoData.manyAccounts(FIXED_NOW),
                            resetProvider = provider,
                        )
                )
            }
        }
        rule.waitForIdle()
    }

    private fun openZAi() {
        rule
            .onNodeWithTag(OVERVIEW_LIST_TAG)
            .performScrollToNode(hasTestTag(accountCardTag("demo-zai")))
        rule.onNodeWithTag(accountCardTag("demo-zai")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(RESETS_CARD_TAG).performScrollTo()
    }

    @Test
    fun `the resets can be used and asked for`() {
        launch()
        openZAi()

        rule.onAllNodes(inCard and hasText("5-hour limit")).assertCountEquals(1)
        rule.onAllNodes(inCard and hasText("Use a reset")).assertCountEquals(1)
        rule.onAllNodes(inCard and hasText("Ask for a reset card")).assertCountEquals(1)
    }

    @Test
    fun `with no resets at all, a reset card can still be asked for`() {
        zAiPools = ResetScenarios.zaiPools(FIXED_NOW, fiveHour = 0, week = 0)
        launch()
        openZAi()

        rule.onAllNodes(inCard and hasText("Ask for a reset card")).assertCountEquals(1)
        rule.onAllNodes(inCard and hasText("Use a reset")).assertCountEquals(0)
    }

    @Test
    fun `the Resets tab offers Z_AI's button`() {
        launch()
        rule.onNodeWithContentDescription("Resets").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(RESETS_TAG).performScrollToNode(hasTestTag(useResetTag("demo-zai")))
        rule.onAllNodes(hasTestTag(useResetTag("demo-zai"))).assertCountEquals(1)
    }
}
