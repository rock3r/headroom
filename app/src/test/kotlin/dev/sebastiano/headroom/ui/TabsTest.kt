package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.resets.RESETS_TAG
import dev.sebastiano.headroom.ui.widgets.WIDGETS_TAG
import dev.sebastiano.headroom.ui.widgets.addWidgetTag
import dev.sebastiano.headroom.widgets.WidgetPinner
import dev.sebastiano.headroom.widgets.WidgetStyle
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class TabsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val pinned = mutableListOf<WidgetStyle>()
    private var pinWorks = true

    private fun launch() {
        val pinner = WidgetPinner { style ->
            pinned += style
            pinWorks
        }
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(graph = testGraph(rule.activity, widgetPinner = pinner))
            }
        }
    }

    @Test
    fun `the floating toolbar switches between the three tabs`() {
        launch()
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()

        rule.onNodeWithContentDescription("Resets").performClick().assertIsSelected()
        rule.onNodeWithTag(RESETS_TAG).assertIsDisplayed()
        rule.onNodeWithText("Alerts are on for 4 of 5 windows").assertIsDisplayed()

        rule.onNodeWithContentDescription("Widgets").performClick().assertIsSelected()
        rule.onNodeWithTag(WIDGETS_TAG).assertIsDisplayed()

        rule.onNodeWithContentDescription("Overview").performClick()
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()
    }

    @Test
    fun `all resets on the hero card opens the resets tab`() {
        launch()
        rule.onNodeWithText("All resets").performClick()
        rule.onNodeWithTag(RESETS_TAG).assertIsDisplayed()
        rule.onNodeWithContentDescription("Resets").assertIsSelected()
    }

    @Test
    fun `the resets tab shows the history with the limit hits`() {
        launch()
        rule.onNodeWithContentDescription("Resets").performClick()
        rule
            .onNodeWithTag(RESETS_TAG)
            .performScrollToNode(
                androidx.compose.ui.test.hasContentDescription(
                    "Claude: 82%, 95%, 100%, 88%, 100% at past resets, 71% now"
                )
            )
        rule
            .onNodeWithContentDescription(
                "Claude: 82%, 95%, 100%, 88%, 100% at past resets, 71% now"
            )
            .assertIsDisplayed()
    }

    @Test
    fun `add to home screen asks the pinner for that style`() {
        launch()
        rule.onNodeWithContentDescription("Widgets").performClick()
        rule
            .onNodeWithTag(WIDGETS_TAG)
            .performScrollToNode(hasTestTag(addWidgetTag(WidgetStyle.Bars)))
        rule.onNodeWithTag(addWidgetTag(WidgetStyle.Bars)).performClick()
        assertEquals(listOf(WidgetStyle.Bars), pinned)
    }

    @Test
    fun `when the launcher cannot pin, the app says so`() {
        pinWorks = false
        launch()
        rule.onNodeWithContentDescription("Widgets").performClick()
        rule.onNodeWithTag(addWidgetTag(WidgetStyle.Rings)).performClick()
        rule
            .onNodeWithText("Your launcher cannot add widgets from apps", substring = true)
            .assertIsDisplayed()
    }
}
