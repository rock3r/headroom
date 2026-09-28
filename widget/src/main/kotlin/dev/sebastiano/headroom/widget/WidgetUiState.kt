package dev.sebastiano.headroom.widget

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.NextReset
import dev.sebastiano.headroom.model.Pace
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import java.time.Duration
import java.time.Instant
import kotlin.math.min
import kotlin.math.roundToInt

/** The size of a widget on screen, from the launcher's widget options. */
public data class WidgetSize(val widthDp: Float, val heightDp: Float) {
    val minDp: Float
        get() = min(widthDp, heightDp)
}

/** Where the widget is placed. The lock screen gets a compact layout. */
public enum class WidgetHostCategory {
    HomeScreen,
    Keyguard,
}

/** The kind of window a gauge shows, for its label. */
public enum class GaugeWindow {
    Session,
    Daily,
    Weekly,
    Monthly,
    Other;

    internal companion object {
        fun of(kind: WindowKind): GaugeWindow =
            when (kind) {
                WindowKind.Session -> Session
                WindowKind.Daily -> Daily
                WindowKind.Weekly -> Weekly
                WindowKind.Monthly -> Monthly
                WindowKind.Other -> Other
            }
    }
}

/** When a window resets: a clock time for long windows, the time left for short ones. */
public sealed interface ResetLabel {
    public data class At(val instant: Instant) : ResetLabel

    public data class In(val remaining: Duration) : ResetLabel
}

/** One account's usage of one window, ready to draw. */
public data class Gauge(
    val accountId: String,
    val name: String,
    val provider: Provider,
    /** Share of the window used, from 0 to 100. */
    val usedPercent: Int,
    val window: GaugeWindow,
    /** Where "even pace" sits on the bar, from 0 to 100. Null for session windows. */
    val pacePercent: Int?,
    /** Over pace or nearly full, see [Pace.needsAttention]. */
    val needsAttention: Boolean,
    val shape: UsageShape,
    val reset: ResetLabel?,
)

/** The next reset shown by the countdown and the lock screen footer. */
public data class NextResetUi(
    val accountId: String,
    val name: String,
    val window: GaugeWindow,
    val resetsAt: Instant,
    val remaining: Duration,
)

public enum class EmptyReason {
    /** None of the configured accounts exists. */
    NoAccounts,
    /** The accounts exist but have no usage data yet. */
    NoData,
    /** The widget shows sessions, and none of the accounts has a session limit. */
    NoSessionLimit,
}

/** Everything a widget renderer needs, built from the accounts by [WidgetUiState.from]. */
public sealed interface WidgetUiState {
    public val colourMode: ColourMode

    public data class Empty(val reason: EmptyReason, override val colourMode: ColourMode) :
        WidgetUiState

    /** One account: the main window outside, the session inside when there is one. */
    public data class SingleRing(
        val gauge: Gauge,
        val session: Gauge?,
        /** True only for a large ring whose account needs attention. */
        val wavy: Boolean,
        override val colourMode: ColourMode,
    ) : WidgetUiState {
        /** Tapping the ring swaps the number between the main window and the session. */
        val canFlip: Boolean
            get() = session != null
    }

    public data class RingGrid(val gauges: List<Gauge>, override val colourMode: ColourMode) :
        WidgetUiState

    public data class Bars(val gauges: List<Gauge>, override val colourMode: ColourMode) :
        WidgetUiState

    public data class SingleShape(val gauge: Gauge, override val colourMode: ColourMode) :
        WidgetUiState

    public data class ShapeGrid(val gauges: List<Gauge>, override val colourMode: ColourMode) :
        WidgetUiState

    public data class Countdown(val next: NextResetUi?, override val colourMode: ColourMode) :
        WidgetUiState

    /** The compact multi-ring layout used on the lock screen, whatever the style. */
    public data class LockScreen(
        val gauges: List<Gauge>,
        val next: NextResetUi?,
        override val colourMode: ColourMode,
    ) : WidgetUiState

