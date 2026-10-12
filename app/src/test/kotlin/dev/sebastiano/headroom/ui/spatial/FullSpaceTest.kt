package dev.sebastiano.headroom.ui.spatial

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.xr.compose.platform.requestHomeSpace
import androidx.xr.compose.testing.getPositionInRoot
import androidx.xr.compose.testing.getSize
import androidx.xr.compose.testing.onSubspaceNodeWithTag
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.ui.APP_TAG
import dev.sebastiano.headroom.ui.HeadroomApp
import dev.sebastiano.headroom.ui.REFRESH_TAG
import dev.sebastiano.headroom.ui.TOOLBAR_TAG
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import dev.sebastiano.headroom.ui.resets.RESETS_TAG
import dev.sebastiano.headroom.ui.resets.resetRowTag
import dev.sebastiano.headroom.ui.settings.SETTINGS_TAG
import dev.sebastiano.headroom.ui.stats.STATS_TAG
import dev.sebastiano.headroom.ui.testGraph
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * On an XR headset in Full Space the app keeps its adaptive layout inside the main panel, moves its
 * navigation to an orbiter, and gives the account detail a panel of its own. The XR test runtime
 * starts every activity in Full Space once the device reports the spatial feature.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h800dp")
class FullSpaceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun runOnAnXrHeadset() {
        shadowOf(rule.activity.packageManager).setSystemFeature(XR_SPATIAL_FEATURE, true)
    }

    private fun launch() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) { HeadroomApp(graph = testGraph(rule.activity)) }
        }
        rule.waitForIdle()
    }

    /**
     * Nodes drawn outside the app's window: in an orbiter, or in a panel of their own.
     *
     * Under the XR test runtime an orbiter or a panel that leaves keeps its view attached, and the
     * view composes its last content again, so its nodes stay findable. These tests check what
     * leaves through the window's own nodes and the subspace's panels instead.
     */
    private val outsideTheWindow = !hasAnyAncestor(hasTestTag(APP_TAG))
    private val insideTheWindow = hasAnyAncestor(hasTestTag(APP_TAG))
    private val inTheDetail = hasAnyAncestor(hasTestTag(DETAIL_TAG)) and outsideTheWindow

    @Test
    fun `the home shows the account detail in a panel beside the main one`() {
        launch()

        val main = rule.onSubspaceNodeWithTag(MAIN_PANEL_TAG).assertExists()
        val detail = rule.onSubspaceNodeWithTag(DETAIL_PANEL_TAG).assertExists()
        // The detail fills its slot after the window composes, so the subspace can place its panel
        // a frame later. Positions are the panels' centres.
        val mainEnd = { main.getPositionInRoot().x + main.getSize().width.value / 2 }
        val detailStart = { detail.getPositionInRoot().x - detail.getSize().width.value / 2 }
        rule.waitUntil(timeoutMillis = 5_000) { detailStart() >= mainEnd() }
        assertTrue(
            detailStart() >= mainEnd(),
            "The detail panel should be beside the main panel, on its end side",
        )
        rule.onNode(hasTestTag(DETAIL_TAG) and outsideTheWindow).assertExists()
        rule.onNode(hasText("Max 20x · sam@example.com") and inTheDetail).assertExists()
        rule.onAllNodes(hasTestTag(DETAIL_TAG) and insideTheWindow).assertCountEquals(0)
    }

    @Test
    fun `the detail panel's title and percentage have the colour of the app's title`() {
        launch()

        val appTitle =
            rule.onNode(
                hasText("Headroom") and isHeading() and insideTheWindow,
                useUnmergedTree = true,
            )
        val title =
            rule.onNode(hasText("Claude") and isHeading() and inTheDetail, useUnmergedTree = true)
        // The hero ring's percentage, above its label. The window list repeats the number.
        val percent =
            rule.onNode(
                hasText("71%") and hasAnySibling(hasText("weekly used")) and inTheDetail,
                useUnmergedTree = true,
            )
        assertEquals(appTitle.textColor(), title.textColor(), "The detail's title")
        assertEquals(appTitle.textColor(), percent.textColor(), "The hero ring's percentage")
    }

    @Test
    fun `the account's name sits 12 dp below the top of the detail panel`() {
        launch()

        val detail =
            rule.onNode(hasTestTag(DETAIL_TAG) and outsideTheWindow).getUnclippedBoundsInRoot()
        val name =
            rule
                .onNode(hasText("Claude") and isHeading() and inTheDetail, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        // The detail's own 4 dp, and 8 dp more above the row with the avatar and the name.
        assertEquals(12.dp, name.top - detail.top)
    }

    @Test
    fun `an opened account shows in the detail panel and the list stays`() {
        launch()

        rule.onNodeWithTag(accountCardTag("demo-codex")).performClick()
        rule.waitForIdle()

        rule.onNode(hasText("Pro · sam@example.com") and inTheDetail).assertExists()
        rule.onNodeWithTag(accountCardTag("demo-claude")).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a narrow main panel also opens accounts in the detail panel`() {
        launch()

        rule.onNodeWithTag(accountCardTag("demo-codex")).performClick()
        rule.waitForIdle()

        rule.onSubspaceNodeWithTag(DETAIL_PANEL_TAG).assertExists()
        rule.onNode(hasText("Pro · sam@example.com") and inTheDetail).assertExists()
        rule.onAllNodes(hasTestTag(DETAIL_TAG) and insideTheWindow).assertCountEquals(0)
        rule.onNodeWithTag(accountCardTag("demo-claude")).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a narrow main panel has the floating toolbar in an orbiter`() {
        launch()

        rule.onNode(hasTestTag(TOOLBAR_TAG) and outsideTheWindow).assertExists()
        rule
            .onNode(hasTestTag(REFRESH_TAG) and hasAnyAncestor(hasTestTag(TOOLBAR_TAG)))
            .assertExists()
        rule.onAllNodes(hasTestTag(TOOLBAR_TAG) and insideTheWindow).assertCountEquals(0)

        rule.onNodeWithContentDescription("Stats").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(STATS_TAG).assertExists()
    }

    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun `a wider main panel has the navigation rail in an orbiter`() {
        launch()

        rule.onNodeWithTag(TOOLBAR_TAG).assertDoesNotExist()
        rule.onNode(hasText("Resets") and outsideTheWindow).assertExists()
        rule.onAllNodes(hasText("Resets") and insideTheWindow).assertCountEquals(0)
        rule.onNode(hasTestTag(REFRESH_TAG) and outsideTheWindow).assertExists()
        rule.onAllNodes(hasTestTag(REFRESH_TAG) and insideTheWindow).assertCountEquals(0)

        rule.onNode(hasText("Resets") and outsideTheWindow).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(RESETS_TAG).assertExists()
    }

    @Test
    fun `the resets and stats tabs fill the main panel alone`() {
        launch()

        rule.onNode(hasText("Resets") and outsideTheWindow).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(RESETS_TAG).assertExists()
        rule.onSubspaceNodeWithTag(DETAIL_PANEL_TAG).assertDoesNotExist()
        rule.onSubspaceNodeWithTag(MAIN_PANEL_TAG).assertExists()

        rule.onNode(hasText("Stats") and outsideTheWindow).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(STATS_TAG).assertExists()
        rule.onSubspaceNodeWithTag(DETAIL_PANEL_TAG).assertDoesNotExist()
    }

    @Test
    fun `an account opened from the resets tab shows in the overview's detail panel`() {
        launch()
        rule.onNode(hasText("Resets") and outsideTheWindow).performClick()
        rule.waitForIdle()

        rule
            .onNodeWithTag(RESETS_TAG)
            .performScrollToNode(hasTestTag(resetRowTag("demo-grok", "weekly")))
        rule.onNodeWithTag(resetRowTag("demo-grok", "weekly")).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertExists()
        rule.onSubspaceNodeWithTag(DETAIL_PANEL_TAG).assertExists()
        rule.onNode(hasText("SuperGrok · sam") and inTheDetail).assertExists()
    }

    @Test
    fun `settings take the place of the home's panels, and back brings them back`() {
        launch()

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(SETTINGS_TAG).assertExists()
        rule.onSubspaceNodeWithTag(MAIN_PANEL_TAG).assertExists()
        rule.onSubspaceNodeWithTag(DETAIL_PANEL_TAG).assertDoesNotExist()

        // Espresso's back key goes to whichever window has focus, maybe a panel's.
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag(SETTINGS_TAG).assertDoesNotExist()
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertExists()
        rule.onSubspaceNodeWithTag(DETAIL_PANEL_TAG).assertExists()
        rule.onAllNodes(hasText("Resets") and insideTheWindow).assertCountEquals(0)
    }

    @Test
    fun `back in the home space the app is the plain adaptive app again`() {
        launch()
        rule.onSubspaceNodeWithTag(DETAIL_PANEL_TAG).assertExists()
        rule.activity.lifecycleScope.launch { rule.activity.requestHomeSpace() }
        rule.waitForIdle()

        rule.onSubspaceNodeWithTag(DETAIL_PANEL_TAG).assertDoesNotExist()
        rule.onNode(hasTestTag(DETAIL_TAG) and insideTheWindow).assertExists()
        rule.onNode(hasText("Resets") and insideTheWindow).assertExists()
    }
}

/** The system feature of devices that run spatial apps, such as XR headsets. */
private const val XR_SPATIAL_FEATURE = "android.software.xr.api.spatial"

/** The colour a text node draws its text in. */
private fun SemanticsNodeInteraction.textColor(): Color {
    val layouts = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    return layouts.single().layoutInput.style.color
}
