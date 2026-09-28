package dev.sebastiano.headroom.ui

import android.content.Context
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import dev.sebastiano.headroom.AppGraph
import dev.sebastiano.headroom.appdata.DemoModeQuotaRepository
import dev.sebastiano.headroom.appdata.DemoResetHistory
import dev.sebastiano.headroom.appdata.InMemoryAlertPreferences
import dev.sebastiano.headroom.designsystem.IndicatorStyle
import dev.sebastiano.headroom.designsystem.IndicatorStyleKey
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.signin.FakeSignInController
import dev.sebastiano.headroom.signin.SignInController
import dev.sebastiano.headroom.widgets.WidgetPinner
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

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
    )
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
