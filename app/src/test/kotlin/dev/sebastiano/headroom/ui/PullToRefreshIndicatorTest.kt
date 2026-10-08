package dev.sebastiano.headroom.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.ui.overview.OverviewScreen
import dev.sebastiano.headroom.ui.overview.PULL_REFRESH_INDICATOR_TAG
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The pull-to-refresh indicator builds its shape morphs when it is composed, which made every
 * return to the overview miss frames. It is composed only while a refresh runs or a pull moves it.
 */
@RunWith(RobolectricTestRunner::class)
class PullToRefreshIndicatorTest {
    @get:Rule val rule = createComposeRule()

    private var refreshing by mutableStateOf(false)

    private fun show() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                OverviewScreen(
                    state =
                        HomeUiState(
                            now = FIXED_NOW,
                            accounts = emptyList(),
                            isDemo = false,
                            isRefreshing = refreshing,
                            lastSyncedAt = null,
                            nextReset = null,
                        ),
                    formatter = ResetFormatter(ZoneOffset.UTC, Locale.UK, is24Hour = true),
                    onRefresh = {},
                    onOpenAccount = {},
                    onNextResetAlertChange = {},
                    onAllResets = {},
                    onOpenAccounts = {},
                    onOpenSettings = {},
                    onSortChange = {},
                )
            }
        }
    }

    @Test
    fun `at rest the overview composes no pull-to-refresh indicator`() {
        show()

        rule.onNodeWithTag(PULL_REFRESH_INDICATOR_TAG).assertDoesNotExist()
    }

    @Test
    fun `the indicator is composed while a refresh runs, and goes once it ends`() {
        // The indicator turns for as long as the refresh runs, so the clock only moves by hand.
        rule.mainClock.autoAdvance = false
        refreshing = true
        show()
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithTag(PULL_REFRESH_INDICATOR_TAG).assertExists()

        refreshing = false
        rule.mainClock.advanceTimeBy(HIDE_MILLIS)

        rule.onNodeWithTag(PULL_REFRESH_INDICATOR_TAG).assertDoesNotExist()
    }

    private companion object {
        /** Longer than the indicator takes to slide away once the refresh ends. */
        const val HIDE_MILLIS = 2_000L
    }
}
