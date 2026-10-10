package dev.sebastiano.headroom.ui.home

import dev.sebastiano.headroom.designsystem.PaceChipState
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.NextReset
import dev.sebastiano.headroom.model.Pace
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetPolicy
import dev.sebastiano.headroom.model.WindowKind
import kotlin.time.Instant

/** Alert switches keyed by account id and window id. */
internal typealias AlertSwitches = Map<Pair<String, String>, Boolean>

/** Used percent at each past reset, oldest first, keyed by account id and window id. */
internal typealias ResetHistories = Map<Pair<String, String>, List<Double>>

internal fun homeUiState(
    accounts: List<AccountState>,
    now: Instant,
    alerts: AlertSwitches,
    pastResets: ResetHistories,
    isDemo: Boolean,
    isRefreshing: Boolean,
    justReset: Set<String> = emptySet(),
    accountsLoaded: Boolean = true,
): HomeUiState {
    val nextReset =
        NextReset.find(accounts, now)?.let { next ->
            NextResetSummary(
                accountId = next.account.id,
                provider = next.account.provider,
                windowId = next.window.id,
                kind = next.window.kind,
                resetsAt = requireNotNull(next.window.resetsAt),
                alertEnabled = alerts.isOn(next.account.id, next.window),
                name = next.account.name,
            )
        }
    return HomeUiState(
        now = now,
        accounts =
            accounts.map {
                it.toSummary(
                    now = now,
                    alerts = alerts,
                    pastResets = pastResets,
                    justReset = it.account.id in justReset,
                )
            },
        isDemo = isDemo,
        isRefreshing = isRefreshing || accounts.any { it.isRefreshing },
        lastSyncedAt = accounts.mapNotNull { it.snapshot?.fetchedAt }.maxOrNull(),
        nextReset = nextReset,
        accountsLoaded = accountsLoaded,
    )
}

internal fun AccountState.toSummary(
    now: Instant,
    alerts: AlertSwitches,
    pastResets: ResetHistories,
    justReset: Boolean = false,
): AccountSummary {
    val primary = primaryWindow
    // Stale numbers are compared with the pace of when they were fetched, not with today's.
    val asOf = if (isSignInExpired) snapshot?.fetchedAt ?: now else now
    val summary = { window: QuotaWindow ->
        window.toSummary(account.id, asOf, alerts, pastResets[account.id to window.id].orEmpty())
    }
    return AccountSummary(
        id = account.id,
        provider = account.provider,
        name = account.name,
        label = account.label,
        plan = snapshot?.planLabel,
        balance = snapshot?.balance,
        primary = primary?.let(summary),
        session = sessionWindow?.let(summary),
        windows =
            snapshot
                ?.windows
                .orEmpty()
                .filterNot { it.isUnlimited }
                .sortedBy { it.kind.ordinal }
                .map(summary),
        pace =
            primary?.let {
                if (justReset) PaceChipState.JustReset else PaceChipState.from(it, asOf)
            },
        // Stale data never asks for attention: it may be long out of date.
        needsAttention = !isSignInExpired && primary?.let { Pace.needsAttention(it, now) } ?: false,
        error = lastError,
        pastResets = primary?.let { pastResets[account.id to it.id] }.orEmpty(),
        justReset = justReset,
        allowances =
            if (account.provider.windowsAreSeparateAllowances) {
                snapshot
                    ?.windows
                    .orEmpty()
                    .filterNot { it.isUnlimited || it.kind == WindowKind.Session }
                    .sortedByDescending { it == primary }
                    .map(summary)
            } else {
                emptyList()
            },
        signInExpired = isSignInExpired,
        dataFrom = snapshot?.fetchedAt,
    )
}

private fun QuotaWindow.toSummary(
    accountId: String,
    now: Instant,
    alerts: AlertSwitches,
    pastResets: List<Double>,
) =
    WindowSummary(
        id = id,
        label = label,
        kind = kind,
        usedPercent = usedPercent,
        expectedPercent = Pace.expectedPercent(this, now),
        resetsAt = resetsAt,
        canAlert = ResetPolicy.canAlert(this),
        alertEnabled = alerts.isOn(accountId, this),
        usedAmount = usedAmount,
        limitAmount = limitAmount,
        amountUnit = amountUnit,
        pastResets = pastResets,
        expiresAt = expiresAt,
        isRecognised = isRecognised,
    )

private fun AlertSwitches.isOn(accountId: String, window: QuotaWindow) =
    ResetPolicy.canAlert(window) &&
        (this[accountId to window.id] ?: ResetPolicy.alertsByDefault(window))
