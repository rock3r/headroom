package dev.sebastiano.headroom.ui.home

import dev.sebastiano.headroom.designsystem.PaceChipState
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.NextReset
import dev.sebastiano.headroom.model.Pace
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetPolicy
import dev.sebastiano.headroom.model.UsagePoint
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Alert switches keyed by account id and window id. */
internal typealias AlertSwitches = Map<Pair<String, String>, Boolean>

internal fun homeUiState(
    accounts: List<AccountState>,
    now: Instant,
    alerts: AlertSwitches,
    pastResets: Map<String, List<Double>>,
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
            )
        }
    return HomeUiState(
        now = now,
        accounts =
            accounts.map {
                it.toSummary(
                    now = now,
                    alerts = alerts,
                    pastResets = pastResets[it.account.id].orEmpty(),
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
    pastResets: List<Double>,
    justReset: Boolean = false,
): AccountSummary {
    val primary = primaryWindow
    return AccountSummary(
        id = account.id,
        provider = account.provider,
        label = account.label,
        plan = snapshot?.planLabel,
        primary = primary?.toSummary(account.id, now, alerts),
        session = sessionWindow?.toSummary(account.id, now, alerts),
        windows =
            snapshot
                ?.windows
                .orEmpty()
                .filterNot { it.isUnlimited }
                .sortedBy { it.kind.ordinal }
                .map { it.toSummary(account.id, now, alerts) },
        pace =
            primary?.let {
                if (justReset) PaceChipState.JustReset else PaceChipState.from(it, now)
            },
        needsAttention = primary?.let { Pace.needsAttention(it, now) } ?: false,
        error = lastError,
        pastResets = pastResets,
        justReset = justReset,
    )
}

private fun QuotaWindow.toSummary(accountId: String, now: Instant, alerts: AlertSwitches) =
    WindowSummary(
        id = id,
        label = label,
        kind = kind,
        usedPercent = usedPercent,
        expectedPercent = Pace.expectedPercent(this, now),
        resetsAt = resetsAt,
        canAlert = ResetPolicy.canAlert(this),
        alertEnabled = alerts.isOn(accountId, this),
    )

private fun AlertSwitches.isOn(accountId: String, window: QuotaWindow) =
    ResetPolicy.canAlert(window) &&
        (this[accountId to window.id] ?: ResetPolicy.alertsByDefault(window))

internal fun chartSummary(
    window: QuotaWindow,
    points: List<UsagePoint>,
    now: Instant,
): ChartSummary? {
    val resetsAt = window.resetsAt ?: return null
    val length = window.length ?: return null
    val start = resetsAt.minus(length)
    // A projection is an estimate; whole minutes keep "in 1d 17h, 1d 1h before" consistent.
    val hit = Pace.projectedLimitAt(window, now)?.truncatedTo(ChronoUnit.MINUTES)
    val elapsed = Duration.between(start, now).toMillis()
    val projectedEnd =
        if (hit == null && elapsed > 0) {
            window.usedPercent / elapsed * length.toMillis()
        } else {
            null
        }
    return ChartSummary(
        start = start,
        end = resetsAt,
        usedPercent = window.usedPercent,
        expectedPercent = Pace.expectedPercent(window, now) ?: 0.0,
        kind = window.kind,
        points = points,
        projectedLimitAt = hit,
        projectedEndPercent = projectedEnd,
    )
}
