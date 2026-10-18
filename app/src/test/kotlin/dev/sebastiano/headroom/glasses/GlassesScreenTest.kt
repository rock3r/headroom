package dev.sebastiano.headroom.glasses

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.xr.glimmer.GlimmerTheme
import dev.sebastiano.headroom.designsystem.ProviderPalette
import dev.sebastiano.headroom.designsystem.headroomColorScheme
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.ThemePalette
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.ui.home.NextResetSummary
import dev.sebastiano.headroom.ui.home.WindowSummary
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
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

    @Test
    fun `an account's logo has its provider's colour from the app's dark theme`() {
        rule.setContent {
            GlimmerTheme {
                AccountGlassesCard(
                    account = claude(),
                    now = now,
                    formatter = formatter,
                    display = QuotaDisplay.Used,
                    palette = ThemePalette.Coral,
                )
            }
        }

        // The avatar's colour in the app's dark theme, harmonised with the palette's primary.
        val primary = headroomColorScheme(rule.activity, ThemePalette.Coral, dark = true).primary
        val claudeColour = ProviderPalette.colors(Provider.Claude, primary, dark = true).accent
        val card = rule.onNodeWithTag(glassesAccountTag("claude")).captureToImage()
        assertTrue(card.contains(claudeColour), "No pixel of the card has Claude's colour")
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
                resetsAt = now.plus(2.hours),
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
                    resetsAt = now.plus(2.hours),
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

/** True if an opaque pixel of this image is [colour], give or take the 8-bit rounding. */
private fun ImageBitmap.contains(colour: Color): Boolean {
    val pixels = toPixelMap()
    return (0 until width).any { x ->
        (0 until height).any { y ->
            val pixel = pixels[x, y]
            pixel.alpha == 1f &&
                abs(pixel.red - colour.red) < COLOUR_TOLERANCE &&
                abs(pixel.green - colour.green) < COLOUR_TOLERANCE &&
                abs(pixel.blue - colour.blue) < COLOUR_TOLERANCE
        }
    }
}

private const val COLOUR_TOLERANCE = 0.01f
