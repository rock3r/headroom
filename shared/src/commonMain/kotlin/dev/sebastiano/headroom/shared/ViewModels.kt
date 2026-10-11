package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.Countdown
import dev.sebastiano.headroom.model.ExpiryLine
import dev.sebastiano.headroom.model.NextReset
import dev.sebastiano.headroom.model.Pace
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetNote
import dev.sebastiano.headroom.model.ResetPolicy
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.ResetTiming
import dev.sebastiano.headroom.model.TileSubtitle
import dev.sebastiano.headroom.model.TileSubtitleMode
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.model.canRedeemResets
import dev.sebastiano.headroom.model.expiryLines
import dev.sebastiano.headroom.model.hasFooter
import dev.sebastiano.headroom.model.holdsNone
import dev.sebastiano.headroom.model.logo
import dev.sebastiano.headroom.model.notes
import dev.sebastiano.headroom.model.showsSummary
import dev.sebastiano.headroom.signin.SignInKind
import dev.sebastiano.headroom.signin.SignInState
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/*
 * What the iOS app shows, in types Swift reads easily: strings, numbers and lists. Text for the
 * user lives in the app's String Catalog; these carry stable ids (`"network"`, `"over"`) instead.
 */

/** The overview: every account, and the reset that comes next. */
public data class OverviewUi(
    /** The accounts, in the order of the overview sort. */
    val accounts: List<AccountUi>,
    /** True when no account is signed in and the accounts are Headroom's demo data. */
    val isDemo: Boolean,
    val nextReset: NextResetUi?,
    /** `used` or `left`: which percentage the user wants to see. */
    val display: String,
    /**
     * The overview sort: `yourOrder`, `mostUsedFirst`, `leastUsedFirst`, `soonestResetFirst`,
     * `latestResetFirst`.
     */
    val sort: String,
    /** "Claude · in 2d 4h", for the control and the widgets. */
    val nextResetTile: TileUi?,
    /** "Grok · 88% used", for the control and the widgets. */
    val tightestTile: TileUi?,
    /** The account ids in the user's own order, whatever [sort] says. */
    val yourOrder: List<String>,
    /** The time this overview was worked out at, for countdowns that match it. */
    val nowEpochSeconds: Long,
)

/**
 * A subtitle in two halves, an account and a value, as the Android Quick Settings tile shows it.
 */
public data class TileUi(
    val name: String,
    /** A countdown such as `2d 4h`, or a whole percentage such as `88`. */
    val value: String,
    /** `countdown`, `used` or `left`: how the app words [value]. */
    val kind: String,
)

public data class NextResetUi(
    val accountId: String,
    val accountTitle: String,
    val providerName: String,
    val windowLabel: String,
    val resetsAtEpochSeconds: Long,
    val windowId: String,
    /** The window's kind, such as `weekly`: a weekly one is "Next weekly reset". */
    val kind: String,
    val alertOn: Boolean,
)

/** An amount left to spend, such as AI credits, in [unit]. */
public data class BalanceUi(val amount: Double, val unit: String)

public data class AccountUi(
    val id: String,
    /** [Provider.id], such as `claude`, for the provider's logo. */
    val providerId: String,
    val providerName: String,
    /** The nickname when there is one, otherwise the provider's name. */
    val title: String,
    /** What the provider calls the account, such as an email address. */
    val label: String,
    val plan: String?,
    /** The window the account leads with: its main limit. */
    val primary: WindowUi?,
    /** Every window, the primary one first, then in the provider's order. */
    val windows: List<WindowUi>,
    val isRefreshing: Boolean,
    /** The sign-in expired: the numbers are from the last good sync and only a sign-in helps. */
    val signInExpired: Boolean,
    /** Why the last sync failed: `auth`, `access`, `rateLimited`, `network`, `parse`, `unknown`. */
    val error: String?,
    val updatedAtEpochSeconds: Long?,
    /** Over pace or nearly used up: the card stands out. */
    val needsAttention: Boolean,
    /** The account's usage-limit resets, or null when its provider has none. */
    val resets: AccountResetsUi?,
    val balance: BalanceUi?,
    /** Each window is its own allowance, as JetBrains licences: the card lists them all. */
    val separateAllowances: Boolean,
    /** Its weekly limit reset while the app was open: "Just reset" for the rest of the session. */
    val justReset: Boolean,
)

