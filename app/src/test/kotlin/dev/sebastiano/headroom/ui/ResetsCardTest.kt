package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.prototype.ResetScenarios
import dev.sebastiano.headroom.ui.components.rememberResetFormatter
import dev.sebastiano.headroom.ui.resets.ResetsCard
import java.time.ZoneOffset
import kotlin.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The detail's Resets card: a count only where it adds something, and every reset's expiry. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ResetsCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(provider: Provider, availability: ResetAvailability) {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                ResetsCard(
                    provider = provider,
                    availability = availability,
                    formatter = rememberResetFormatter(ZoneOffset.UTC),
                    onUse = {},
                    onAsk = {},
                    redeemEnabled = provider == Provider.Codex,
                )
            }
        }
    }

    @Test
    fun `a single pool shows its count once, and when each reset expires`() {
        val credits =
            ResetPool(
                id = "codex",
                label = "Usage limit reset",
                available = 3,
                scope = ResetScope.of(WindowKind.Session, WindowKind.Weekly),
                expiries =
                    listOf(
                        Instant.parse("2026-10-05T06:18:00Z"),
                        Instant.parse("2026-10-08T11:02:00Z"),
                        Instant.parse("2026-10-08T11:02:00Z"),
                    ),
            )
        show(Provider.Codex, ResetAvailability(listOf(credits)))

        rule.onAllNodesWithText("Available now").assertCountEquals(0)
        rule.onNodeWithText("Expires Mon 5 Oct, 6:18 AM").assertIsDisplayed()
        rule.onNodeWithText("2 expire Thu 8 Oct, 11:02 AM").assertIsDisplayed()
        rule.onAllNodesWithText("The first one expires", substring = true).assertCountEquals(0)
    }

    @Test
    fun `grants with queued resets keep the summary`() {
        show(Provider.Claude, ResetScenarios.claudeGrants(FIXED_NOW))

        rule.onNodeWithText("Available now").assertIsDisplayed()
        rule.onNodeWithText("3 with no expiry").assertIsDisplayed()
    }

    @Test
    fun `an account with no resets says so in one row, with no count and no button`() {
        show(
            Provider.Grok,
            ResetAvailability(
                listOf(
                    ResetScenarios.grokPool(FIXED_NOW)
                        .pools
                        .single()
                        .copy(available = 0, expiries = emptyList())
                )
            ),
        )

        rule.onNodeWithText("No resets available").assertIsDisplayed()
        rule.onAllNodesWithText("Available now").assertCountEquals(0)
        rule.onAllNodesWithText("0").assertCountEquals(0)
        rule.onAllNodesWithText("Use a reset").assertCountEquals(0)
    }
}
