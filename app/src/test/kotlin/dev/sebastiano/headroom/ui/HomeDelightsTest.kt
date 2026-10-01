package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.ui.delights.CONFETTI_OVERLAY_TAG
import dev.sebastiano.headroom.ui.delights.Delights
import dev.sebastiano.headroom.ui.delights.DelightsHost
import dev.sebastiano.headroom.ui.delights.LocalDelights
import dev.sebastiano.headroom.ui.delights.SHIMMER_OVERLAY_TAG
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app plays the shimmer after a refresh that brings new data, and confetti on a live reset. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class HomeDelightsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var clock: Instant = FIXED_NOW
    private val accounts = FakeQuotaRepository({ clock }, DemoData.accounts(FIXED_NOW))
    private val gated = GatedRepository(accounts)

    private var delights: Delights? = null

    private fun show(shimmer: Boolean = true, confetti: Boolean = true) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                DelightsHost(refreshShimmer = shimmer, resetConfetti = confetti) {
                    delights = LocalDelights.current
                    HeadroomApp(graph = testGraph(rule.activity, real = gated))
                }
            }
        }
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    @Test
    fun `a refresh that brings new data plays the shimmer`() {
        show()
        rule.onNodeWithTag(REFRESH_TAG).performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.onNodeWithTag(SHIMMER_OVERLAY_TAG).assertDoesNotExist()

        clock = FIXED_NOW.plus(Duration.ofMinutes(5))
        gated.finish()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithTag(SHIMMER_OVERLAY_TAG).assertExists()
    }

    @Test
    fun `a failed refresh plays nothing`() {
        show()
        rule.onNodeWithTag(REFRESH_TAG).performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)

        gated.finish(fail = true)
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithTag(SHIMMER_OVERLAY_TAG).assertDoesNotExist()
    }

    @Test
    fun `a reset while the app is open bursts confetti`() {
        show()
        rule.onNodeWithTag(CONFETTI_OVERLAY_TAG).assertDoesNotExist()

        rule.runOnIdle { accounts.set(accounts.accounts.value.withGrokReset()) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithTag(CONFETTI_OVERLAY_TAG).assertExists()
    }

    @Test
    fun `a reset that a surface in front already celebrated bursts nothing more`() {
        show()
        rule.runOnIdle { requireNotNull(delights).claimReset("demo-grok", "weekly") }

        rule.runOnIdle { accounts.set(accounts.accounts.value.withGrokReset()) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithTag(CONFETTI_OVERLAY_TAG).assertDoesNotExist()
    }

    @Test
    fun `a claim for another window leaves the reset its confetti`() {
        show()
        rule.runOnIdle { requireNotNull(delights).claimReset("demo-grok", "session") }

        rule.runOnIdle { accounts.set(accounts.accounts.value.withGrokReset()) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithTag(CONFETTI_OVERLAY_TAG).assertExists()
    }

    @Test
    fun `with confetti off a reset bursts nothing`() {
        show(confetti = false)

        rule.runOnIdle { accounts.set(accounts.accounts.value.withGrokReset()) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithTag(CONFETTI_OVERLAY_TAG).assertDoesNotExist()
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
    }
}

/** Real accounts whose refresh waits until the test lets it [finish]. */
private class GatedRepository(private val delegate: FakeQuotaRepository) : QuotaRepository {
    private var gate = CompletableDeferred<Boolean>()

    override val accounts: StateFlow<List<AccountState>> = delegate.accounts

    override suspend fun refresh(accountId: String?) {
        val fail = gate.await()
        gate = CompletableDeferred()
        if (!fail) delegate.refresh(accountId)
    }

    override fun history(accountId: String, windowId: String): Flow<List<UsagePoint>> =
        delegate.history(accountId, windowId)

    fun finish(fail: Boolean = false) {
        gate.complete(fail)
    }
}

/** Grok's weekly window, reset: nearly unused, and resetting a week later. */
internal fun List<AccountState>.withGrokReset(): List<AccountState> = map { state ->
    if (state.account.id != "demo-grok") return@map state
    val snapshot = requireNotNull(state.snapshot)
    state.copy(
        snapshot =
            snapshot.copy(
                windows =
                    snapshot.windows.map {
                        it.copy(usedPercent = 1.0, resetsAt = it.resetsAt?.plus(Duration.ofDays(7)))
                    }
            )
    )
}