/** An account's usage-limit resets. */
public data class AccountResetsUi(
    /** Resets the user can use now or as soon as a limit is reached. */
    val availableNow: Int,
    /** Resets waiting in line, shown as "(+5)". */
    val queued: Int,
    val pools: List<ResetPoolUi>,
    val canAskForMore: Boolean,
    /** The provider needs its own sign-in before its resets can be seen or used. */
    val requiresSignIn: Boolean,
    /** Headroom can use them: false for Claude unless the experimental setting is on. */
    val canRedeem: Boolean,
    /** Why the account cannot have resets, in the provider's words. The pools are then empty. */
    val ineligibleReason: String?,
    /** The card's "Available now" count adds to the pool rows: several pools, or some queued. */
    val showsSummary: Boolean,
    /** The account holds no reset at all, counting the queued and paused ones. */
    val holdsNone: Boolean,
    /** The card's footer notes: `waitingForLimit` and `queued`. */
    val notes: List<String>,
    /** The card has a footer: a note, or a button to use or ask for a reset. */
    val hasFooter: Boolean,
)

public data class ResetPoolUi(
    val id: String,
    val label: String,
    val available: Int,
    /** `ready`, `waitingForLimit`, `queued`, `paused` or `notUsableYet`. */
    val status: String,
    /**
     * What a reset gives back, worded per provider: `codex`, `codexMonthly`, `grok`, `zaiFiveHour`,
     * `zaiWeek`, `windows` (the limits in [scopeWindows]) or `unknown`.
     */
    val scope: String,
    /** The labels of the limits a `windows` reset refills. */
    val scopeWindows: List<String>,
    val soonestExpiryEpochSeconds: Long?,
    val canUseNow: Boolean,
    /** How many resets the pool started with, when the provider says. */
    val total: Int?,
    /** When each reset expires, soonest first, at most four lines and then "and N more". */
    val expiryLines: List<ExpiryLineUi>,
    /** A reset from it can be used far from a limit, where it is wasted: the sheet warns. */
    val anyTime: Boolean,
    /** The ids of the windows in a `windows` scope, for the words of what it refills. */
    val scopeWindowIds: List<String>,
    /** The account's windows a reset from it clears, for the redeem sheet's bars. */
    val clearsWindowIds: List<String>,
    /** The redeem sheet offers it: to use now, or to say why not yet. */
    val isOffered: Boolean,
)

/** One line of a pool's expiry list: [kind] is `at`, `noExpiry` or `more`. */
public data class ExpiryLineUi(val kind: String, val atEpochSeconds: Long?, val count: Int)

public data class WindowUi(
    val id: String,
    val label: String,
    /** `session`, `daily`, `weekly`, `monthly`, `other` or `credit`. */
    val kind: String,
    val usedPercent: Double,
    val leftPercent: Double,
    val resetsAtEpochSeconds: Long?,
    /** When a credit expires. Credits do not reset. */
    val expiresAtEpochSeconds: Long?,
    /** `under`, `on` or `over` pace, or null when the window has no pace. */
    val pace: String?,
    /** Where usage would be at even pace, in percent, or null without a pace. */
    val expectedPercent: Double?,
    val needsAttention: Boolean,
    /** A credit or a window Headroom does not know yet: it never alerts and has no pace. */
    val isInformational: Boolean,
    val isUnlimited: Boolean,
    val usedAmount: Double?,
    val limitAmount: Double?,
    /** A currency code such as `USD`, for [usedAmount] and [limitAmount]. */
    val amountUnit: String?,
    /** The window can send a reset alert: weekly and monthly limits only. */
    val canAlert: Boolean,
    /** The reset alert is on. Always false when [canAlert] is false. */
    val alertOn: Boolean,
    /** False for a window the app does not know: it shows "About this quota". */
    val isRecognised: Boolean,
    /** The pace chip: `over`, `under`, `on` or `justReset`, or null without a pace. */
    val paceChip: String?,
    /** How many points over or under pace, for "5 pts over pace". */
    val pacePoints: Int,
)

