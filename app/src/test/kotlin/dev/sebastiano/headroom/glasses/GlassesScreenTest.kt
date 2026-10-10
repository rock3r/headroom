package dev.sebastiano.headroom.glasses

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.xr.glimmer.GlimmerTheme
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.ui.home.NextResetSummary
import dev.sebastiano.headroom.ui.home.WindowSummary
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassesScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val formatter = ResetFormatter(ZoneOffset.UTC, Locale.US, is24Hour = false)
    private var backs = 0

    @Test
    fun `the first card is the next reset and holds focus for the touchpad`() {
        show(state())

        rule.onNodeWithTag(GLASSES_NEXT_RESET_TAG).assertIsDisplayed().assertIsFocused()
        rule.onNodeWithText("Next weekly reset").assertIsDisplayed()
        rule.onNodeWithText("2h 00m").assertIsDisplayed()
    }

    @Test
    fun `without a coming reset the first card says so`() {
        show(state(nextReset = null))

        rule.onNodeWithText("No resets coming up").assertIsDisplayed()
    }

    @Test
    fun `back leaves the glasses`() {
        show(state())

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

        assertEquals(1, backs)
    }

    private fun show(state: HomeUiState) {
        rule.setContent { GlimmerTheme { GlassesScreen(state, formatter, onBack = { backs++ }) } }
    }

    private fun state(
        nextReset: NextResetSummary? =
            NextResetSummary(
                accountId = "claude",
                provider = Provider.Claude,
                windowId = "weekly",
                kind = WindowKind.Weekly,
                resetsAt = now.plusSeconds(2 * 3_600),
                alertEnabled = true,
            )
    ) =
        HomeUiState(
            now = now,
            accounts = listOf(claude()),
            isDemo = false,
            isRefreshing = false,
            lastSyncedAt = null,
            nextReset = nextReset,
        )

    private fun claude() =
        AccountSummary(
            id = "claude",
            provider = Provider.Claude,
            label = "Claude",
            plan = "Max 20x",
            primary =
                WindowSummary(
                    id = "weekly",
                    label = "Weekly",
                    kind = WindowKind.Weekly,
                    usedPercent = 71.0,
                    expectedPercent = null,
                    resetsAt = now.plusSeconds(2 * 3_600),
                    canAlert = true,
                    alertEnabled = true,
                ),
            session = null,
            windows = emptyList(),
            pace = null,
            needsAttention = false,
            error = null,
            pastResets = emptyList(),
        )
}
