package dev.sebastiano.headroom.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.ui.overview.OverviewScreen
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The overview's glasses button shows only while display glasses are connected. */
@RunWith(RobolectricTestRunner::class)
class OpenOnGlassesButtonTest {
    @get:Rule val rule = createComposeRule()

    private var opened = 0

    @Test
    fun `with glasses connected the button opens Headroom on them`() {
        show(onOpenOnGlasses = { opened++ })

        rule.onNodeWithContentDescription("Open on glasses").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `without glasses there is no button`() {
        show(onOpenOnGlasses = null)

        rule.onNodeWithContentDescription("Open on glasses").assertDoesNotExist()
    }

    private fun show(onOpenOnGlasses: (() -> Unit)?) {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                OverviewScreen(
                    state =
                        HomeUiState(
                            now = FIXED_NOW,
                            accounts = emptyList(),
                            isDemo = false,
                            isRefreshing = false,
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
                    onOpenOnGlasses = onOpenOnGlasses,
                )
            }
        }
    }
}
