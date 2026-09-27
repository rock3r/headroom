package dev.sebastiano.headroom.model

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/**
 * An in-memory repository over [DemoData]. Demo mode, UI tests and end-to-end tests use it. Each
 * refresh adds a little usage, so a refresh visibly changes the numbers.
 */
public class FakeQuotaRepository(
    private val clock: () -> Instant = Instant::now,
    initial: List<AccountState> = DemoData.accounts(clock()),
) : QuotaRepository {
    private val state = MutableStateFlow(initial)

    override val accounts: StateFlow<List<AccountState>> = state.asStateFlow()

    override suspend fun refresh(accountId: String?) {
        val now = clock()
        state.update { accounts ->
            accounts.map { account ->
                if (accountId != null && account.account.id != accountId) {
                    account
                } else {
                    account.bumped(now)
                }
            }
        }
    }

    override fun history(accountId: String, windowId: String): Flow<List<UsagePoint>> {
        val window =
            state.value
                .firstOrNull { it.account.id == accountId }
                ?.snapshot
                ?.windows
                ?.firstOrNull { it.id == windowId } ?: return flowOf(emptyList())
        return flowOf(DemoHistory.pointsFor(window, clock()))
    }

    /** Replaces the whole state, for tests that need a specific scenario. */
    public fun set(accounts: List<AccountState>) {
        state.value = accounts
    }

    private fun AccountState.bumped(now: Instant): AccountState {
        val snapshot = snapshot ?: return this
        val windows =
            snapshot.windows.map { window ->
                val step = if (window.kind == WindowKind.Session) SESSION_STEP else WEEKLY_STEP
                window.copy(usedPercent = (window.usedPercent + step).coerceAtMost(MAX_PERCENT))
            }
        return copy(snapshot = snapshot.copy(windows = windows, fetchedAt = now), lastError = null)
    }

    private companion object {
        const val WEEKLY_STEP = 1.0
        const val SESSION_STEP = 4.0
    }
}

/** Plausible usage history for a demo window: roughly steady use with some wobble. */
public object DemoHistory {
    private const val POINTS = 12
    private const val CURVE = 1.12
    private const val WOBBLE = 1.6
    private const val WOBBLE_FREQUENCY = 1.7

    public fun pointsFor(window: QuotaWindow, now: Instant): List<UsagePoint> {
        val resetsAt = window.resetsAt ?: return emptyList()
        val length = window.length ?: return emptyList()
        val start = resetsAt.minus(length)
        if (!now.isAfter(start)) return emptyList()
        val elapsed = java.time.Duration.between(start, now)
        return (0..POINTS).map { index ->
            val fraction = index.toDouble() / POINTS
            val wobble =
                if (index in 1 until POINTS) kotlin.math.sin(index * WOBBLE_FREQUENCY) * WOBBLE
                else 0.0
            UsagePoint(
                at = start.plusMillis((elapsed.toMillis() * fraction).toLong()),
                usedPercent =
                    (window.usedPercent * Math.pow(fraction, CURVE) + wobble).coerceIn(
                        0.0,
                        MAX_PERCENT,
                    ),
            )
        }
    }
}
