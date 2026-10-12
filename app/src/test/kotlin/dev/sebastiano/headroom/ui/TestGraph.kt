package dev.sebastiano.headroom.ui

import android.content.Context
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.AppGraph
import dev.sebastiano.headroom.appdata.DemoAwareResetEvents
import dev.sebastiano.headroom.appdata.DemoAwareUsageHistory
import dev.sebastiano.headroom.appdata.DemoModeQuotaRepository
import dev.sebastiano.headroom.appdata.DemoResetEvents
import dev.sebastiano.headroom.appdata.DemoResetHistory
import dev.sebastiano.headroom.appdata.DemoUsageHistory
import dev.sebastiano.headroom.appdata.InMemoryAlertPreferences
import dev.sebastiano.headroom.appdata.RepositoryUsageHistory
import dev.sebastiano.headroom.designsystem.IndicatorStyle
import dev.sebastiano.headroom.designsystem.IndicatorStyleKey
import dev.sebastiano.headroom.island.ResetIslandAccess
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.NoResets
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.ResetEventLog
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.prototype.PrototypeTools
import dev.sebastiano.headroom.signin.FakeSignInController
import dev.sebastiano.headroom.signin.SignInController
import dev.sebastiano.headroom.ui.settings.SETTINGS_ACCOUNTS_TAG
import dev.sebastiano.headroom.widgets.WidgetPinner
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Opens the accounts screen the way a user does: from the overview, through Settings. */
fun ComposeTestRule.openAccounts() {
    onNodeWithContentDescription("Settings").performClick()
    // Screenshot tests pause the clock; let the settings page arrive before tapping in it.
    if (!mainClock.autoAdvance) mainClock.advanceTimeBy(PAGE_SETTLE_MILLIS)
    onNodeWithTag(SETTINGS_ACCOUNTS_TAG).performClick()
}

private const val PAGE_SETTLE_MILLIS = 1_000L

/** The instant every UI test and screenshot runs at. */
val FIXED_NOW: Instant = Instant.parse("2026-09-27T12:32:00Z")

/**
 * The app's graph with a fixed clock, UTC, no ticking and no simulated latency, so every run shows
 * the same numbers. [realAccounts] empty means demo mode.
 */
@Suppress("UNUSED_PARAMETER") // The context mirrors AppGraph.create, for call sites that swap them.
fun testGraph(
    context: Context,
    realAccounts: List<AccountState> = emptyList(),
    signInController: SignInController = FakeSignInController(),
    widgetPinner: WidgetPinner = WidgetPinner { false },
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    demo: FakeQuotaRepository = FakeQuotaRepository({ FIXED_NOW }),
    real: QuotaRepository = FakeQuotaRepository({ FIXED_NOW }, initial = realAccounts),
    settings: SettingsRepository = InMemorySettingsRepository(),
    statsDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    resetIsland: ResetIslandAccess = ResetIslandAccess.Unavailable,
    resetProvider: ResetProvider = NoResets,
    prototypes: PrototypeTools? = null,
): AppGraph {
    val clock = { FIXED_NOW }
    val repository =
        DemoModeQuotaRepository(
            real = real,
            demo = demo,
            scope = scope,
            simulatedLatency = Duration.ZERO,
        )
    return AppGraph(
        quotaRepository = repository,
        isDemo = repository.isDemo,
        accountsLoaded = repository.isLoaded,
        alertPreferences = InMemoryAlertPreferences(),
        clock = clock,
        zone = ZoneOffset.UTC,
        signInController = signInController,
        widgetPinner = widgetPinner,
        resetHistory = DemoResetHistory,
        tickInterval = null,
        settings = settings,
        appVersion = TEST_APP_VERSION,
        usageHistory =
            DemoAwareUsageHistory(
                isDemo = repository.isDemo,
                real = RepositoryUsageHistory(real),
                demo = DemoUsageHistory(DemoResetHistory, clock, ZoneOffset.UTC),
            ),
        statsDispatcher = statsDispatcher,
        resetIsland = resetIsland,
        resetProvider = resetProvider,
        resetScope = scope,
        prototypes = prototypes,
        demoAccounts = demo,
        resetEvents =
            DemoAwareResetEvents(
                isDemo = repository.isDemo,
                real = ResetEventLog.None,
                demo = DemoResetEvents(clock),
            ),
    )
}

/** A reset island whose service state a test can change, and that records what was shown. */
class FakeResetIslandAccess(
    ready: Boolean = false,
    enabledInSettings: Boolean = false,
    overlayAllowed: Boolean = false,
) : ResetIslandAccess {
    val readyState = MutableStateFlow(ready)
    val overlayState = MutableStateFlow(overlayAllowed)
    val enabledState = MutableStateFlow(enabledInSettings)
    var refreshes = 0
    val demos = mutableListOf<Pair<Provider, String>>()

    override val ready: StateFlow<Boolean> = readyState
    override val overlayAllowed: StateFlow<Boolean> = overlayState
    override val enabledInSettings: StateFlow<Boolean> = enabledState

    override fun refresh() {
        refreshes++
    }

    override fun showDemo(provider: Provider, message: String): Boolean {
        if (!readyState.value && !overlayState.value) return false
        demos += provider to message
        return true
    }
}

/** The version the settings screen shows in tests and screenshots. */
const val TEST_APP_VERSION: String = "0.1.0"

/** Matches the progress indicators inside the node tagged [tag]. */
fun indicatorsIn(tag: String): SemanticsMatcher =
    hasAnyAncestor(hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(IndicatorStyleKey)

fun hasIndicatorStyle(style: IndicatorStyle): SemanticsMatcher =
    SemanticsMatcher.expectValue(IndicatorStyleKey, style)

/** What the platform installs when the user turns animations off. */
object ZeroMotion : MotionDurationScale {
    override val scaleFactor: Float = 0f
}
