package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.OverviewSort
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.overview.OVERVIEW_SORT_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The overview's sort control. The screen is tall enough for every card to be laid out. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class OverviewSortingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val settings = InMemorySettingsRepository()
    private val mostUsedFirst = listOf("demo-grok", "demo-claude", "demo-copilot", "demo-codex")

    private fun launch(reduceMotion: Boolean = false) {
        rule.setContent {
            HeadroomTheme(dynamicColor = false, reduceMotion = reduceMotion) {
                HeadroomApp(graph = testGraph(rule.activity, settings = settings))
            }
        }
    }

    private fun cardTops(): List<Pair<String, Dp>> =
        listOf("demo-claude", "demo-codex", "demo-grok", "demo-copilot").map { id ->
            id to rule.onNodeWithTag(accountCardTag(id)).getUnclippedBoundsInRoot().top
        }

    private fun cardOrder(): List<String> = cardTops().sortedBy { it.second }.map { it.first }

    @Test
    fun `the control says how the cards are sorted and announces changes politely`() {
        launch()
        rule
            .onNodeWithTag(OVERVIEW_SORT_TAG)
            .assertIsDisplayed()
            .assert(hasContentDescription("Sort accounts"))
            .assert(hasStateDescription("Your order"))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                )
            )
    }

    @Test
    fun `picking most used first puts the fullest account on top and keeps the choice`() {
        launch()
        assertEquals(listOf("demo-claude", "demo-codex", "demo-grok", "demo-copilot"), cardOrder())

        rule.onNodeWithTag(OVERVIEW_SORT_TAG).performClick()
        rule.onNodeWithText("Most used first").performClick()

        assertEquals(mostUsedFirst, cardOrder())
        rule.onNodeWithTag(OVERVIEW_SORT_TAG).assert(hasStateDescription("Most used first"))
        assertEquals(OverviewSort.MostUsedFirst, settings.settings.value.overviewSort)
    }

    @Test
    fun `the menu offers every sort`() {
        launch()
        rule.onNodeWithTag(OVERVIEW_SORT_TAG).performClick()
        listOf(
                "Your order",
                "Most used first",
                "Least used first",
                "Soonest reset first",
                "Latest reset first",
            )
            .forEach { rule.onNode(hasText(it) and hasAnyAncestor(isPopup())).assertIsDisplayed() }
    }

    @Test
    fun `in left mode the labels say what is left`() {
        kotlinx.coroutines.runBlocking { settings.setQuotaDisplay(QuotaDisplay.Left) }
        launch()
        rule.onNodeWithTag(OVERVIEW_SORT_TAG).performClick()
        rule.onNodeWithText("Least left first").assertIsDisplayed()
        rule.onNodeWithText("Most left first").performClick()
        // Most left first is least used first: the emptiest account goes on top.
        assertEquals(
            listOf("demo-codex", "demo-copilot", "demo-claude", "demo-grok"),
            cardOrder(),
        )
    }

    @Test
    fun `the cards move to their new places over a few frames`() {
        launch()
        val before = cardTops().toMap()
        rule.onNodeWithTag(OVERVIEW_SORT_TAG).performClick()
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Most used first").performClick()
        advanceFrames(PARTWAY_FRAMES)

        // Grok is on its way up: it has left its old place but not reached the top yet.
        val grok = rule.onNodeWithTag(accountCardTag("demo-grok")).getUnclippedBoundsInRoot().top
        assertNotEquals(before.getValue("demo-grok"), grok)
        assertNotEquals(before.getValue("demo-claude"), grok)

        rule.mainClock.autoAdvance = true
        assertEquals(mostUsedFirst, cardOrder())
    }

    @Test
    fun `with motion reduced the cards snap to their new places`() {
        launch(reduceMotion = true)
        val before = cardTops().toMap()
        rule.onNodeWithTag(OVERVIEW_SORT_TAG).performClick()
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Most used first").performClick()
        advanceFrames(PARTWAY_FRAMES)

        // Grok is already where Claude was, the top card's place.
        val grok = rule.onNodeWithTag(accountCardTag("demo-grok")).getUnclippedBoundsInRoot().top
        assertEquals(before.getValue("demo-claude"), grok)
        assertEquals(mostUsedFirst, cardOrder())
    }

    @Test
    fun `a stored sort orders the cards when the overview opens, without a reorder`() {
        kotlinx.coroutines.runBlocking { settings.setOverviewSort(OverviewSort.MostUsedFirst) }
        rule.mainClock.autoAdvance = false
        launch()
        advanceFrames(PARTWAY_FRAMES)
        // Grok is already above Claude: the cards did not start in your order and move.
        assertEquals(mostUsedFirst, cardOrder())

        rule.mainClock.autoAdvance = true
        assertEquals(mostUsedFirst, cardOrder())
        rule.onNodeWithTag(OVERVIEW_SORT_TAG).assert(hasStateDescription("Most used first"))
    }

    @Test
    @Config(qualifiers = "w1280dp-h1600dp")
    fun `in two panes the list follows the sort and the detail keeps its account`() {
        launch()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.onNodeWithText("Max 20x · sam@example.com").assertIsDisplayed()

        rule.onNodeWithTag(OVERVIEW_SORT_TAG).performClick()
        rule.onNodeWithText("Most used first").performClick()

        assertEquals(mostUsedFirst, cardOrder())
        rule.onNodeWithText("Max 20x · sam@example.com").assertIsDisplayed()

        rule.onNodeWithTag(accountCardTag("demo-codex")).performClick()
        rule.onNodeWithTag(OVERVIEW_SORT_TAG).performClick()
        rule.onNodeWithText("Soonest reset first").performClick()
        rule.onNodeWithText("Pro · sam@example.com").assertIsDisplayed()
    }

    private fun advanceFrames(count: Int) = repeat(count) { rule.mainClock.advanceTimeByFrame() }

    private fun hasContentDescription(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text))

    private fun hasStateDescription(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private companion object {
        /** Long enough for the list to recompose and start moving, far short of settling. */
        const val PARTWAY_FRAMES = 4
    }
}