/** A provider the user can add, and how it signs in. */
public data class ProviderUi(
    val id: String,
    val name: String,
    /** `browser`, `deviceCode` or `apiKey`. */
    val signIn: String,
    /** The provider's monochrome logo as SVG path data, filled with the nonzero rule. */
    val logoPath: String,
    /** Side of the square [logoPath] is drawn in. */
    val logoViewport: Float,
    /** Empty space to add on every side of the viewport, so every logo looks the same size. */
    val logoInset: Float,
)

/** One step of a sign-in, as [SignInState] but with Swift-friendly fields. */
public sealed interface SignInUi {
    public data object Idle : SignInUi

    public data class Starting(val providerId: String, val providerName: String) : SignInUi

    /**
     * The provider's page is open in the browser sheet. [authorizationUrl] is the page to show;
     * [codeRejected] is true after the user pasted a code that did not work.
     */
    public data class Browser(
        val providerId: String,
        val providerName: String,
        val authorizationUrl: String,
        val codeRejected: Boolean,
    ) : SignInUi

    public data class DeviceCode(
        val providerId: String,
        val providerName: String,
        val userCode: String,
        val verificationUrl: String,
    ) : SignInUi

    public data class ApiKey(
        val providerId: String,
        val providerName: String,
        val keyRejected: Boolean,
    ) : SignInUi

    public data class Finishing(val providerId: String, val providerName: String) : SignInUi

    public data class Success(
        val providerId: String,
        val providerName: String,
        val accountLabel: String,
    ) : SignInUi

    /** [error] is `denied`, `expired`, `network`, `differentAccount` or `unknown`. */
    public data class Failed(val providerId: String, val providerName: String, val error: String) :
        SignInUi
}

/** Maps the model to what the iOS app shows. */
internal object UiMapping {
    fun overview(
        accounts: List<AccountState>,
        isDemo: Boolean,
        now: Instant,
        settings: AppSettings = AppSettings(),
        alerts: AlertStates = AlertStates.Defaults,
        justReset: Set<String> = emptySet(),
    ): OverviewUi =
        OverviewUi(
            accounts =
                accounts.sortedFor(settings.overviewSort).map {
                    account(it, now, settings, alerts, it.account.id in justReset)
                },
            yourOrder = accounts.map { it.account.id },
            nowEpochSeconds = now.epochSeconds,
            isDemo = isDemo,
            display = settings.quotaDisplay.id(),
            sort = settings.overviewSort.id(),
            nextResetTile = tile(TileSubtitleMode.NextReset, accounts, now, settings.quotaDisplay),
            tightestTile =
                tile(TileSubtitleMode.TightestQuota, accounts, now, settings.quotaDisplay),
            nextReset =
                NextReset.find(accounts, now)?.let { next ->
                    NextResetUi(
                        accountId = next.account.id,
                        accountTitle = next.account.name,
                        providerName = next.account.provider.displayName,
                        windowLabel = next.window.label,
                        resetsAtEpochSeconds = checkNotNull(next.window.resetsAt).epochSeconds,
                        windowId = next.window.id,
                        kind = next.window.kind.id(),
                        alertOn = alerts.isOn(next.account.id, next.window),
                    )
                },
        )