    public companion object {
        /** Grids hold a 2×2 arrangement. */
        public const val MAX_GRID_ITEMS: Int = 4
        /** The lock screen is narrow, so it shows fewer rings. */
        public const val MAX_LOCK_SCREEN_ITEMS: Int = 3
        /** Rings at least this big are "hero" rings and may be wavy. */
        public const val HERO_RING_MIN_DP: Float = 100f

        public fun from(
            accounts: List<AccountState>,
            config: WidgetConfig,
            now: Instant,
            size: WidgetSize,
            host: WidgetHostCategory,
        ): WidgetUiState {
            val selected = select(accounts, config.accountIds)
            val mode = config.colourMode
            if (selected.isEmpty()) return Empty(EmptyReason.NoAccounts, mode)

            val names = displayNames(selected)
            val gauges = selected.mapNotNull { state ->
                val window =
                    when (config.window) {
                        WidgetWindow.Weekly -> state.primaryWindow
                        WidgetWindow.Session -> state.sessionWindow
                    } ?: return@mapNotNull null
                gauge(state, names.getValue(state.account.id), window, now)
            }
            val next = nextReset(selected, names, now)

            return when {
                gauges.isEmpty() -> Empty(emptyReason(config), mode)
                host == WidgetHostCategory.Keyguard ->
                    LockScreen(gauges.take(MAX_LOCK_SCREEN_ITEMS), next, mode)
                config.style == WidgetStyle.Countdown -> Countdown(next, mode)
                config.style == WidgetStyle.Bars -> Bars(gauges, mode)
                config.style == WidgetStyle.Rings && gauges.size == 1 ->
                    singleRing(selected, names, gauges.single(), config, now, size)
                config.style == WidgetStyle.Rings -> RingGrid(gauges.take(MAX_GRID_ITEMS), mode)
                gauges.size == 1 -> SingleShape(gauges.single(), mode)
                else -> ShapeGrid(gauges.take(MAX_GRID_ITEMS), mode)
            }
        }

        private fun select(accounts: List<AccountState>, ids: List<String>): List<AccountState> =
            if (ids.isEmpty()) {
                accounts
            } else {
                val byId = accounts.associateBy { it.account.id }
                ids.mapNotNull { byId[it] }
            }

        private fun emptyReason(config: WidgetConfig): EmptyReason =
            when (config.window) {
                WidgetWindow.Weekly -> EmptyReason.NoData
                WidgetWindow.Session -> EmptyReason.NoSessionLimit
            }

        /**
         * The names the user gave the accounts. Unnamed accounts show the provider's name, or the
         * account label where one provider appears more than once.
         */
        private fun displayNames(selected: List<AccountState>): Map<String, String> {
            val repeated =
                selected.groupingBy { it.account.provider }.eachCount().filterValues { it > 1 }.keys
            return selected.associate { state ->
                val account = state.account
                val name =
                    account.nickname
                        ?: if (account.provider in repeated) account.label
                        else account.provider.style.shortName
                account.id to name
            }
        }

        private fun singleRing(
            selected: List<AccountState>,
            names: Map<String, String>,
            main: Gauge,
            config: WidgetConfig,
            now: Instant,
            size: WidgetSize,
        ): SingleRing {
            val state = selected.first { it.account.id == main.accountId }
            val session =
                if (config.window == WidgetWindow.Weekly) {
                    state.sessionWindow?.let {
                        gauge(state, names.getValue(main.accountId), it, now)
                    }
                } else {
                    null
                }
            val wavy =
                main.needsAttention &&
                    main.window != GaugeWindow.Session &&
                    size.minDp >= HERO_RING_MIN_DP
            return SingleRing(main, session, wavy, config.colourMode)
        }

        private fun gauge(
            state: AccountState,
            name: String,
            window: QuotaWindow,
            now: Instant,
        ): Gauge {
            val used = window.usedPercent.roundToInt().coerceIn(0, PERCENT)
            val isSession = window.kind == WindowKind.Session
            return Gauge(
                accountId = state.account.id,
                name = name,
                provider = state.account.provider,
                usedPercent = used,
                window = GaugeWindow.of(window.kind),
                pacePercent =
                    if (isSession) null else Pace.expectedPercent(window, now)?.roundToInt(),
                needsAttention = Pace.needsAttention(window, now),
                shape = UsageShape.forPercent(used),
                reset = resetLabel(window, isSession, now),
            )
        }

        private fun resetLabel(window: QuotaWindow, isSession: Boolean, now: Instant): ResetLabel? {
            val resetsAt = window.resetsAt ?: return null
            return if (isSession) ResetLabel.In(Duration.between(now, resetsAt))
            else ResetLabel.At(resetsAt)
        }

        private fun nextReset(
            selected: List<AccountState>,
            names: Map<String, String>,
            now: Instant,
        ): NextResetUi? {
            val next = NextReset.find(selected, now) ?: return null
            val resetsAt = next.window.resetsAt ?: return null
            return NextResetUi(
                accountId = next.account.id,
                name = names.getValue(next.account.id),
                window = GaugeWindow.of(next.window.kind),
                resetsAt = resetsAt,
                remaining = Duration.between(now, resetsAt),
            )
        }

        private const val PERCENT = 100
    }
}
