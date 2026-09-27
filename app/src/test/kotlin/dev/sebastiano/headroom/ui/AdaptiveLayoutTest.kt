package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AdaptiveLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun launch() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) { HeadroomApp(graph = testGraph(rule.activity)) }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `compact uses the floating toolbar and one column`() {
        launch()
        rule.onNodeWithTag(TOOLBAR_TAG).assertIsDisplayed()
        val claude = rule.onNodeWithTag(accountCardTag("demo-claude")).getUnclippedBoundsInRoot()
        val codex = rule.onNodeWithTag(accountCardTag("demo-codex")).getUnclippedBoundsInRoot()
        assertTrue(codex.top > claude.top, "Codex should be below Claude")
        assertEquals(claude.left, codex.left)
    }

    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun `medium uses a navigation rail and two columns of cards`() {
        launch()
        rule.onNodeWithTag(TOOLBAR_TAG).assertDoesNotExist()
        rule.onNodeWithText("Overview").assertIsDisplayed()
        rule.onNodeWithText("Resets").assertIsDisplayed()
        val claude = rule.onNodeWithTag(accountCardTag("demo-claude")).getUnclippedBoundsInRoot()
        val codex = rule.onNodeWithTag(accountCardTag("demo-codex")).getUnclippedBoundsInRoot()
        assertEquals(claude.top, codex.top)
        assertTrue(codex.left > claude.left, "Codex should be beside Claude")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `expanded shows the list and the detail side by side`() {
        launch()
        rule.onNodeWithTag(TOOLBAR_TAG).assertDoesNotExist()
        rule.onNodeWithTag(accountCardTag("demo-claude")).assertIsDisplayed()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.onNodeWithText("Max 20x · sam@example.com").assertIsDisplayed()

        rule.onNodeWithTag(accountCardTag("demo-codex")).performClick()
        rule.onNodeWithText("Pro · sam@example.com").assertIsDisplayed()
        rule.onNodeWithTag(accountCardTag("demo-claude")).assertIsDisplayed()
    }
}