    fun account(
        state: AccountState,
        now: Instant,
        settings: AppSettings = AppSettings(),
        alerts: AlertStates = AlertStates.Defaults,
        justReset: Boolean = false,
    ): AccountUi {
        val snapshot = state.snapshot
        val primary = state.primaryWindow
        // The pace of a stale account is worked out at the time of its last good sync.
        val paceTime = if (state.isSignInExpired) snapshot?.fetchedAt ?: now else now
        val windows = snapshot?.windows.orEmpty().sortedByDescending { it.id == primary?.id }
        val windowUis =
            windows
                .map { window(it, paceTime, alerts.isOn(state.account.id, it)) }
                .map {
                    if (justReset && it.id == primary?.id) it.copy(paceChip = PaceChip.JUST_RESET)
                    else it
                }
        return AccountUi(
            id = state.account.id,
            providerId = state.account.provider.id,
            providerName = state.account.provider.displayName,
            title = state.account.name,
            label = state.account.label,
            plan = snapshot?.planLabel,
            primary = windowUis.firstOrNull { it.id == primary?.id },
            windows = windowUis,
            isRefreshing = state.isRefreshing,
            signInExpired = state.isSignInExpired,
            error = state.lastError?.name?.replaceFirstChar { it.lowercase() },
            updatedAtEpochSeconds = snapshot?.fetchedAt?.epochSeconds,
            needsAttention = windowUis.any { it.needsAttention },
            resets = snapshot?.resets?.let { resets(state, it, settings) },
            balance = snapshot?.balance?.let { BalanceUi(it.amount, it.unit) },
            separateAllowances = state.account.provider.windowsAreSeparateAllowances,
            justReset = justReset,
        )
    }

    private fun resets(
        state: AccountState,
        resets: ResetAvailability,
        settings: AppSettings,
    ): AccountResetsUi {
        val provider = state.account.provider
        val windows = state.snapshot?.windows.orEmpty()
        return AccountResetsUi(
            availableNow = resets.availableNow,
            queued = resets.queued,
            pools = resets.pools.map { pool(provider, windows, it) },
            canAskForMore = resets.canAskForMore,
            requiresSignIn = resets.requiresSignIn,
            canRedeem = provider.canRedeemResets(settings),
            ineligibleReason = resets.ineligibleReason,
            showsSummary = resets.showsSummary,
            holdsNone = resets.holdsNone,
            notes =
                resets.notes.map {
                    when (it) {
                        ResetNote.WaitingForLimit -> "waitingForLimit"
                        ResetNote.Queued -> "queued"
                    }
                },
            hasFooter = resets.hasFooter,
        )
    }

    /** A reset pool of [provider], whose snapshot has [windows]. */
    fun pool(provider: Provider, windows: List<QuotaWindow>, pool: ResetPool): ResetPoolUi =
        ResetPoolUi(
            id = pool.id,
            label = pool.label,
            available = pool.available,
            status = pool.status.id(),
            scope = scopeId(provider, pool.scope),
            scopeWindows =
                pool.scope.windowIds.sorted().map { id ->
                    windows.firstOrNull { it.id == id }?.label ?: id
                },
            soonestExpiryEpochSeconds = pool.soonestExpiry?.epochSeconds,
            canUseNow = pool.canUseNow,
            total = pool.total,
            expiryLines = expiryLines(pool).map(::expiryLine),
            anyTime = pool.timing == ResetTiming.AnyTime,
            scopeWindowIds = pool.scope.windowIds.sorted(),
            clearsWindowIds = windows.filter { pool.scope.covers(it.id, it.kind) }.map { it.id },
            isOffered = pool.isOffered,
        )

    private fun expiryLine(line: ExpiryLine): ExpiryLineUi =
        when (line) {
            is ExpiryLine.At -> ExpiryLineUi("at", line.at.epochSeconds, line.count)
            is ExpiryLine.NoExpiry -> ExpiryLineUi("noExpiry", null, line.count)
            is ExpiryLine.More -> ExpiryLineUi("more", null, line.count)
        }

    /** Which sentence says what a reset gives back, as the Android app's `ResetCopy` picks it. */
    private fun scopeId(provider: Provider, scope: ResetScope): String {
        val kinds = scope.kinds
        return when {
            scope.windowIds.isNotEmpty() -> "windows"
            kinds == null -> "unknown"
            provider == Provider.Codex && kinds == setOf(WindowKind.Monthly) -> "codexMonthly"
            provider == Provider.Codex -> "codex"
            provider == Provider.Grok -> "grok"
            provider == Provider.ZAi && kinds == setOf(WindowKind.Session) -> "zaiFiveHour"
            provider == Provider.ZAi -> "zaiWeek"
            else -> "unknown"
        }
    }

