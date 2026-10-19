package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.prototype.ResetScenarios
import dev.sebastiano.headroom.ui.accounts.ACCOUNTS_TAG
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.resets.REDEEM_CONFIRM_TAG
import dev.sebastiano.headroom.ui.resets.REDEEM_SHEET_TAG
import dev.sebastiano.headroom.ui.resets.RESETS_CARD_TAG
import dev.sebastiano.headroom.ui.resets.useResetTag
import dev.sebastiano.headroom.ui.settings.ADD_TILE_TAG
import dev.sebastiano.headroom.ui.settings.RESET_ISLAND_SETUP_TAG
import dev.sebastiano.headroom.ui.settings.RESET_ISLAND_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_LIST_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_TAG
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * External requests that open a screen (a widget tap, a sign-in warning, a reset reminder or a long
 * press on the Quick Settings tile) close the sheets that are open on top of the app, unless a
 * redeem step is in progress.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SheetDismissOnRequestTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var accountRequest by mutableStateOf<OpenAccountRequest?>(null)
    private var tileSettingsRequest by mutableStateOf<Long?>(null)
    private val answer = CompletableDeferred<RedeemOutcome>()
    private val resetScope = TestScope(UnconfinedTestDispatcher())

    private val provider =
        object : ResetProvider {
            override suspend fun availability(account: Account): ResetAvailability? =
                when (account.provider) {
                    Provider.Codex -> ResetScenarios.codexPool(FIXED_NOW, available = 2)
                    else -> null
                }

            override suspend fun redeem(
                account: Account,
                poolId: String,
                attemptKey: ResetAttemptKey,
            ): RedeemOutcome = answer.await()
        }

    private fun launchApp(scope: CoroutineScope = resetScope) {
        val graph =
            testGraph(
                rule.activity,
                realAccounts = DemoData.accounts(FIXED_NOW),
                resetProvider = provider,
                resetIsland = FakeResetIslandAccess(),
                scope = scope,
            )
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph = graph,
                    openAccountRequest = accountRequest,
                    onConsumeOpenAccount = { accountRequest = null },
                    openTileSettingsRequest = tileSettingsRequest,
                    onConsumeOpenTileSettings = { tileSettingsRequest = null },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun openRedeemSheet() {
        launchApp()
        rule.onNodeWithContentDescription("Resets").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(useResetTag("demo-codex")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(REDEEM_SHEET_TAG).assertIsDisplayed()
    }

    private fun openIslandSetup() {
        launchApp()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag(RESET_ISLAND_TAG))
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(RESET_ISLAND_SETUP_TAG).assertIsDisplayed()
    }

    @Test
    fun `a widget tap closes the redeem sheet and opens the account`() {
        openRedeemSheet()

        accountRequest = OpenAccountRequest("demo-grok", serial = 1)
        rule.waitForIdle()

        rule.onNodeWithTag(REDEEM_SHEET_TAG).assertDoesNotExist()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.onNodeWithText("SuperGrok · sam").assertIsDisplayed()
    }

    @Test
    fun `a sign-in warning closes the redeem sheet and opens the sign-in`() {
        openRedeemSheet()

        accountRequest = OpenAccountRequest("demo-claude", serial = 1, signInAgain = true)
        rule.waitForIdle()

        rule.onNodeWithTag(REDEEM_SHEET_TAG).assertDoesNotExist()
        rule.onNodeWithTag(ACCOUNTS_TAG).assertIsDisplayed()
        rule.onNodeWithText("Sign in to Claude again").assertIsDisplayed()
    }

    @Test
    fun `a reset reminder closes the redeem sheet and opens the account's resets`() {
        openRedeemSheet()

        accountRequest = OpenAccountRequest("demo-codex", serial = 1, showResets = true)
        rule.waitForIdle()

        rule.onNodeWithTag(REDEEM_SHEET_TAG).assertDoesNotExist()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.onNodeWithTag(RESETS_CARD_TAG).assertIsDisplayed()
    }

    @Test
    fun `a long press on the tile closes the redeem sheet and opens the tile's settings`() {
        openRedeemSheet()

        tileSettingsRequest = 1L
        rule.waitForIdle()

        rule.onNodeWithTag(REDEEM_SHEET_TAG).assertDoesNotExist()
        rule.onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()
        rule.onNodeWithTag(ADD_TILE_TAG).assertIsDisplayed()
    }

    @Test
    fun `while a reset runs an external request does not close the redeem sheet`() {
        openRedeemSheet()
        rule.onNodeWithTag(REDEEM_CONFIRM_TAG).performClick()
        rule.waitForIdle()

        accountRequest = OpenAccountRequest("demo-grok", serial = 1)
        rule.waitForIdle()
        accountRequest = OpenAccountRequest("demo-claude", serial = 2, signInAgain = true)
        rule.waitForIdle()
        tileSettingsRequest = 3L
        rule.waitForIdle()

        rule.onNodeWithTag(REDEEM_SHEET_TAG).assertIsDisplayed()
        rule.onNodeWithTag(DETAIL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(ACCOUNTS_TAG).assertDoesNotExist()
        rule.onNodeWithTag(SETTINGS_TAG).assertDoesNotExist()

        // When the reset finishes, the sheet stays open on the outcome instead of closing late.
        answer.complete(RedeemOutcome.NothingToReset)
        rule.waitForIdle()
        rule.onNodeWithTag(REDEEM_SHEET_TAG).assertIsDisplayed()
    }

    @Test
    fun `while the new usage comes in after a reset an external request does not close the sheet`() {
        openRedeemSheet()
        rule.onNodeWithTag(REDEEM_CONFIRM_TAG).performClick()
        rule.waitForIdle()
        answer.complete(RedeemOutcome.Success(resetsLeft = 1))
        rule.waitForIdle()

        accountRequest = OpenAccountRequest("demo-grok", serial = 1)
        rule.waitForIdle()
        tileSettingsRequest = 2L
        rule.waitForIdle()

        rule.onNodeWithTag(REDEEM_SHEET_TAG).assertIsDisplayed()
        rule.onNodeWithTag(DETAIL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(SETTINGS_TAG).assertDoesNotExist()
    }

    @Test
    fun `once the reset outcome shows an external request closes the redeem sheet`() {
        openRedeemSheet()
        rule.onNodeWithTag(REDEEM_CONFIRM_TAG).performClick()
        rule.waitForIdle()
        answer.complete(RedeemOutcome.Success(resetsLeft = 1))
        rule.waitForIdle()
        resetScope.advanceTimeBy(AFTER_REFRESH_DELAY)
        rule.waitForIdle()

        accountRequest = OpenAccountRequest("demo-grok", serial = 1)
        rule.waitForIdle()

        rule.onNodeWithTag(REDEEM_SHEET_TAG).assertDoesNotExist()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
    }

    @Test
    fun `a widget tap closes the reset island set-up and opens the account`() {
        openIslandSetup()
        rule.mainClock.autoAdvance = false

        accountRequest = OpenAccountRequest("demo-codex", serial = 1)
        Snapshot.sendApplyNotifications()
        repeat(4) { rule.mainClock.advanceTimeByFrame() }
        // Closed while Settings is still shrinking away.
        rule.onNodeWithTag(SETTINGS_TAG).assertExists()
        rule.onNodeWithTag(RESET_ISLAND_SETUP_TAG).assertDoesNotExist()
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()

        // Returning to Settings does not reopen the set-up sheet.
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(RESET_ISLAND_SETUP_TAG).assertDoesNotExist()
    }

    @Test
    fun `a sign-in warning closes the reset island set-up and opens the sign-in`() {
        openIslandSetup()

        accountRequest = OpenAccountRequest("demo-claude", serial = 1, signInAgain = true)
        rule.waitForIdle()

        rule.onNodeWithTag(RESET_ISLAND_SETUP_TAG).assertDoesNotExist()
        rule.onNodeWithTag(ACCOUNTS_TAG).assertIsDisplayed()
        rule.onNodeWithText("Sign in to Claude again").assertIsDisplayed()
    }

    @Test
    fun `a reset reminder closes the reset island set-up and opens the account's resets`() {
        openIslandSetup()

        accountRequest = OpenAccountRequest("demo-codex", serial = 1, showResets = true)
        rule.waitForIdle()

        rule.onNodeWithTag(RESET_ISLAND_SETUP_TAG).assertDoesNotExist()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.onNodeWithTag(RESETS_CARD_TAG).assertIsDisplayed()
    }

    @Test
    fun `a long press on the tile closes the reset island set-up and scrolls to the tile's rows`() {
        openIslandSetup()

        tileSettingsRequest = 1L
        rule.waitForIdle()

        rule.onNodeWithTag(RESET_ISLAND_SETUP_TAG).assertDoesNotExist()
        rule.onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()
        rule.onNodeWithTag(ADD_TILE_TAG).assertIsDisplayed()
    }

    private companion object {
        val AFTER_REFRESH_DELAY = Duration.parse("3s")
    }
}
