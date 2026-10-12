package dev.sebastiano.headroom.model

import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/**
 * An in-memory repository over [DemoData]. Demo mode, UI tests and end-to-end tests use it. Each
 * refresh adds a little usage, so a refresh visibly changes the numbers.
 */
public class FakeQuotaRepository(
    private val clock: () -> Instant = Clock.System::now,
    initial: List<AccountState> = DemoData.accounts(clock()),
) : QuotaRepository {
    private val state = MutableStateFlow(initial)

    /** Resets the provider made on its side, by account id, that the next refresh reads. */
    private val serverResets = MutableStateFlow(emptyMap<String, ResetScope>())

    override val accounts: StateFlow<List<AccountState>> = state.asStateFlow()

    override suspend fun refresh(accountId: String?) {
        val now = clock()
        state.update { accounts ->
            accounts.map { account ->
                val id = account.account.id
                if (accountId != null && id != accountId) return@map account
                val reset = serverResets.getAndUpdate { it - id }[id]
                if (reset != null) account.reset(reset, now) else account.bumped(now)
            }
        }
    }

    /**
     * Simulates the provider resetting [accountId]'s limits in [scope] on its side, as a redeemed
     * reset does. Nothing changes on screen until the next refresh reads it, as with a real
     * provider.
     */
    public fun resetOnServer(accountId: String, scope: ResetScope) {
        serverResets.update { it + (accountId to scope) }
    }

    private fun AccountState.reset(scope: ResetScope, now: Instant): AccountState {
        val snapshot = snapshot ?: return this
        val windows =
            snapshot.windows.map { window ->
                // A reset restores usage limits. Credits and quotas the app does not know stay.
                if (!window.isInformational && scope.covers(window.id, window.kind)) {
                    window.copy(usedPercent = 0.0)
                } else {
                    window
                }
            }
        return copy(snapshot = snapshot.copy(windows = windows, fetchedAt = now), lastError = null)
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
        if (now <= start) return emptyList()
        val elapsed = now - start
        return (0..POINTS).map { index ->
            val fraction = index.toDouble() / POINTS
            val wobble =
                if (index in 1 until POINTS) kotlin.math.sin(index * WOBBLE_FREQUENCY) * WOBBLE
                else 0.0
            UsagePoint(
                at = start + ((elapsed.inWholeMilliseconds * fraction).toLong()).milliseconds,
                usedPercent =
                    (window.usedPercent * fraction.pow(CURVE) + wobble).coerceIn(
                        0.0,
                        MAX_PERCENT,
                    ),
            )
        }
    }
}
