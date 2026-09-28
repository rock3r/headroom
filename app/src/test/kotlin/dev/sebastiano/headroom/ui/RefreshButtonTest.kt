package dev.sebastiano.headroom.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * While a refresh runs, the pull-to-refresh indicator shows the progress. The refresh button only
 * turns its icon and cannot be pressed again.
 */
@RunWith(RobolectricTestRunner::class)
class RefreshButtonTest {
    @get:Rule val rule = createComposeRule()

    private var refreshing by mutableStateOf(false)
    private var inToolbar by mutableStateOf(true)
    private var presses = 0

    private fun show() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                RefreshButton(refreshing = refreshing, onRefresh = { presses++ }, inToolbar)
            }
        }
    }

    @Test
    fun `the button refreshes when nothing is refreshing`() {
        show()

        rule.onNodeWithTag(REFRESH_TAG).assertIsEnabled().performClick()

        assertEquals(1, presses)
    }

    @Test
    fun `while refreshing the button is disabled and shows no second loading indicator`() {
        // The icon turns for as long as the refresh runs, so the clock only moves by hand.
        rule.mainClock.autoAdvance = false
        refreshing = true
        show()
        for (toolbar in listOf(true, false)) {
            inToolbar = toolbar
            presses = 0
            rule.mainClock.advanceTimeByFrame()

            rule
                .onNodeWithTag(REFRESH_TAG)
                .assertIsNotEnabled()
                .assertContentDescriptionEquals("Refreshing")
                .performClick()

            assertEquals(0, presses, "in toolbar: $inToolbar")
            rule
                .onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
                .fetchSemanticsNodes()
                .let { assertEquals(0, it.size, "in toolbar: $inToolbar") }
        }
    }
}