    private fun tile(
        mode: TileSubtitleMode,
        accounts: List<AccountState>,
        now: Instant,
        display: QuotaDisplay,
    ): TileUi? =
        when (val subtitle = TileSubtitle.of(mode, accounts, now, display)) {
            is TileSubtitle.Reset -> TileUi(subtitle.name, subtitle.countdown, "countdown")
            is TileSubtitle.Tightest ->
                TileUi(subtitle.name, subtitle.percent.toString(), display.name.lowercase())
            null -> null
        }

    fun window(window: QuotaWindow, now: Instant, alertOn: Boolean = false): WindowUi {
        val hasPace = Pace.expectedPercent(window, now) != null
        val chip = PaceChip.of(window, now)
        return WindowUi(
            id = window.id,
            label = window.label,
            kind = window.kind.id(),
            usedPercent = window.usedPercent,
            leftPercent = window.remainingPercent,
            resetsAtEpochSeconds = window.resetsAt?.epochSeconds,
            expiresAtEpochSeconds = window.expiresAt?.epochSeconds,
            pace = if (hasPace) Pace.status(window, now).id() else null,
            expectedPercent = Pace.expectedPercent(window, now),
            needsAttention = Pace.needsAttention(window, now),
            isInformational = window.isInformational,
            isUnlimited = window.isUnlimited,
            usedAmount = window.usedAmount,
            limitAmount = window.limitAmount,
            amountUnit = window.amountUnit,
            canAlert = ResetPolicy.canAlert(window),
            alertOn = ResetPolicy.canAlert(window) && alertOn,
            isRecognised = window.isRecognised,
            paceChip = chip?.first,
            pacePoints = chip?.second ?: 0,
        )
    }

    fun provider(provider: Provider, kind: SignInKind): ProviderUi =
        ProviderUi(
            id = provider.id,
            name = provider.displayName,
            signIn =
                when (kind) {
                    SignInKind.Browser -> "browser"
                    SignInKind.DeviceCode -> "deviceCode"
                    SignInKind.ApiKey -> "apiKey"
                },
            logoPath = provider.logo.pathData,
            logoViewport = provider.logo.viewportSize,
            logoInset = provider.logo.inset,
        )

    fun signIn(state: SignInState): SignInUi =
        when (state) {
            SignInState.Idle -> SignInUi.Idle
            is SignInState.Starting ->
                SignInUi.Starting(state.provider.id, state.provider.displayName)
            is SignInState.Browser ->
                SignInUi.Browser(
                    state.provider.id,
                    state.provider.displayName,
                    state.authorizationUrl,
                    state.codeRejected,
                )
            is SignInState.DeviceCode ->
                SignInUi.DeviceCode(
                    state.provider.id,
                    state.provider.displayName,
                    state.userCode,
                    state.verificationUrl,
                )
            is SignInState.ApiKey ->
                SignInUi.ApiKey(state.provider.id, state.provider.displayName, state.keyRejected)
            is SignInState.Finishing ->
                SignInUi.Finishing(state.provider.id, state.provider.displayName)
            is SignInState.Success ->
                SignInUi.Success(state.provider.id, state.provider.displayName, state.accountLabel)
            is SignInState.Failed ->
                SignInUi.Failed(
                    state.provider.id,
                    state.provider.displayName,
                    state.error.id(),
                )
        }

    /** Window kinds are all lower case: `weekly`, `session`. */
    private fun WindowKind.id(): String = name.lowercase()
}

/** Formats the time left until [epochSeconds], as the Android app does: "15h 28m" or "2d 18h". */
public fun countdown(epochSeconds: Long, nowEpochSeconds: Long): String =
    Countdown.format((epochSeconds - nowEpochSeconds).seconds)
